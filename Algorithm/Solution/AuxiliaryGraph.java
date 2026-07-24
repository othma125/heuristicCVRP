// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.LocalSearchMove;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.Phaser;

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
public class AuxiliaryGraph {

    private final int Length;
    private final double Bound;
    private final GiantTour[] GiantTours;
    private final AuxiliaryGraphNode[] Nodes;
    private final InputData Data;
    private final Set<ArcSetter> ArcsSetters;
    private static final ForkJoinPool Pool = ForkJoinPool.commonPool();
    private final Phaser phaser = new Phaser(1);

    /**
     * Builds and fully explores the split graph for the given giant tours,
     * blocking until all arcs have been relaxed.
     *
     * @param data        the problem instance
     * @param bound       cost upper bound used to prune partial solutions
     * @param giant_tours one or more tours to split (more than one enables the
     *                    graph-based crossover)
     */
    AuxiliaryGraph(InputData data, double bound, GiantTour ... giant_tours) {
        this.Data = data;
        this.Bound = bound;
        this.GiantTours = giant_tours;
        this.Length = this.GiantTours[0].Sequence.length;
        this.Nodes = new AuxiliaryGraphNode[this.Length + 1];
        for (int i = 0; i <= this.Length; i++) 
            this.Nodes[i] = new AuxiliaryGraphNode(i);
        this.ArcsSetters = ConcurrentHashMap.newKeySet();
        for (GiantTour gt : this.GiantTours) {
            if (data.isStopRequested())
                break;
            ArcSetter setter = new ArcSetter(this.Nodes[0], null, gt);
            this.ArcsSetters.add(setter);
            this.phaser.register();
            this.Pool.execute(setter);
        }
        this.phaser.arriveAndAwaitAdvance();
    }

    /**
     * Spawns successor arc setters from {@code node} once every setter still
     * running has advanced past it, so the node's labels are final before they
     * are extended. Solutions above the pruning bound are skipped.
     *
     * @param node the node whose outgoing arcs should be scheduled
     */
    private void setNewSetters(AuxiliaryGraphNode node) {
        // A stopped run spawns no further arcs: the setters still in flight drain, the
        // phaser advances, and the constructor returns instead of exploring the graph.
        if (node.NodeIndex == this.Length || this.Data.isStopRequested())
            return;
        node.Lock.lock();
        try {
            boolean allMatch = true;
            for (ArcSetter setter : this.ArcsSetters) 
                if (setter.StartingNode.NodeIndex == node.NodeIndex || setter.NodeProcessingWith < node.NodeIndex) {
                    allMatch = false;
                    break;
                }
            if (allMatch) 
                for (Solution solution : node.getSolutions()) 
                    // if (solution.getTotalDistance() >= this.Bound || Math.random() < 0.1d) 
                        // solution.InterRoutesLocalSearch(this.Data);
                    if (solution.getTotalDistance() < this.Bound)
                        for (GiantTour gt : this.GiantTours) {
                            ArcSetter setter = new ArcSetter(node, solution, gt);
                            this.ArcsSetters.add(setter);
                            this.phaser.register();
                            this.Pool.execute(setter);
                        }
        } finally {
            node.Lock.unlock();
        }
    }
    
    /**
     * A parallel task that, starting from one node and one partial solution,
     * grows candidate routes stop by stop along a giant tour and relaxes the
     * labels of the downstream nodes until capacity is exhausted.
     */
    class ArcSetter extends RecursiveAction {

        private final AuxiliaryGraphNode StartingNode;
        private final GiantTour GiantTour;
        private final Solution Solution;
        private volatile int NodeProcessingWith;

        /**
         * @param node     the node this setter starts from
         * @param solution the partial solution reaching {@code node}, or
         *                 {@code null} for the source
         * @param gt       the giant tour whose ordering guides route growth
         */
        ArcSetter(AuxiliaryGraphNode node, Solution solution, GiantTour gt) {
            this.StartingNode = node;
            this.Solution = solution;
            this.GiantTour = gt;
            this.NodeProcessingWith = this.StartingNode.NodeIndex;
        }

