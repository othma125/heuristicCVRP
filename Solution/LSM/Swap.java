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
public class Swap extends LocalSearchMove {

    private final int FirstBorder;

    public Swap(InputData data, int i, int j, int[]... sequences) {
        super("Swap", i, j, sequences);
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
        if (this.I + 1 < this.J && this.OneSequence) {
            this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J - 1], this.FirstSequence[this.I]);
            this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J - 1], this.SecondSequence[this.J]);
            this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J], this.FirstSequence[this.I + 1]);
            this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I], this.FirstSequence[this.I + 1]);
        }
        else if (!this.OneSequence) {
            if (this.J > 0) {
                this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J - 1], this.FirstSequence[this.I]);
                this.Gain -= data.getTwoStopsDistance(this.SecondSequence[this.J - 1], this.SecondSequence[this.J]);
            }
            else {
                this.Gain += data.getDepotToStopDistance(this.FirstSequence[this.I]);
                this.Gain -= data.getDepotToStopDistance(this.SecondSequence[this.J]);
            }
            if (this.I + 1 < this.FirstBorder) {
                this.Gain += data.getTwoStopsDistance(this.SecondSequence[this.J], this.FirstSequence[this.I + 1]);
                this.Gain -= data.getTwoStopsDistance(this.FirstSequence[this.I], this.FirstSequence[this.I + 1]);
            }
            else {
                this.Gain += data.getStopToDepotDistance(this.SecondSequence[this.J]);
                this.Gain -= data.getStopToDepotDistance(this.FirstSequence[this.I]);
            }
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
            new Move(this.I, this.J).Swap(this.FirstSequence);
        else {
            this.FirstSequence = this.FirstSequence.clone();
            this.SecondSequence = this.SecondSequence.clone();
            int aux = this.FirstSequence[this.I];
            this.FirstSequence[this.I] = this.SecondSequence[this.J];
            this.SecondSequence[this.J] = aux;
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
        int available_capacity1 = data.getCapacity() - IntStream.range(0, this.FirstBorder)
                                                                .filter(i -> i != this.I)
                                                                .map(i -> data.getDemand(this.FirstSequence[i]))
                                                                .sum();
        if (data.getDemand(this.SecondSequence[this.J]) > available_capacity1)
            return false;
        int available_capacity2 = data.getCapacity() - IntStream.range(0, this.Border)
                                                                .filter(j -> j != this.J)
                                                                .map(j -> data.getDemand(this.SecondSequence[j]))
                                                                .sum();
        return data.getDemand(this.FirstSequence[this.I]) <= available_capacity2;
    }
}