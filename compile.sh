#!/bin/bash

# Compile all Java files in the project
rm -rf out
mkdir -p out
javac -encoding UTF-8 -d out -sourcepath Algorithm:Web \
    $(find . -name '*.java' ! -path './out/*' ! -path './.git/*' ! -path './Algorithm/CVRPLib/*' ! -path './Output/*') 
