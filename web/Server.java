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
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
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
    // ponytail: one solve at a time — System.out is redirected globally to the
    // SSE stream while solving. Per-session isolation only if concurrency matters.
    private static final Object SOLVE_LOCK = new Object();
    // The solver currently running, so /api/stop can ask it to stop early.
    private static volatile GeneticAlgorithm currentAlgo;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : envPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
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

    /** Reads PORT from the .env file at the project root; defaults to 8080. */
    private static int envPort() {
        File env = new File(".env");
        if (env.exists()) try {
            for (String line : Files.readAllLines(env.toPath())) {
                String t = line.trim();
                if (t.startsWith("PORT=")) return Integer.parseInt(t.substring(5).trim());
            }
        } catch (IOException | NumberFormatException ignored) {}
        return 8080;
    }

    /* ---------------- endpoints ---------------- */

    private static void serveIndex(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) { send(ex, 404, "text/plain", "Not found".getBytes()); return; }
        serveFile(ex, new File("Web/index.html"), "text/html; charset=utf-8");
    }

    private static void folders(HttpExchange ex) throws IOException {
        File[] dirs = CVRP_DIR.listFiles(File::isDirectory);
        List<String> names = dirs == null ? List.of()
                : Arrays.stream(dirs).map(File::getName).sorted().collect(Collectors.toList());
        send(ex, 200, "application/json", jsonStringArray(names).getBytes(StandardCharsets.UTF_8));
    }

    private static void instances(HttpExchange ex) throws IOException {
        String folder = safeName(query(ex).get("folder"));
        File dir = new File(CVRP_DIR, folder);
        String[] files = dir.list((d, n) -> n.endsWith(".vrp"));
        List<String> names = files == null ? List.of()
                : Arrays.stream(files).map(n -> n.substring(0, n.length() - 4)).sorted().collect(Collectors.toList());
        send(ex, 200, "application/json", jsonStringArray(names).getBytes(StandardCharsets.UTF_8));
    }

    private static void vrp(HttpExchange ex) throws IOException {
        File f = vrpFile(query(ex));
        if (f == null || !f.exists()) { send(ex, 404, "text/plain", "Not found".getBytes()); return; }
        serveFile(ex, f, "text/plain; charset=utf-8");
    }

    /** SSE: streams the live solver log, then a final {@code result} event with cost/time/routes. */
    private static void solve(HttpExchange ex) throws IOException {
        File f = vrpFile(query(ex));
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        OutputStream out = ex.getResponseBody();
        if (f == null || !f.exists()) { sse(out, "log", "Instance not found"); out.close(); return; }

        synchronized (SOLVE_LOCK) {
            PrintStream original = System.out;
            System.setOut(new PrintStream(new SseLineStream(out), true, StandardCharsets.UTF_8));
            try {
                InputData data = new InputData(f.getPath().replace("\\", "//"));
                GeneticAlgorithm algo = new GeneticAlgorithm(data);
                currentAlgo = algo;
                algo.Run();
                System.setOut(original);
                if (algo.isFeasible()) {
                    GiantTour gt = algo.getBestGiantTour();
                    gt.export(data); // writes Output/<instance>/... and lets us read routes back
                    sse(out, "sol", solOf(data, gt));
                    sse(out, "result", resultJson(true, (int) gt.getFitness(), algo.getRunningTime(), routesOf(data, gt), optimalOf(f)));
                } else {
                    sse(out, "result", resultJson(false, 0, 0, "[]", Double.NaN));
                }
            } catch (Exception e) {
                System.setOut(original);
                sse(out, "log", "ERROR: " + e.getMessage());
                sse(out, "result", resultJson(false, 0, 0, "[]", Double.NaN));
            } finally {
                currentAlgo = null;
                out.close();
            }
        }
    }

    /** Asks the running solve to stop early; it then returns the best tour found so far. */
    private static void stop(HttpExchange ex) throws IOException {
        GeneticAlgorithm a = currentAlgo;
        if (a != null) a.requestStop();
        send(ex, 200, "text/plain", "stopping".getBytes());
    }

    /* ---------------- helpers ---------------- */

    /** Reads the just-exported .sol file and returns its raw content. */
    private static String solOf(InputData data, GiantTour gt) throws IOException {
        String name = new File(data.FileName).getName().replaceFirst("\\.vrp$", "");
        File dir = new File("Output", name);
        File sol = new File(dir, "Instance = " + name + " Cost = " + (int) gt.getFitness() + ".sol");
        return Files.readString(sol.toPath(), StandardCharsets.UTF_8);
    }

    /** Reads back the just-exported .sol file and returns routes as a JSON array of node-id arrays. */
    private static String routesOf(InputData data, GiantTour gt) throws IOException {
        String name = new File(data.FileName).getName().replaceFirst("\\.vrp$", "");
        File dir = new File("Output", name);
        File sol = new File(dir, "Instance = " + name + " Cost = " + (int) gt.getFitness() + ".sol");
        List<String> routes = new ArrayList<>();
        for (String line : Files.readAllLines(sol.toPath())) {
            int colon = line.indexOf(':');
            if (line.startsWith("Route") && colon >= 0) {
                String ids = Arrays.stream(line.substring(colon + 1).trim().split("\\s+"))
                        .filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
                routes.add("[" + ids + "]");
            }
        }
        return "[" + String.join(",", routes) + "]";
    }

    private static String resultJson(boolean feasible, int cost, long ms, String routes, double optimal) {
        String opt = Double.isNaN(optimal) ? "null" : Double.toString(optimal);
        String gap = Double.isNaN(optimal) || optimal == 0 ? "null"
                : String.format(Locale.US, "%.2f", (cost - optimal) / optimal * 100d);
        return "{\"feasible\":" + feasible + ",\"cost\":" + cost + ",\"timeMs\":" + ms
                + ",\"optimal\":" + opt + ",\"gap\":" + gap + ",\"routes\":" + routes + "}";
    }

    /** Reads the known-optimal cost from the CVRPLIB {@code .sol/.opt.sol/.bst.sol} sibling of the instance. */
    private static double optimalOf(File vrp) {
        String base = vrp.getPath().replaceFirst("\\.vrp$", "");
        for (String ext : new String[]{".sol", ".opt.sol", ".bst.sol"}) {
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
            } catch (IOException | NumberFormatException ignored) {}
        }
        return Double.NaN;
    }

    private static File vrpFile(Map<String, String> q) {
        String folder = safeName(q.get("folder"));
        String file = safeName(q.get("file"));
        if (folder == null || file == null) return null;
        return new File(new File(CVRP_DIR, folder), file + ".vrp");
    }

    /** Trust boundary: reject anything but a plain file/dir name (no traversal). */
    private static String safeName(String s) {
        if (s == null || s.isEmpty() || s.contains("..") || s.contains("/") || s.contains("\\")) return null;
        return s;
    }

    private static Map<String, String> query(HttpExchange ex) {
        String raw = ex.getRequestURI().getRawQuery();
        Map<String, String> m = new java.util.HashMap<>();
        if (raw != null) for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) m.put(p.substring(0, i), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static String jsonStringArray(List<String> items) {
        return "[" + items.stream().map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private static void sse(OutputStream out, String event, String data) throws IOException {
        StringBuilder sb = new StringBuilder("event: ").append(event).append('\n');
        for (String line : data.split("\n", -1)) sb.append("data: ").append(line).append('\n');
        sb.append('\n');
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void serveFile(HttpExchange ex, File f, String type) throws IOException {
        if (!f.exists()) { send(ex, 404, "text/plain", "Not found".getBytes()); return; }
        send(ex, 200, type, Files.readAllBytes(f.toPath()));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    /** Buffers bytes written to System.out into lines and emits each as an SSE {@code log} event. */
    private static final class SseLineStream extends OutputStream {
        private final OutputStream sink;
        private final StringBuilder buf = new StringBuilder();
        SseLineStream(OutputStream sink) { this.sink = sink; }
        @Override public synchronized void write(int b) throws IOException {
            if (b == '\n') { flushLine(); } else if (b != '\r') { buf.append((char) b); }
        }
        private void flushLine() throws IOException {
            sse(sink, "log", buf.toString());
            buf.setLength(0);
        }
    }
}
