// Author: Othmane

package Algorithm.Solution;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.LocalSearchMove;

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
    private final BitSet Stops;
    private double TotalDistance;
    private int LeftoverLoad;

    /**
     * @param distance the initial total travelled distance
     * @param capacity the expected number of routes, used to size the backing set
     */
    Solution(double distance, int capacity) {
        this.TotalDistance = distance;
        this.LeftoverLoad = 0;
        this.Routes = new HashSet<>(capacity, 1f);
        this.Stops = new BitSet();
    }

    /**
     * Adds a route to the solution, registers all of its stops as served and
     * keeps the leftover load equal to the largest leftover among the routes.
     *
     * @param new_route the route to add
     */
    void add(Route new_route) {
        this.Routes.add(new_route);
        this.LeftoverLoad = Math.max(this.LeftoverLoad, new_route.getLeftover());
        for (int stop : new_route.getSequence())
            this.Stops.set(stop);
    }

    /**
     * Improves the solution by first optimising each route internally, then
     * applying the best available inter-route move. Routes replaced by a move
     * are swapped in and the total distance is updated accordingly.
     *
     * <p>Each applied move is followed by another pass, up to
     * max(10, sqrt(routes count)) of them, since a move creates two new routes
     * the remaining ones may now combine with. The cap keeps the cost bounded:
     * the search is called on every solution of every Pareto set, so descending
     * all the way to a local optimum would starve the genetic loop of generations.
     *
     * @param data the problem instance providing distances and capacity
     */
    void InterRoutesLocalSearch(InputData data) {
        int passes = Math.max(10, (int) Math.sqrt(this.Routes.size()));
        this.InterRoutesLocalSearch(data, passes);
    }

    /**
     * @param data the problem instance providing distances and capacity
     * @param passes the number of moves still allowed
     */
    private void InterRoutesLocalSearch(InputData data, int passes) {
        this.TotalDistance = 0d;
        this.Routes.forEach(r -> {
            r.IntraRoutesLocalSearch(data);
            this.TotalDistance += r.getTraveledDistance();
        });
        // random pair order: the first improving move found is applied, so scanning
        // the routes in a fixed order would always favour the same pairs
        List<Route> shuffled_routes = new ArrayList<>(this.Routes);
        Collections.shuffle(shuffled_routes, ThreadLocalRandom.current());
        for (Route r1 : shuffled_routes) 
            for (Route r2 : shuffled_routes) 
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
                        this.updateLeftoverLoad();
                        if (passes > 1)
                            this.InterRoutesLocalSearch(data, passes - 1);
                        return;
                    }
                }
    }
    
    /**
     * @param stop a 0-based customer index
     * @return {@code true} if the stop is already served by this solution
     */
    boolean contains(int stop) {
        return this.Stops.get(stop);
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
     * Recomputes the leftover load as the largest unused capacity among the
     * routes of this solution.
     */
    void updateLeftoverLoad() {
        this.LeftoverLoad = 0;
        for (Route route : this.Routes)
            this.LeftoverLoad = Math.max(this.LeftoverLoad, route.getLeftover());
    }

    /**
     * @return the largest unused capacity among the routes, i.e. the load the
     *         emptiest vehicle could still carry
     */
    int getLeftoverLoad() {
        return this.LeftoverLoad;
    }

    /**
     * Same as {@link #getLeftoverLoad()} but ignoring one route, for labels that
     * replace a route: the leftover of the route being dropped must not count
     * towards the leftover of the solution replacing it.
     *
     * @param excluded the route being replaced
     * @return the largest unused capacity among the other routes
     */
    int getLeftoverLoadWithout(Route excluded) {
        int leftover = 0;
        for (Route route : this.Routes)
            if (route != excluded)
                leftover = Math.max(leftover, route.getLeftover());
        return leftover;
    }

    /**
     * Flattens the routes back into a single giant-tour sequence by
     * concatenating their stops in {@link #Routes} iteration order.
     *
     * <p>Split only needs each route's customers to be contiguous, so the order
     * the routes are concatenated in costs nothing: re-splitting the sequence
     * yields the same routes whichever way they are laid out. The stops inside a
     * route keep their order, since that is what the local search just optimised.
     *
     * @return the concatenated stop sequence
     */
    int[] getNewSequence() {
        int[] sequence = new int[this.Stops.cardinality()];
        int index = 0;
        for (Route route : this.Routes) 
            for (int stop : route.getSequence()) 
                sequence[index++] = stop;
        // assert index == sequence.length : "routes and served stops disagree: " + index + " != " + sequence.length;
        return sequence;
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
        return Double.compare(this.TotalDistance, sol.TotalDistance);
    }

    /**
     * Releases the solution by closing all of its routes and clearing the route
     * and stop sets. Because routes may be shared with other solutions, do not
     * close a solution whose routes are still in use elsewhere.
     */
    @Override
    public void close() {
        for (Route route : this.Routes)
            route.close();
        this.Routes.clear();
        this.Stops.clear();
    }
}