/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Solution.LSM;

import Data.InputData;
import Solution.Route;
import java.util.stream.IntStream;

/**
 *
 * @author Othmane
 */

public class Swap extends LocalSearchMove {

    private final int FirstBorder;

    public Swap(InputData data, int i, int j, Route... routes) {
        super("Swap", i, j, routes);
        this.FirstBorder = this.FirstRoute.getLength();
    }

    @Override
    public void setGain(InputData data) {

        // --- First route: predecessor of I ---
        if (this.I == 0) {
            this.Gain += data.getDepotToStopDistance(this.SecondRoute.getStop(this.J));
            this.Gain -= data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
        }
        else {
            this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.SecondRoute.getStop(this.J));
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.FirstRoute.getStop(this.I));
        }
        // --- Middle part ---
        if (this.I + 1 < this.J && this.OneSequence) {
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.FirstRoute.getStop(this.I));
            this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.SecondRoute.getStop(this.J));
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I + 1));
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.FirstRoute.getStop(this.I + 1));
        }
        else if (!this.OneSequence) {
            if (this.J > 0) {
                this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.FirstRoute.getStop(this.I));
                this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.SecondRoute.getStop(this.J));
            }
            else {
                this.Gain += data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
                this.Gain -= data.getDepotToStopDistance(this.SecondRoute.getStop(this.J));
            }
            if (this.I + 1 < this.FirstBorder) {
                this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I + 1));
                this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.FirstRoute.getStop(this.I + 1));
            }
            else {
                this.Gain += data.getStopToDepotDistance(this.SecondRoute.getStop(this.J));
                this.Gain -= data.getStopToDepotDistance(this.FirstRoute.getStop(this.I));
            }
        }
        // --- Successor of J ---
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
            this.FirstRoute.Swap(this.I, this.J);
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = this.FirstRoute.getSequence().clone();
            int[] seq2 = this.SecondRoute.getSequence().clone();
            int aux = seq1[this.I];
            seq1[this.I] = seq2[this.J];
            seq2[this.J] = aux;
            this.FirstRoute = new Route(data, seq1);
            this.SecondRoute = new Route(data, seq2);
        }
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int availableCapacity1 = data.getCapacity() - IntStream.range(0, this.FirstBorder)
                                                                .filter(i -> i != this.I)
                                                                .map(i -> data.getDemand(this.FirstRoute.getStop(i)))
                                                                .sum();
        if (availableCapacity1 < 0 || data.getDemand(this.SecondRoute.getStop(this.J)) > availableCapacity1)
            return false;
        int availableCapacity2 = data.getCapacity() - IntStream.range(0, this.Border)
                                                                .filter(j -> j != this.J)
                                                                .map(j -> data.getDemand(this.SecondRoute.getStop(j)))
                                                                .sum();
        return data.getDemand(this.FirstRoute.getStop(this.I)) <= availableCapacity2;
    }

    @Override
    public String toString() {
        return this.Name + " (" + this.I + ";" + this.J + ")";
    }
}
