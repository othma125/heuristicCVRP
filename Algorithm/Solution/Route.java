// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A single vehicle route: an ordered sequence of customer stops (depot
 * implicit at both ends) together with its total demand and travelled
 * distance. Provides the intra-route and inter-route local search moves
 * (2-opt, swap, left/right shift) used to improve solutions, along with the
 * elementary array operations that apply an accepted move.
 *
 * @author Othmane EL YAAKOUBI
 */
public final class Route implements Comparable<Route>, AutoCloseable {

    private int[] Sequence;
    private int SumDemand;
    private double TraveledDistance;

    @Override
    public int hashCode() {
        int hash = this.Sequence.length;
        hash = 31 * hash + this.SumDemand;
        int sum = 0;
        for (int value : this.Sequence) 
            sum += value;
        return 31 * hash + sum;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        final Route other = (Route) obj;
        if (this.Sequence.length != other.Sequence.length || this.SumDemand != other.SumDemand)
            return false;
        return Arrays.equals(this.Sequence, other.Sequence);
    }  
    
    /**
     * Builds a route with precomputed cost, avoiding a distance recomputation.
     *
     * @param seq        the ordered stop sequence
     * @param sum_demand the total demand of the sequence
     * @param dist       the total travelled distance of the sequence
     */
    public Route(int[] seq, int sum_demand, double dist) {
        this.Sequence = seq;
        this.SumDemand = sum_demand;
        this.TraveledDistance = dist;
    }

    /**
     * Builds a route from a stop sequence and computes its cost and demand.
     *
     * @param data the problem instance providing distances and demands
     * @param seq  the ordered stop sequence
     */
    public Route(InputData data, int[] seq) {
        this.Sequence = seq;
        this.setCost(data);
    }

    /**
     * Computes and stores the total travelled distance and total demand of the
     * current sequence.
     *
     * @param data the problem instance providing distances and demands
     */
    private void setCost(InputData data) {
        this.TraveledDistance = data.getDepotToStopDistance(this.Sequence[0]);
        this.SumDemand = 0;
        int i = 0;
        while (i < this.Sequence.length - 1) {
            this.SumDemand += data.getDemand(this.Sequence[i]);
            this.TraveledDistance += data.getTwoStopsDistance(this.Sequence[i], this.Sequence[++i]);
        }
        this.TraveledDistance += data.getStopToDepotDistance(this.Sequence[i]);
        this.SumDemand += data.getDemand(this.Sequence[i]);
    }
    
    /**
     * Diversification pass that scans all index pairs for the single best
     * intra-route move (swap, left shift or right shift, with varying degree)
     * and applies it if it is improving. Used to escape local optima when the
     * regular local search stalls.
     *
     * @param data the problem instance providing distances and demands
     * @return {@code true} if an improving move was found and applied
     */
    public boolean StagnationBreaker(InputData data) {
	int max = (int) Math.sqrt(this.Sequence.length);
        for (int i = 0; i < this.Sequence.length - 1; i++) {
            LocalSearchMove best_lsm = null;
            for (int j = i + 1; j < this.Sequence.length; j++) {   
                if (j > i + 1) {
                    LocalSearchMove lsm = new Swap(data, i, j, this);
                    lsm.setGain(data);
                    if (best_lsm == null || lsm.getGain() < best_lsm.getGain())
                        best_lsm = lsm;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max && j + degree < this.Sequence.length; degree++) {
                    LocalSearchMove lsm1 = new RightShift(data, true, degree, i, j, this);
                    lsm1.setGain(data);
                    if (best_lsm == null || lsm1.getGain() < best_lsm.getGain())
                        best_lsm = lsm1;
                    if (degree == 0)
                        continue;
                    LocalSearchMove lsm2 = new RightShift(data, false, degree, i, j, this);
                    lsm2.setGain(data);
                    if (best_lsm == null || lsm2.getGain() < best_lsm.getGain())
                        best_lsm = lsm2;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max && i - degree >= 0; degree++) {
                    LocalSearchMove lsm1 = new LeftShift(data, true, degree, i, j, this);
                    lsm1.setGain(data);
                    if (best_lsm == null || lsm1.getGain() < best_lsm.getGain())
                        best_lsm = lsm1;
                    if (degree == 0)
                        continue;
                    LocalSearchMove lsm2 = new LeftShift(data, false, degree, i, j, this);
                    lsm2.setGain(data);
                    if (best_lsm == null || lsm2.getGain() < best_lsm.getGain())
                        best_lsm = lsm2;
                }
            }          
            if (best_lsm != null && best_lsm.getGain() < 0d) {
                best_lsm.Perform(data);
                return true;
            }
        }
        return false;
    }

