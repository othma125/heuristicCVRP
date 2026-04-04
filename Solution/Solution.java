/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Solution;

import Data.InputData;
import Solution.LSM.LocalSearchMove;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

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

    void InterRoutesLocalSearch(InputData data) {
        this.Routes.forEach(r -> r.IntraRoutesLocalSearch(data));
        for (Route r1 : this.Routes) 
            for (Route r2 : this.Routes) 
                if (r1 != r2) {
                    LocalSearchMove lsm = r1.getLSM(data, r2);
                    if (lsm != null) {
                        lsm.Perform(data);
                        this.Routes.remove(r1);
                        this.TotalDistance -= r1.getTraveledDistance();
                        this.Routes.remove(r2);
                        this.TotalDistance -= r2.getTraveledDistance(); 
                        if (lsm.getFirstRoute() != null) {
                            this.Routes.add(lsm.getFirstRoute());
                            this.TotalDistance += lsm.getFirstRoute().getTraveledDistance();
                        }
                        if (lsm.getSecondRoute() != null) {
                            this.Routes.add(lsm.getSecondRoute());
                            this.TotalDistance += lsm.getSecondRoute().getTraveledDistance();
                        }
                        this.InterRoutesLocalSearch(data);
                        return;
                    }
                }
    }
    
    boolean contains(int stop) {
        return this.Stops.contains(stop);
    }

    void add(Route new_route) {
        this.Routes.add(new_route);
        List<Integer> sequenceList = new_route.getSequenceAsList();
        for (int stop : new_route.getSequence()) 
            this.Stops.add(stop);
    }
    
    Set<Route> getRoutes() {
        return this.Routes;
    }

    int getRoutesCount() {
        return this.Routes.size();
    }

    public double getTotalDistance() {
        return this.TotalDistance;
    }

    int[] getNewSequence() {
        List<Integer> combinedSequence = new ArrayList<>();
        for (Route route : this.Routes) {
            int[] routeSequence = route.getSequence();
            for (int stop : routeSequence) {
                combinedSequence.add(stop);
            }
        }
        int[] result = new int[combinedSequence.size()];
        for (int i = 0; i < result.length; i++) 
            result[i] = combinedSequence.get(i);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<Route> sortedRoutes = new ArrayList<>(this.Routes);
        Collections.sort(sortedRoutes);
        for (Route r : sortedRoutes) {
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
        List<Route> sortedRoutes = new ArrayList<>(this.Routes);
        Collections.sort(sortedRoutes);
        for (Route r : sortedRoutes) {
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