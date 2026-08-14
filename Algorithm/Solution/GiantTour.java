// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * The chromosome of the genetic algorithm: a permutation of all customers with
 * no depot markers (a "giant tour"). Feasible vehicle routes are obtained on
 * demand by the graph-based split procedure ({@link AuxiliaryGraph}), whose
 * shortest path defines the optimal partition of the tour into routes. Fitness
 * is the cost of that split; giant tours are comparable by fitness.
 *
 * @author Othmane EL YAAKOUBI
 */
public class GiantTour implements Comparable<GiantTour>, AutoCloseable {

    public int[] Sequence;
    public AuxiliaryGraph AuxiliaryGraph = null;

    /**
     * Creates a random giant tour and optionally splits it into routes.
     *
     * @param data  the problem instance
     * @param split if {@code true}, the tour is immediately split into routes
     */
    public GiantTour(InputData data, boolean split) {
        this.setRandomGiantTour(data);
        if (split)
            this.Split(data);
    }

    /**
     * Creates a random giant tour and immediately splits it into routes.
     *
     * @param data the problem instance
     */
    public GiantTour(InputData data) {
        this(data, true);
    }

    /**
     * Creates a giant tour from a given sequence.
     *
     * @param seq the customer sequence to use
     */
    public GiantTour(int[] seq) {
        this.Sequence = seq;
    }

