import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import Data.InputData;
import Metaheuristics.GeneticAlgorithm;
import Metaheuristics.MetaHeuristic;
import Solution.GiantTour;



/**
 * Batch entry point: solves every {@code .vrp} instance in a benchmark
 * directory (in ascending size order), looks up each instance's best-known cost
 * from its solution file, and writes the results with the optimality gap to a
 * CSV report.
 *
 * @author Othmane EL YAAKOUBI
 */
public class benchmark {

    /**
     * @param args the command line arguments (unused)
     */
    public static void main(String[] args) {
        
        String benchmarkDirPath = "CVRPLib//QOBLIB";
//        String benchmarkDirPath = "CVRPLib//A";
//        String benchmarkDirPath = "CVRPLib//B";
        
        File dir = new File(benchmarkDirPath);
        File[] files = dir.listFiles();
        if (files == null) {
            System.err.println("Directory not found or empty: " + dir.getAbsolutePath());
            return;
        }
        // Output CSV
        String outputFile = "results " + benchmarkDirPath.replace("//", ".") +".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Header
            writer.println("File Name,Dimension,Best Solution Reach Time(ms),Cost Value,Known Optimal,Gap(%)");

            // Build and sort by StopsCount
            Map<String, InputData> datasets = Arrays.stream(files)
                                                    .filter(file -> file.getName().endsWith(".vrp"))
                                                    .parallel()
                                                    .map(file -> {
                                                        try {
                                                            return new InputData(benchmarkDirPath + "//" + file.getName());
                                                        } catch (IOException e) {
                                                            System.err.println("Error reading file: " + file.getAbsolutePath());
                                                            return null;
                                                        }
                                                    })
                                                    .collect(Collectors.toMap(data -> new File(data.FileName).getName().replaceFirst("\\.vrp$", ""), data -> data));
            Map<String, Double> bestKnownMap = datasets.keySet()
                                                        .stream()
                                                        .collect(Collectors.toMap(
                                                            name -> name,
                                                            name -> {
                                                                File file = new File(benchmarkDirPath + "//" + name + ".sol");
                                                                if (!file.exists()) {
                                                                    file = new File(benchmarkDirPath + "//" + name + ".opt.sol");
                                                                    if (!file.exists()) 
                                                                        file = new File(benchmarkDirPath + "//" + name + ".bst.sol");
                                                                }
                                                                try (BufferedReader br = Files.newBufferedReader(file.toPath())) {
                                                                    String line;
                                                                    while ((line = br.readLine()) != null) {
                                                                        line = line.trim();
                                                                        if (line.startsWith("Cost")) {
                                                                            String[] parts = line.split(" ");
                                                                            if (parts.length == 2) 
                                                                                return Double.valueOf(parts[1].trim());
                                                                        }
                                                                    }
                                                                } catch (IOException e) {
                                                                    System.err.println("Error reading solution file: " + file.getAbsolutePath());
                                                                }
                                                                return Double.NaN;
                                                            }
                                                        ));

            // Process in sorted order
            datasets.entrySet()
                    .stream()
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().getDimension()))
                    .forEach(entry -> {
                        InputData data = entry.getValue();
                        MetaHeuristic algorithm = new GeneticAlgorithm(data);
                        algorithm.Run();
                        if (algorithm.isFeasible()) {
                            GiantTour gt = algorithm.getBestGiantTour();
                            System.out.println(gt);
                            try {
                                gt.export(data);
                            } catch (IOException e) {
                                System.err.println("Error exporting solution: " + e.getMessage());
                            }
                            System.out.println("\nEnd Time = " + algorithm.getRunningTime() + " ms\n");

                            // Print/display solution
                            long end_time = algorithm.getRunningTime();
                            // Lookup best known
                            Double best = bestKnownMap.getOrDefault(entry.getKey(), Double.NaN);

                            // Compute gap
                            String gapStr = "NA";
                            if (!best.isNaN()) {
                                double gap = (gt.getFitness() - best) / best;
                                gapStr = String.format(Locale.US, "%.2f", gap  * 100d);
                            }
                            // Write result to CSV
                            writer.printf(Locale.US, "%s,%s,%s,%s,%s,%s\n", entry.getKey(), data.getDimension(), end_time, gt.getFitness(), Double.toString(best), gapStr);
                        }
                        else {
                            System.out.println("No feasible solution found for " + data.FileName);
                            System.out.println();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error writing results: " + e.getMessage());
        }
        System.out.println("All results stored in \"" + outputFile + "\"");
    }
}
