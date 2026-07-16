// Author: Othmane

package web;

import Algorithm.Data.InputData;
import Algorithm.Metaheuristics.GeneticAlgorithm;
import Algorithm.Solution.GiantTour;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Minimal landing-page backend built on the JDK's {@link HttpServer} (no
 * dependencies). Serves {@code web/index.html}, lists CVRPLIB instances, streams
 * the solver log live over Server-Sent Events, and returns the best routes for
 * in-browser visualization.
 *
 * @author Othmane EL YAAKOUBI
 */
public class Server {

    private static final File CVRP_DIR = new File("Algorithm/CVRPLib");
    private static final File OUTPUT_DIR = new File("Output");
    private static final String VRP_EXT = ".vrp";
    private static final String[] SOLUTION_EXTS = {".sol", ".opt.sol", ".bst.sol"};

    // ponytail: one solve at a time — System.out is redirected globally to the
    // SSE stream while solving. Per-session isolation only if concurrency matters.
    private static final Object SOLVE_LOCK = new Object();
    // The solver currently running, so /api/stop can ask it to stop early.
    private static volatile GeneticAlgorithm currentAlgo;

    /**
     * Starts the HTTP server on the port supplied as the first argument, or on the
     * port read from {@code .env} (default 8081), and registers all API contexts.
     *
     * @param args optional port number
     * @throws IOException if the server socket cannot be created
     */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : envPort();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use. Stop the running server"
                    + " (bash kill-server.sh) or pick another port: java -cp out web.Server 9090");
            System.exit(1);
            return;
        }
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", Server::serveIndex);
        server.createContext("/app.js", ex -> serveFile(ex, new File("Web/app.js"), "text/javascript; charset=utf-8"));
        server.createContext("/styles.css", ex -> serveFile(ex, new File("Web/styles.css"), "text/css; charset=utf-8"));
        server.createContext("/assets/profile.jpg", ex -> serveFile(ex, new File("profile.jpg"), "image/jpeg"));
        server.createContext("/api/folders", Server::folders);
        server.createContext("/api/instances", Server::instances);
        server.createContext("/api/vrp", Server::vrp);
        server.createContext("/api/solve", Server::solve);
        server.createContext("/api/stop", Server::stop);

        server.start();
        System.out.println("Landing page ready -> http://localhost:" + port);
    }

    /** Reads PORT from the .env file at the project root; defaults to 8081. */
    private static int envPort() {
        File env = new File(".env");
        if (env.exists()) {
            try {
                for (String line : Files.readAllLines(env.toPath())) {
                    String t = line.trim();
                    if (t.startsWith("PORT=")) return Integer.parseInt(t.substring(5).trim());
                }
            } catch (IOException | NumberFormatException ignored) {
                // Fall back to the default port.
            }
        }
        return 8081;
    }

    /* ---------------- endpoints ---------------- */

    /**
     * Serves the landing page ({@code Web/index.html}) for the root path only.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    private static void serveIndex(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, "text/plain", "Not found".getBytes());
            return;
        }
        serveFile(ex, new File("Web/index.html"), "text/html; charset=utf-8");
    }

    /**
     * Returns the list of CVRPLIB subdirectories as a JSON string array.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    private static void folders(HttpExchange ex) throws IOException {
        File[] dirs = CVRP_DIR.listFiles(File::isDirectory);
        List<String> names = dirs == null ? List.of()
                : Arrays.stream(dirs).map(File::getName).sorted().collect(Collectors.toList());
        send(ex, 200, "application/json", jsonStringArray(names).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the list of {@code .vrp} files inside the requested folder as a
     * JSON string array (without the extension).
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    private static void instances(HttpExchange ex) throws IOException {
        String folder = safeName(query(ex).get("folder"));
        File dir = new File(CVRP_DIR, folder);
        String[] files = dir.list((d, n) -> n.endsWith(VRP_EXT));
        List<String> names = files == null ? List.of()
                : Arrays.stream(files).map(n -> n.substring(0, n.length() - VRP_EXT.length()))
                        .sorted().collect(Collectors.toList());
        send(ex, 200, "application/json", jsonStringArray(names).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Streams the raw {@code .vrp} instance file for the requested folder/file.
     *
     * @param ex the HTTP exchange
     * @throws IOException when reading the file or writing the response fails
     */
    private static void vrp(HttpExchange ex) throws IOException {
        File f = vrpFile(query(ex));
        if (f == null || !f.exists()) {
            send(ex, 404, "text/plain", "Not found".getBytes());
            return;
        }
        serveFile(ex, f, "text/plain; charset=utf-8");
    }

    /** SSE: streams the live solver log, then a final {@code result} event with cost/time/routes. */
    private static void solve(HttpExchange ex) throws IOException {
        File instance = vrpFile(query(ex));

        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);

        OutputStream out = ex.getResponseBody();
        if (instance == null || !instance.exists()) {
            sse(out, "log", "Instance not found");
            out.close();
            return;
        }

        synchronized (SOLVE_LOCK) {
            solveLocked(instance, out);
        }
    }

    /**
     * Runs the genetic algorithm for the given instance while redirecting the
     * process standard output to the SSE stream. Sends the final {@code result}
     * event when the run completes or aborts.
     *
     * @param instance the VRP instance file to solve
     * @param out the output stream for the SSE connection
     * @throws IOException when writing SSE events fails
     */
    private static void solveLocked(File instance, OutputStream out) throws IOException {
        PrintStream original = System.out;
        System.setOut(new PrintStream(new SseLineStream(out), true, StandardCharsets.UTF_8));
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
        // The solver only prints on improvement, so it can run silent for minutes and never
        // notice a closed tab. Ping instead: a failed write means nobody is listening.
        watchdog.scheduleWithFixedDelay(() -> {
            try {
                sse(out, "ping", "");
            } catch (IOException hungUp) {
                GeneticAlgorithm algorithm = currentAlgo;
                if (algorithm != null)
                    algorithm.requestStop();
            }
        }, 5, 5, TimeUnit.SECONDS);
        try {
            InputData data = new InputData(instance.getPath().replace("\\", "//"));
            GeneticAlgorithm algo = new GeneticAlgorithm(data);
            currentAlgo = algo;
            algo.Run();
            System.setOut(original);

            if (algo.isFeasible()) {
                GiantTour gt = algo.getBestGiantTour();
                gt.export(data); // writes Output/<instance>/... and lets us read routes back
                sse(out, "sol", solOf(data, gt));
                sse(out, "result", resultJson(true, (int) gt.getFitness(), algo.getRunningTime(),
                        routesOf(data, gt), optimalOf(instance)));
            } else {
                sse(out, "result", resultJson(false, 0, 0, "[]", Double.NaN));
            }
        } catch (Exception e) {
            System.setOut(original);
            sse(out, "log", "ERROR: " + e.getMessage());
            sse(out, "result", resultJson(false, 0, 0, "[]", Double.NaN));
        } finally {
            watchdog.shutdownNow();
            currentAlgo = null;
            out.close();
        }
    }

    /**
     * Asks the running solve to stop early.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    private static void stop(HttpExchange ex) throws IOException {
        GeneticAlgorithm algorithm = currentAlgo;
        if (algorithm != null)
            algorithm.requestStop();
        send(ex, 200, "text/plain", "stopping".getBytes());
    }

    /* ---------------- helpers ---------------- */

    /**
     * Reads the just-exported {@code .sol} file and returns its raw content.
     *
     * @param data the VRP input data
     * @param gt the best giant tour found by the solver
     * @return the raw solution file content
     * @throws IOException when reading the solution file fails
     */
    private static String solOf(InputData data, GiantTour gt) throws IOException {
        File sol = solutionFile(data, gt.getFitness());
        return Files.readString(sol.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * Builds the path of the exported solution file for the given instance and cost.
     *
     * @param data the VRP input data
     * @param fitness the cost of the best tour
     * @return the solution file handle
     */
    private static File solutionFile(InputData data, double fitness) {
        String name = new File(data.FileName).getName().replaceFirst("\\.vrp$", "");
        File dir = new File(OUTPUT_DIR, name);
        return new File(dir, "Instance = " + name + " Cost = " + (int) fitness + ".sol");
    }

    /**
     * Reads the just-exported {@code .sol} file and returns the routes as a
     * JSON array of node-id arrays.
     *
     * @param data the VRP input data
     * @param gt the best giant tour found by the solver
     * @return a JSON array of route arrays
     * @throws IOException when reading the solution file fails
     */
    private static String routesOf(InputData data, GiantTour gt) throws IOException {
        File sol = solutionFile(data, gt.getFitness());
        List<String> routes = new ArrayList<>();
        for (String line : Files.readAllLines(sol.toPath())) {
            int colon = line.indexOf(':');
            if (line.startsWith("Route") && colon >= 0) {
                String ids = Arrays.stream(line.substring(colon + 1).trim().split("\\s+"))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(","));
                routes.add("[" + ids + "]");
            }
        }
        return "[" + String.join(",", routes) + "]";
    }

    /**
     * Builds the JSON payload for the final {@code result} SSE event.
     *
     * @param feasible whether the solver found a feasible solution
     * @param cost the cost of the best solution
     * @param ms the running time in milliseconds
     * @param routes the JSON array of routes
     * @param optimal the known optimal cost, or {@code NaN} if unknown
     * @return the JSON string for the result event
     */
    private static String resultJson(boolean feasible, int cost, long ms, String routes, double optimal) {
        String opt = Double.isNaN(optimal) ? "null" : Double.toString(optimal);
        String gap = Double.isNaN(optimal) || optimal == 0
                ? "null"
                : String.format(Locale.US, "%.2f", (cost - optimal) / optimal * 100d);
        return "{\"feasible\":" + feasible
                + ",\"cost\":" + cost
                + ",\"timeMs\":" + ms
                + ",\"optimal\":" + opt
                + ",\"gap\":" + gap
                + ",\"routes\":" + routes + "}";
    }

    /** Reads the known-optimal cost from the CVRPLIB {@code .sol/.opt.sol/.bst.sol} sibling of the instance. */
    private static double optimalOf(File vrp) {
        String base = vrp.getPath().replaceFirst("\\.vrp$", "");
        for (String ext : SOLUTION_EXTS) {
            File sol = new File(base + ext);
            if (!sol.exists()) continue;
            try {
                for (String line : Files.readAllLines(sol.toPath())) {
                    String t = line.trim();
                    if (t.startsWith("Cost")) {
                        String[] parts = t.split("\\s+");
                        if (parts.length == 2) return Double.parseDouble(parts[1]);
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // Try the next candidate file.
            }
        }
        return Double.NaN;
    }

    /**
     * Resolves the requested folder/file query parameters into a {@code .vrp}
     * file, or returns {@code null} if the parameters are missing or unsafe.
     *
     * @param query the parsed query parameters
     * @return the VRP file, or {@code null} if not resolvable
     */
    private static File vrpFile(Map<String, String> query) {
        String folder = safeName(query.get("folder"));
        String file = safeName(query.get("file"));
        if (folder == null || file == null) return null;
        return new File(new File(CVRP_DIR, folder), file + VRP_EXT);
    }

    /** Trust boundary: reject anything but a plain file/dir name (no traversal). */
    private static String safeName(String name) {
        if (name == null || name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        return name;
    }

    /**
     * Parses the query string of the request into a key-value map.
     *
     * @param exchange the HTTP exchange
     * @return the parsed query parameters
     */
    private static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = new HashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    params.put(pair.substring(0, i), URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return params;
    }

    /**
     * Builds a JSON string array from the given strings, escaping quotes and
     * backslashes.
     *
     * @param items the strings to encode
     * @return a JSON string array
     */
    private static String jsonStringArray(List<String> items) {
        return "[" + items.stream()
                .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Writes a single Server-Sent Event to the output stream.
     *
     * @param out the output stream
     * @param event the event name
     * @param data the event data, possibly multi-line
     * @throws IOException when writing fails
     */
    private static void sse(OutputStream out, String event, String data) throws IOException {
        StringBuilder sb = new StringBuilder("event: ").append(event).append('\n');
        for (String line : data.split("\n", -1))
            sb.append("data: ").append(line).append('\n');
        sb.append('\n');
        // Locked so the watchdog's ping cannot interleave with a solver log line.
        synchronized (out) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /**
     * Sends a file as the response body, or a 404 if the file does not exist.
     *
     * @param ex the HTTP exchange
     * @param f the file to serve
     * @param type the MIME type to set
     * @throws IOException when reading the file or writing the response fails
     */
    private static void serveFile(HttpExchange ex, File f, String type) throws IOException {
        if (f.exists()) {
            send(ex, 200, type, Files.readAllBytes(f.toPath()));
            return;
        }
        send(ex, 404, "text/plain", "Not found".getBytes());
    }

    /**
     * Sends an HTTP response with the given status, content type, and body.
     *
     * @param ex the HTTP exchange
     * @param code the HTTP status code
     * @param type the content type
     * @param body the response body
     * @throws IOException when writing the response fails
     */
    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    /** Buffers bytes written to System.out into lines and emits each as an SSE {@code log} event. */
    private static final class SseLineStream extends OutputStream {
        private final OutputStream sink;
        private final StringBuilder buf = new StringBuilder();

        /**
         * Creates a new line buffer writing to the provided sink.
         *
         * @param sink the output stream receiving SSE events
         */
        SseLineStream(OutputStream sink) {
            this.sink = sink;
        }

        /**
         * Buffers a single byte; flushes a completed line on a newline.
         *
         * @param b the byte to write
         * @throws IOException when flushing a line fails
         */
        @Override public synchronized void write(int b) throws IOException {
            if (b == '\n') 
                flushLine();
            else if (b != '\r') 
                buf.append((char) b);
        }
        /**
         * Emits the buffered line as an SSE {@code log} event and clears the buffer.
         *
         * @throws IOException when writing the event fails
         */
        private void flushLine() throws IOException {
            sse(sink, "log", buf.toString());
            buf.setLength(0);
        }
    }
}
