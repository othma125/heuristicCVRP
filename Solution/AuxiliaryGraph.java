package Solution;

import Data.InputData;
import Solution.LSM.LocalSearchMove;
import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Othmane
 */
public class AuxiliaryGraph {
    
    private final boolean LSM;
    private final int Length;
    private final double Bound;
    private final GiantTour[] GiantTours;
    private final AuxiliaryGraphNode[] Nodes;
    private final InputData Data;
    private final Set<ArcSetter> ArcsSetters;
    private final int AvailableProcessorCors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
    private final ExecutorService Executor = Executors.newFixedThreadPool(this.AvailableProcessorCors);

    AuxiliaryGraph(InputData data, double bound, boolean lsm, GiantTour ... giant_tours) {
        this.Data = data;
        this.Bound = bound;
        this.LSM = lsm;
        this.GiantTours = giant_tours;
        this.Length = this.GiantTours[0].Sequence.length;
        this.Nodes = IntStream.range(0, this.Length + 1)
                                .mapToObj(AuxiliaryGraphNode::new)
                                .toArray(AuxiliaryGraphNode[]::new);
        this.ArcsSetters = ConcurrentHashMap.newKeySet();
        Stream.of(this.GiantTours).map(gt -> new ArcSetter(this.Nodes[0], gt))
                                    .peek(this.ArcsSetters::add)
                                    .forEach(this.Executor::submit);
        synchronized (this.ArcsSetters) {
            try {
                this.ArcsSetters.wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(AuxiliaryGraph.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        this.Executor.shutdown();
    }

    private void setNewSetters(AuxiliaryGraphNode node) {
        node.Lock.lock();
        try {
            if (node.getLabel() < AuxiliaryGraph.this.Bound && this.ArcsSetters.stream().allMatch(setter -> setter.StartingNode.NodeIndex != node.NodeIndex && setter.NodeProcessingWith >= node.NodeIndex)) {     
                Stream.of(this.GiantTours)
                        .map(gt -> new ArcSetter(node, gt))
                        .peek(this.ArcsSetters::add)
                        .forEach(this.Executor::submit);
            }
        } finally {
            node.Lock.unlock();
        }
    }
    class ArcSetter implements Callable<Void> {

        private final AuxiliaryGraphNode StartingNode;
        private final GiantTour GiantTour;
        private final Solution Solution;
        private int NodeProcessingWith;

        ArcSetter(AuxiliaryGraphNode node, GiantTour gt) {
            this.StartingNode = node;
            this.Solution = this.StartingNode.getBestSolution();
            this.GiantTour = gt;
            this.NodeProcessingWith = this.StartingNode.NodeIndex;
        }

        @Override
        public int hashCode() {
            int hash = 31 * this.StartingNode.NodeIndex;
            if (Double.isFinite(this.GiantTour.getFitness()))
                hash += Double.hashCode(this.GiantTour.getFitness());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) 
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final ArcSetter other = (ArcSetter) obj;
            if (this.StartingNode.NodeIndex != other.StartingNode.NodeIndex || this.GiantTour != other.GiantTour)
                return false;
            return true;
        }

        @Override
        public Void call() {
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
                    this.Foreward(EndingNode);
                    continue;
                }
                while (sequence_as_list.size() < length) {
                    int stop = this.GiantTour.getStop(j++ % AuxiliaryGraph.this.Length);
                    if (this.Solution == null || !this.Solution.contains(stop)) {
                        cumulative_demand += AuxiliaryGraph.this.Data.getDemand(stop);
                        if (sequence_as_list.isEmpty())
                            cumulative_distance += AuxiliaryGraph.this.Data.getDepotToStopDistance(stop);
                        else
                            cumulative_distance += AuxiliaryGraph.this.Data.getTwoStopsDistance(sequence_as_list.getLast(), stop);
                        sequence_as_list.add(stop);
                    }
                }
                int[] sequence_as_array = sequence_as_list.stream().mapToInt(stop -> (int) stop).toArray();
                Route new_route = new Route(sequence_as_array, cumulative_demand, cumulative_distance + AuxiliaryGraph.this.Data.getStopToDepotDistance(sequence_as_list.getLast()));
                if ((this.Solution == null ? 1 : this.Solution.getRoutesCount() + 1) <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()
                    && cumulative_demand <= AuxiliaryGraph.this.Data.getCapacity()) {
                    if (!EndingNode.UpdateLabel(this.Solution, new_route) && AuxiliaryGraph.this.LSM) {
                        new_route.LocalSearch(AuxiliaryGraph.this.Data);
                        EndingNode.UpdateLabel(this.Solution, new_route);
                    }
                }
                boolean c = true;
                if (this.Solution != null)
                    for (Route old_route : this.Solution.getRoutes()) {
                        final int combined_demand = old_route.getSumDemand() + cumulative_demand;
                        if (combined_demand <= AuxiliaryGraph.this.Data.getCapacity()
                            && this.Solution.getRoutesCount() <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()) {
                            int[] combined_sequence1 = IntStream.range(0, old_route.getLength() + length)
                                                                .map(index -> {
                                                                    if (index < old_route.getLength())
                                                                        return old_route.getStop(index);
                                                                    return sequence_as_array[index - old_route.getLength()];
                                                                })
                                                                .toArray();
                            Route combined_route1 = new Route(AuxiliaryGraph.this.Data, combined_sequence1);
                            if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route1) && AuxiliaryGraph.this.LSM) {
                                combined_route1.LocalSearch(AuxiliaryGraph.this.Data);
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route1);
                            }
                            int[] combined_sequence2 = IntStream.range(0, old_route.getLength() + length)
                                                                .map(index -> {
                                                                    if (index < sequence_as_array.length)
                                                                        return sequence_as_array[index];
                                                                    return old_route.getStop(index - sequence_as_array.length);
                                                                })
                                                                .toArray();
                            Route combined_route2 = new Route(AuxiliaryGraph.this.Data, combined_sequence2);
                            if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route2) && AuxiliaryGraph.this.LSM) {
                                combined_route2.LocalSearch(AuxiliaryGraph.this.Data);
                                EndingNode.UpdateLabel(this.Solution, old_route, combined_route2);
                            }
                        }
                        else if (combined_demand <= 2 * AuxiliaryGraph.this.Data.getCapacity()
                            && this.Solution.getRoutesCount() + 1 <= AuxiliaryGraph.this.Data.getMaxVehicleNumber()) {
                            c = false;
                            LocalSearchMove lsm = old_route.getLSM(AuxiliaryGraph.this.Data, new_route);
                            if (lsm != null && lsm.getGain() + new_route.getTraveledDistance() + this.Solution.getTotalDistance() < EndingNode.getLabel()) {
                                lsm.Perform(AuxiliaryGraph.this.Data);
                                EndingNode.UpdateLabel(this.Solution, old_route, lsm.getFirstRoute(), lsm.getSecondRoute());
                            }
                        }
                    }
                if (c && cumulative_demand > AuxiliaryGraph.this.Data.getCapacity()) {
                    this.Break(EndingNode);
                    break;
                }
                this.Foreward(EndingNode);
            }
            return null;
        }
        
        private void Break(AuxiliaryGraphNode node) {
            this.NodeProcessingWith = AuxiliaryGraph.this.Length;
            AuxiliaryGraph.this.ArcsSetters.remove(this);
            if (AuxiliaryGraph.this.ArcsSetters.isEmpty()) {
                synchronized (AuxiliaryGraph.this.ArcsSetters) {
                    AuxiliaryGraph.this.ArcsSetters.notifyAll();
                }
            }
            else if (node.NodeIndex < AuxiliaryGraph.this.Length)
                AuxiliaryGraph.this.setNewSetters(node);
        }
        
        private void Foreward(AuxiliaryGraphNode node) {
            this.NodeProcessingWith++;
            if (this.NodeProcessingWith == AuxiliaryGraph.this.Length) {
                AuxiliaryGraph.this.ArcsSetters.remove(this);
                if (AuxiliaryGraph.this.ArcsSetters.isEmpty()) {
                    synchronized (AuxiliaryGraph.this.ArcsSetters) {
                        AuxiliaryGraph.this.ArcsSetters.notifyAll();
                    }
                }
                else if (node.NodeIndex < AuxiliaryGraph.this.Length)
                    AuxiliaryGraph.this.setNewSetters(node);
            }
            else if (node.NodeIndex < AuxiliaryGraph.this.Length)
                AuxiliaryGraph.this.setNewSetters(node);
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

    int[] getNewSequence() {
        return this.getLastNode().getNewSequence();
    }

    @Override
    public String toString() {
        return this.getLastNode().toString();
    }
}