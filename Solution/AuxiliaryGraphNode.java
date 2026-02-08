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

    private Solution BestSolution = null;
    private Solution OldSolution;
    private double Label = Double.POSITIVE_INFINITY;
    final int NodeIndex;
    final ReentrantLock Lock = new ReentrantLock();

    AuxiliaryGraphNode(int NodeIndex) {
        this.NodeIndex = NodeIndex;
        if (this.NodeIndex == 0)
            this.Label = 0d;
    }

    boolean UpdateLabel(Solution old_solution, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = (old_solution == null ? 0d : old_solution.getTotalDistance()) + new_route.getTraveledDistance();
            if (label < this.Label) {
                c = Double.isFinite(this.Label);
                this.Label = label;
                this.OldSolution = old_solution;
                this.BestSolution = new Solution(this.Label, this.OldSolution == null ? 1 : this.OldSolution.getRoutes().size() + 1);
                if(this.OldSolution != null)
                    for(Route route : this.OldSolution.getRoutes())
                        this.BestSolution.add(route);
                this.BestSolution.add(new_route);
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    boolean UpdateLabel(Solution old_solution, Route old_route, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + new_route.getTraveledDistance();
            if (label < this.Label) {
                c = Double.isFinite(this.Label);
                this.Label = label;
                this.OldSolution = old_solution;
                this.BestSolution = new Solution(this.Label, this.OldSolution.getRoutes().size());
                for (Route route : this.OldSolution.getRoutes())
                    this.BestSolution.add(route.equals(old_route) ? new_route : route);
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    boolean UpdateLabel(Solution old_solution, Route old_route, Route route1, Route route2) {
        if (route1 == null)
            return this.UpdateLabel(old_solution, old_route, route2);
        else if (route2 == null)
            return this.UpdateLabel(old_solution, old_route, route1);
        boolean c = false;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.Label) {
                c = Double.isFinite(this.Label);
                this.Label = label;
                this.OldSolution = old_solution;
                this.BestSolution = new Solution(this.Label, this.OldSolution.getRoutes().size() + 1);
                this.OldSolution.getRoutes()
                                .stream()
                                .filter(route -> !route.equals(old_route))
                                .forEach(this.BestSolution::add);
                this.BestSolution.add(route1);
                this.BestSolution.add(route2);
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
        return this.BestSolution;
    }

    Solution getOldSolution() {
        return this.OldSolution;
    }

    @Override
    public String toString() {
        return this.isFeasible() ? this.BestSolution.toString() : "NULL";
    }

    boolean isFeasible() {
         return this.BestSolution != null;
    }

    int[] getNewSequence() {
        if (this.isFeasible()) {
            int[] seq = null;
            this.Lock.lock();
            try {
                seq = this.BestSolution.getNewSequence();
            } finally {
                this.Lock.unlock();
            }
            return seq;
        }
        return null;
    }
    
    String export() {
        return this.isFeasible() ? this.BestSolution.export() : "NULL";
    }

    int getRoutesCount() {
        return this.isFeasible() ? this.BestSolution.getRoutes().size() : 0;
    }

    double getLabel() {
//        return this.Label;
        return this.isFeasible() ? this.BestSolution.getTotalDistance() : Double.POSITIVE_INFINITY;
    }
}
