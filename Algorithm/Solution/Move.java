// Author: Othmane

package Algorithm.Solution;

/**
 * A pair of indices that applies elementary in-place transformations to an
 * integer sequence: right shift, left shift, swap and segment reversal
 * (2-opt). These are the low-level array operations underlying the local
 * search moves.
 *
 * @author Othmane EL YAAKOUBI
 */
public class Move {
    private int Index1, Index2;

//    void display(){
//        System.out.println("( " + this.Index1 + " , " + this.Index2 + " )");
//    }

    /**
     * @param a the first index
     * @param b the second index
     */
    public Move(int a, int b) {
        this.Index1 = a;
        this.Index2 = b;
    }

//    boolean EqualsTo(Move m) {
//        return (this.Index1 == m.Index2 && this.Index2 == m.Index1) || (this.Index1 == m.Index1 && this.Index2 == m.Index2);
//    }

    /**
     * Moves the element at {@code Index2} to {@code Index1}, shifting the
     * elements in between one position to the right.
     *
     * @param sequence the sequence to modify in place
     */
    public void RightShift(int[] sequence) {
        if (this.Index1 < this.Index2) {
            int aux = sequence[this.Index2];
            for (int k = this.Index2; k > this.Index1;)
                sequence[k] = sequence[--k];
            sequence[this.Index1] = aux;
        }
    }
    
    /**
     * Moves the element at {@code Index1} to {@code Index2}, shifting the
     * elements in between one position to the left.
     *
     * @param array the sequence to modify in place
     */
    public void LeftShift(int[] array){
        if(this.Index1 < this.Index2){
            int aux = array[this.Index1];
            for(int k = this.Index1; k < this.Index2;)
                array[k] = array[++k];
            array[this.Index2] = aux;
        }
    }

    /**
     * Swaps the elements at {@code Index1} and {@code Index2}.
     *
     * @param array the sequence to modify in place
     */
    public void Swap(int[] array){
       int aux = array[this.Index1];
       array[this.Index1] = array[this.Index2];
       array[this.Index2] = aux;
    }

    /**
     * Reverses the segment between {@code Index1} and {@code Index2} inclusive
     * (the 2-opt array operation).
     *
     * @param array the sequence to modify in place
     */
    public void _2Opt(int[] array) {
        if (this.Index1 < this.Index2) {
            for (int k = this.Index1, l = this.Index2; k < l; k++, l--)
                new Move(k, l).Swap(array);
        }
    }
}