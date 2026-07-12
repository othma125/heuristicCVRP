#!/usr/bin/env bash
# Author: Othmane

set -e

cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out $(find . -name '*.java' ! -path './out/*' ! -path './.git/*' ! -path './Algorithm/CVRPLib/*' ! -path './Output/*')
java -cp out web.Server "$@"
