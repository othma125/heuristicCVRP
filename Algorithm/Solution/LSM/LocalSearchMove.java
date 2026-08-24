// Author: Othmane

package Algorithm.Solution.LSM;


import Algorithm.Data.InputData;
import Algorithm.Solution.Route;


/**
 * Base class for local search moves. A move is defined by two positions
 * ({@code I}, {@code J}) over either one route (intra-route) or two routes
 * (inter-route), and knows how to evaluate its cost change, test its
 * capacity feasibility, and apply itself. Subclasses implement the specific
 * neighbourhoods: {@link _2Opt}, {@link Swap}, {@link LeftShift},
 * {@link RightShift}.
 *
 * <p>The route(s) a move works on are fixed at construction while its positions
 * are not, so a neighbourhood scan builds one move and re-aims it with
 * {@link #reset(int, int)} at every candidate instead of allocating one move per
 * candidate. A move must not be reset once it has been performed: performing it
 * replaces the routes it was built against.
 *
 * @author Othmane EL YAAKOUBI
 */
public abstract class LocalSearchMove {

    final String Name;
    final boolean OneSequence;
    int I, J;
    final int Border;
    Route FirstRoute, SecondRoute;
    double Gain = 0d;

    /**
     * Computes the change in total distance the move would produce and stores
     * it in {@code Gain} (negative means improving).
     *
     * @param data the problem instance providing distances
     */
    public abstract void setGain(InputData data);

    /**
     * Applies the move, mutating the route(s) in place or rebuilding them.
     *
     * @param data the problem instance providing distances
     */
    public abstract void Perform(InputData data);

    /**
     * @param data the problem instance providing demands and capacity
     * @return {@code true} if applying the move keeps both routes within
     *         capacity
     */
    public abstract boolean isFeasible(InputData data);

    /**
     * @param name   the move's name, used in {@code toString}
     * @param i      the first position
     * @param j      the second position
     * @param routes one route for an intra-route move, or two for an
     *               inter-route move
     * @throws IllegalArgumentException if a single-route move has
     *         {@code i >= j}, or if more than two routes are given
     */
    LocalSearchMove(String name, int i, int j, Route ... routes) {
        this.Name = name;
        if(routes.length == 1 && i >= j)
            throw new IllegalArgumentException("i should be smaller than j in LSM");
        if(routes.length > 2)
            throw new IllegalArgumentException("routes number should be equals to 1 or 2 in LSM");
        this.Gain = 0d;
        this.I = i;
        this.J = j;
        this.OneSequence = routes.length == 1;
        this.FirstRoute = routes[0];
        this.SecondRoute = this.OneSequence ? this.FirstRoute : routes[1];
        this.Border = this.OneSequence ? this.FirstRoute.getLength() : this.SecondRoute.getLength();
    }

    /**
     * Re-aims this move at another pair of positions on the same route(s) and
     * clears the gain, so the instance can be evaluated again.
     *
     * @param i the first position
     * @param j the second position
     * @throws IllegalArgumentException if an intra-route move has {@code i >= j}
     */
    public void reset(int i, int j) {
        if (this.OneSequence && i >= j)
            throw new IllegalArgumentException("i should be smaller than j in LSM");
        this.Gain = 0d;
        this.I = i;
        this.J = j;
    }

    /**
     * @return the cost change of the move (negative means improving)
     */
    public double getGain() {
        return this.Gain;
    }

    /**
     * @return the first route after the move (may be {@code null} if the move
     *         emptied it)
     */
    public Route getFirstRoute() {
        return this.FirstRoute;
    }

    /**
     * @return the second route after the move (may be {@code null} if the move
     *         emptied it)
     */
    public Route getSecondRoute() {
        return this.SecondRoute;
    }
}