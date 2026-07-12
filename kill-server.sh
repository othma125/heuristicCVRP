#!/usr/bin/env bash
# Author: Othmane

wmic process where "name='java.exe' and CommandLine like '%web.Server%'" get ProcessId /format:csv 2>/dev/null | tail -n +3 | grep -o '[0-9]\+' | while read -r pid; do
  MSYS_NO_PATHCONV=1 taskkill /F /PID "$pid"
done
