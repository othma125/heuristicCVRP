package Solution;

import Data.InputData;
import Solution.LSM.LocalSearchMove;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.Phaser;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AuxiliaryGraph {
    
    private final int Length;
    private final double Bound;
    private final GiantTour[] GiantTours;
    private final AuxiliaryGraphNode[] Nodes;
    private final InputData Data;
    private final Set<ArcSetter> ArcsSetters;
    private final ForkJoinPool Pool = new ForkJoinPool();
    private final Phaser phaser = new Phaser(1);

    AuxiliaryGraph(InputData data, double bound, GiantTour ... giant_tours) {
        this.Data = data;
        this.Bound = bound;
        this.GiantTours = giant_tours;
        this.Length = this.GiantTours[0].Sequence.length;
        this.Nodes = IntStream.range(0, this.Length + 1)
                                .mapToObj(AuxiliaryGraphNode::new)
                                .toArray(AuxiliaryGraphNode[]::new);
        this.ArcsSetters = ConcurrentHashMap.newKeySet();
        Stream.of(this.GiantTours)
                .map(gt -> new ArcSetter(this.Nodes[0], null, gt))
                .peek(this.ArcsSetters::add)
                .forEach(task -> {
                    this.phaser.register();
                    this.Pool.execute(task);
                });
        this.phaser.arriveAndAwaitAdvance();
        this.Pool.shutdown();
    }

    private void setNewSetters(AuxiliaryGraphNode node) {
        if (node.NodeIndex == this.Length)
            return;
        node.Lock.lock();
        try {
            if (this.ArcsSetters.stream().allMatch(setter -> setter.StartingNode.NodeIndex != node.NodeIndex && setter.NodeProcessingWith >= node.NodeIndex)) {
                node.getSolutions().stream()
                                    .filter(solution -> solution.getTotalDistance() < this.Bound)
                                    .flatMap(solution -> Stream.of(this.GiantTours).map(gt -> new ArcSetter(node, solution, gt)))
                                    .peek(this.ArcsSetters::add)
                                    .forEach(task -> {
                                        this.phaser.register();
                                        this.Pool.execute(task);
                                    });
            }
        } finally {
            node.Lock.unlock();
        }
    }
    
    class ArcSetter extends RecursiveAction {

        private final AuxiliaryGraphNode StartingNode;
        private final GiantTour GiantTour;
        private final Solution Solution;
        private int NodeProcessingWith;

        ArcSetter(AuxiliaryGraphNode node, Solution solution, GiantTour gt) {
            this.StartingNode = node;
            this.Solution = solution;
            this.GiantTour = gt;
            this.NodeProcessingWith = this.StartingNode.NodeIndex;
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

        @Override
        protected void compute() {
            try {
                int i = this.StartingNode.NodeIndex;
                int j = this.StartingNode.NodeIndex;
                int length = 0;
                int cumulative_demand = 0;
                double cumulative_distance = 0d;
                final List<Integer> sequence_as_list = new LinkedList<>();
                while (i < AuxiliaryGraph.this.Length) {
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
                                cumulative_distance += AuxiliaryGraph.this.Data.getTwoStopsDistance(
                                        sequence_as_list.get(sequence_as_list.size() - 1), stop);

                            sequence_as_list.add(stop);
                        }
                    }
                    int[] sequence_as_array = sequence_as_list.stream().mapToInt(s -> s).toArray();
                    Route new_route = new Route(sequence_as_array, cumulative_demand, cumulative_distance + AuxiliaryGraph.this.Data.getStopToDepotDistance(sequence_as_list.get(sequence_as_list.size() - 1)));
                    if ((this.Solution == null ? 1 : this.Solution.getRoutesCount() + 1) <= AuxiliaryGraph.this.Data.getMaxVehicleNumber() && cumulative_demand <= AuxiliaryGraph.this.Data.getCapacity()) {
                        new_route.IntraRoutesLocalSearch(AuxiliaryGraph.this.Data);
                        EndingNode.UpdateLabel(this.Solution, new_route);
                    }
                    boolean c = true;
                    if (this.Solution != null) 
                        for (Route old_route : this.Solution.getRoutes()) {
                            final int combined_demand = old_route.getSumDemand() + cumulative_demand;
                            if (combined_demand <= AuxiliaryGraph.this.Data.getCapacity() && this.Solution.getRoutesCount() <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()) {
                                int[] combined_sequence1 = IntStream.range(0, old_route.getLength() + length)
                                                                    .map(index -> {
                                                                        if (index < old_route.getLength())
                                                                            return old_route.getStop(index);
                                                                        return sequence_as_array[index - old_route.getLength()];
                                                                    })
                                                                    .toArray();
                                Route combined_route1 = new Route(AuxiliaryGraph.this.Data, combined_sequence1);
    //                            combined_route1.IntraRoutesLocalSearch(AuxiliaryGraph.this.Data);
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route1);
                                int[] combined_sequence2 = IntStream.range(0, old_route.getLength() + length)
                                                                    .map(index -> {
                                                                        if (index < sequence_as_array.length)
                                                                            return sequence_as_array[index];
                                                                        return old_route.getStop(index - sequence_as_array.length);
                                                                    })
                                                                    .toArray();
                                Route combined_route2 = new Route(AuxiliaryGraph.this.Data, combined_sequence2);
    //                            combined_route2.IntraRoutesLocalSearch(AuxiliaryGraph.this.Data);
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route2);
                            }
                            if (combined_demand <= 2 * AuxiliaryGraph.this.Data.getCapacity() && this.Solution.getRoutesCount() + 1 <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()) {
                                c = false;
                                LocalSearchMove lsm = old_route.getLSM(AuxiliaryGraph.this.Data, new_route);
                                if (lsm != null) {
                                    lsm.Perform(AuxiliaryGraph.this.Data);
                                    EndingNode.UpdateLabel(AuxiliaryGraph.this.Data, this.Solution, old_route, lsm.getFirstRoute(), lsm.getSecondRoute());
                                    // break;
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
    }

    AuxiliaryGraphNode getLastNode() {
        return this.getNode(this.Length);
    }

    AuxiliaryGraphNode getNode(int i) {
        return this.Nodes[i];
    }

    boolean isFeasible() {
        return this.getLastNode().isFeasible();
    }

    double getLabel() {
        return this.getLastNode().getLabel();
    }

    int getRoutesCount() {
        return this.getLastNode().getRoutesCount();
    }

    String export() {
        return this.getLastNode().export();
    }

    int[] getNewSequence(InputData data) {
        return this.getLastNode().getNewSequence(data);
    }

    @Override
    public String toString() {
        return this.getLastNode().toString();
    }
}
