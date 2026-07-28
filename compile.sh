#!/bin/bash

# Compile all Java files in the project
mkdir -p out
javac -d out -sourcepath Algorithm:Web \
    Algorithm/Data/*.java \
    Algorithm/Metaheuristics/*.java \
    Algorithm/Solution/*.java \
    Algorithm/Solution/LSM/*.java \
    Web/server/*.java \
    Algorithm/main.java \
    Algorithm/benchmark.java \
    campaign.java
