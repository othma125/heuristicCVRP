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
 * @author pc
 */
public class LeftShift extends LocalSearchMove {

    private final int FirstBorder;
    private final int Degree;
    private final boolean With2Opt;

    public LeftShift(InputData data, boolean with2opt, int degree, int i, int j, Route... routes) {
        super("LeftShift", i, j, routes);
        this.With2Opt = with2opt;
        this.Degree = degree;
        this.FirstBorder = this.FirstRoute.getLength();
    }

    @Override
    public void setGain(InputData data) {
        if (this.Border == 0) {
            if (this.With2Opt) {
                this.Gain += data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I - this.Degree));
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I));
                this.Gain += data.getDepotToStopDistance(this.FirstRoute.getStop(this.I - this.Degree));
            }
        } 
        else if (this.With2Opt) {
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I));
            if (this.J + 1 < this.Border) {
                this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - this.Degree), this.SecondRoute.getStop(this.J + 1));
                this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.SecondRoute.getStop(this.J + 1));
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I - this.Degree));
                this.Gain -= data.getStopToDepotDistance(this.SecondRoute.getStop(this.J));
            }
        } 
        else {
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I - this.Degree));
            if (this.J + 1 < this.Border) {
                this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.SecondRoute.getStop(this.J + 1));
                this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.SecondRoute.getStop(this.J + 1));
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I));
                this.Gain -= data.getStopToDepotDistance(this.SecondRoute.getStop(this.J));
            }
        }
        if (this.I + 1 < this.FirstBorder || this.OneSequence)
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.FirstRoute.getStop(this.I + 1));
        else
            this.Gain -= data.getStopToDepotDistance(this.FirstRoute.getStop(this.I));
        if (this.I - this.Degree == 0) {
            if (this.I + 1 < this.FirstBorder || this.OneSequence)
                this.Gain += data.getDepotToStopDistance(this.FirstRoute.getStop(this.I + 1));
            this.Gain -= data.getDepotToStopDistance(this.FirstRoute.getStop(this.I - this.Degree));
        } 
        else {
            if (this.I + 1 < this.FirstBorder || this.OneSequence)
                this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - this.Degree - 1), this.FirstRoute.getStop(this.I + 1));
            else
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I - this.Degree - 1));
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - this.Degree - 1), this.FirstRoute.getStop(this.I - this.Degree));
        }
    }

    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            IntStream.range(0, this.Degree + 1)
                     .forEach(i -> new Move(this.I - i, this.With2Opt ? this.J : this.J - i).LeftShift(this.FirstRoute.getSequence()));
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = new int[this.FirstRoute.getLength() - this.Degree - 1];
            IntStream.range(0, this.I - this.Degree).forEach(i -> seq1[i] = this.FirstRoute.getStop(i));
            IntStream.range(this.I + 1, this.FirstRoute.getLength()).forEach(i -> seq1[i - this.Degree - 1] = this.FirstRoute.getStop(i));
            int[] seq2 = new int[this.SecondRoute.getLength() + this.Degree + 1];
            if (this.SecondRoute.getLength() > 0) {
                IntStream.range(0, this.J + 1).forEach(i -> seq2[i] = this.SecondRoute.getStop(i));
                IntStream.range(this.J + 1, this.SecondRoute.getLength()).forEach(i -> seq2[i + this.Degree + 1] = this.SecondRoute.getStop(i));
            }
            IntStream.range(0, this.Degree + 1)
                     .forEach(i -> seq2[this.SecondRoute.getLength() > 0 ? this.J + 1 + i : i] = this.FirstRoute.getStop(this.With2Opt ? this.I - i : this.I - this.Degree + i));
            this.FirstRoute = seq1.length > 0 ? new Route(data, seq1) : null;
            this.SecondRoute = seq2.length > 0 ? new Route(data, seq2) : null;
        }
    }

    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity = data.getCapacity() - IntStream.range(0, this.Border)
                                                               .map(i -> data.getDemand(this.SecondRoute.getStop(i)))
                                                               .sum();
        int sum_demand = IntStream.range(this.I - this.Degree, this.I + 1)
                                 .map(i -> data.getDemand(this.FirstRoute.getStop(i)))
                                 .sum();
        return sum_demand <= available_capacity && this.FirstRoute.getSumDemand() - sum_demand <= data.getCapacity();
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
