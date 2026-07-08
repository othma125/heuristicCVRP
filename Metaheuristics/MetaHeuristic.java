package Metaheuristics;


import Solution.GiantTour;
import Data.InputData;

/**
 * Base class for metaheuristic solvers. Holds the problem instance, tracks the
 * best giant tour found and the time it was reached, and derives a
 * stagnation-based minimum running time from the instance size. Concrete
 * solvers implement {@link #Run()}.
 *
 * @author Othmane EL YAAKOUBI
 */
public abstract class MetaHeuristic {
    InputData Data;
    long StartTime;// Start Time in milliseconds
    long EndTime;
    long BestSolutionReachingTime;
    private GiantTour BestGiantTour = null;
    public final long StagnationMinTime;


    /**
     * @param data the problem instance to solve
     */
    public MetaHeuristic(InputData data) {
        this.Data = data;
        this.StagnationMinTime = (long) Math.max(100, 100 * Math.sqrt(data.getDimension()));
    }

    /**
     * Records {@code new_gt} as the incumbent if it improves on the current
     * best, updating the best-reaching timestamp and logging the improvement.
     *
     * @param new_gt a candidate giant tour
     * @return {@code true} if the incumbent was replaced
     */
    public boolean setBestSolution(GiantTour new_gt) {
        if (this.BestGiantTour == null || new_gt.compareTo(this.BestGiantTour) < 0) {
            this.BestSolutionReachingTime = System.currentTimeMillis();
            this.BestGiantTour = new_gt;
            System.out.println(this.BestGiantTour.getFitness() + " after " + (this.BestSolutionReachingTime  - this.StartTime) + " ms");
            return true;
        }
        return false;
    }

    /**
     * @return the best giant tour found so far, or {@code null} if none
     */
    public GiantTour getBestGiantTour() {
        return this.BestGiantTour;
    }

    /**
     * @return {@code true} if a feasible solution has been found
     */
    public boolean isFeasible() {
        return this.BestGiantTour != null;
    }

    /**
     * @return the total running time in milliseconds
     */
    public long getRunningTime() {
        return this.EndTime;
    }

    /**
     * Runs the metaheuristic to completion.
     */
    public abstract void Run();
}