// Author: Othmane

import Algorithm.Metaheuristics.GeneticAlgorithm;
import Algorithm.Solution.GiantTour;
import Algorithm.Data.InputData;
import java.io.IOException;



/**
 * Single-instance entry point: loads one CVRPLIB instance, solves it with the
 * memetic {@link GeneticAlgorithm}, and prints the best solution and running
 * time.
 *
 * @author Othmane EL YAAKOUBI
 */
public class main {

    /**
     * @param args the command line arguments (unused)
     * @throws IOException if the instance file cannot be read
     */
    public static void main(String[] args) throws IOException {
        
        // InputData data = new InputData("Algorithm/CVRPLib/QOBLIB/XSH-n20-k4-51.vrp");
        InputData data = new InputData("Algorithm/CVRPLib/B/B-n57-k7.vrp");
//        InputData data = new InputData("Algorithm/CVRPLib/XL/XL-n1048-k237.vrp");
        GeneticAlgorithm algorithm = new GeneticAlgorithm(data);
        algorithm.Run();
        
        if (algorithm.isFeasible()) {
            GiantTour gt = algorithm.getBestGiantTour();
            System.out.println(gt);
            //gt.export(data);
            System.out.println("\nEnd Time = " + algorithm.getRunningTime() + " ms");
        }
        else
            System.out.println("No feasible solution found\n");
    }
}