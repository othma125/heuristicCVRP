package Solution;

import Data.InputData;
import java.util.concurrent.locks.ReentrantLock;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Othmane
 */
public class AuxiliaryGraphNode {

    private Solution Solution;
    private double Label = Double.POSITIVE_INFINITY;
    final int NodeIndex;
    final ReentrantLock Lock = new ReentrantLock();

    AuxiliaryGraphNode(int NodeIndex) {
        this.NodeIndex = NodeIndex;
        this.Solution = null;
        if (this.NodeIndex == 0)
            this.Label = 0d;
    }

    boolean UpdateLabel(Solution solution, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = (solution == null ? 0d : solution.getTotalDistance()) + new_route.getTraveledDistance();
            if (label < this.Label) {
                c = this.Label < Double.POSITIVE_INFINITY;
                this.Label = label;
                Solution new_solution = new Solution(this.Label, solution == null ? 1 : solution.getRoutes().size() + 1);
                if(solution != null)
                    for(Route route : solution.getRoutes())
                        new_solution.add(route);
                new_solution.add(new_route);
                this.Solution = new_solution;
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    boolean UpdateLabel(Solution solution, Route old_route, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = solution.getTotalDistance() - old_route.getTraveledDistance() + new_route.getTraveledDistance();
            if (label < this.Label) {
                c = this.Label < Double.POSITIVE_INFINITY;
                this.Label = label;
                Solution new_solution = new Solution(this.Label, solution.getRoutes().size());
                for (Route route : solution.getRoutes())
                    new_solution.add(route == old_route ? new_route : route);
                this.Solution = new_solution;
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    boolean UpdateLabel(Solution solution, Route old_route, Route route1, Route route2) {
        boolean c = false;
        this.Lock.lock();
        try {
            double label = solution.getTotalDistance() - old_route.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.Label) {
                c = this.Label < Double.POSITIVE_INFINITY;
                this.Label = label;
                Solution new_solution = new Solution(this.Label, solution.getRoutes().size() + 1);
                solution.getRoutes()
                        .stream()
                        .filter(route -> route != old_route)
                        .forEach(new_solution::add);
                new_solution.add(route1);
                new_solution.add(route2);
                this.Solution = new_solution;
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }
    
    void LocalSearch(InputData data) {
        if (this.isFeasible())
            this.getBestSolution().LocalSearch(data);
    }

    Solution getBestSolution() {
        return this.Solution;
    }

    @Override
    public String toString() {
        return this.isFeasible() ? this.Solution.toString() : "NULL";
    }

    boolean isFeasible() {
        return this.Solution != null;
//        return Double.isFinite(this.Label);
    }

    int[] getNewSequence() {
        return this.Solution.getNewSequence();
    }
    
    String export() {
        return this.isFeasible() ? this.Solution.export() : "NULL";
    }

    int getRoutesCount() {
        return this.isFeasible() ? this.Solution.getRoutes().size() : 0;
    }

    double getLabel() {
        return this.isFeasible() ? this.Solution.getTotalDistance() : Double.POSITIVE_INFINITY;
    }
}
