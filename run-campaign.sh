#!/bin/bash
# Compile (incremental) then run the loop-until-feasible campaign entry point (Algorithm.campaign).
set -e
cd "$(dirname "$0")"
./compile.sh
java -cp out Algorithm.campaign "$@"
