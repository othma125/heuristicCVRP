package Metaheuristics;

import Data.InputData;
import Solution.GiantTour;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

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
        while (this.runCrossovers() || this.nonStopCondition()) {}
        this.EndTime = System.currentTimeMillis() - this.StartTime;
        System.out.println();
    }

    /**
     * Runs one crossover per individual (a generation).
     *
     * @return {@code true} if any crossover improved the incumbent
     */
    private boolean runCrossovers() {
        boolean crossoverResult = false;
        for (int i = 0; i < this.PopulationSize; i++)
            if (this.Crossover())
                crossoverResult = true;
        return crossoverResult;
    }

    /**
     * Selects two parents by tournament and recombines them: a graph crossover
     * at the crossover rate, a crossover with a fresh random tour when the same
     * parent is drawn twice, otherwise a re-split of both parents.
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
            GiantTour random;
            do {
                random = new GiantTour(this.Data);
            } while (!random.isFeasible());
            GiantTour graph_crossover = new GiantTour(this.Data, parent1, random);
            return this.UpdatePopulation(graph_crossover); 
        }
        else {
            // repeat splitting procedure to discover more improvement possibilities
            parent1.Split(this.Data);
            parent2.Split(this.Data);
            Arrays.sort(this.Population);
            return this.setBestSolution(parent1) || this.setBestSolution(parent2);
        }
    }
    
    /**
     * Inserts an offspring into the population if it beats the worst
     * individual, replacing a random member of the worse half and re-sorting.
     * When the offspring becomes the new best, it is further recombined with
     * the best and a random individual.
     *
     * @param newGiantTour the candidate offspring
     * @return {@code true} if the offspring became the new incumbent
     */
    private boolean UpdatePopulation(GiantTour newGiantTour) {
        if (newGiantTour == null || !newGiantTour.isFeasible())
            return false;
        boolean c = false;
        if (newGiantTour.compareTo(this.getLast()) < 0) {
            int half = this.PopulationSize / 2;
            int randomIndex = half + ThreadLocalRandom.current().nextInt(this.Population.length - half);
            if (this.setBestSolution(newGiantTour)) {
                c = true;
                GiantTour graph_crossover = new GiantTour(this.Data, newGiantTour, this.Population[0], this.Population[randomIndex]);
                this.UpdatePopulation(graph_crossover);
            }
            this.Population[randomIndex] = newGiantTour;
            Arrays.sort(this.Population);
        }
        return c;
    }
    
    /**
     * Fills the population with feasible random giant tours, giving up on the
     * first slot after 100 failed attempts (which aborts the run), and sorts
     * the population by fitness.
     */
    private void InitialPopulation() {
        for (int i = 0; i < this.PopulationSize; i++) {
            int failure_count = 0;
            do {
                this.Population[i] = new GiantTour(this.Data);
                failure_count++;
            } while (!this.Population[i].isFeasible() && (i > 0 || failure_count < 100));
            if (i == 0 && failure_count == 100)
                return;
            this.setBestSolution(this.Population[i]);
        }
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