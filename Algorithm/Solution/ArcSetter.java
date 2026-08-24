// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.LocalSearchMove;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A parallel task that, starting from one node and one partial solution,
 * grows candidate routes stop by stop along a giant tour and relaxes the
 * labels of the downstream nodes until capacity is exhausted.
 *
 * @author Othmane EL YAAKOUBI
 */
public class ArcSetter extends RecursiveAction {

    private final AuxiliaryGraph graph;
    final AuxiliaryGraphNode StartingNode;
    final GiantTour GiantTour;
    final Solution Solution;
    volatile int NodeProcessingWith;

    /**
     * @param graph   the auxiliary graph this setter belongs to
     * @param node    the node this setter starts from
     * @param solution the partial solution reaching {@code node}, or {@code null} for the source
     * @param gt      the giant tour whose ordering guides route growth
     */
    ArcSetter(AuxiliaryGraph graph, AuxiliaryGraphNode node, Solution solution, GiantTour gt) {
        this.graph = graph;
        this.StartingNode = node;
        this.Solution = solution;
        this.GiantTour = gt;
        this.NodeProcessingWith = this.StartingNode.NodeIndex;
    }

    /**
     * Walks forward from the starting node, accumulating stops into a
     * candidate route and, at each reachable node, relaxing its label with
     * the new route (and with routes merged into or split from the existing
     * solution, those taken in random order). Stops once capacity is exceeded,
     * then deregisters from the graph's {@link Phaser}.
     */
    @Override
    protected void compute() {
        try {
            final InputData data = this.graph.getData();
            int i = this.StartingNode.NodeIndex;
            int j = this.StartingNode.NodeIndex;
            int length = 0;
            int cumulative_demand = 0;
            double cumulative_distance = 0d;
            // The walk grows this buffer and copies it out per candidate route: an int[]
            // keeps the accumulation free of boxing and makes reading the stop just added
            // an array access instead of a linked list traversal.
            int[] sequence = new int[16];
            int size = 0;
            // random route order: the first improving combination wins, so a fixed order
            // would always favour the same routes. The solution is not mutated during the
            // walk, so one shuffle up front serves every node.
            final List<Route> old_routes;
            if (this.Solution != null) {
                old_routes = new ArrayList<>(this.Solution.getRoutes());
                Collections.shuffle(old_routes, ThreadLocalRandom.current());
            }
            else
                old_routes = null;
            // Setters already queued in the pool when the stop arrived would otherwise each
            // walk the whole tour running local search, so the walk checks the flag too.
            while (i < this.graph.getLength() && !data.isStopRequested()) {
                length++;
                AuxiliaryGraphNode EndingNode = this.graph.getNode(++i);
                // if (this.Solution != null 
                //     && (this.Solution.getTotalDistance() >= EndingNode.getLabel() || this.Solution.getLeftoverLoad() >= EndingNode.getLeftoverLoad())) {
                //     this.NodeProcessingWith++;
                //     this.graph.setNewSetters(EndingNode);
                //     continue;
                // }
                while (size < length) {
                    int stop = this.GiantTour.getStop(j++ % this.graph.getLength());
                    if (this.Solution == null || !this.Solution.contains(stop)) {
                        cumulative_demand += data.getDemand(stop);
                        cumulative_distance += size == 0 ? data.getDepotToStopDistance(stop)
                                                        : data.getTwoStopsDistance(sequence[size - 1], stop);
                        if (size == sequence.length)
                            sequence = Arrays.copyOf(sequence, 2 * size);
                        sequence[size++] = stop;
                    }
                }
                double distance = cumulative_distance + data.getStopToDepotDistance(sequence[size - 1]);
                // The route owns its sequence and permutes it in place, so it gets a copy.
                Route new_route = new Route(data, Arrays.copyOf(sequence, size), cumulative_demand, distance);
                if ((this.Solution == null ? 0 : this.Solution.getRoutesCount()) + 1 <= data.getMaxVehicleNumber()
                    && cumulative_demand <= data.getCapacity()) {
                    if (!EndingNode.UpdateLabel(this.Solution, new_route)) {
                        new_route.IntraRoutesLocalSearch(data);
                        EndingNode.UpdateLabel(this.Solution, new_route);
                    }
                }
                if (this.Solution != null) {
                    // The combined routes use the pre-local-search order of the new route
                    // (the snapshot in `sequence`), not the post-LS order of `new_route`:
                    // the original code built them from `sequence_as_array` taken before
                    // IntraRoutesLocalSearch permuted the route, and the combined route is
                    // a different route whose optimum is not the standalone route's optimum.
                    final int new_len = size;
                    for (Route old_route : old_routes) {
                        final int combined_demand = old_route.getSumDemand() + cumulative_demand;
                        if (combined_demand <= data.getCapacity() && this.Solution.getRoutesCount() <= data.getMaxVehicleNumber()) {
                            int[] combined_sequence1 = new int[old_route.getLength() + new_len];
                            System.arraycopy(old_route.getSequence(), 0, combined_sequence1, 0, old_route.getLength());
                            System.arraycopy(sequence, 0, combined_sequence1, old_route.getLength(), new_len);
                            Route combined_route1 = new Route(data, combined_sequence1);
                            if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route1)) {
                                combined_route1.IntraRoutesLocalSearch(data);
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route1);  
                            }
                            int[] combined_sequence2 = new int[old_route.getLength() + new_len];
                            System.arraycopy(sequence, 0, combined_sequence2, 0, new_len);
                            System.arraycopy(old_route.getSequence(), 0, combined_sequence2, new_len, old_route.getLength());
                            Route combined_route2 = new Route(data, combined_sequence2);
                            if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route2)) {
                                combined_route2.IntraRoutesLocalSearch(data);
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route2);
                            }
                        }
                        if (combined_demand <= 2 * data.getCapacity() && this.Solution.getRoutesCount() + 1 <= data.getMaxVehicleNumber()) {
                            LocalSearchMove lsm = old_route.getLSM(data, new_route);
                            if (lsm != null) {
                                lsm.Perform(data);
                                EndingNode.UpdateLabel(data, this.Solution, old_route, lsm.getFirstRoute(), lsm.getSecondRoute());
                            }
                        }
                    }
                }
                if (cumulative_demand > data.getCapacity()) {
                    this.NodeProcessingWith = this.graph.getLength();
                    this.graph.setNewSetters(EndingNode);
                    break;
                }
                this.NodeProcessingWith++;
                this.graph.setNewSetters(EndingNode);
            }

        } finally {
            this.graph.getPhaser().arriveAndDeregister();
            this.graph.getArcsSetters().remove(this);
        }
    }

    @Override
    public int hashCode() {
        int hash = this.StartingNode.NodeIndex;
        if (this.graph.getGiantTours().length > 1)
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
        if (this.graph.getGiantTours().length > 1 && this.GiantTour.getStop(this.StartingNode.NodeIndex) != other.GiantTour.getStop(other.StartingNode.NodeIndex))
            return false;
        return this.Solution == null ? other.Solution == null : this.Solution.getTotalDistance() == other.Solution.getTotalDistance() && this.Solution.getRoutesCount() == other.Solution.getRoutesCount();
    }
}