    /**
     * Runs intra-route local search with a default restart probability derived
     * from the route length. No-op for routes of two stops or fewer.
     *
     * @param data the problem instance providing distances and demands
     */
    public void IntraRoutesLocalSearch(InputData data) {
        if (this.Sequence.length <= 2)
            return;
        this.IntraRoutesLocalSearch(data, Math.sqrt(this.Sequence.length) / this.Sequence.length);
    }

    /**
     * Applies improving 2-opt moves within the route, capped at
     * {@code sqrt(length)} improvements per pass, then probabilistically either
     * repeats the pass or invokes {@link #StagnationBreaker(InputData)}.
     *
     * @param data        the problem instance providing distances and demands
     * @param probability controls how likely the search is to stop rather than
     *                    recurse for another pass
     */
    public void IntraRoutesLocalSearch(InputData data, double probability) {
        int max = (int) Math.sqrt(this.Sequence.length);
        int improvementCounter = 0;
        for (int i = 0; improvementCounter < max && i < this.Sequence.length - 1; i++)
            for (int j = i + 1; improvementCounter < max && j < this.Sequence.length ; j++) {
                LocalSearchMove lsm = new _2Opt(data, i , j, this);
                lsm.setGain(data);
                if (lsm.getGain() < 0d) {
                    lsm.Perform(data);
                    improvementCounter++;
                }
            }
        boolean again = ThreadLocalRandom.current().nextDouble() > probability;
        if ((again && improvementCounter > 0) || (!again && improvementCounter < max && this.StagnationBreaker(data))) 
            this.IntraRoutesLocalSearch(data);
    }
    
