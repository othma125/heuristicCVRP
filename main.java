
import Metaheuristics.GeneticAlgorithm;
import Solution.GiantTour;
import Data.InputData;
import java.io.IOException;



/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Othmane
 */
public class main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        
        InputData data = new InputData("CVRPLib//B//B-n51-k7.vrp");
//        InputData data = new InputData("CVRPLib//XL//XL-n1048-k237.vrp");
        GeneticAlgorithm algorithm = new GeneticAlgorithm(data);
        algorithm.Run();
        
        if (algorithm.isFeasible()) {
            GiantTour gt = algorithm.getBestGiantTour();
            System.out.println(gt);
            // gt.export(data);
            System.out.println("\nEnd Time = " + algorithm.getRunningTime() + " ms");
        }
        else
            System.out.println("No feasible solution found");
    }
}