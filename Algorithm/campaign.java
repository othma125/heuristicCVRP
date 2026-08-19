// Author: Othmane

import Algorithm.Data.InputData;
import Algorithm.Metaheuristics.GeneticAlgorithm;
import Algorithm.Metaheuristics.MetaHeuristic;
import Algorithm.Solution.GiantTour;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Loop-until-feasible benchmark campaign with incumbent-trace recording.
 *
 * For every instance it performs RUNS independent runs; each run restarts the GA
 * from a fresh random population until it returns a feasible solution (capped at
 * MAX_ATTEMPTS). Times are wall-clock from the meta-run start, so they include
 * failed-attempt retries -- the trace of the successful attempt is offset by the
 * time already burnt on failures, which keeps the last trace point equal to the
 * reported time-to-solution.
 *
 * Writes per-instance {@code <out>/<instance>/trace.json} and a campaign CSV.
 */
public class campaign {

    static final int RUNS = 5;
    static final int MAX_ATTEMPTS = 100;
    static final String INSTANCE_DIR = "Algorithm/CVRPLib/QOBLIB";
    static final String OUT_DIR = "campaign_out";

    /** One completed run: the winning attempt's trace plus wall-clock accounting. */
    record RunResult(int cost, long wallMs, long ttsMs, int attempts, List<long[]> trace) {}

    public static void main(String[] args) throws Exception {
        File[] vrps = new File(INSTANCE_DIR).listFiles(f -> f.getName().endsWith(".vrp"));
        if (vrps == null || vrps.length == 0) {
            System.err.println("No instances in " + INSTANCE_DIR);
            return;
        }
        Arrays.sort(vrps);
        new File(OUT_DIR).mkdirs();

        PrintWriter csv = new PrintWriter(new File(OUT_DIR, "campaign_summary.csv"));
        csv.println("Instance,BestCost,KnownOptimal,Gap(%),BestRunWallMs,BestRunTtsMs,MeanWallMs,MeanAttempts,MinAttempts,MaxAttempts,Runs,FeasibleRuns");
        csv.flush();

        long campaignStart = System.currentTimeMillis();
        for (int i = 0; i < vrps.length; i++) {
            String name = vrps[i].getName().replaceFirst("\\.vrp$", "");
            List<RunResult> results = new ArrayList<>();
            GiantTour bestTour = null;
            InputData bestData = null;
            int bestCost = Integer.MAX_VALUE;

            for (int r = 0; r < RUNS; r++) {
                long metaStart = System.currentTimeMillis();
                int attempts = 0;
                MetaHeuristic algo = null;
                InputData data = null;
                long offset = 0;
                while (attempts < MAX_ATTEMPTS) {
                    offset = System.currentTimeMillis() - metaStart;
                    InputData prevData = data;
                    data = new InputData(vrps[i].getPath());
                    if (prevData != null) prevData.close();
                    algo = new GeneticAlgorithm(data);
                    try {
                        algo.Run();
                    } catch (RuntimeException e) {
                        // A crashed attempt counts as a failed one; keep the campaign alive.
                        System.out.println("!! " + name + " run " + (r + 1) + " attempt " + (attempts + 1) + " crashed: " + e);
                        algo = null;
                        attempts++;
                        continue;
                    }
                    attempts++;
                    if (algo.isFeasible()) break;
                }
                if (algo == null || !algo.isFeasible()) {
                    System.out.println("!! " + name + " run " + (r + 1) + " hit the attempt cap with no feasible solution");
                    continue;
                }
                long wall = System.currentTimeMillis() - metaStart;
                List<long[]> trace = new ArrayList<>();
                for (long[] p : algo.Trace) trace.add(new long[]{p[0] + offset, p[1]});
                long tts = trace.isEmpty() ? wall : trace.get(trace.size() - 1)[0];
                int cost = (int) algo.getBestGiantTour().getFitness();
                results.add(new RunResult(cost, wall, tts, attempts, trace));
                if (cost < bestCost) {
                    bestCost = cost;
                    bestTour = algo.getBestGiantTour();
                    bestData = data;
                }
                // Close the InputData from this run
                data.close();
            }

            if (results.isEmpty()) {
                System.out.println("!! " + name + ": no feasible run");
                progress(i + 1, vrps.length, name, -1, campaignStart);
                continue;
            }

            File instOut = new File(OUT_DIR, name);
            instOut.mkdirs();
            bestTour.export(bestData);          // Output/<instance>/Instance = <name> Cost = N.sol
            writeTrace(new File(instOut, "objective_time_series.json"), results);

            double known = knownOptimal(name);
            RunResult best = results.stream().min((a, b) -> Integer.compare(a.cost(), b.cost())).get();
            double meanWall = results.stream().mapToLong(RunResult::wallMs).average().orElse(0);
            double meanAtt = results.stream().mapToInt(RunResult::attempts).average().orElse(0);
            int minAtt = results.stream().mapToInt(RunResult::attempts).min().orElse(0);
            int maxAtt = results.stream().mapToInt(RunResult::attempts).max().orElse(0);
            String gap = Double.isNaN(known) ? "NA"
                    : String.format(Locale.US, "%.2f", (best.cost() - known) / known * 100d);

            csv.printf(Locale.US, "%s,%d,%s,%s,%d,%d,%.1f,%.2f,%d,%d,%d,%d%n",
                    name, best.cost(), Double.isNaN(known) ? "NA" : String.valueOf((int) known), gap,
                    best.wallMs(), best.ttsMs(), meanWall, meanAtt, minAtt, maxAtt, RUNS, results.size());
            csv.flush();

            // Close the best InputData instance
            if (bestData != null) bestData.close();

            progress(i + 1, vrps.length, name, best.cost(), campaignStart);
        }
        csv.close();
        System.out.println("DONE in " + (System.currentTimeMillis() - campaignStart) / 1000 + "s");
    }

