#!/bin/bash
# Compile (incremental) then run the batch benchmark entry point (Algorithm.benchmark).
set -e
cd "$(dirname "$0")"
./compile.sh
java -cp out Algorithm.benchmark "$@"
