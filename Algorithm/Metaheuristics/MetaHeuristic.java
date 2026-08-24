// Author: Othmane

package Algorithm.Metaheuristics;


import Algorithm.Solution.GiantTour;
import Algorithm.Data.InputData;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

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
    private final ReentrantLock BestLock = new ReentrantLock();
    public final long StagnationMinTime;
    // How much longer than the elapsed time the search tolerates stagnation before
    // it starts rolling to stop: higher means longer runs, no wall-clock cap. Spent
    // one unit per survived roll, so a stagnating search tightens until it gives up.
    private int Patience = 5;

    /** Incumbent trace: one {time_ms_since_StartTime, cost} pair per improvement. */
    public final List<long[]> Trace = Collections.synchronizedList(new ArrayList<>());


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
     * Guarded by {@link #BestLock} so concurrent callers cannot interleave the
     * comparison with the update.
     *
     * @param new_gt a candidate giant tour
     * @return {@code true} if the incumbent was replaced
     */
    public boolean setBestSolution(GiantTour new_gt) {
        this.BestLock.lock();
        try {
            if (this.BestGiantTour == null || new_gt == this.BestGiantTour || new_gt.compareTo(this.BestGiantTour) < 0) {
                this.BestSolutionReachingTime = System.currentTimeMillis();
                this.BestGiantTour = new_gt;
                System.out.println(this.BestGiantTour.getFitness() + " after " + (this.BestSolutionReachingTime  - this.StartTime) + " ms");
                this.Trace.add(new long[]{this.BestSolutionReachingTime - this.StartTime, (long) this.BestGiantTour.getFitness()});
                return true;
            }
            return false;
        } finally {
            this.BestLock.unlock();
        }
    }

    /**
     * Stagnation-based stopping rule: always continues while the last
     * improvement is within {@link #StagnationMinTime}, then continues with a
     * probability that decays as the stagnation stretch grows relative to the
     * total elapsed time, tolerating {@code Patience} times that stretch. Being
     * relative to the elapsed time, a run that keeps improving keeps extending
     * itself instead of hitting a fixed budget. Every survived roll spends one
     * unit of patience, which sharpens the next roll and bounds the tail: at zero
     * the search stops without rolling, so the ratio is never divided by zero.
     *
     * @return {@code true} if the search should keep running
     */
    protected boolean nonStopCondition() {
        long current_time = System.currentTimeMillis();
        if (current_time - this.BestSolutionReachingTime <= this.StagnationMinTime)
            return true;
        if (this.Patience == 0)
            return false;
        double stagnationRatio = current_time - this.BestSolutionReachingTime - this.StagnationMinTime;
        stagnationRatio /= (double) (current_time - this.StartTime) * this.Patience;
        if (ThreadLocalRandom.current().nextDouble() < stagnationRatio)
            return false;
        this.Patience--;
        return true;
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
     * Requests the running solver to stop early; it will return the best tour found so far.
     * The flag lives on the instance so the split and its local search can abort mid-run
     * instead of finishing the current giant tour first.
     */
    public void requestStop() {
        this.Data.requestStop();
    }

    /**
     * @return {@code true} once {@link #requestStop()} has been called
     */
    protected boolean isStopRequested() {
        return this.Data.isStopRequested();
    }

    /**
     * Runs the metaheuristic to completion.
     */
    public abstract void Run();
}