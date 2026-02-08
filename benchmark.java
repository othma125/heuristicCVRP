import Data.InputData;
import Metaheuristics.*;
import Solution.GiantTour;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Comparator;



/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Othmane
 */
public class benchmark {

    /**
     * @param args the command line arguments
     */

    public static void main(String[] args) {
        String benchmarkDirPath = "CVRPLib//A";
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
                                                                try (BufferedReader br = Files.newBufferedReader(file.toPath())) {
                                                                    String line;
                                                                    while ((line = br.readLine()) != null) {
                                                                        line = line.trim();
                                                                        if (line.startsWith("Cost")) {
                                                                            String[] parts = line.split(" ");
                                                                            if (parts.length == 2) 
                                                                                return Double.parseDouble(parts[1].trim());
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
//                            System.out.println(gt);
                            // gt.export(data);
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
                        else 
                            System.out.println("No feasible solution found for " + data.FileName);
                    });
        } catch (IOException e) {
            System.err.println("Error writing results: " + e.getMessage());
        }
        System.out.println("All results stored in \"" + outputFile + "\"");
    }

    // Helper: read tsplib_best_known.csv into a map
    private static Map<String, String> loadBestKnown(String csvPath) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(csvPath))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading best known CSV: " + e.getMessage());
        }
        return map;
    }

    // Helper: strip extension like .tsp or .txt
    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }
}