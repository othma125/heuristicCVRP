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
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, false, giant_tours);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
            this.Sequence = this.AuxiliaryGraph.getNewSequence();
            this.Split(data, this.getFitness(), true);
        }
    }
    
    private void Split(InputData data) {
        this.Split(data, Double.POSITIVE_INFINITY, false);
    }
    
    public void Split(InputData data, double bound, boolean lsm) {
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, lsm, this);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
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
        if (this.isFeasible())
            this.Sequence = this.AuxiliaryGraph.getNewSequence();
    }

    @Override
    public int compareTo(GiantTour gt) {
        return Double.compare(this.getFitness() * 100d , gt.getFitness() * 100d);
    }
    
    private String export() {
        return this.AuxiliaryGraph == null ? "NULL" : this.AuxiliaryGraph.export();
    }
    
    public void export(InputData data) throws IOException {
        String instanceName = data.FileName.replaceFirst("\\.vrp$", "");
        String fileName = "Instance = " + instanceName + ", Cost = " + this.getFitness() + ".sol";
        File output = new File("Output");
        if (!output.exists())
            output.mkdirs();
        output = new File("Output//" + instanceName);
        if (!output.exists())
            output.mkdirs();
        File outFile = new File(output, fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            bw.write(this.export());
            bw.write("Cost " + (int) this.getFitness());
            bw.newLine();
        }
    }
}