package Solution.LSM;

import Data.InputData;
import Solution.Route;

/**
 * Left-shift (or-opt) move: relocates a block of {@code Degree + 1} stops
 * ending at position {@code I} of the first route to position {@code J} of the
 * second (or the same) route. The {@code with2Opt} flag reverses the relocated
 * block.
 *
 * @author Othmane EL YAAKOUBI
 */
public class LeftShift extends LocalSearchMove {

    private final int FirstBorder;
    private final int Degree;
    private final boolean with2Opt;

    /**
     * @param data     the problem instance
     * @param with2opt whether the relocated block is reversed
     * @param degree   number of extra stops moved with the anchor (block size
     *                 is {@code degree + 1})
     * @param i        anchor position in the first route
     * @param j        insertion position in the second route
     * @param routes   one route (intra-route) or two routes (inter-route)
     */
    public LeftShift(InputData data, boolean with2opt, int degree, int i, int j, Route... routes) {
        super("LeftShift", i, j, routes);
        this.with2Opt = with2opt;
        this.Degree = degree;
        this.FirstBorder = this.FirstRoute.getLength();
    }

    /** {@inheritDoc} */
    @Override
    public void setGain(InputData data) {
        if (this.Border == 0) {
            if (this.with2Opt) {
                this.Gain += data.getDepotToStopDistance(this.FirstRoute.getStop(this.I));
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I - this.Degree));
            } 
            else {
                this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I));
                this.Gain += data.getDepotToStopDistance(this.FirstRoute.getStop(this.I - this.Degree));
            }
        } 
        else if (this.with2Opt) {
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

    /** {@inheritDoc} */
    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            this.FirstRoute.LeftShift(this.I, this.J, this.Degree, this.with2Opt);
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            int[] seq1 = new int[this.FirstRoute.getLength() - this.Degree - 1];
            for (int i = 0; i < this.I - this.Degree; i++) 
                seq1[i] = this.FirstRoute.getStop(i);
            for (int i = this.I + 1; i < this.FirstRoute.getLength(); i++) 
                seq1[i - this.Degree - 1] = this.FirstRoute.getStop(i);
            int[] seq2 = new int[this.SecondRoute.getLength() + this.Degree + 1];
            if (this.SecondRoute.getLength() > 0) {
                for (int i = 0; i <= this.J; i++) 
                    seq2[i] = this.SecondRoute.getStop(i);
                for (int i = this.J + 1; i < this.SecondRoute.getLength(); i++) 
                    seq2[i + this.Degree + 1] = this.SecondRoute.getStop(i);
            }
            for (int i = 0; i <= this.Degree; i++) 
                seq2[this.SecondRoute.getLength() > 0 ? this.J + 1 + i : i] = this.FirstRoute.getStop(this.with2Opt ? this.I - i : this.I - this.Degree + i);
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
        for (int i = 0; i < this.Border; i++) 
            available_capacity -= data.getDemand(this.SecondRoute.getStop(i));
        int sum_demand = 0;
        for (int i = this.I - this.Degree; i <= this.I; i++) 
            sum_demand += data.getDemand(this.FirstRoute.getStop(i));
        return sum_demand <= available_capacity && this.FirstRoute.getSumDemand() - sum_demand <= data.getCapacity();
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
