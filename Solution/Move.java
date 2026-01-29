package Solution;

import java.util.List;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Othmane
 */
public class Move {
    private int Index1, Index2;
    
//    void display(){
//        System.out.println("( " + this.Index1 + " , " + this.Index2 + " )");
//    }
    
    public Move(int a, int b) {
        this.Index1 = a;
        this.Index2 = b;
    }
    
//    boolean EqualsTo(Move m) {
//        return (this.Index1 == m.Index2 && this.Index2 == m.Index1) || (this.Index1 == m.Index1 && this.Index2 == m.Index2);
//    }

    public void RightShift(int[] sequence) {
        if (this.Index1 < this.Index2) {
            int aux = sequence[this.Index2];
            for (int k = this.Index2; k > this.Index1;)
                sequence[k] = sequence[--k];
            sequence[this.Index1] = aux;
        }
    }
    
    public void LeftShift(int[] array){
        if(this.Index1 < this.Index2){
            int aux = array[this.Index1];
            for(int k = this.Index1; k < this.Index2;)
                array[k] = array[++k];
            array[this.Index2] = aux;
        }
    }
    
    public void Swap(int[] array){
       int aux = array[this.Index1];
       array[this.Index1] = array[this.Index2];
       array[this.Index2] = aux;
    }    

    public void _2Opt(int[] array) {
        if (this.Index1 < this.Index2) {
            for (int k = this.Index1, l = this.Index2; k < l; k++, l--)
                new Move(k, l).Swap(array);
        }
    }
}