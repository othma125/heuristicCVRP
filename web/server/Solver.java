// Author: Othmane

package web.server;

import Algorithm.Data.InputData;
import Algorithm.Metaheuristics.GeneticAlgorithm;
import Algorithm.Solution.GiantTour;

import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The solving endpoints. {@link #solve} runs the genetic algorithm on the
 * requested instance and streams its log live over Server-Sent Events, closing
 * with a {@code result} event carrying cost, time, gap, and routes;
 * {@link #stop} asks the run in progress to finish early.
 *
 * <p>Both methods match {@code HttpHandler}, so the server registers them as
 * method references. The run in progress is instance state, which is why this
 * class is instantiated rather than static.
 *
 * @author Othmane EL YAAKOUBI
 */
final class Solver {

    private static final File OUTPUT_DIR = new File("Output");

    // ponytail: one solve at a time — System.out is redirected globally to the
    // SSE stream while solving. Per-session isolation only if concurrency matters.
    private final Object lock = new Object();
    /** The solver currently running, so {@link #stop} can ask it to stop early. */
    private volatile GeneticAlgorithm current;

    /**
     * SSE endpoint: streams the live solver log, then a final {@code result}
     * event.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing SSE events fails
     */
    void solve(HttpExchange ex) throws IOException {
        File instance = Instances.vrpFile(Http.query(ex));

        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);

        OutputStream out = ex.getResponseBody();
        if (instance == null || !instance.exists()) {
            Http.sse(out, "log", "Instance not found");
            out.close();
            return;
        }

        synchronized (this.lock) {
            run(instance, out);
        }
    }

    /**
     * Asks the running solve to stop early.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    void stop(HttpExchange ex) throws IOException {
        GeneticAlgorithm algorithm = this.current;
        if (algorithm != null)
            algorithm.requestStop();
        Http.send(ex, 200, "text/plain", "stopping".getBytes(StandardCharsets.UTF_8));
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
    private void run(File instance, OutputStream out) throws IOException {
        PrintStream original = System.out;
        System.setOut(new PrintStream(new SseLineStream(out), true, StandardCharsets.UTF_8));
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
        // The solver only prints on improvement, so it can run silent for minutes and never
        // notice a closed tab. Ping instead: a failed write means nobody is listening.
        watchdog.scheduleWithFixedDelay(() -> {
            try {
                Http.sse(out, "ping", "");
            } catch (IOException hungUp) {
                GeneticAlgorithm algorithm = this.current;
                if (algorithm != null)
                    algorithm.requestStop();
            }
        }, 5, 5, TimeUnit.SECONDS);
        try {
            InputData data = new InputData(instance.getPath().replace("\\", "//"));
            GeneticAlgorithm algo = new GeneticAlgorithm(data);
            this.current = algo;
            algo.Run();
            System.setOut(original);

            if (algo.isFeasible()) {
                GiantTour gt = algo.getBestGiantTour();
                gt.export(data); // writes Output/<instance>/... and lets us read routes back
                Http.sse(out, "sol", solOf(data, gt));
                Http.sse(out, "result", resultJson(true, (int) gt.getFitness(), algo.getRunningTime(),
                        routesOf(data, gt), Instances.optimalOf(instance)));
            } else {
                Http.sse(out, "result", resultJson(false, 0, 0, "[]", Double.NaN));
            }
        } catch (Exception e) {
            System.setOut(original);
            Http.sse(out, "log", "ERROR: " + e.getMessage());
            Http.sse(out, "result", resultJson(false, 0, 0, "[]", Double.NaN));
        } finally {
            watchdog.shutdownNow();
            this.current = null;
            out.close();
        }
    }

    /* ---------------- reading back the exported solution ---------------- */

    /**
     * Reads the just-exported {@code .sol} file and returns its raw content.
     *
     * @param data the VRP input data
     * @param gt the best giant tour found by the solver
     * @return the raw solution file content
     * @throws IOException when reading the solution file fails
     */
    private static String solOf(InputData data, GiantTour gt) throws IOException {
        return Files.readString(solutionFile(data, gt.getFitness()).toPath(), StandardCharsets.UTF_8);
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
            Http.sse(sink, "log", buf.toString());
            buf.setLength(0);
        }
    }
}
