// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.InputData;
import Algorithm.Solution.Route;

/**
 * Right-shift (or-opt) move: relocates a block of {@code Degree + 1} stops
 * starting at position {@code J} of the second route into position {@code I} of
 * the first (or the same) route. The {@code with2Opt} flag reverses the
 * relocated block. It is the mirror image of {@link LeftShift}.
 *
 * @author Othmane EL YAAKOUBI
 */
public class RightShift extends LocalSearchMove {

    private final int Degree;
    private final boolean with2Opt;
    private final int FirstBorder;

    /**
     * @param data     the problem instance
     * @param with2opt whether the relocated block is reversed
     * @param degree   number of extra stops moved with the anchor (block size
     *                 is {@code degree + 1})
     * @param i        insertion position in the first route
     * @param j        anchor position in the second route
     * @param routes   one route (intra-route) or two routes (inter-route)
     */
    public RightShift(InputData data, boolean with2opt, int degree, int i, int j, Route... routes) {
        super("RightShift", i, j, routes);
        this.with2Opt = with2opt;
        this.Degree = degree;
        this.FirstBorder = this.FirstRoute.getLength();
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            this.FirstRoute.RightShift(this.I, this.J, this.Degree, this.with2Opt);
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = new int[this.FirstRoute.getLength() + this.Degree + 1];
            for (int i = 0; i < this.I; i++) 
                seq1[i] = this.FirstRoute.getStop(i);
            for (int i = 0; i <= this.Degree; i++) 
                seq1[this.I + i] = this.SecondRoute.getStop(this.with2Opt ? this.J + this.Degree - i : this.J + i);
            for (int i = this.I; i < this.FirstRoute.getLength(); i++) 
                seq1[i + this.Degree + 1] = this.FirstRoute.getStop(i);
            int[] seq2 = new int[this.SecondRoute.getLength() - this.Degree - 1];
            for (int i = 0; i < this.J; i++) 
                seq2[i] = this.SecondRoute.getStop(i);
            for (int i = this.J + this.Degree + 1; i < this.SecondRoute.getLength(); i++) 
                seq2[i - this.Degree - 1] = this.SecondRoute.getStop(i);
            this.FirstRoute = seq1.length > 0 ? new Route(data, seq1) : null;
            this.SecondRoute = seq2.length > 0 ? new Route(data, seq2) : null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int available_capacity = data.getCapacity();
        for (int i = 0; i < this.FirstBorder; i++) 
            available_capacity -= data.getDemand(this.FirstRoute.getStop(i));
        int sum_demand = 0;
        for (int i = this.J; i <= this.J + this.Degree; i++) 
            sum_demand += data.getDemand(this.SecondRoute.getStop(i));
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
