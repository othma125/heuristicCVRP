#!/bin/bash
# Compile (incremental) then run the single-instance entry point (Algorithm.main).
set -e
cd "$(dirname "$0")"
./compile.sh
java -cp out Algorithm.main "$@"
