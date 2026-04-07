package Data;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Othmane
 */
public class Edge implements Cloneable {
    
    private int X, Y;

    public Edge(int x, int y){
        if (x < y) {
            this.X = x;
            this.Y = y;
        } else {
            this.X = y;
            this.Y = x;
        }
    }

    Edge(Edge edge){
        this(edge.X, edge.Y);
    }

    @Override
    public Edge clone(){
        return new Edge(this);
    }

    public int getX() {
        return this.X;
    }

    public int getY() {
        return this.Y;
    }

    public boolean isNotFake() {
        return this.X != this.Y;
    }

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