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
public class RightShift extends LocalSearchMove {

    private final int Degree;
    private final boolean with2Opt;
    private final int FirstBorder;

    public RightShift(InputData data, boolean with2opt, int degree, int i, int j, Route... routes) {
        super("RightShift", i, j, routes);
        this.with2Opt = with2opt;
        this.Degree = degree;
        this.FirstBorder = this.FirstRoute.getLength();
    }

    @Override
    public void setGain(InputData data) {
        if (this.with2Opt) {
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I));
            if (this.I == 0) {
                this.Gain += data.getDepotToStopDistance(this.SecondRoute.getStop(this.J + this.Degree));
                this.Gain -= data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
            }
            else {
                this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.SecondRoute.getStop(this.J + this.Degree));
                this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.FirstRoute.getStop(this.I));
            }
        }
        else {
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J + this.Degree), this.FirstRoute.getStop(this.I));
            if (this.I == 0) {
                this.Gain += data.getDepotToStopDistance(this.SecondRoute.getStop(this.J));
                this.Gain -= data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
            }
            else {
                this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.SecondRoute.getStop(this.J));
                this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.FirstRoute.getStop(this.I));
            }
        }
        if (this.J > 0 || this.OneSequence)
            this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.SecondRoute.getStop(this.J));
        else
            this.Gain -= data.getDepotToStopDistance(this.SecondRoute.getStop(this.J));
        if (this.J + this.Degree + 1 < this.Border) {
            if (this.J > 0 || this.OneSequence)
                this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.SecondRoute.getStop(this.J + this.Degree + 1));
            else
                this.Gain += data.getDepotToStopDistance(this.SecondRoute.getStop(this.J + this.Degree + 1));
            this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J + this.Degree), this.SecondRoute.getStop(this.J + this.Degree + 1));
        }
        else {
            if (this.J > 0 || this.OneSequence)
                this.Gain += data.getStopToDepotDistance(this.SecondRoute.getStop(this.J - 1));
            this.Gain -= data.getStopToDepotDistance(this.SecondRoute.getStop(this.J + this.Degree));
        }
    }

    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            this.FirstRoute.RightShift(this.I, this.J, this.Degree, this.with2Opt);
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = new int[this.FirstRoute.getLength() + this.Degree + 1];
            IntStream.range(0, this.I).forEach(i -> seq1[i] = this.FirstRoute.getStop(i));
            IntStream.range(0, this.Degree + 1).forEach(i -> seq1[this.I + i] = this.SecondRoute.getStop(this.with2Opt ? this.J + this.Degree - i : this.J + i));
            IntStream.range(this.I, this.FirstRoute.getLength()).forEach(i -> seq1[i + this.Degree + 1] = this.FirstRoute.getStop(i));
            int[] seq2 = new int[this.SecondRoute.getLength() - this.Degree - 1];
            IntStream.range(0, this.J).forEach(i -> seq2[i] = this.SecondRoute.getStop(i));
            IntStream.range(this.J + this.Degree + 1, this.SecondRoute.getLength()).forEach(i -> seq2[i - this.Degree - 1] = this.SecondRoute.getStop(i));
            this.FirstRoute = seq1.length > 0 ? new Route(data, seq1) : null;
            this.SecondRoute = seq2.length > 0 ? new Route(data, seq2) : null;
        }
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity = data.getCapacity() - IntStream.range(0, this.FirstBorder)
                                                                .map(i -> data.getDemand(this.FirstRoute.getStop(i)))
                                                                .sum();
        int sum_demand = IntStream.range(this.J, this.J + this.Degree + 1)
                                 .map(i -> data.getDemand(this.SecondRoute.getStop(i)))
                                 .sum();
        return sum_demand <= available_capacity && this.SecondRoute.getSumDemand() - sum_demand <= data.getCapacity();
    }

    @Override
    public String toString() {
        if (this.Degree == 0)
            return this.Name + " (" + this.I + ";" + this.J + ")";
        else if (this.with2Opt)
            return this.Name + " (" + this.I + ";" + this.J + ") " + -this.Degree;
        return this.Name + " (" + this.I + ";" + this.J + ") " + this.Degree;
    }
}
