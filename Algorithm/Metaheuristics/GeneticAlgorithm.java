// Author: Othmane

package Algorithm.Metaheuristics;

import Algorithm.Data.InputData;
import Algorithm.Solution.GiantTour;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Memetic solver: a genetic algorithm over giant tours whose graph-based
 * crossover already embeds local search through the split procedure. It uses
 * tournament selection, a graph crossover between selected parents, and a
 * steady-state replacement that inserts offspring into the worse half of the
 * population. The search continues while crossovers keep improving, and beyond
 * that with a stagnation-driven probabilistic stopping condition.
 *
 * @author Othmane EL YAAKOUBI
 */
public class GeneticAlgorithm extends MetaHeuristic {
    
    private final double CrossoverRate = 0.8d;
    private final GiantTour[] Population;
    private final int PopulationSize;
    private final int TournamentSize = 5;
    private static final int MAX_ALLOWED_FAILURES = 100;
    // Two threads only: the work submitted here nests parallel work over the split
    // graph, and running it in a narrow pool leaves the common pool free for the arcs.
    // Daemon threads so an idle pool never keeps the JVM alive.
    private static final ExecutorService CrossoverPool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "crossover-pool");
        thread.setDaemon(true);
        return thread;
    });
    private final ReentrantLock PopulationLock = new ReentrantLock();

    
    /**
     * @param data the problem instance to solve
     */
    public GeneticAlgorithm(InputData data) {
        super(data);
        this.PopulationSize = (int) Math.max(20, 10 * Math.log10(data.getDimension()));
        this.Population = new GiantTour[this.PopulationSize];
    }

    /**
     * Initialises the population, then repeatedly runs crossover generations
     * until neither improvement nor the stagnation condition keeps it going.
     * Aborts early if no feasible initial individual can be produced.
     */
    @Override
    @SuppressWarnings("empty-statement")
    public void Run() {
        System.out.println("File to solve = " + this.Data.FileName);
        System.out.println("Dimension = " + this.Data.getDimension());
        System.out.println("Solution approach = Memetic Algorithm");
        System.out.println();
        this.StartTime = System.currentTimeMillis();
        this.InitialPopulation();
        if(!this.Population[0].isFeasible())
            return;
        // requestStop() (e.g. a web Stop request) breaks out, keeping the best-so-far tour.
        while (!this.isStopRequested() && (this.runCrossovers() || this.nonStopCondition())) {}
        this.EndTime = System.currentTimeMillis() - this.StartTime;
        System.out.println();
    }

    /**
     * Runs one crossover per individual (a generation), each on
     * {@link #CrossoverPool}.
     *
     * @return {@code true} if any crossover improved the incumbent
     */
    private boolean runCrossovers() {
        boolean crossoverResult = false;
        for (int i = 0; i < this.PopulationSize && !this.isStopRequested(); i++)
            if (await(CrossoverPool.submit(this::Crossover)))
                crossoverResult = true;
        return crossoverResult;
    }

    /**
     * Waits for a task submitted to {@link #CrossoverPool} and unwraps its
     * result, rethrowing any failure as unchecked.
     *
     * @param <T>    the task result type
     * @param future the submitted task
     * @return the task result
     */
    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Selects two parents by tournament and recombines them: a graph crossover
     * at the crossover rate, a crossover with a fresh random tour when the same
     * parent is drawn twice, otherwise a re-split of both parents. Runs on
     * {@link #CrossoverPool}.
     *
     * @return {@code true} if the incumbent was improved
     */
    private boolean Crossover() {
        GiantTour parent1 = this.tournamentSelection();
        GiantTour parent2 = this.tournamentSelection();
        if (ThreadLocalRandom.current().nextDouble() < this.CrossoverRate && parent1 != parent2) {
            GiantTour graph_crossover = new GiantTour(this.Data, parent1, parent2);
            return this.UpdatePopulation(graph_crossover);
        }
        else if (parent1 == parent2) {
            GiantTour random = new GiantTour(this.Data, false);
            GiantTour graph_crossover = new GiantTour(this.Data, parent1, random);
            return this.UpdatePopulation(graph_crossover); 
        }
        else {
            // repeat splitting procedure to discover more improvement possibilities
            boolean c1 = parent1.Split(this.Data);
            boolean c2 = parent2.Split(this.Data);
            if (c1 || c2) {
                this.PopulationLock.lock();
                try {
                    Arrays.sort(this.Population);
                } finally {
                    this.PopulationLock.unlock();
                }
                return true;
            }
            return false;
        }
    }
    
    /**
     * Inserts an offspring into the population if it beats the worst
     * individual, replacing a random member of the worse half and re-sorting.
     * When the offspring becomes the new best, it is further recombined with
     * the best and a random individual. Held under {@link #PopulationLock} so
     * the replacement and the re-sort cannot interleave with another update.
     *
     * @param newGiantTour the candidate offspring
     * @return {@code true} if the offspring became the new incumbent
     */
    private boolean UpdatePopulation(GiantTour newGiantTour) {
        if (newGiantTour == null || !newGiantTour.isFeasible())
            return false;
        boolean c = false;
        // reentrant: the nested call below re-enters with the lock already held
        this.PopulationLock.lock();
        try {
            if (newGiantTour.compareTo(this.getLast()) < 0) {
                int half = this.PopulationSize / 2;
                int randomIndex = half + ThreadLocalRandom.current().nextInt(this.Population.length - half);
                if (this.setBestSolution(newGiantTour)) {
                    GiantTour graph_crossover = new GiantTour(this.Data, newGiantTour, this.Population[0], this.Population[randomIndex]);
                    this.UpdatePopulation(graph_crossover);
                    c = true;
                }
                this.Population[randomIndex] = newGiantTour;
                Arrays.sort(this.Population);
            }
            else
                newGiantTour.close();
        } finally {
            this.PopulationLock.unlock();
        }
        return c;
    }
    
    /**
     * Fills the population with feasible random giant tours and sorts it by
     * fitness. The first slot probes feasibility and gives up after 100 failed
     * attempts, which aborts the run; once it succeeds the instance is known to
     * be feasible, so the remaining slots retry until they are and are filled
     * concurrently on {@link #CrossoverPool}. Bails out early if a stop is
     * requested, leaving the sort out since trailing slots may be unfilled.
     */
    private void InitialPopulation() {
        int failure_count = 0;
        do {
            if (this.Population[0] != null)
                this.Population[0].close();
            this.Population[0] = new GiantTour(this.Data);
        } while (!this.Population[0].isFeasible() && ++failure_count < MAX_ALLOWED_FAILURES && !this.isStopRequested());
        if (!this.Population[0].isFeasible())
            return;
        this.setBestSolution(this.Population[0]);
        IntStream.range(1, this.PopulationSize)
                .mapToObj(i -> CrossoverPool.submit(() -> {
                    do {
                        if (this.Population[i] != null)
                            this.Population[i].close();
                        this.Population[i] = new GiantTour(this.Data);
                    } while (!this.Population[i].isFeasible() && !this.isStopRequested());
                    this.setBestSolution(this.Population[i]);
                }))
                .toList()
                .forEach(GeneticAlgorithm::await);
        if (this.isStopRequested())
            return;
        Arrays.sort(this.Population);
    }
    
    /**
     * Stagnation-based stopping rule: always continues while the last
     * improvement is within {@code StagnationMinTime}, then continues with a
     * probability that decays as the stagnation stretch grows relative to the
     * total elapsed time.
     *
     * @return {@code true} if the search should keep running
     */
    private boolean nonStopCondition() {
        long current_time = System.currentTimeMillis();
        if (current_time - this.BestSolutionReachingTime <= this.StagnationMinTime)
            return true;
        double probability = current_time - this.BestSolutionReachingTime - this.StagnationMinTime;
        probability /= (double) (current_time - this.StartTime);
        return ThreadLocalRandom.current().nextDouble() > probability;
    }
    
    /**
     * Picks the fittest of {@code TournamentSize} randomly drawn individuals.
     *
     * @return the tournament winner
     */
    private GiantTour tournamentSelection() {
        GiantTour bestInTournament = null;
        for (int i = 0; i < this.TournamentSize; i++) {
            GiantTour randomCompetitor = this.Population[ThreadLocalRandom.current().nextInt(this.PopulationSize)];
            if (bestInTournament == null || randomCompetitor.getFitness() < bestInTournament.getFitness())
                bestInTournament = randomCompetitor;
        }
        return bestInTournament;
    }
    
    /**
     * @return the worst individual in the (sorted) population
     */
    private GiantTour getLast() {
        return this.Population[this.PopulationSize - 1];
    }
}
