package Solution;

import Data.InputData;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.LinkedList;

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

    private List<Solution> Solutions = new LinkedList<>();
    final ReentrantLock Lock = new ReentrantLock();
    final int NodeIndex;

    AuxiliaryGraphNode(int NodeIndex) {
        this.NodeIndex = NodeIndex;
    }

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
                this.Solutions.add(newSolution);
            }
        } finally {
            this.Lock.unlock();
        }
    }

    void UpdateLabel(InputData data, Solution old_solution, Route old_route, Route new_route) {
        if (new_route == null)
            return;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + new_route.getTraveledDistance();
            if (data.getMaxVehicleNumber() == old_solution.getRoutesCount() || label < this.getLabel()) {
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

    void UpdateLabel(InputData data, Solution old_solution, Route old_route, Route route1, Route route2) {
        if (route1 == null) {
            this.UpdateLabel(data, old_solution, old_route, route2);
            return;
        }
        else if (route2 == null) {
            this.UpdateLabel(data, old_solution, old_route, route1);
            return;
        }
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.getLabel()) {
                Solution newSolution = new Solution(label, old_solution.getRoutesCount() + 1);
                old_solution.getRoutes()
                            .stream()
                            .filter(route -> route != old_route)
                            .forEach(newSolution::add);
                newSolution.add(route1);
                newSolution.add(route2);
                this.Solutions.addFirst(newSolution);
            }
        } finally {
            this.Lock.unlock();
        }
    }

    Solution getBestSolution() {
        return this.Solutions.getFirst();
    }

    List<Solution> getSolutions() {
        return this.Solutions;
    }

    boolean isFeasible() {
         return !this.Solutions.isEmpty();
    }

    @Override
    public String toString() {
        return this.isFeasible() ? this.getBestSolution().toString() : "NULL";
    }
    
    String export() {
        return this.isFeasible() ? this.getBestSolution().export() : "NULL";
    }

    int getRoutesCount() {
        return this.isFeasible() ? this.getBestSolution().getRoutes().size() : 0;
    }

    double getLabel() {
        return this.isFeasible() ? this.getBestSolution().getTotalDistance() : Double.POSITIVE_INFINITY;
    }

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