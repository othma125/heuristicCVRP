#!/usr/bin/env bash
# Author: Othmane

set -e

cd "$(dirname "$0")"
./compile.sh

# Resolve port the same way Server does: CLI arg > .env PORT > 8080
port="${1:-$(grep -oP '^PORT=\K.*' .env 2>/dev/null || echo 8081)}"
url="http://localhost:$port"
printf 'Server: \033]8;;%s\033\\%s\033]8;;\033\\\n' "$url" "$url"
opener="$(command -v xdg-open || command -v open || echo start)"
(sleep 2 && "$opener" "$url" >/dev/null 2>&1 || true) &

java -cp out Web.server.Server "$@"
