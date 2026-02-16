package Metaheuristics;


import Solution.GiantTour;
import Data.InputData;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Othmane
 */
public abstract class MetaHeuristic {
    InputData Data;
    long StartTime;// Start Time in milliseconds
    long EndTime;
    long BestSolutionReachingTime;
    private GiantTour BestGiantTour = null;
    public final long StagnationMinTime;


    public MetaHeuristic(InputData data) {
        this.Data = data;
        this.StagnationMinTime = (long) Math.max(100, 100 * Math.sqrt(data.getDimension()));
    }

    public boolean setBestSolution(GiantTour new_gt) {
        if (this.BestGiantTour == null || new_gt.compareTo(this.BestGiantTour) < 0) {
            this.BestSolutionReachingTime = System.currentTimeMillis();
            this.BestGiantTour = new_gt;
            System.out.println(this.BestGiantTour.getFitness() + " after " + (this.BestSolutionReachingTime  - this.StartTime) + " ms");
            return true;
        }
        return false;
    }

    public GiantTour getBestGiantTour() {
        return this.BestGiantTour;
    }
    
    public boolean isFeasible() {
        return this.BestGiantTour != null;
    }
    
    public long getRunningTime() {
        return this.EndTime;
    }
    
    public abstract void Run();
}