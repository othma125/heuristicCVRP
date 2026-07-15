// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.LocalSearchMove;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * A complete CVRP solution: a set of vehicle {@link Route}s together with the
 * set of stops they cover and their total travelled distance. Solutions are
 * comparable by total distance, and can be improved in place by inter-route
 * local search. Instances are built incrementally by the auxiliary graph while
 * decoding a giant tour.
 *
 * @author Othmane EL YAAKOUBI
 */
public final class Solution implements Comparable<Solution>, AutoCloseable {

    private final Set<Route> Routes;
    private final Set<Integer> Stops;
    private double TotalDistance;

    /**
     * @param distance the initial total travelled distance
     * @param capacity the expected number of routes, used to size the backing
     *                 set
     */
    Solution(double distance, int capacity) {
        this.TotalDistance = distance;
        this.Routes = new HashSet<>(capacity, 1f);
        this.Stops = new HashSet<>();
    }

    /**
     * Improves the solution by first optimising each route internally, then
     * repeatedly applying the best available inter-route move until no further
     * improving move exists. Routes replaced by a move are swapped in and the
     * total distance is updated accordingly.
     *
     * @param data the problem instance providing distances and capacity
     */
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
    
    /**
     * @param stop a 0-based customer index
     * @return {@code true} if the stop is already served by this solution
     */
    boolean contains(int stop) {
        return this.Stops.contains(stop);
    }

    /**
     * Adds a route to the solution and registers all of its stops as served.
     *
     * @param new_route the route to add
     */
    void add(Route new_route) {
        this.Routes.add(new_route);
        for (int stop : new_route.getSequence())
            this.Stops.add(stop);
    }

    /**
     * @return the routes making up this solution
     */
    Set<Route> getRoutes() {
        return this.Routes;
    }

    /**
     * @return the number of routes (vehicles) used
     */
    int getRoutesCount() {
        return this.Routes.size();
    }

    /**
     * @return the total travelled distance of the solution
     */
    public double getTotalDistance() {
        return this.TotalDistance;
    }

    /**
     * Flattens the routes back into a single giant-tour sequence by
     * concatenating their stops.
     *
     * @return the concatenated stop sequence
     */
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
    
    /**
     * Renders the solution in CVRPLIB {@code .sol} route format, one
     * {@code Route #k: ...} line per vehicle.
     *
     * @return the CVRPLIB-formatted route listing
     */
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

    /**
     * Orders solutions by ascending total travelled distance.
     *
     * @param sol the solution to compare against
     * @return a negative value, zero or a positive value as this solution is
     *         cheaper than, equal to, or costlier than {@code sol}
     */
    @Override
    public int compareTo(Solution sol) {
        return Double.compare(this.TotalDistance * 100d, sol.TotalDistance * 100d);
    }
    
    @Override
    public void close() {
        for (Route r : this.Routes)
            r.close();
        this.Routes.clear();
        this.Stops.clear();
    }
}