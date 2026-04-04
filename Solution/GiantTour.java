package Solution;

import Data.InputData;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.IntStream;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Othmane
 */
public class GiantTour implements Comparable<GiantTour> {

    public int[] Sequence;
    public AuxiliaryGraph AuxiliaryGraph = null;

    public GiantTour(InputData data) {
        this.setRandomGiantTour(data);
        this.Split(data);
    }
    
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
    
    public void Split(InputData data) {
        this.Split(data, this.getFitness(), 0);
    } 
    
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
                    int j = (int) (Math.random() * partial_sequence.length);
                    Move move = new Move(i, j);
                    move.Swap(this.Sequence);
                }
                this.Split(data, bound, k);
            }
        }
    }

    private void setRandomGiantTour(InputData data) {
        this.Sequence = IntStream.range(0, data.getDimension() - 1).toArray();
        int max = this.Sequence.length / 2;
        for (int k = 0; k < max; k++) {
            int i = (int) (Math.random() * this.Sequence.length);
            int j = (int) (Math.random() * this.Sequence.length);
            Move move = new Move(i, j);
            move.Swap(this.Sequence);
        }
    }
    
    public int getStop(int i) {
        return this.Sequence[i];
    }
    
    public int getLength() {
        return this.Sequence.length;
    }

    @Override
    public String toString() {
        return this.AuxiliaryGraph.toString();
    }

    public double getFitness() {
        return this.isFeasible() ? this.AuxiliaryGraph.getLabel() : Double.POSITIVE_INFINITY;
    }

    public int getRoutesCount() {
        return this.AuxiliaryGraph.getRoutesCount();
    }

    public boolean isFeasible() {
        return this.AuxiliaryGraph == null ? false : this.AuxiliaryGraph.isFeasible();
    }

    @Override
    public int compareTo(GiantTour gt) {
        return Double.compare(this.getFitness() * 100d , gt.getFitness() * 100d);
    }
    
    private String export() {
        return this.AuxiliaryGraph == null ? "NULL" : this.AuxiliaryGraph.export();
    }
    
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