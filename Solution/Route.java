package Solution;

import Data.InputData;
import Solution.LSM.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Othmane
 */
public final class Route implements Comparable<Route> {

    private int[] Sequence;
    private int SumDemand;
    private double TraveledDistance;

    @Override
    public int hashCode() {
        return 31 + Double.hashCode(this.TraveledDistance) + this.Sequence.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        final Route other = (Route) obj;
        if (this.Sequence.length != other.Sequence.length)
            return false;
        return IntStream.range(0, this.Sequence.length).allMatch(i -> this.Sequence[i] == other.Sequence[i]);
    }  
    
    public Route(int[] seq, int sum_demand, double dist) {
        this.Sequence = seq;
        this.SumDemand = sum_demand;
        this.TraveledDistance = dist;
    }
    
    public Route(InputData data, int[] seq) {
        this.Sequence = seq;
        this.setCost(data);
    }

    private void setCost(InputData data) {
        this.TraveledDistance = data.getDepotToStopDistance(this.Sequence[0]);
        this.SumDemand = 0;
        int i = 0;
        while (i < this.Sequence.length - 1) {
            this.SumDemand += data.getDemand(this.Sequence[i]);
            this.TraveledDistance += data.getTwoStopsDistance(this.Sequence[i], this.Sequence[++i]);
        }
        this.SumDemand += data.getDemand(this.Sequence[i]);
        this.TraveledDistance += data.getStopToDepotDistance(this.Sequence[i]);
    }
    
    public boolean StagnationBreaker(InputData data) {
	int max = (int) Math.sqrt(this.Sequence.length);
        for (int i = 0; i < this.Sequence.length - 1; i++) {
            LocalSearchMove best_lsm = null;
            for (int j = i + 1; j < this.Sequence.length; j++) {   
                if (j > i + 1) {
                    LocalSearchMove lsm = new Swap(data, i, j, this.Sequence);
                    lsm.setGain(data);
                    if (best_lsm == null || lsm.getGain() < best_lsm.getGain())
                        best_lsm = lsm;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max && j + degree < this.Sequence.length; degree++) {
                    LocalSearchMove lsm1 = new RightShift(data, true, degree, i, j, this.Sequence);
                    lsm1.setGain(data);
                    if (best_lsm == null || lsm1.getGain() < best_lsm.getGain())
                        best_lsm = lsm1;
                    if (degree == 0)
                        continue;
                    LocalSearchMove lsm2 = new RightShift(data, false, degree, i, j, this.Sequence);
                    lsm2.setGain(data);
                    if (best_lsm == null || lsm2.getGain() < best_lsm.getGain())
                        best_lsm = lsm2;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max && i - degree >= 0; degree++) {
                    LocalSearchMove lsm1 = new LeftShift(data, true, degree, i, j, this.Sequence);
                    lsm1.setGain(data);
                    if (best_lsm == null || lsm1.getGain() < best_lsm.getGain())
                        best_lsm = lsm1;
                    if (degree == 0)
                        continue;
                    LocalSearchMove lsm2 = new LeftShift(data, false, degree, i, j, this.Sequence);
                    lsm2.setGain(data);
                    if (best_lsm == null || lsm2.getGain() < best_lsm.getGain())
                        best_lsm = lsm2;
                }
            }          
            if (best_lsm != null && best_lsm.getGain() < 0d) {
                best_lsm.Perform();
                this.TraveledDistance += best_lsm.getGain();
                return true;
            }
        }
        return false;
    }

    public void LocalSearch(InputData data) {
        if (this.Sequence.length <= 2)
            return;
        this.LocalSearch(data, Math.sqrt(this.Sequence.length) / this.Sequence.length);
    }

