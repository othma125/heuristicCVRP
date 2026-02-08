/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Solution.LSM;

import Data.InputData;
import Solution.Move;
import Solution.Route;
import java.util.stream.IntStream;

/**
 *
 * @author Othmane
 */
public class _2Opt extends LocalSearchMove {

    private final int FirstBorder;

    public _2Opt(InputData data, int i, int j, Route... routes) {
        super("2Opt", i, j, routes);
        this.FirstBorder = this.FirstRoute.getLength();
    }

    @Override
    public void setGain(InputData data) {
        if (this.I == 0) {
            this.Gain += data.getDepotToStopDistance(this.SecondRoute.getStop(this.J));
            this.Gain -= data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
        }
        else {
            this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.SecondRoute.getStop(this.J));
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.FirstRoute.getStop(this.I));
        }
        if (this.J + 1 < this.Border) {
            this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.SecondRoute.getStop(this.J + 1));
            this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.SecondRoute.getStop(this.J + 1));
        }
        else {
            this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I));
            this.Gain -= data.getStopToDepotDistance(this.SecondRoute.getStop(this.J));
        }
    }

    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            new Move(this.I, this.J)._2Opt(this.FirstRoute.getSequence());
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = new int[this.I + this.J + 1];
            IntStream.range(0, this.I)
                     .forEach(i -> seq1[i] = this.FirstRoute.getStop(i));
            IntStream.range(0, this.J + 1)
                     .forEach(i -> seq1[i + this.I] = this.SecondRoute.getStop(this.J - i));
            int[] seq2 = new int[this.SecondRoute.getLength() + this.FirstRoute.getLength() - seq1.length];
            int k = 0;
            for (int i = this.FirstRoute.getLength() - 1; i >= this.I; i--) {
                seq2[k] = this.FirstRoute.getStop(i);
                k++;
            }
            for (int i = this.J + 1; i < this.SecondRoute.getLength(); i++) {
                seq2[k] = this.SecondRoute.getStop(i);
                k++;
            }
            this.FirstRoute = new Route(data, seq1);
            this.SecondRoute = new Route(data, seq2);
        }
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity1 = data.getCapacity() - IntStream.range(0, this.I)
                                                                .map(i -> data.getDemand(this.FirstRoute.getStop(i)))
                                                                .sum();
        int sum_demand2 = IntStream.range(0, this.J + 1)
                                     .map(j -> data.getDemand(this.SecondRoute.getStop(j)))
                                     .sum();
        if (sum_demand2 > available_capacity1 || available_capacity1 < 0)
            return false;
        int available_capacity2 = data.getCapacity() - IntStream.range(this.J + 1, this.Border)
                                                                .map(j -> data.getDemand(this.SecondRoute.getStop(j)))
                                                                .sum();
        int sum_demand1 = IntStream.range(this.I, this.FirstBorder)
                                    .map(i -> data.getDemand(this.FirstRoute.getStop(i)))
                                    .sum();
        return sum_demand1 <= available_capacity2;
    }

    @Override
    public String toString() {
        return this.Name + " (" + this.I + ";" + this.J + ")";
    }
}
