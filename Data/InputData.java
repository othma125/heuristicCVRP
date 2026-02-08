package Data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InputData {
    public final String FileName;
    private int Dimension;
    private ConcurrentMap<Edge, Integer> DistanceMap;
    private int Capacity = -1;
    private int DepotId = -1;
    private int[] Demands;
    private double[] Abscissas;
    private double[] Ordinates;
    
    public InputData(String file) throws IOException {
        this.FileName = file;
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
                    this.DepotId = Integer.parseInt(line);
                }
        }
        int capacity = this.Dimension * (this.Dimension - 1);
        capacity /= 2;
        this.DistanceMap = new ConcurrentHashMap<>(capacity, 1f);
    }

    private int euclidean(int stop1, int stop2) {
        double dx = this.Abscissas[stop1] - this.Abscissas[stop2];
        double dy = this.Ordinates[stop1] - this.Ordinates[stop2];
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy));
    }

    /* ======================
       Distance access
       ====================== */
    private int getDistance(int stop1, int stop2) {
        if (stop1 == stop2)
            return 0;
        Edge edge = new Edge(stop1, stop2), inverse = edge.Inverse();
        if (this.DistanceMap.containsKey(edge))
            return this.DistanceMap.get(edge);
        else if (this.DistanceMap.containsKey(inverse))
            return this.DistanceMap.get(inverse);
        else {
            int dist = euclidean(stop1, stop2);
            this.DistanceMap.put(edge, dist);
            return dist;
        }
    }
    
    public int getTwoStopsDistance(int stop1, int stop2) {
        return this.getDistance(stop1 + 1, stop2 + 1);
    }
    
    public int getStopToDepotDistance(int stop) {
        return this.getDistance(stop + 1, 0);
    }
    
    public int getDepotToStopDistance(int stop) {
        return this.getDistance(0, stop + 1);
    }

    /* ======================
       Getters
       ====================== */
    public int getDimension() {
        return this.Dimension;
    }

    public int getCapacity() {
        return this.Capacity;
    }

//    public int getDepotId() {
//        return this.DepotId;
//    }

    public int getDemand(int stop) {
        return this.Demands[stop + 1];
    }

    @Override
    public String toString() {
        return "InputData {" + " Dimension = " + this.Dimension + " }";
    }
}
