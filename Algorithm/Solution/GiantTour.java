// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * The chromosome of the genetic algorithm: a permutation of all customers with
 * no depot markers (a "giant tour"). Feasible vehicle routes are obtained on
 * demand by the graph-based split procedure ({@link AuxiliaryGraph}), whose
 * shortest path defines the optimal partition of the tour into routes. Fitness
 * is the cost of that split; giant tours are comparable by fitness.
 *
 * @author Othmane EL YAAKOUBI
 */
public class GiantTour implements Comparable<GiantTour> {

    public int[] Sequence;
    public AuxiliaryGraph AuxiliaryGraph = null;

    /**
     * Snapshot copy: captures the tour's current sequence and split.
     * {@link #Split(InputData)} replaces those fields rather than mutating
     * them, so re-splitting {@code gt} leaves this copy untouched.
     *
     * @param gt the giant tour to snapshot
     */
    public GiantTour(GiantTour gt) {
        this.Sequence = gt.Sequence == null ? null : gt.Sequence.clone();
        this.AuxiliaryGraph = gt.AuxiliaryGraph;
    }

    /**
     * Creates a random giant tour and immediately splits it into routes.
     *
     * @param data the problem instance
     */
    public GiantTour(InputData data) {
        this.setRandomGiantTour(data);
        this.Split(data);
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
            if (gt.isFeasible()) {
                double fitness = gt.getFitness();
                if (fitness > bound) 
                    bound = fitness;
            }
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, giant_tours);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
            this.Sequence = this.AuxiliaryGraph.getNewSequence(data);
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
     * @param feasibility_index the furthest feasible node reached so far, used
     *                          to detect and stop non-progressing retries
     */
    private void Split(InputData data, double bound, int feasibility_index) {
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, this);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
            this.Sequence = this.AuxiliaryGraph.getNewSequence(data);
        }
        else {
            int k = 0;
            while (graph.getNode(++k).isFeasible()) {}
            int[] partial_sequence = graph.getNode(k - 1).getNewSequence(data);
            System.arraycopy(partial_sequence, 0, this.Sequence, 0, partial_sequence.length);
            if (k > feasibility_index) {
                for (int i = partial_sequence.length; i < this.Sequence.length; i++) {
                    int j = ThreadLocalRandom.current().nextInt(partial_sequence.length);
                    Move move = new Move(i, j);
                    move.Swap(this.Sequence);
                }
                this.Split(data, bound, k);
            }
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
            Move move = new Move(i, j);
            move.Swap(this.Sequence);
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
        return Double.compare(this.getFitness() * 100d , gt.getFitness() * 100d);
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
}