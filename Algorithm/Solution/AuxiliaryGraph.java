// Author: Othmane

package Algorithm.Solution;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveAction;

import Algorithm.Data.InputData;

/**
 * The route-first/cluster-second split structure. Given one or more giant
 * tours, it builds a directed graph whose nodes are tour positions and whose
 * arcs are feasible routes (respecting capacity); the shortest source-to-sink
 * path is the optimal partition of the tour into vehicle routes.
 *
 * <p>Arcs are relaxed concurrently: each {@link ArcSetter} is a
 * {@link RecursiveAction} submitted to the common {@link ForkJoinPool}, and a
 * {@link Phaser} keeps the constructor blocked until the whole graph has been
 * explored. The {@code Bound} prunes partial solutions that cannot improve on
 * the incumbent cost.
 *
 * @author Othmane EL YAAKOUBI
 */
public class AuxiliaryGraph implements AutoCloseable {

    private final int Length;
    private final double Bound;
    private final int[][] Tours;
    private AuxiliaryGraphNode[] Nodes;
    private final InputData Data;
    private final Set<ArcSetter> ArcsSetters;
    private static final ExecutorService CleanupPool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auxiliary-graph-cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private final Phaser phaser = new Phaser(1);

    /**
     * Builds and fully explores the split graph for the given giant tours,
     * blocking until all arcs have been relaxed.
     *
     * @param data        the problem instance
     * @param bound       cost upper bound used to prune partial solutions
     * @param giant_tours one or more tours to split (more than one enables the graph-based crossover)
     */
    AuxiliaryGraph(InputData data, double bound, GiantTour ... giant_tours) {
        this.Data = data;
        this.Bound = bound;
        // Snapshot the parents once: the individuals being recombined are re-split by other
        // threads, which swaps their sequence, and every walk must see one stable permutation.
        this.Tours = new int[giant_tours.length][];
        for (int i = 0; i < giant_tours.length; i++)
            this.Tours[i] = giant_tours[i].getSequenceSnapshot();
        this.Length = this.Tours[0].length;
        this.Nodes = new AuxiliaryGraphNode[this.Length + 1];
        for (int i = 0; i <= this.Length; i++) 
            this.Nodes[i] = new AuxiliaryGraphNode(i);
        this.ArcsSetters = ConcurrentHashMap.newKeySet();
        for (int[] tour : this.Tours) {
            if (data.isStopRequested())
                break;
            ArcSetter setter = new ArcSetter(this, this.Nodes[0], null, tour);
            this.ArcsSetters.add(setter);
            this.phaser.register();
            ForkJoinPool.commonPool().execute(setter);
        }
        this.phaser.arriveAndAwaitAdvance();
        // assert this.ArcsSetters.isEmpty() : "setters set not empty" + this.ArcsSetters.size();
        if (this.isFeasible())
            this.getLastNode().getSolutions()
                                .stream()
                                .forEach(s -> s.InterRoutesLocalSearch(this.Data));
    }

    /**
     * Spawns successor arc setters from {@code node} once every setter still
     * running has advanced past it, so the node's labels are final before they
     * are extended. Solutions above the pruning bound are skipped.
     *
     * @param node the node whose outgoing arcs should be scheduled
     */
    void setNewSetters(AuxiliaryGraphNode node) {
        if (this.Data.isStopRequested())
            return;
        // A stopped run spawns no further arcs: the setters still in flight drain, the
        // phaser advances, and the constructor returns instead of exploring the graph.
        if (!node.isFeasible() || node.NodeIndex == this.Length)
            return;
        node.Lock.lock();
        try {
            boolean allMatch = true;
            for (ArcSetter setter : this.ArcsSetters) 
                if (setter.StartingNode == node || setter.NodeProcessingWith < node.NodeIndex) {
                    allMatch = false;
                    break;
                }
            if (allMatch) 
                for (Solution solution : Double.isInfinite(this.Bound) ? node.getParetoSet() : node.getSolutions())
                    if (solution.getTotalDistance() < this.Bound) 
                        for (int[] tour : this.Tours) {
                            ArcSetter setter = new ArcSetter(this, node, solution, tour);
                            this.ArcsSetters.add(setter);
                            this.phaser.register();
                            ForkJoinPool.commonPool().execute(setter);
                        }
        } finally {
            node.Lock.unlock();
        }
    }
    
    /**
     * @return the sink node (end of the tour)
     */
    AuxiliaryGraphNode getLastNode() {
        return this.getNode(this.Length);
    }

    /**
     * @param i node index
     * @return the node at the given index
     */
    AuxiliaryGraphNode getNode(int i) {
        return this.Nodes[i];
    }

    /**
     * @return {@code true} if the sink node was reached, i.e. a full split
     *         exists
     */
    boolean isFeasible() {
        return this.getLastNode().isFeasible();
    }

    /**
     * @return the cost of the optimal split (sink node label)
     */
    double getLabel() {
        return this.getLastNode().getLabel();
    }

    /**
     * @return the number of routes in the optimal split
     */
    int getRoutesCount() {
        return this.getLastNode().getRoutesCount();
    }

    /**
     * @return the number of candidate solutions at the sink node
     */
    int getSolutionsCount() {
        return this.getLastNode().getSolutions().size();
    }

    /**
     * @return the CVRPLIB route listing of the optimal split
     */
    String export() {
        return this.getLastNode().export();
    }

    /**
     * Returns the flattened giant-tour sequence of the optimal split. Inter-route
     * local search is applied to the sink's Pareto set in the constructor, so the
     * returned sequence already reflects those improvements.
     *
     * @param data the problem instance
     * @return the flattened sequence of the optimal split
     */
    int[] getNewSequence(InputData data) {
        return this.getLastNode().getNewSequence(data);
    }

    @Override
    public String toString() {
        return this.getLastNode().toString();
    }

    /**
     * Releases the graph by closing all of its nodes and dropping the node
     * array. Runs on a background thread so the caller does not block on the
     * teardown of a large graph.
     */
    @Override
    public void close() {
        AuxiliaryGraphNode[] nodes = this.Nodes;
        this.Nodes = null;
        CleanupPool.execute(() -> {
            for (AuxiliaryGraphNode node : nodes)
                node.close();
        });
    }

    // Getter methods for ArcSetter access
    int getLength() {
        return this.Length;
    }

    InputData getData() {
        return this.Data;
    }

    int[][] getTours() {
        return this.Tours;
    }

    Phaser getPhaser() {
        return this.phaser;
    }

    Set<ArcSetter> getArcsSetters() {
        return this.ArcsSetters;
    }
}
