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
 * @author Othmane
 */
public class RightShift extends LocalSearchMove {

    private final int Degree;
    private final boolean With2Opt;
    private final int FirstBorder;

    public RightShift(InputData data, boolean with2opt, int degree, int i, int j, int[]... sequences) {
        super("RightShift", i, j, sequences);
        this.With2Opt = with2opt;
        this.Degree = degree;
        this.FirstBorder = this.FirstSequence.length;
    }

    @Override
    public void setGain(InputData data) {
        if (this.With2Opt) {
            this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J], this.FirstSequence[this.I]);
            if (this.I == 0) {
                this.Gain += data.getDepotToStopDistance(this.SecondSequence[this.J + this.Degree]);
                this.Gain -= data.getDepotToStopDistance(this.FirstSequence[this.I]);
            }
            else {
                this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I - 1], this.SecondSequence[this.J + this.Degree]);
                this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I - 1], this.FirstSequence[this.I]);
            }
        }
        else {
            this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J + this.Degree], this.FirstSequence[this.I]);
            if (this.I == 0) {
                this.Gain += data.getDepotToStopDistance(this.SecondSequence[this.J]);
                this.Gain -= data.getDepotToStopDistance(this.FirstSequence[this.I]);
            }
            else {
                this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I - 1], this.SecondSequence[this.J]);
                this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I - 1], this.FirstSequence[this.I]);
            }
        }
        if (this.J > 0 || this.OneSequence)
            this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J - 1], this.SecondSequence[this.J]);
        else
            this.Gain -= data.getDepotToStopDistance(this.SecondSequence[this.J]);
        if (this.J + this.Degree + 1 < this.Border) {
            if (this.J > 0 || this.OneSequence)
                this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J - 1], this.SecondSequence[this.J + this.Degree + 1]);
            else
                this.Gain += data.getDepotToStopDistance(this.SecondSequence[this.J + this.Degree + 1]);
            this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J + this.Degree], this.SecondSequence[this.J + this.Degree + 1]);
        }
        else {
            if (this.J > 0 || this.OneSequence)
                this.Gain += data.getStopToDepotDistance(this.SecondSequence[this.J - 1]);
            this.Gain -= data.getStopToDepotDistance(this.SecondSequence[this.J + this.Degree]);
        }
    }

    @Override
    public void Perform() {
        if (this.OneSequence)
            IntStream.range(0, this.Degree + 1).forEach(i -> new Move(this.With2Opt ? this.I : this.I + i, this.J + i)
                                                .RightShift(this.FirstSequence));
        else {
            int[] seq1 = new int[this.FirstSequence.length + this.Degree + 1];
            IntStream.range(0, this.I).forEach(i -> seq1[i] = this.FirstSequence[i]);
            IntStream.range(0, this.Degree + 1).forEach(i -> seq1[this.I + i] = this.SecondSequence[(this.With2Opt) ? this.J + this.Degree - i : this.J + i]);
            IntStream.range(this.I, this.FirstSequence.length).forEach(i -> seq1[i + this.Degree + 1] = this.FirstSequence[i]);
            int[] seq2 = new int[this.SecondSequence.length - this.Degree - 1];
            this.FirstSequence = seq1;
            IntStream.range(0, this.J).forEach(i -> seq2[i] = this.SecondSequence[i]);
            IntStream.range(this.J + this.Degree + 1, this.SecondSequence.length).forEach(i -> seq2[i - this.Degree - 1] = this.SecondSequence[i]);
            this.SecondSequence = seq2;
        }
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity = data.getCapacity() - IntStream.range(0, this.FirstBorder)
                                                                .map(i -> data.getDemand(this.FirstSequence[i]))
                                                                .sum();
        int sum_demand = IntStream.range(this.J, this.J + this.Degree + 1)
                                    .map(i -> data.getDemand(this.SecondSequence[i]))
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