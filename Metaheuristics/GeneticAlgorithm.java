/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Metaheuristics;

import Data.InputData;
import Solution.GiantTour;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 *
 * @author Othmane
 */
public class GeneticAlgorithm extends MetaHeuristic {
    
    private final double CrossoverRate = 0.8d;
    private final GiantTour[] Population;
    private final int PopulationSize;
    private final int TournamentSize = 5;

    
    public GeneticAlgorithm(InputData data) {
        super(data);
        this.PopulationSize = (int) Math.max(20, 10 * Math.log10(data.getDimension()));
        this.Population = new GiantTour[this.PopulationSize];
    }
    
    @Override
    public void Run() {
        System.out.println("File to solve = " + this.Data.FileName);
        System.out.println("Dimension = " + this.Data.getDimension());
        System.out.println("Solution approach = Memetic Algorithm");
        System.out.println();
        this.StartTime = System.currentTimeMillis();
        this.InitialPopulation();
        if(!this.Population[0].isFeasible())
            return;
        do {
            IntStream.range(0, this.PopulationSize).forEach(i -> this.Selection());
        } while (this.nonStopCondition());
        this.EndTime = System.currentTimeMillis() - this.StartTime;
        System.out.println();
    }
    
    private void Selection() {
        GiantTour parent1 = this.tournamentSelection();
        GiantTour parent2 = this.tournamentSelection();
        if (Math.random() < this.CrossoverRate && parent1 != parent2) {
            GiantTour graph_crossover = new GiantTour(this.Data, parent1, parent2);
            this.UpdatePopulation(graph_crossover);
        }
        else if (parent1 == parent2) {
            GiantTour random = new GiantTour(this.Data); 
            GiantTour graph_crossover = new GiantTour(this.Data, parent1, random);
            this.UpdatePopulation(graph_crossover); 
        }
        else {
            // repeat splitting procedure to discover more improvement possibilities
            parent1.Split(this.Data, parent1.getFitness(), true);
            parent2.Split(this.Data, parent2.getFitness(), true);
            if (this.setBestSolution(parent1) || this.setBestSolution(parent2))
                Arrays.sort(this.Population);
        }
    }
    
    private void UpdatePopulation(GiantTour newGiantTour) {
        if (newGiantTour == null || !newGiantTour.isFeasible())
            return;
        if (newGiantTour.compareTo(this.Population[this.PopulationSize - 1]) < 0) {
            int half = this.PopulationSize / 2;
            int randomIndex = half + (int) (Math.random() * (this.Population.length - half));
            if (this.setBestSolution(newGiantTour)) {
                GiantTour graph_crossover = new GiantTour(this.Data, newGiantTour, this.Population[0], this.Population[randomIndex]);
                this.UpdatePopulation(graph_crossover);
            }
            this.Population[randomIndex] = newGiantTour;
            Arrays.sort(this.Population);
        }
    }
    
    private void InitialPopulation() {
        for (int i = 0; i < this.PopulationSize; i++) 
            if (i == 0) {
                byte failure_count = 0;
                do {
                    failure_count++;
                    this.Population[i] = new GiantTour(this.Data);
                } while (!this.Population[i].isFeasible() && failure_count < 10);
                if (failure_count == 10)
                    return;
                this.setBestSolution(this.Population[i]);
            }
            else {
                do {
                    this.Population[i] = new GiantTour(this.Data);
                } while (!this.Population[i].isFeasible());
                this.setBestSolution(this.Population[i]);
            }
        Arrays.sort(this.Population);
        System.out.println();
    }
    
    private boolean nonStopCondition() {
        long current_time = System.currentTimeMillis();
        if (current_time - this.BestSolutionReachingTime <= this.StagnationMinTime)
            return true;
        double probability = current_time - this.BestSolutionReachingTime;
        probability /= (double) (current_time - this.StartTime);
        return Math.random() > probability;
    }
    
    private GiantTour tournamentSelection() {
        GiantTour bestInTournament = null;
        for (int i = 0; i < this.TournamentSize; i++) {
            int randomIndex = (int) (Math.random() * this.PopulationSize);
            GiantTour randomCompetitor = this.Population[randomIndex];
            if (bestInTournament == null || randomCompetitor.getFitness() < bestInTournament.getFitness())
                bestInTournament = randomCompetitor;
        }
        return bestInTournament;
    }
}