    public void LocalSearch(InputData data, double probability) {
        int max = (int) Math.sqrt(this.Sequence.length);
        int improvementCounter = 0;
        for (int i = 0; improvementCounter < max && i < this.Sequence.length - 1; i++)
            for (int j = i + 1; improvementCounter < max && j < this.Sequence.length ; j++) {
                LocalSearchMove lsm = new _2Opt(data, i , j, this.Sequence);
                lsm.setGain(data);
                if (lsm.getGain() < 0d) {
                    lsm.Perform();
                    this.TraveledDistance += lsm.getGain();
                    improvementCounter++;
                }
            }
        boolean again = Math.random() > probability;
        if ((again && improvementCounter > 0) || (!again && improvementCounter < max && this.StagnationBreaker(data))) 
            this.LocalSearch(data);
    }
    
    public LocalSearchMove get2Opt(InputData data, Route other) {
        LocalSearchMove lsm;
        for (int i = 0; i < this.Sequence.length; i++)
            for (int j = 0; j < other.Sequence.length ; j++) {
                lsm = new _2Opt(data, i , j, this.Sequence, other.Sequence);
                lsm.setGain(data);
                if (lsm.getGain() < 0d && lsm.isFeasible(data))
                    return lsm;
            }
        for (int i = 0; i < other.Sequence.length; i++)
            for (int j = 0; j < this.Sequence.length ; j++) {
                lsm = new _2Opt(data, i , j, other.Sequence, this.Sequence);
                lsm.setGain(data);
                if (lsm.getGain() < 0d && lsm.isFeasible(data))
                    return lsm;
            }
        return null;
    }
    
    public LocalSearchMove getSwap(InputData data, Route other) {
        LocalSearchMove lsm;
        for (int i = 0; i < this.Sequence.length; i++)
            for (int j = 0; j < other.Sequence.length ; j++) {
                lsm = new Swap(data, i, j, this.Sequence, other.Sequence);
                if (lsm.isFeasible(data)) {
                    lsm.setGain(data);
                    if (lsm.getGain() < 0d)
                        return lsm;
                }
            }
        return null;
    }
    
    public LocalSearchMove getShift(InputData data, Route other) {
        LocalSearchMove lsm;
	int max1 = (int) Math.sqrt(this.Sequence.length);
	int max2 = (int) Math.sqrt(other.Sequence.length);
        for (int i = 0; i < this.Sequence.length; i++)
            for (int j = 0; j < other.Sequence.length ; j++) {
                for (int degree = j == i + 1 ? 1 : 0; degree <= max2 && j + degree < other.Sequence.length; degree++) {
                    lsm = new RightShift(data, true, degree, i, j, this.Sequence, other.Sequence);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else
                        break;
                    if (degree == 0)
                        continue;
                    lsm = new RightShift(data, false, degree, i, j, this.Sequence, other.Sequence);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else
                        break;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max1 && i - degree >= 0; degree++) {
                    lsm = new LeftShift(data, true, degree, i, j, this.Sequence, other.Sequence);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else
                        break;
                    if (degree == 0)
                        continue;
                    lsm = new LeftShift(data, false, degree, i, j, this.Sequence, other.Sequence);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else
                        break;
                }
            }
        return null;
    }
    
    @Override
    public int compareTo(Route route) {
        return Double.compare(this.TraveledDistance * 100d, route.getTraveledDistance() * 100d);
    }
    
    @Override
    public String toString() {
        return Arrays.toString(IntStream.of(this.Sequence).map(stop -> stop + 2).toArray());
    }
    
    public Set<Integer> getStops() {
        return IntStream.of(this.Sequence).boxed().collect(Collectors.toSet());
    }
    
    public int getFirst() {
        return this.Sequence[0];
    }
    
    public int getLast() {
        return this.Sequence[this.Sequence.length - 1];
    }

    public int[] getSequence() {
        return this.Sequence;
    }

    public int getSumDemand() {
        return this.SumDemand;
    }
    
    public double getTraveledDistance() {
        return this.TraveledDistance;
    }
    
    public int getStop(int index) {
        return this.Sequence[index];
    }

    String export() {
        StringBuilder sb = new StringBuilder();
        for (int stop : this.Sequence)
            sb.append(stop + 1).append(" ");
        return sb.toString();
    }

    public int getLength() {
        return this.Sequence.length;
    }
}