    /**
     * Graph-based crossover: builds a giant tour by combining the given parent
     * tours through the auxiliary graph, keeping the best subsequences bounded
     * by the parents' fitness. The result is only feasible if the combined
     * graph yields a complete split.
     *
     * @param data        the problem instance
     * @param giant_tours the parent tours to recombine
     */
    public GiantTour(InputData data, GiantTour ... giant_tours) {
        double bound = Double.NEGATIVE_INFINITY;
        for (GiantTour gt : giant_tours) 
            if (gt.getFitness() > bound) 
                bound = gt.getFitness();
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, giant_tours);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
            this.Sequence = this.AuxiliaryGraph.getNewSequence(data);
        }
        else {
            // an infeasible child still has to be a usable parent: keep a sequence
            this.Sequence = giant_tours[0].Sequence.clone();
            graph.close();
        }
    }
    
    /**
     * Re-runs the split procedure on the current sequence, using the current
     * fitness as the pruning bound.
     *
     * @param data the problem instance
     */
    public void Split(InputData data) {
        this.Split(data, this.getFitness(), 0);
    }

    /**
     * Splits the giant tour into routes via the auxiliary graph. When the graph
     * is infeasible, the feasible prefix is kept and the remaining tail is
     * randomly shuffled before retrying, as long as progress is being made.
     *
     * @param data              the problem instance
     * @param bound             cost upper bound used to prune the graph
     * @param feasibility_index the furthest feasible node reached so far, used to detect and stop non-progressing retries
     */
    private void Split(InputData data, double bound, int feasibility_index) {
        if (this.AuxiliaryGraph == null) {
            AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, this);
            if (graph.isFeasible()) 
                this.AuxiliaryGraph = graph;
            // A stopped run leaves the tour infeasible rather than reshuffling and re-splitting:
            // the graph above was abandoned half-built, so its partial prefix is not worth keeping.
            else if (!data.isStopRequested()) {
                int k = 0;
                while (graph.getNode(++k).isFeasible()) {}
                int[] partial_sequence = graph.getNode(k - 1).getNewSequence(data);
                System.arraycopy(partial_sequence, 0, this.Sequence, 0, partial_sequence.length);
                if (k > feasibility_index) {
                    for (int i = partial_sequence.length; i < this.Sequence.length; i++) {
                        int j = ThreadLocalRandom.current().nextInt(partial_sequence.length);
                        new Move(i, j).Swap(this.Sequence);
                    }
                    this.Split(data, bound, k);
                }
            }
            if (!graph.isFeasible())
                graph.close();
        }
        else {
            var feasibleTours = this.AuxiliaryGraph.getLastNode()
                                                    .getSolutions()
                                                    .parallelStream()
                                                    .map(solution -> new GiantTour(solution.getNewSequence()))
                                                    .peek(gt -> gt.Split(data, bound, 0))
                                                    .filter(GiantTour::isFeasible)
                                                    .collect(Collectors.toList());
            GiantTour best = feasibleTours.stream()
                                          .min(Comparator.comparingDouble(GiantTour::getFitness))
                                          .orElse(null);
            for (GiantTour gt : feasibleTours) 
                if (gt != best)
                    gt.close();
            if (best != null) {
                this.Sequence = best.Sequence;
                this.AuxiliaryGraph.close();
                this.AuxiliaryGraph = best.AuxiliaryGraph;
            }
            feasibleTours.clear();
        }
    }

    /**
     * Initialises the sequence to the identity permutation of all customers and
     * randomly shuffles it with a series of swaps.
     *
     * @param data the problem instance
     */
    private void setRandomGiantTour(InputData data) {
        this.Sequence = IntStream.range(0, data.getDimension() - 1).toArray();
        int max = this.Sequence.length / 2;
        for (int k = 0; k < max; k++) {
            int i = ThreadLocalRandom.current().nextInt(this.Sequence.length);
            int j = ThreadLocalRandom.current().nextInt(this.Sequence.length);
            new Move(i, j).Swap(this.Sequence);
        }
    }
    
    /**
     * @param i position in the sequence
     * @return the stop at the given position
     */
    public int getStop(int i) {
        return this.Sequence[i];
    }

    /**
     * @return the number of stops in the tour
     */
    public int getLength() {
        return this.Sequence.length;
    }

    @Override
    public String toString() {
        return this.AuxiliaryGraph.toString();
    }

    /**
     * @return the cost of the best split, or {@link Double#POSITIVE_INFINITY}
     *         if the tour has no feasible split
     */
    public double getFitness() {
        return this.isFeasible() ? this.AuxiliaryGraph.getLabel() : Double.POSITIVE_INFINITY;
    }

    /**
     * @return the number of routes in the best split
     */
    public int getRoutesCount() {
        return this.AuxiliaryGraph.getRoutesCount();
    }

    /**
     * @return {@code true} if the tour has a feasible split into routes
     */
    public boolean isFeasible() {
        return this.AuxiliaryGraph == null ? false : this.AuxiliaryGraph.isFeasible();
    }

    /**
     * Orders giant tours by ascending fitness (split cost).
     *
     * @param gt the giant tour to compare against
     * @return a negative value, zero or a positive value as this tour is
     *         fitter than, equal to, or worse than {@code gt}
     */
    @Override
    public int compareTo(GiantTour gt) {
        if (this == gt)
            return 0;
        return Double.compare(this.getFitness(), gt.getFitness());
    }

    /**
     * @return the CVRPLIB route listing of the best split, or {@code "NULL"} if
     *         infeasible
     */
    private String export() {
        return this.AuxiliaryGraph == null ? "NULL" : this.AuxiliaryGraph.export();
    }

    /**
     * Writes the solution to {@code Output/<instance>/<instance> Cost = N.sol}
     * in CVRPLIB route format followed by a {@code Cost} line.
     *
     * @param data the problem instance (used for the instance name)
     * @throws IOException if the output file cannot be written
     */
    public void export(InputData data) throws IOException {
        String instanceName = new File(data.FileName).getName().replaceFirst("\\.vrp$", "");
        File baseDir = new File("Output");
        File instanceDir = new File(baseDir, instanceName);
        instanceDir.mkdirs();
        String fileName = "Instance = " + instanceName + " Cost = " + (int) this.getFitness() + ".sol";
        File outFile = new File(instanceDir, fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            bw.write(this.export());
            bw.newLine();
            bw.write("Cost " + (int) this.getFitness());
            bw.newLine();
        }
    }

    /**
     * Releases the giant tour by closing its auxiliary graph, if any, and
     * dropping the sequence.
     */
    @Override
    public void close() {
        if (this.AuxiliaryGraph != null)
            this.AuxiliaryGraph.close();
        this.Sequence = null;
    }
}