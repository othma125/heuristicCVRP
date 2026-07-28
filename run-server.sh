#!/usr/bin/env bash
# Author: Othmane

set -e

cd "$(dirname "$0")"
./compile.sh

# Resolve port the same way Server does: CLI arg > .env PORT > 8080
port="${1:-$(grep -oP '^PORT=\K.*' .env 2>/dev/null || echo 8081)}"
(sleep 2 && start "" "http://localhost:$port") &

java -cp out web.server.Server "$@"