    /**
     * Searches for the first improving, capacity-feasible inter-route move
     * between this route and {@code other}, trying 2-opt, swap and left/right
     * shift moves in turn.
     *
     * @param data  the problem instance providing distances and capacity
     * @param other the other route to exchange stops with
     * @return an improving feasible move, or {@code null} if none exists
     */
    public LocalSearchMove getLSM(InputData data, Route other) {
        LocalSearchMove lsm;
        for (int i = 0; i < this.Sequence.length; i++)
            for (int j = 0; j < other.Sequence.length ; j++) {
                lsm = new _2Opt(data, i , j, this, other);
                lsm.setGain(data);
                if (lsm.getGain() < 0d && lsm.isFeasible(data))
                    return lsm;
            }
        for (int i = 0; i < other.Sequence.length; i++)
            for (int j = 0; j < this.Sequence.length ; j++) {
                lsm = new _2Opt(data, i , j, other, this);
                lsm.setGain(data);
                if (lsm.getGain() < 0d && lsm.isFeasible(data))
                    return lsm;
            }
        for (int i = 0; i < this.Sequence.length; i++)
            for (int j = 0; j < other.Sequence.length ; j++) {
                lsm = new Swap(data, i, j, this, other);
                if (lsm.isFeasible(data)) {
                    lsm.setGain(data);
                    if (lsm.getGain() < 0d)
                        return lsm;
                }
            }
        int max1 = (int) Math.sqrt(this.Sequence.length);
        int max2 = (int) Math.sqrt(other.Sequence.length);
        for (int i = 0; i < this.Sequence.length; i++)
            for (int j = 0; j < other.Sequence.length ; j++) {
                for (int degree = j == i + 1 ? 1 : 0; degree <= max2 && j + degree < other.Sequence.length; degree++) {
                    lsm = new RightShift(data, true, degree, i, j, this, other);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (other.getSumDemand() <= data.getCapacity())
                        break;
                    if (degree == 0)
                        continue;
                    lsm = new RightShift(data, false, degree, i, j, this, other);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (other.getSumDemand() <= data.getCapacity())
                        break;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max1 && i - degree >= 0; degree++) {
                    lsm = new LeftShift(data, true, degree, i, j, this, other);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (this.getSumDemand() <= data.getCapacity())
                        break;
                    if (degree == 0)
                        continue;
                    lsm = new LeftShift(data, false, degree, i, j, this, other);
                    if (lsm.isFeasible(data)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (this.getSumDemand() <= data.getCapacity())
                        break;
                }
            }
        return null;
    }
    
    /**
     * Orders routes by ascending travelled distance.
     *
     * @param route the route to compare against
     * @return a negative value, zero or a positive value as this route is
     *         shorter than, equal to, or longer than {@code route}
     */
    @Override
    public int compareTo(Route route) {
        return Double.compare(this.TraveledDistance * 100d, route.getTraveledDistance() * 100d);
    }

    @Override
    public String toString() {
        int[] modifiedSequence = new int[this.Sequence.length];
        for (int i = 0; i < this.Sequence.length; i++)
            modifiedSequence[i] = this.Sequence[i] + 2;
        return Arrays.toString(modifiedSequence);
    }

    /**
     * @return the stop sequence as a boxed list
     */
    public java.util.List<Integer> getSequenceAsList() {
        List<Integer> list = new ArrayList<>();
        for (int stop : this.Sequence)
            list.add(stop);
        return list;
    }

    /**
     * @return the first stop of the route
     */
    public int getFirst() {
        return this.Sequence[0];
    }

    /**
     * @return the last stop of the route
     */
    public int getLast() {
        return this.Sequence[this.Sequence.length - 1];
    }

    /**
     * @return the backing stop sequence (not a copy)
     */
    public int[] getSequence() {
        return this.Sequence;
    }

    /**
     * @return the total demand served by the route
     */
    public int getSumDemand() {
        return this.SumDemand;
    }

    /**
     * @return the total travelled distance of the route
     */
    public double getTraveledDistance() {
        return this.TraveledDistance;
    }

    /**
     * @param index position in the sequence
     * @return the stop at the given position
     */
    public int getStop(int index) {
        return this.Sequence[index];
    }

    /**
     * @return the route's stops in CVRPLIB 1-based numbering, space-separated
     */
    String export() {
        StringBuilder sb = new StringBuilder();
        for (int stop : this.Sequence)
            sb.append(stop + 1).append(" ");
        return sb.toString();
    }

    /**
     * @return the number of stops in the route
     */
    public int getLength() {
        return this.Sequence.length;
    }

    /**
     * Adjusts the cached travelled distance by an accepted move's gain (a
     * negative gain shortens the route).
     *
     * @param gain the change in travelled distance
     */
    public void Improve(double gain) {
        this.TraveledDistance += gain;
    }

    /**
     * Swaps the stops at positions {@code i} and {@code j} in place.
     *
     * @param i first position
     * @param j second position
     */
    public void Swap(int i, int j) {
        new Move(i, j).Swap(this.Sequence);
    }

    /**
     * Reverses the segment between positions {@code i} and {@code j} in place.
     *
     * @param i segment start
     * @param j segment end
     */
    public void _2Opt(int i, int j) {
        new Move(i, j)._2Opt(this.Sequence);
    }

    /**
     * Applies a left shift of the given degree in place.
     *
     * @param i      target position
     * @param j      source position
     * @param degree number of extra stops moved together with the anchor
     * @param _2opt  whether the shifted segment is reversed (2-opt variant)
     */
    public void LeftShift(int i, int j, int degree, boolean _2opt) {
        for (int k = 0; k <= degree; k++)
            new Move(i - k, _2opt ? j : j - k).LeftShift(this.Sequence);
    }

    /**
     * Applies a right shift of the given degree in place.
     *
     * @param i      source position
     * @param j      target position
     * @param degree number of extra stops moved together with the anchor
     * @param _2opt  whether the shifted segment is reversed (2-opt variant)
     */
    public void RightShift(int i, int j, int degree, boolean _2opt) {
        for (int k = 0; k <= degree; k++)
            new Move(_2opt ? i : i + k, j + k).RightShift(this.Sequence);
    }

    /**
     * Releases the stop sequence. Every other method dereferences
     * {@code Sequence} and will throw {@link NullPointerException} once this
     * has been called, so only close a route that nothing else still holds.
     */
    @Override
    public void close() {
        this.Sequence = null;
    }
}