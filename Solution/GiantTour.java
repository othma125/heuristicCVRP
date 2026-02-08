package Solution;

import Data.InputData;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        double bound = Stream.of(giant_tours).filter(GiantTour::isFeasible)
                                            .mapToDouble(GiantTour::getFitness)
                                            .max()
                                            .getAsDouble();
        if (giant_tours.length > 1) {
            AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, false, giant_tours);
            if (graph.isFeasible()) {
                this.AuxiliaryGraph = graph;
                this.Sequence = this.AuxiliaryGraph.getNewSequence();
                this.Split(data, this.getFitness(), true);
            }
        }
        else {
            this.Sequence = giant_tours[0].Sequence.clone();
            this.Split(data, bound, true);
        }
    }
    
    private void Split(InputData data) {
        this.Split(data, Double.POSITIVE_INFINITY, false);
    }
    
    private void Split(InputData data, double bound, boolean lsm) {
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, lsm, this);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
//            this.AuxiliaryGraph.LocalSearch(data);
            this.setNewSequence();
        }
    }

    private void setRandomGiantTour(InputData data) {
        this.Sequence = IntStream.range(0, data.getDimension() - 1).toArray();
        int max = this.Sequence.length / 2;
        IntStream.range(0, max)
                .mapToObj(i -> new Move(i, (int) (Math.random() * this.Sequence.length)))
                .forEach(move -> move.Swap(this.Sequence));
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
        return this.AuxiliaryGraph == null ? Double.POSITIVE_INFINITY : this.AuxiliaryGraph.getLabel();
    }

    public int getRoutesCount() {
        return this.AuxiliaryGraph.getRoutesCount();
    }

    public boolean isFeasible() {
        return this.AuxiliaryGraph == null ? false : this.AuxiliaryGraph.isFeasible();
    }
    
    public void LocalSearch(InputData data) {
        if (this.AuxiliaryGraph != null && this.isFeasible())
            this.AuxiliaryGraph.LocalSearch(data);
    }

    private void setNewSequence() {
//        int length = this.Sequence.length;
        this.Sequence = this.AuxiliaryGraph.getNewSequence();
//        if (this.Sequence.length != length)
//            System.exit(0);
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

    int indexOf(int stop) {
        int index = 0;
        while (this.Sequence[index] != stop)
            index++;
        return index;
    }
}