// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * A node of the {@link AuxiliaryGraph}, representing a position in the giant
 * tour. Each node holds the best partial solutions (labels) reaching it; the
 * shortest-path label of the last node is the optimal split. Label updates are
 * guarded by a {@link ReentrantLock} because the graph is built concurrently.
 *
 * @author Othmane EL YAAKOUBI
 */
public class AuxiliaryGraphNode implements AutoCloseable {

    private final List<Solution> Solutions = new LinkedList<>();
    private Solution bestDistance = null;
    private Solution bestLeftOver = null;
    final ReentrantLock Lock = new ReentrantLock();
    final int NodeIndex;

    /**
     * @param NodeIndex the position of this node in the giant tour
     */
    AuxiliaryGraphNode(int NodeIndex) {
        this.NodeIndex = NodeIndex;
    }

    /**
     * Relaxes this node with a solution formed by extending {@code old_solution}
     * with one new route, keeping it only if it improves the node's label or
     * leftover load.
     *
     * @param old_solution the partial solution reaching the predecessor node,
     *                     or {@code null} for the source
     * @param new_route    the route appended to reach this node
     * @return {@code true} if the node was already feasible when an improving
     *         label was accepted
     */
    boolean UpdateLabel(Solution old_solution, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            int leftover_load = old_solution != null ? Math.max(old_solution.getLeftoverLoad(), new_route.getLeftover()) : new_route.getLeftover();
            double label = (old_solution == null ? 0d : old_solution.getTotalDistance()) + new_route.getTraveledDistance();
            if (label < this.getLabel() || leftover_load < this.getLeftoverLoad()) {
                c = this.isFeasible();
                int routes_count = old_solution != null ? old_solution.getRoutesCount() + 1 : 1;
                Solution newSolution = new Solution(label, routes_count);
                if(old_solution != null)
                    for(Route route : old_solution.getRoutes())
                        newSolution.add(route);
                newSolution.add(new_route);
                this.Solutions.addFirst(newSolution);
                if (this.bestDistance == null || newSolution.getTotalDistance() < this.bestDistance.getTotalDistance())
                    this.bestDistance = newSolution;
                if (this.bestLeftOver == null || newSolution.getLeftoverLoad() < this.bestLeftOver.getLeftoverLoad())
                    this.bestLeftOver = newSolution;
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    /**
     * Relaxes this node with a solution obtained by replacing {@code old_route}
     * with {@code new_route} in {@code old_solution}, keeping it if it improves
     * either the cost or the leftover load.
     *
     * @param old_solution the partial solution to derive from
     * @param old_route    the route being replaced
     * @param new_route    the replacement route
     * @return {@code true} if the node was already feasible when an improving
     *         label was accepted
     */
    boolean UpdateLabel(Solution old_solution, Route old_route, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + new_route.getTraveledDistance();
            if (label < this.getLabel()
                    || Math.max(old_solution.getLeftoverLoadWithout(old_route), new_route.getLeftover()) < this.getLeftoverLoad()) {
                c = this.isFeasible();
                Solution newSolution = new Solution(label, old_solution.getRoutesCount());
                for (Route route : old_solution.getRoutes())
                    newSolution.add(route == old_route ? new_route : route);
                this.Solutions.addFirst(newSolution);
                if (this.bestDistance == null || newSolution.getTotalDistance() < this.bestDistance.getTotalDistance())
                    this.bestDistance = newSolution;
                if (this.bestLeftOver == null || newSolution.getLeftoverLoad() < this.bestLeftOver.getLeftoverLoad())
                    this.bestLeftOver = newSolution;
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    /**
     * Relaxes this node with a solution that replaces {@code old_route} with
     * two routes (the result of an inter-route move that splits into two).
     * Delegates to the single-route overload when one of the routes is
     * {@code null}.
     *
     * @param data         the problem instance
     * @param old_solution the partial solution to derive from
     * @param old_route    the route being replaced
     * @param route1       the first replacement route (may be {@code null})
     * @param route2       the second replacement route (may be {@code null})
     * @return {@code true} if the node was already feasible when an improving
     *         label was accepted
     */
    boolean UpdateLabel(InputData data, Solution old_solution, Route old_route, Route route1, Route route2) {
        if (route1 == null)
            return this.UpdateLabel(old_solution, old_route, route2);
        else if (route2 == null)
            return this.UpdateLabel(old_solution, old_route, route1);
        boolean c = false;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.getLabel()
                    || Math.max(old_solution.getLeftoverLoadWithout(old_route), Math.max(route1.getLeftover(), route2.getLeftover())) < this.getLeftoverLoad()) {
                c = this.isFeasible();
                Solution newSolution = new Solution(label, old_solution.getRoutesCount() + 1);
                for (Route route : old_solution.getRoutes()) 
                    if (route != old_route) 
                        newSolution.add(route);
                newSolution.add(route1);
                newSolution.add(route2);
                this.Solutions.addFirst(newSolution);
                if (this.bestDistance == null || newSolution.getTotalDistance() < this.bestDistance.getTotalDistance())
                    this.bestDistance = newSolution;
                if (this.bestLeftOver == null || newSolution.getLeftoverLoad() < this.bestLeftOver.getLeftoverLoad())
                    this.bestLeftOver = newSolution;
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    /**
     * @return the current best (lowest-cost) solution reaching this node
     */
    Solution getBestSolution() {
        Solution best = this.bestDistance;
        for (Solution solution : this.Solutions) 
            if (solution.getTotalDistance() < best.getTotalDistance()) 
                best = solution;
        return best;
    }

    /**
     * @return all candidate solutions currently held at this node
     */
    List<Solution> getSolutions() {
        return this.Solutions;
    }

    /**
     * Extracts the non-dominated solutions of this node for the two minimised
     * objectives: total travelled distance and leftover load. A solution is
     * dominated when another one is at least as good on both objectives and
     * strictly better on one of them.
     *
     * @return the Pareto-optimal solutions, sorted by ascending distance
     */
    List<Solution> getParetoSet() {
        List<Solution> pareto = new LinkedList<>();
        this.Lock.lock();
        try {
            this.Solutions.sort(Comparator.comparingInt(Solution::getLeftoverLoad).thenComparingDouble(Solution::getTotalDistance));
            double best_distance = Double.POSITIVE_INFINITY;
            for (Solution solution : this.Solutions)
                if (solution.getTotalDistance() < best_distance) {
                    pareto.addFirst(solution);
                    best_distance = solution.getTotalDistance();
                }
                else
                    break;
        } finally {
            this.Lock.unlock();
        }
        return pareto;
    }

    /**
     * @return {@code true} if at least one solution reaches this node
     */
    boolean isFeasible() {
         return !this.Solutions.isEmpty();
    }

    @Override
    public String toString() {
        return this.isFeasible() ? this.getBestSolution().toString() : "NULL";
    }

    /**
     * @return the CVRPLIB route listing of the best solution, or {@code "NULL"}
     *         if infeasible
     */
    String export() {
        return this.isFeasible() ? this.getBestSolution().export() : "NULL";
    }

    /**
     * @return the number of routes in the best solution, or 0 if infeasible
     */
    int getRoutesCount() {
        return this.isFeasible() ? this.getBestSolution().getRoutes().size() : 0;
    }

    /**
     * @return the cost of the best solution, or
     *         {@link Double#POSITIVE_INFINITY} if infeasible
     */
    double getLabel() {
        return this.isFeasible() ? this.getBestSolution().getTotalDistance() : Double.POSITIVE_INFINITY;
    }

    /**
     * @return the minimum leftover load among all solutions at this node, or
     *         {@link Integer#MAX_VALUE} if infeasible
     */
    int getLeftoverLoad() {
        return this.isFeasible() ? this.bestLeftOver.getLeftoverLoad() : Integer.MAX_VALUE;
    }

    /**
     * Returns the flattened giant-tour sequence of the best solution. Inter-route
     * local search is applied to the sink's Pareto set in the
     * {@link AuxiliaryGraph} constructor, so the returned sequence already
     * reflects those improvements.
     *
     * @param data the problem instance
     * @return the flattened sequence of the best solution, or {@code null} if infeasible
     */
    int[] getNewSequence(InputData data) {
        if (this.isFeasible()) {
            int[] seq = null;
            this.Lock.lock();
            try {
                seq = this.getBestSolution().getNewSequence();
            } finally {
                this.Lock.unlock();
            }
            return seq;
        }
        return null;
    }

    /**
     * Releases the node by closing all of its solutions and clearing the list.
     * Guarded by the node {@link #Lock} since the graph is built concurrently.
     */
    @Override
    public void close() {
        this.Lock.lock();
        try {
            for (Solution solution : this.Solutions)
                solution.close();
            this.Solutions.clear();
        } finally {
            this.Lock.unlock();
        }
    }
}