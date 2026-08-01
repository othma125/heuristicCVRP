#!/bin/bash

# Compile Java files modified since the last successful build ( --clean forces a full rebuild )
[ "$1" = "--clean" ] && rm -rf out
mkdir -p out

stamp=out/.build-stamp
[ -f "$stamp" ] && newer=(-newer "$stamp")

files=$(find . -name '*.java' ! -path './out/*' ! -path './.git/*' ! -path './Algorithm/CVRPLib/*' ! -path './Output/*' "${newer[@]}")

if [ -z "$files" ]; then
    echo "Nothing to compile."
    exit 0
fi

javac -encoding UTF-8 -d out -cp out -sourcepath Algorithm:Web $files && touch "$stamp"
