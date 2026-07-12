#!/usr/bin/env bash
# Author: Othmane

set -e

cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out $(find . -name '*.java' ! -path './out/*' ! -path './.git/*' ! -path './Algorithm/CVRPLib/*' ! -path './Output/*')

# Resolve port the same way Server does: CLI arg > .env PORT > 8080
port="${1:-$(grep -oP '^PORT=\K.*' .env 2>/dev/null || echo 8080)}"
(sleep 2 && start "" "http://localhost:$port") &

java -cp out web.Server "$@"
