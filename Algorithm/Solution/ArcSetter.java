// Author: Othmane

package Algorithm.Solution;

import Algorithm.Solution.LSM.LocalSearchMove;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveAction;

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
            while (i < this.graph.getLength() && !this.graph.getData().isStopRequested()) {
                length++;
                AuxiliaryGraphNode EndingNode = this.graph.getNode(++i);
                if (this.Solution != null && this.Solution.getTotalDistance() >= EndingNode.getLabel()) {
                    this.NodeProcessingWith++;
                    this.graph.setNewSetters(EndingNode);
                    continue;
                }
                while (sequence_as_list.size() < length) {
                    int stop = this.GiantTour.getStop(j++ % this.graph.getLength());
                    if (this.Solution == null || !this.Solution.contains(stop)) {
                        cumulative_demand += this.graph.getData().getDemand(stop);
                        if (sequence_as_list.isEmpty())
                            cumulative_distance += this.graph.getData().getDepotToStopDistance(stop);
                        else
                            cumulative_distance += this.graph.getData().getTwoStopsDistance(sequence_as_list.get(sequence_as_list.size() - 1), stop);
                        sequence_as_list.add(stop);
                    }
                }
                int[] sequence_as_array = sequence_as_list.stream().mapToInt(Integer::intValue).toArray();
                double distance = cumulative_distance + this.graph.getData().getStopToDepotDistance(sequence_as_list.get(sequence_as_list.size() - 1));
                Route new_route = new Route(this.graph.getData(), sequence_as_array, cumulative_demand, distance);
                if ((this.Solution == null ? 0 : this.Solution.getRoutesCount()) + 1 <= this.graph.getData().getMaxVehicleNumber()
                    && cumulative_demand <= this.graph.getData().getCapacity()) {
                    if (!EndingNode.UpdateLabel(this.Solution, new_route)) {
                        new_route.IntraRoutesLocalSearch(this.graph.getData());
                        EndingNode.UpdateLabel(this.Solution, new_route);
                    }
                }
                boolean c = true;
                if (this.Solution != null) 
                    for (Route old_route : this.Solution.getRoutes()) {
                        final int combined_demand = old_route.getSumDemand() + cumulative_demand;
                        if (combined_demand <= this.graph.getData().getCapacity()
                            && this.Solution.getRoutesCount() <= this.graph.getData().getMaxVehicleNumber()) {
                            int[] combined_sequence1 = new int[old_route.getLength() + length];
                            for (int index = 0; index < combined_sequence1.length; index++) {
                                if (index < old_route.getLength())
                                    combined_sequence1[index] = old_route.getStop(index);
                                else
                                    combined_sequence1[index] = sequence_as_array[index - old_route.getLength()];
                            }
                            Route combined_route1 = new Route(this.graph.getData(), combined_sequence1);
                            if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route1)) {
                                combined_route1.IntraRoutesLocalSearch(this.graph.getData());
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route1);  
                            }
                            int[] combined_sequence2 = new int[old_route.getLength() + length];
                            for (int index = 0; index < combined_sequence2.length; index++) {
                                if (index < sequence_as_array.length)
                                    combined_sequence2[index] = sequence_as_array[index];
                                else
                                    combined_sequence2[index] = old_route.getStop(index - sequence_as_array.length);
                            }
                            Route combined_route2 = new Route(this.graph.getData(), combined_sequence2);
                            if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route2)) {
                                combined_route2.IntraRoutesLocalSearch(this.graph.getData());
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route2);
                            }
                        }
                        if (combined_demand <= 2 * this.graph.getData().getCapacity()
                            && this.Solution.getRoutesCount() + 1 <= this.graph.getData().getMaxVehicleNumber()) {
                            c = false;
                            LocalSearchMove lsm = old_route.getLSM(this.graph.getData(), new_route);
                            if (lsm != null) {
                                lsm.Perform(this.graph.getData());
                                EndingNode.UpdateLabel(this.graph.getData(), this.Solution, old_route, lsm.getFirstRoute(), lsm.getSecondRoute());
                                break;
                            }
                        }
                    }
                if (c && cumulative_demand > this.graph.getData().getCapacity()) {
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
