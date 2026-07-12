// Author: Othmane

package Algorithm.Data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CVRPLIB instance parser and distance provider.
 *
 * <p>Parses the {@code DIMENSION}, {@code CAPACITY}, {@code NODE_COORD_SECTION},
 * {@code DEMAND_SECTION} and {@code DEPOT_SECTION} sections of a {@code .vrp}
 * file, and exposes rounded Euclidean distances between stops. Distances are
 * computed lazily and cached in a concurrent map keyed by {@link Edge}, so
 * repeated lookups during the search are cheap and thread-safe.
 *
 * <p>Stop indices exposed through the public accessors are 0-based over the
 * customers; the depot is handled internally as index 0 of the underlying
 * arrays.
 *
 * @author Othmane EL YAAKOUBI
 */
public class InputData {
    public final String FileName;
    private int Dimension;
    public int MaxVehicleNumber;
    private ConcurrentMap<Edge, Integer> DistanceMap;
    private int Capacity = -1;
    // private int DepotId = -1;
    private int[] Demands;
    private double[] Abscissas;
    private double[] Ordinates;
    
    /**
     * Parses a CVRPLIB {@code .vrp} instance from disk.
     *
     * @param file path to the {@code .vrp} file
     * @throws IOException if the file cannot be read
     */
    public InputData(String file) throws IOException {
        this.FileName = file;
        this.MaxVehicleNumber = Integer.MAX_VALUE;
        try (BufferedReader br = new BufferedReader(new FileReader(this.FileName))) {
            String line;
            String section = "";
            
            /* ---------- HEADER ---------- */ 
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String upper = line.toUpperCase();
                if (upper.startsWith("DIMENSION")) {
                    this.Dimension = Integer.parseInt(line.split(":")[1].trim());
                    this.Abscissas = new double[this.Dimension];
                    this.Ordinates = new double[this.Dimension];
                    this.Demands = new int[this.Dimension];
                }
                else if (upper.startsWith("NAME")) {
                    String name = line.split(":")[1].trim();
                    String[] nameParts = name.split("-");
                    if (nameParts.length > 1) {
                        for (String part : nameParts) {
                            if (part.contains("k")) {
                                this.MaxVehicleNumber = Integer.parseInt(part.trim().replaceAll("k", ""));
                                break;
                            }
                        }
                    }
                }
                else if (upper.startsWith("CAPACITY"))
                    this.Capacity = Integer.parseInt(line.split(":")[1].trim());
                else if (upper.startsWith("NODE_COORD_SECTION")) {
                    section = "NODE_COORD_SECTION";
                    break;
                }
                else if (upper.startsWith("EDGE_WEIGHT_SECTION")) {
                    section = "EDGE_WEIGHT_SECTION";
                    break;
                }
            }
            
            /* ---------- NODE COORDS ---------- */
            if ("NODE_COORD_SECTION".equals(section))
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;
                    String upper = line.toUpperCase();
                    if (upper.startsWith("DEMAND_SECTION")) {
                        section = "DEMAND_SECTION";
                        break;
                    }
                    if (upper.startsWith("EOF"))
                        break;
                    String[] split = line.split("\\s+");
                    int id = Integer.parseInt(split[0]);
                    this.Abscissas[id - 1] = Double.parseDouble(split[1]);
                    this.Ordinates[id - 1] = Double.parseDouble(split[2]);
                }
            
            /* ---------- DEMAND SECTION ---------- */
            if ("DEMAND_SECTION".equals(section))
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;
                    if (line.toUpperCase().startsWith("DEPOT_SECTION")) {
                        section = "DEPOT_SECTION";
                        break;
                    }
                    String[] split = line.split("\\s+");
                    int id = Integer.parseInt(split[0]);
                    int dem = Integer.parseInt(split[1]);
                    this.Demands[id - 1] = dem;
                }
            
            /* ---------- DEPOT SECTION ---------- */
            if ("DEPOT_SECTION".equals(section))
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("-1") || line.equalsIgnoreCase("EOF"))
                        break;
                    // this.DepotId = Integer.parseInt(line);
                }
        }
        int capacity = this.Dimension * (this.Dimension - 1);
        capacity /= 2;
        this.DistanceMap = new ConcurrentHashMap<>(capacity, 1f);
    }

    /**
     * Computes the CVRPLIB-rounded Euclidean distance between two internal
     * (0-based, depot-inclusive) indices.
     *
     * @param stop1 first internal index
     * @param stop2 second internal index
     * @return the rounded Euclidean distance
     */
    private int euclidean(int stop1, int stop2) {
        double dx = this.Abscissas[stop1] - this.Abscissas[stop2];
        double dy = this.Ordinates[stop1] - this.Ordinates[stop2];
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy));
    }

    /* ======================
       Distance access
       ====================== */
    /**
     * Returns the cached distance between two internal indices, computing and
     * storing it on first access.
     *
     * @param stop1 first internal index
     * @param stop2 second internal index
     * @return the distance, or 0 if both indices are equal
     */
    private int getDistance(int stop1, int stop2) {
        if (stop1 == stop2)
            return 0;
        Edge edge = new Edge(stop1, stop2);
        return this.DistanceMap.computeIfAbsent(edge, e -> euclidean(stop1, stop2));
    }

    /**
     * @param stop1 first 0-based customer index
     * @param stop2 second 0-based customer index
     * @return the distance between the two customers
     */
    public int getTwoStopsDistance(int stop1, int stop2) {
        return this.getDistance(stop1 + 1, stop2 + 1);
    }

    /**
     * @param stop 0-based customer index
     * @return the distance from the customer back to the depot
     */
    public int getStopToDepotDistance(int stop) {
        return this.getDistance(stop + 1, 0);
    }

    /**
     * @param stop 0-based customer index
     * @return the distance from the depot out to the customer
     */
    public int getDepotToStopDistance(int stop) {
        return this.getDistance(0, stop + 1);
    }

    /* ======================
       Getters
       ====================== */
    /**
     * @return the number of nodes (depot + customers)
     */
    public int getDimension() {
        return this.Dimension;
    }

    /**
     * @return the vehicle capacity
     */
    public int getCapacity() {
        return this.Capacity;
    }

//    public int getDepotId() {
//        return this.DepotId;
//    }

    /**
     * @param stop 0-based customer index
     * @return the demand of the customer
     */
    public int getDemand(int stop) {
        return this.Demands[stop + 1];
    }

    /**
     * @return the maximum number of vehicles allowed (parsed from the instance
     *         name, or {@link Integer#MAX_VALUE} when unspecified)
     */
    public int getMaxVehicleNumber() {
        return this.MaxVehicleNumber;
    }

     /* ======================
       toString
       ====================== */

    @Override
    public String toString() {
        return "InputData {" + " Dimension = " + this.Dimension + ", MaxVehicleNumber = " + this.MaxVehicleNumber + " }";
    }
}
