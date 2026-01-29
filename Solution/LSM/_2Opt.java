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
public class _2Opt extends LocalSearchMove {

    private final int FirstBorder;

    public _2Opt(InputData data, int i, int j, int[]... portions) {
        super("2Opt", i, j, portions);
        this.FirstBorder = this.FirstSequence.length;
    }

    @Override
    public void setGain(InputData data) {
        if (this.I == 0) {
            this.Gain += data.getDepotToStopDistance(this.SecondSequence[this.J]);
            this.Gain -= data.getDepotToStopDistance(this.FirstSequence[this.I]);
        }
        else {
            this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I - 1], this.SecondSequence[this.J]);
            this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I - 1], this.FirstSequence[this.I]);
        }
        if (this.J + 1 < this.Border) {
            this.Gain += data.getTwoStopsDistance(this.FirstSequence[this.I], this.SecondSequence[this.J + 1]);
            this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J], this.SecondSequence[this.J + 1]);
        }
        else {
            this.Gain += data.getStopToDepotDistance(this.FirstSequence[this.I]);
            this.Gain -= data.getStopToDepotDistance(this.SecondSequence[this.J]);
        }
    }

    @Override
    public void Perform() {
        if (this.OneSequence) 
            new Move(this.I, this.J)._2Opt(this.FirstSequence);
        else {
            int[] seq1 = new int[this.I + this.J + 1];
            IntStream.range(0, this.I).forEach(i -> seq1[i] = this.FirstSequence[i]);
            IntStream.range(0, this.J + 1).forEach(i -> seq1[i + this.I] = this.SecondSequence[this.J - i]);
            int[] seq2 = new int[this.SecondSequence.length + this.FirstSequence.length - seq1.length];
            int k = 0;
            for (int i = this.FirstSequence.length - 1; i >= this.I; i--) {
                seq2[k] = this.FirstSequence[i];
                k++;
            }
            for (int i = this.J + 1; i < this.SecondSequence.length; i++) {
                seq2[k] = this.SecondSequence[i];
                k++;
            }
            this.FirstSequence = seq1;
            this.SecondSequence = seq2;
        }
    }

    @Override
    public String toString() {
        return this.Name + " (" + this.I + ";" + this.J + ")";
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity1 = data.getCapacity() - IntStream.range(0, this.I)
                                                                .map(i -> data.getDemand(this.FirstSequence[i]))
                                                                .sum();
        int sum_demand2 = IntStream.range(0, this.J + 1)
                                    .map(j -> data.getDemand(this.SecondSequence[j]))
                                    .sum();
        if (sum_demand2 > available_capacity1)
            return false;
        int available_capacity2 = data.getCapacity() - IntStream.range(this.J + 1, this.Border)
                                                                .map(j -> data.getDemand(this.SecondSequence[j]))
                                                                .sum();
        int sum_demand1 = IntStream.range(this.I, this.FirstBorder)
                                    .map(i -> data.getDemand(this.FirstSequence[i]))
                                    .sum();
        return sum_demand1 <= available_capacity2;
    }
}
