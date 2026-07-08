package Solution;

import Data.InputData;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.LinkedList;

/**
 * A node of the {@link AuxiliaryGraph}, representing a position in the giant
 * tour. Each node holds the best partial solutions (labels) reaching it; the
 * shortest-path label of the last node is the optimal split. Label updates are
 * guarded by a {@link ReentrantLock} because the graph is built concurrently.
 *
 * @author Othmane EL YAAKOUBI
 */
public class AuxiliaryGraphNode {

    private final List<Solution> Solutions = new LinkedList<>();
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
     * with one new route, keeping it only if it improves the node's label.
     *
     * @param old_solution the partial solution reaching the predecessor node,
     *                     or {@code null} for the source
     * @param new_route    the route appended to reach this node
     */
    void UpdateLabel(Solution old_solution, Route new_route) {
        if (new_route == null)
            return;
        this.Lock.lock();
        try {
            double label = (old_solution == null ? 0d : old_solution.getTotalDistance()) + new_route.getTraveledDistance();
            if (label < this.getLabel()) {
                Solution newSolution = new Solution(label, old_solution == null ? 1 : old_solution.getRoutesCount() + 1);
                if(old_solution != null)
                    for(Route route : old_solution.getRoutes())
                        newSolution.add(route);
                newSolution.add(new_route);
                this.Solutions.addFirst(newSolution);
            }
        } finally {
            this.Lock.unlock();
        }
    }

    /**
     * Relaxes this node with a solution obtained by replacing {@code old_route}
     * with {@code new_route} in {@code old_solution}, keeping it if it improves
     * either the cost or the vehicle count.
     *
     * @param old_solution the partial solution to derive from
     * @param old_route    the route being replaced
     * @param new_route    the replacement route
     */
    void UpdateLabel(Solution old_solution, Route old_route, Route new_route) {
        if (new_route == null)
            return;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + new_route.getTraveledDistance();
            if (label < this.getLabel() || old_solution.getRoutesCount() < this.getRoutesCount()) {
                Solution newSolution = new Solution(label, old_solution.getRoutesCount());
                for (Route route : old_solution.getRoutes())
                    newSolution.add(route == old_route ? new_route : route);
                if (label < this.getLabel()) 
                    this.Solutions.addFirst(newSolution);
                else
                    this.Solutions.add(newSolution);
            }
        } finally {
            this.Lock.unlock();
        }
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
     */
    void UpdateLabel(InputData data, Solution old_solution, Route old_route, Route route1, Route route2) {
        if (route1 == null) {
            this.UpdateLabel(old_solution, old_route, route2);
            return;
        }
        else if (route2 == null) {
            this.UpdateLabel(old_solution, old_route, route1);
            return;
        }
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.getLabel()) {
                Solution newSolution = new Solution(label, old_solution.getRoutesCount() + 1);
                for (Route route : old_solution.getRoutes()) {
                    if (route != old_route) {
                        newSolution.add(route);
                    }
                }
                newSolution.add(route1);
                newSolution.add(route2);
                this.Solutions.addFirst(newSolution);
            }
        } finally {
            this.Lock.unlock();
        }
    }

    /**
     * @return the current best (lowest-cost) solution reaching this node
     */
    Solution getBestSolution() {
        return this.Solutions.getFirst();
    }

    /**
     * @return all candidate solutions currently held at this node
     */
    List<Solution> getSolutions() {
        return this.Solutions;
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
     * Runs inter-route local search on the best solution and returns its
     * flattened giant-tour sequence.
     *
     * @param data the problem instance
     * @return the improved sequence, or {@code null} if infeasible
     */
    int[] getNewSequence(InputData data) {
        if (this.isFeasible()) {
            int[] seq = null;
            this.Lock.lock();
            try {
                this.getBestSolution().InterRoutesLocalSearch(data);
                seq = this.getBestSolution().getNewSequence();
            } finally {
                this.Lock.unlock();
            }
            return seq;
        }
        return null;
    }
}