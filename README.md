# Heuristic CVRP Solver (Memetic / Genetic Algorithm)

This project solves **Capacitated Vehicle Routing Problem (CVRP)** instances using a **Memetic Algorithm** (Genetic Algorithm + Local Search), with instances taken from **CVRPLIB**.

The solver is **CVRP-native** (not a TSP wrapper): it uses a **giant tour representation** combined with a **graph-based splitting procedure** to generate feasible vehicle routes under capacity constraints, and applies rich intra- and inter-route local search moves.

> ⚠️ Note: a **benchmark main class is not implemented yet**. Currently, the project is designed to run **single CVRP instances**.

Reference: CVRPLIB – http://vrp.atd-lab.inf.puc-rio.br/index.php/en/

---

## Problem definition (CVRP)

- Given:
  - A depot
  - A set of customers with demands
  - Vehicle capacity Q
  - Symmetric distance matrix
- Objective:
  - Minimize total routing cost
  - Serve all customers exactly once
  - Each route starts and ends at the depot
  - Vehicle capacity constraints must be respected

---

## Project structure

```
HEURISTICCVRP
├── CVRPLib/              # CVRPLIB instances (.vrp)
├── Data/
│   ├── InputData.java    # CVRPLIB parser + distance handling
│   └── Edge.java         # Graph edge abstraction
├── Metaheuristics/
│   ├── MetaHeuristic.java
│   └── GeneticAlgorithm.java
├── Solution/
│   ├── GiantTour.java    # Giant tour chromosome representation
│   ├── Route.java        # Single vehicle route
│   ├── Solution.java     # Full CVRP solution (set of routes)
│   ├── AuxiliaryGraph.java       # Graph-based split procedure
│   ├── AuxiliaryGraphNode.java
│   ├── Move.java
│   ├── LSM/              # Local Search Moves
│   │   ├── _2Opt.java
│   │   ├── Swap.java
│   │   ├── LeftShift.java
│   │   ├── RightShift.java
│   │   └── LocalSearchMove.java
└── main.java             # Entry point (single instance run)
```

---

## Algorithm overview

### 1. Representation: Giant Tour

- Each individual is a **permutation of all customers** (giant tour, no depot).
- No feasibility is enforced at chromosome level.

### 2. Graph-based split (CVRP decoding)

- A **directed auxiliary graph** is built from the giant tour.
- Nodes represent customer positions.
- Edges represent feasible routes respecting vehicle capacity.
- Edge cost = routing cost of the corresponding segment.
- **Shortest path** from start to end gives the optimal split.

This guarantees:
- Capacity feasibility
- Optimal route partitioning for a given giant tour

---

## Genetic Algorithm (Memetic framework)

Implemented in `Metaheuristics/GeneticAlgorithm.java`.

### Population
- Initialized using randomized giant tours
- Each individual is decoded using the auxiliary graph

### Selection
- Tournament selection

### Crossover (Graph-based genetic crossover)

- **Not a classical cut-point crossover**
- Parents are combined using the auxiliary graph logic
- Best subsequences from both parents are inherited
- Shortest-path logic decides which segments survive

This crossover is:
- CVRP-aware
- Cost-driven
- Structure-preserving

### Mutation
- Random perturbations on the giant tour

### Local Search (Memetic component)

Applied both:
- Inside routes (intra-route)
- Between routes (inter-route)

Moves implemented:
- 2-Opt
- Swap
- Left Shift
- Right Shift

Local search is executed **inside the auxiliary graph context**, allowing high-quality improvements without breaking feasibility.

---

## Parallelism

- Java 23
- Multi-threaded execution
- Parallel:
  - Fitness evaluations
  - Auxiliary graph construction
  - Local search moves

Thread pool management is handled in `MetaHeuristic.java`.

---

## Input format (CVRPLIB)

- Supported files: `.vrp`
- Parsed sections:
  - DIMENSION
  - CAPACITY
  - NODE_COORD_SECTION
  - DEMAND_SECTION
  - DEPOT_SECTION

Distance computation:
- Euclidean distance (rounded as per CVRPLIB standard)
- Storage strategy:
  - Dense matrix for small instances
  - Hash-based cache for large instances

---

## How to run

### Compile

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find . -name "*.java")
```

### Run a single instance

Edit `main.java`:
- Set the CVRPLIB file path

Then run:

```bash
java -Xmx4g -cp out main
```

Recommended JVM options for large instances:

```bash
-Xms512m -Xmx4g
```

---

## Current limitations

- ❌ No benchmark runner yet
- ❌ No automatic comparison with CVRPLIB best-known solutions
- ❌ Single-instance execution only

These are planned extensions.

---

## Future work

- Benchmark main class (batch CVRPLIB evaluation)
- Gap computation vs. best-known solutions
- Time-to-target statistics
- Multi-objective extensions (distance + tardiness)
- Heterogeneous fleet variants

---

## Author

**Othmane EL YAAKOUBI**  
Backend & Operations Research Engineer  
Specialized in metaheuristics, VRP, and large-scale optimization

---

## Notes

- Results are stochastic
- Multiple runs recommended
- Designed for research and experimentation on large-scale CVRP instances