        /**
         * Walks forward from the starting node, accumulating stops into a
         * candidate route and, at each reachable node, relaxing its label with
         * the new route (and with routes merged into or split from the existing
         * solution). Stops once capacity is exceeded, then deregisters from the
         * graph's {@link Phaser}.
         */
        @Override
        protected void compute() {
            try {
                int i = this.StartingNode.NodeIndex;
                int j = this.StartingNode.NodeIndex;
                int length = 0;
                int cumulative_demand = 0;
                double cumulative_distance = 0d;
                final List<Integer> sequence_as_list = new LinkedList<>();
                // Setters already queued in the pool when the stop arrived would otherwise each
                // walk the whole tour running local search, so the walk checks the flag too.
                while (i < AuxiliaryGraph.this.Length && !AuxiliaryGraph.this.Data.isStopRequested()) {
                    length++;
                    AuxiliaryGraphNode EndingNode = AuxiliaryGraph.this.getNode(++i);
                    if (this.Solution != null && this.Solution.getTotalDistance() >= EndingNode.getLabel()) {
                        this.NodeProcessingWith++;
                        AuxiliaryGraph.this.setNewSetters(EndingNode);
                        continue;
                    }
                    while (sequence_as_list.size() < length) {
                        int stop = this.GiantTour.getStop(j++ % AuxiliaryGraph.this.Length);
                        if (this.Solution == null || !this.Solution.contains(stop)) {
                            cumulative_demand += AuxiliaryGraph.this.Data.getDemand(stop);
                            if (sequence_as_list.isEmpty())
                                cumulative_distance += AuxiliaryGraph.this.Data.getDepotToStopDistance(stop);
                            else
                                cumulative_distance += AuxiliaryGraph.this.Data.getTwoStopsDistance(sequence_as_list.get(sequence_as_list.size() - 1), stop);
                            sequence_as_list.add(stop);
                        }
                    }
                    int[] sequence_as_array = sequence_as_list.stream().mapToInt(Integer::intValue).toArray();
                    Route new_route = new Route(sequence_as_array, cumulative_demand, cumulative_distance + AuxiliaryGraph.this.Data.getStopToDepotDistance(sequence_as_list.get(sequence_as_list.size() - 1)));
                    if ((this.Solution == null ? 0 : this.Solution.getRoutesCount()) + 1 <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()
                        && cumulative_demand <= AuxiliaryGraph.this.Data.getCapacity()) {
                        if (!EndingNode.UpdateLabel(this.Solution, new_route)) {
                            new_route.IntraRoutesLocalSearch(AuxiliaryGraph.this.Data);
                            EndingNode.UpdateLabel(this.Solution, new_route);
                        }
                    }
                    boolean c = true;
                    if (this.Solution != null) 
                        for (Route old_route : this.Solution.getRoutes()) {
                            final int combined_demand = old_route.getSumDemand() + cumulative_demand;
                            if (combined_demand <= AuxiliaryGraph.this.Data.getCapacity()
                                && this.Solution.getRoutesCount() <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()) {
                                int[] combined_sequence1 = new int[old_route.getLength() + length];
                                for (int index = 0; index < combined_sequence1.length; index++) {
                                    if (index < old_route.getLength())
                                        combined_sequence1[index] = old_route.getStop(index);
                                    else
                                        combined_sequence1[index] = sequence_as_array[index - old_route.getLength()];
                                }
                                Route combined_route1 = new Route(AuxiliaryGraph.this.Data, combined_sequence1);
                                if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route1)) {
                                    combined_route1.IntraRoutesLocalSearch(AuxiliaryGraph.this.Data);
                                    EndingNode.UpdateLabel(this.Solution, old_route, combined_route1);  
                                }
                                int[] combined_sequence2 = new int[old_route.getLength() + length];
                                for (int index = 0; index < combined_sequence2.length; index++) {
                                    if (index < sequence_as_array.length)
                                        combined_sequence2[index] = sequence_as_array[index];
                                    else
                                        combined_sequence2[index] = old_route.getStop(index - sequence_as_array.length);
                                }
                                Route combined_route2 = new Route(AuxiliaryGraph.this.Data, combined_sequence2);
                                if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route2)) {
                                    combined_route2.IntraRoutesLocalSearch(AuxiliaryGraph.this.Data);
                                    EndingNode.UpdateLabel(this.Solution, old_route, combined_route2);
                                }
                            }
                            if (combined_demand <= 2 * AuxiliaryGraph.this.Data.getCapacity()
                                && this.Solution.getRoutesCount() + 1 <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()) {
                                c = false;
                                LocalSearchMove lsm = old_route.getLSM(AuxiliaryGraph.this.Data, new_route);
                                if (lsm != null) {
                                    lsm.Perform(AuxiliaryGraph.this.Data);
                                    EndingNode.UpdateLabel(AuxiliaryGraph.this.Data, this.Solution, old_route, lsm.getFirstRoute(), lsm.getSecondRoute());
                                    break;
                                }
                            }
                        }
                    if (c && cumulative_demand > AuxiliaryGraph.this.Data.getCapacity()) {
                        this.NodeProcessingWith = AuxiliaryGraph.this.Length;
                        AuxiliaryGraph.this.setNewSetters(EndingNode);
                        break;
                    }
                    this.NodeProcessingWith++;
                    AuxiliaryGraph.this.setNewSetters(EndingNode);
                }

            } finally {
                AuxiliaryGraph.this.phaser.arriveAndDeregister();
                AuxiliaryGraph.this.ArcsSetters.remove(this);
            }
        }

        @Override
        public int hashCode() {
            int hash = this.StartingNode.NodeIndex;
            if (AuxiliaryGraph.this.GiantTours.length > 1)
                hash = 31 * hash + this.GiantTour.getStop(this.StartingNode.NodeIndex);
            return this.Solution != null ? 31 * hash + Double.hashCode(this.Solution.getTotalDistance()) : hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            ArcSetter other = (ArcSetter) obj;
            if (this.StartingNode.NodeIndex != other.StartingNode.NodeIndex)
                return false;
            if (AuxiliaryGraph.this.GiantTours.length > 1 && this.GiantTour.getStop(this.StartingNode.NodeIndex) != other.GiantTour.getStop(other.StartingNode.NodeIndex))
                return false;
            return this.Solution == null ? other.Solution == null : this.Solution.getTotalDistance() == other.Solution.getTotalDistance() && this.Solution.getRoutesCount() == other.Solution.getRoutesCount();
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
     * @return the CVRPLIB route listing of the optimal split
     */
    String export() {
        return this.getLastNode().export();
    }

    /**
     * Applies inter-route local search to the optimal split and returns its
     * flattened giant-tour sequence.
     *
     * @param data the problem instance
     * @return the improved sequence
     */
    int[] getNewSequence(InputData data) {
        return this.getLastNode().getNewSequence(data);
    }

    @Override
    public String toString() {
        return this.getLastNode().toString();
    }
}
