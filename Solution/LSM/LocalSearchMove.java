package Solution.LSM;


import Data.InputData;
import Solution.Route;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Othmane
 */
public abstract class LocalSearchMove {
    
    final String Name;
    final boolean OneSequence;
    final int I, J;
    final int Border;
    Route FirstRoute, SecondRoute;
    double Gain = 0d;
    
    public abstract void setGain(InputData data);
    public abstract void Perform(InputData data);
    public abstract boolean isFeasible(InputData data);
    
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

    public double getGain() {
        return this.Gain;
    }

    public Route getFirstRoute() {
        return FirstRoute;
    }

    public Route getSecondRoute() {
        return SecondRoute;
    }
}