// Author: Othmane

package Algorithm.Data;

/**
 * Undirected edge between two stops, used as a symmetric key into the distance
 * cache. The endpoints are stored in canonical order ({@code X <= Y}) so that
 * {@code (a, b)} and {@code (b, a)} are equal and hash identically.
 *
 * @author Othmane EL YAAKOUBI
 */
public class Edge implements Cloneable {

    private int X, Y;

    /**
     * Creates an edge between two stops, storing the endpoints in canonical
     * order so that the pair is order-independent.
     *
     * @param x one endpoint
     * @param y the other endpoint
     */
    public Edge(int x, int y){
        if (x < y) {
            this.X = x;
            this.Y = y;
        } else {
            this.X = y;
            this.Y = x;
        }
    }

    /**
     * Copy constructor.
     *
     * @param edge the edge to copy
     */
    Edge(Edge edge){
        this(edge.X, edge.Y);
    }

    /**
     * @return a copy of this edge
     */
    @Override
    public Edge clone(){
        return new Edge(this);
    }

    /**
     * @return the smaller endpoint
     */
    public int getX() {
        return this.X;
    }

    /**
     * @return the larger endpoint
     */
    public int getY() {
        return this.Y;
    }

    /**
     * @return {@code true} if the endpoints differ (a real edge rather than a
     *         self-loop)
     */
    public boolean isNotFake() {
        return this.X != this.Y;
    }

    /**
     * @param other the edge to compare against
     * @return {@code true} if both endpoints match
     */
    boolean isEqualsTo(Edge other){
        return this.X == other.X && this.Y == other.Y;
    }

    @Override
    public boolean equals(Object other) {
        if(this == other)
            return true;
        if(other == null || this.getClass() != other.getClass())
            return false;
        return this.isEqualsTo((Edge) other);
    }

    @Override
    public String toString(){
        return this.X + " " + this.Y;
    }

    @Override
    public int hashCode() {
        return 17389 * this.X + this.Y;
    }
}