package Solution.LSM;

import Data.InputData;
import Solution.Route;

/**
 * 2-opt move. Intra-route it reverses the segment between the two positions;
 * inter-route it reconnects the two routes by swapping their tails, reversing
 * one prefix. Removes edge crossings.
 *
 * @author Othmane EL YAAKOUBI
 */
public class _2Opt extends LocalSearchMove {

    private final int FirstBorder;

    /**
     * @param data   the problem instance
     * @param i      position in the first route
     * @param j      position in the second route
     * @param routes one route (intra-route) or two routes (inter-route)
     */
    public _2Opt(InputData data, int i, int j, Route... routes) {
        super("2Opt", i, j, routes);
        this.FirstBorder = this.FirstRoute.getLength();
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            this.FirstRoute._2Opt(this.I, this.J);
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = new int[this.I + this.J + 1];
            for (int i = 0; i < this.I; i++) 
                seq1[i] = this.FirstRoute.getStop(i);
            for (int i = 0; i <= this.J; i++) 
                seq1[i + this.I] = this.SecondRoute.getStop(this.J - i);
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

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity1 = data.getCapacity();
        for (int i = 0; i < this.I; i++) {
            available_capacity1 -= data.getDemand(this.FirstRoute.getStop(i));
        }
        int sum_demand2 = 0;
        for (int j = 0; j <= this.J; j++) {
            sum_demand2 += data.getDemand(this.SecondRoute.getStop(j));
        }
        if (sum_demand2 > available_capacity1 || available_capacity1 < 0)
            return false;
        int available_capacity2 = data.getCapacity();
        for (int j = this.J + 1; j < this.Border; j++) {
            available_capacity2 -= data.getDemand(this.SecondRoute.getStop(j));
        }
        int sum_demand1 = 0;
        for (int i = this.I; i < this.FirstBorder; i++) {
            sum_demand1 += data.getDemand(this.FirstRoute.getStop(i));
        }
        return sum_demand1 <= available_capacity2;
    }

    @Override
    public String toString() {
        return this.Name + " (" + this.I + ";" + this.J + ")";
    }
}
