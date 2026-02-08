/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Solution;

import Data.InputData;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.IntStream;

/**
 *
 * @author Othmane
 */
public final class Solution implements Comparable<Solution> {
    
    private final Set<Route> Routes;
    private final Set<Integer> Stops;
    private double TotalDistance;

    Solution(double distance, int capacity) {
        this.TotalDistance = distance;
        this.Routes = new HashSet<>(capacity, 1f);
        this.Stops = new HashSet<>();
    }
    
    int getSize() {
        return this.Stops.size();
    }
    
    boolean contains(int stop) {
        return this.Stops.contains(stop);
    }

    void add(Route new_route) {
        this.Routes.add(new_route);
        new_route.getStops().forEach(this.Stops::add);
    }
    
    void LocalSearch(InputData data) {
        this.Routes.forEach(r -> r.LocalSearch(data));
        this.TotalDistance = this.Routes.stream().mapToDouble(Route::getTraveledDistance).sum();
    }
    
    Set<Route> getRoutes() {
        return this.Routes;
    }

    public double getTotalDistance() {
        return this.TotalDistance;
    }

    int[] getNewSequence() {
        return this.Routes.stream()
                            .map(Route::getSequence)
                            .flatMapToInt(IntStream::of)
                            .toArray();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Route r : this.Routes.stream()
                                    .sorted()
                                    .toArray(Route[]::new)) {
            sb.append("This route contains ").append(r.getLength()).append(" stops : ");
            sb.append(r.toString()).append(" = ").append(r.getTraveledDistance());
            sb.append("\n");
        }
        sb.append("Total traveled distance = ").append(this.TotalDistance);
        return sb.toString();
    }
    
    String export() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Route r : this.Routes.stream()
                                    .sorted()
                                    .toArray(Route[]::new)) {
            sb.append("Route #").append(++i).append(": ");
            sb.append(r.export());
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public int compareTo(Solution sol) {
        return Double.compare(this.TotalDistance * 100d, sol.TotalDistance * 100d);
    }
}