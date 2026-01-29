/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Solution.LSM;

import Data.InputData;
import Solution.Move;
import java.util.stream.IntStream;

/**
 *
 * @author pc
 */
public class LeftShift extends LocalSearchMove {

    private final int FirstBorder;
    private final int Degree;
    private final boolean With2Opt;

    public LeftShift(InputData data, boolean with2opt, int degree, int i, int j, int[]... sequences) {
        super("LeftShift", i, j, sequences);
        this.With2Opt = with2opt;
        this.Degree = degree;
        this.FirstBorder = this.FirstSequence.length;
    }

    @Override
    public void setGain(InputData data) {
        if (this.Border == 0) {
            if (this.With2Opt) {
                this.Gain += data.getDepotToStopDistance(this.FirstSequence[this.I]);
                this.Gain += data.getStopToDepotDistance(this.FirstSequence[this.I - this.Degree]);
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstSequence[this.I]);
                this.Gain += data.getDepotToStopDistance(this.FirstSequence[this.I - this.Degree]);
            }
        } 
        else if (this.With2Opt) {
            this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J], this.FirstSequence[this.I]);
            if (this.J + 1 < this.Border) {
                this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I - this.Degree], this.SecondSequence[this.J + 1]);
                this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J], this.SecondSequence[this.J + 1]);
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstSequence[this.I - this.Degree]);
                this.Gain -= data.getStopToDepotDistance(this.SecondSequence[this.J]);
            }
        } 
        else {
            this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J], this.FirstSequence[this.I - this.Degree]);
            if (this.J + 1 < this.Border) {
                this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I], this.SecondSequence[this.J + 1]);
                this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J], this.SecondSequence[this.J + 1]);
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstSequence[this.I]);
                this.Gain -= data.getStopToDepotDistance(this.SecondSequence[this.J]);
            }
        }
        if (this.I + 1 < this.FirstBorder || this.OneSequence)
            this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I], this.FirstSequence[this.I + 1]);
        else
            this.Gain -= data.getStopToDepotDistance(this.FirstSequence[this.I]);
        if (this.I - this.Degree == 0) {
            if (this.I + 1 < this.FirstBorder || this.OneSequence)
                this.Gain += data.getDepotToStopDistance(this.FirstSequence[this.I + 1]);
            this.Gain -= data.getDepotToStopDistance(this.FirstSequence[this.I - this.Degree]);
        } 
        else {
            if (this.I + 1 < this.FirstBorder || this.OneSequence)
                this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I - this.Degree - 1], this.FirstSequence[this.I + 1]);
            else
                this.Gain += data.getStopToDepotDistance(this.FirstSequence[this.I - this.Degree - 1]);
            this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I - this.Degree - 1], this.FirstSequence[this.I - this.Degree]);
        }
    }

    @Override
    public void Perform() {
        if (this.OneSequence)
            IntStream.range(0, this.Degree + 1).forEach(i -> new Move(this.I - i, this.With2Opt ? this.J : this.J - i)
                                                .LeftShift(this.FirstSequence));
        else {
            int[] seq1 = new int[this.FirstSequence.length - this.Degree - 1];
            IntStream.range(0, this.I - this.Degree).forEach(i -> seq1[i] = this.FirstSequence[i]);
            IntStream.range(this.I + 1, this.FirstSequence.length).forEach(i -> seq1[i - this.Degree - 1] = this.FirstSequence[i]);
            int[] seq2 = new int[this.SecondSequence.length + this.Degree + 1];
            if (this.SecondSequence.length > 0) {
                IntStream.range(0, this.J + 1).forEach(i -> seq2[i] = this.SecondSequence[i]);
                IntStream.range(this.J + 1, this.SecondSequence.length).forEach(i -> seq2[i + this.Degree + 1] = this.SecondSequence[i]);
            }
            IntStream.range(0, this.Degree + 1).forEach(i -> seq2[(this.SecondSequence.length > 0) ? this.J + 1 + i : i] = this.FirstSequence[(this.With2Opt) ? this.I - i : this.I - this.Degree + i]);
            this.FirstSequence = seq1;
            this.SecondSequence = seq2;
        }
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity = data.getCapacity() - IntStream.range(0, this.Border)
                                                                .map(i -> data.getDemand(this.SecondSequence[i]))
                                                                .sum();
        int sum_demand = IntStream.range(this.I - this.Degree, this.I + 1)
                                    .map(i -> data.getDemand(this.FirstSequence[i]))
                                    .sum();
        return sum_demand <= available_capacity;
    }

    @Override
    public String toString() {
        if (this.Degree == 0)
            return this.Name + " (" + this.I + ";" + this.J + ")";
        else if (this.With2Opt)
            return this.Name + " (" + this.I + ";" + this.J + ") " + -this.Degree;
        return this.Name + " (" + this.I + ";" + this.J + ") " + this.Degree;
    }
}