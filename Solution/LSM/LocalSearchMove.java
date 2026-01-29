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
    int[] FirstSequence, SecondSequence;
    double Gain = 0d;
    
    public abstract void setGain(InputData data);
    public abstract boolean isFeasible(InputData data);
    public abstract void Perform();
    
    LocalSearchMove(String name, int i, int j, int[] ... sequences) {
        this.Name = name;
        if(sequences.length == 1 && i >= j)
            throw new IllegalArgumentException("i should be smaller than j in LSM");
        if(sequences.length > 2)
            throw new IllegalArgumentException("routes number should be equals to 1 or 2 in LSM");
        this.Gain = 0d;
        this.I = i;
        this.J = j;
        this.OneSequence = sequences.length == 1;
        this.FirstSequence = sequences[0];
        this.SecondSequence = this.OneSequence ? this.FirstSequence : sequences[1];
        this.Border = this.OneSequence ? this.FirstSequence.length : this.SecondSequence.length;
    }

    public double getGain() {
        return this.Gain;
    }

    public Route getRoute1(InputData data) {
        if (this.FirstSequence.length == 0)
            return null;
        return new Route(data, this.FirstSequence);
    }

    public Route getRoute2(InputData data) {
        if (this.SecondSequence.length == 0)
            return null;
        return new Route(data, this.SecondSequence);
    }
}