    /** Appends a machine-readable heartbeat line consumed by the progress watcher. */
    static void progress(int done, int total, String name, int cost, long start) throws IOException {
        long elapsed = (System.currentTimeMillis() - start) / 1000;
        String line = String.format(Locale.US, "%d/%d %s cost=%s elapsed=%ds%n",
                done, total, name, cost < 0 ? "INFEASIBLE" : String.valueOf(cost), elapsed);
        Files.writeString(Path.of(OUT_DIR, "progress.log"), line,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        System.out.print("PROGRESS " + line);
        System.out.flush();
    }

    /** Reads the {@code Cost N} line from the instance's reference solution. */
    static double knownOptimal(String name) {
        for (String suffix : new String[]{".sol", ".opt.sol", ".bst.sol"}) {
            File f = new File(INSTANCE_DIR, name + suffix);
            if (!f.exists()) continue;
            try {
                for (String line : Files.readAllLines(f.toPath())) {
                    line = line.trim();
                    if (line.startsWith("Cost")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length == 2) return Double.parseDouble(parts[1]);
                    }
                }
            } catch (IOException ignored) {}
        }
        return Double.NaN;
    }

    /** Writes runs as a JSON array of arrays of {Time (s), Incumbent, step}. */
    static void writeTrace(File f, List<RunResult> results) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (int r = 0; r < results.size(); r++) {
            if (r > 0) sb.append(",");
            sb.append("\n [");
            List<long[]> trace = results.get(r).trace();
            for (int i = 0; i < trace.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(String.format(Locale.US, "\n  {\"Time\": %.3f, \"Incumbent\": %d, \"step\": %d}",
                        trace.get(i)[0] / 1000d, trace.get(i)[1], i + 1));
            }
            sb.append("\n ]");
        }
        sb.append("\n]\n");
        Files.writeString(f.toPath(), sb.toString());
    }
}
