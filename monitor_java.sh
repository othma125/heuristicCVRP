#!/bin/bash

# Java Multi-Instance Resource Monitor
# Monitors all Java processes with detailed technical metrics
# Usage: ./monitor_java.sh [refresh_interval] [options]
# Options:
#   -h, --help     Show this help message
#   -j, --json     Output in JSON format
#   -q, --quiet    Quiet mode (no colors)
#   -f, --filter   Filter by process name pattern
#   -u, --user     Filter by user name
#   -o, --output   Save output to file

# Default configuration
REFRESH_INTERVAL=1
OUTPUT_FORMAT="terminal"
QUIET_MODE=false
FILTER_PATTERN=""
FILTER_USER=""
OUTPUT_FILE=""
SCRIPT_NAME=$(basename "$0")

# Color definitions (can be disabled for quiet mode)
setup_colors() {
    if [ "$QUIET_MODE" = true ]; then
        RED='' GREEN='' YELLOW='' CYAN='' NC=''
    else
        RED='\033[0;31m'
        GREEN='\033[0;32m'
        YELLOW='\033[1;33m'
        CYAN='\033[0;36m'
        NC='\033[0m'
    fi
}

# Configuration thresholds
CPU_HIGH_THRESHOLD=80
CPU_MEDIUM_THRESHOLD=50
MEM_HIGH_THRESHOLD=20
MEM_MEDIUM_THRESHOLD=10

# Help function
show_help() {
    cat << EOF
Java Multi-Instance Resource Monitor v2.0

USAGE:
    $SCRIPT_NAME [refresh_interval] [OPTIONS]

OPTIONS:
    -h, --help              Show this help message
    -j, --json              Output in JSON format
    -q, --quiet             Quiet mode (no colors)
    -f, --filter PATTERN    Filter Java processes by name pattern
    -u, --user USER         Filter processes by user name
    -o, --output FILE       Save output to file
    --cpu-high THRESHOLD    Set high CPU threshold (default: 80)
    --cpu-medium THRESHOLD  Set medium CPU threshold (default: 50)
    --mem-high THRESHOLD    Set high memory threshold (default: 20)
    --mem-medium THRESHOLD  Set medium memory threshold (default: 10)

EXAMPLES:
    $SCRIPT_NAME                     # Use default 1s refresh
    $SCRIPT_NAME 5                   # Refresh every 5 seconds
    $SCRIPT_NAME -q -f "gradle"      # Quiet mode, filter gradle processes
    $SCRIPT_NAME -j -o stats.json    # JSON output to file

KEYS (terminal mode):
    k       Kill -9 a monitored Java process (prompts for the PID, or "all")
    q       Quit

EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -j|--json)
                OUTPUT_FORMAT="json"
                shift
                ;;
            -q|--quiet)
                QUIET_MODE=true
                shift
                ;;
            -f|--filter)
                FILTER_PATTERN="$2"
                shift 2
                ;;
            -u|--user)
                FILTER_USER="$2"
                shift 2
                ;;
            -o|--output)
                OUTPUT_FILE="$2"
                shift 2
                ;;
            --cpu-high)
                CPU_HIGH_THRESHOLD="$2"
                shift 2
                ;;
            --cpu-medium)
                CPU_MEDIUM_THRESHOLD="$2"
                shift 2
                ;;
            --mem-high)
                MEM_HIGH_THRESHOLD="$2"
                shift 2
                ;;
            --mem-medium)
                MEM_MEDIUM_THRESHOLD="$2"
                shift 2
                ;;
            -*)
                echo "Unknown option: $1" >&2
                show_help
                exit 1
                ;;
            *)
                if [[ "$1" =~ ^[0-9]+$ ]]; then
                    REFRESH_INTERVAL="$1"
                else
                    echo "Invalid refresh interval: $1" >&2
                    exit 1
                fi
                shift
                ;;
        esac
    done
}

# Per-process CPU sampling state is persisted to disk (one file per PID)
# so the baseline survives the subshells that $(...) and pipelines create
# during JSON assembly. ps's %cpu column is a lifetime cumulative average
# and does not drop to 0 when a process goes idle, which is misleading
# for a live monitor; we replace it with an instantaneous delta from
# /proc/<pid>/stat between refresh ticks.
CPU_STATE_DIR=""
init_cpu_state_dir() {
    if [ -z "$CPU_STATE_DIR" ]; then
        CPU_STATE_DIR=$(mktemp -d -t monitor_java.XXXXXX) || CPU_STATE_DIR="/tmp/monitor_java.$$"
        mkdir -p "$CPU_STATE_DIR"
    fi
}

# Initialize system information with error handling
init_system_info() {
    # Get system info with error handling
    if ! TOTAL_CORES=$(nproc 2>/dev/null); then
        echo "Error: Could not determine CPU cores" >&2
        exit 1
    fi
    
    if ! TOTAL_MEM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}'); then
        echo "Error: Could not read memory information" >&2
        exit 1
    fi
    
    TOTAL_MEM_MB=$((TOTAL_MEM_KB / 1024))
    TOTAL_MEM_GB=$(awk "BEGIN {printf \"%.1f\", $TOTAL_MEM_MB/1024}")

    # Clock ticks per second (for /proc/<pid>/stat utime/stime conversion).
    CLK_TCK=$(getconf CLK_TCK 2>/dev/null)
    [ -z "$CLK_TCK" ] && CLK_TCK=100
    
    # Initialize colors
    setup_colors
    
    # Validate refresh interval
    if [[ ! "$REFRESH_INTERVAL" =~ ^[0-9]+$ ]] || [ "$REFRESH_INTERVAL" -lt 1 ]; then
        echo "Error: Invalid refresh interval: $REFRESH_INTERVAL" >&2
        exit 1
    fi
}

# Improved function to format bytes to human readable
format_bytes() {
    local bytes=$1
    if [[ ! "$bytes" =~ ^[0-9]+$ ]]; then
        echo "N/A"
        return
    fi
    
    if [ $bytes -ge 1073741824 ]; then
        awk "BEGIN {printf \"%.2f GB\", $bytes/1073741824}"
    elif [ $bytes -ge 1048576 ]; then
        awk "BEGIN {printf \"%.1f MB\", $bytes/1048576}"
    elif [ $bytes -ge 1024 ]; then
        awk "BEGIN {printf \"%.1f KB\", $bytes/1024}"
    else
        echo "${bytes} B"
    fi
}

# Improved Java process detection with filtering
get_java_pids() {
    local java_pids=""
    
    # Get all Java processes and filter for actual Java executables
    for pid in $(pgrep java 2>/dev/null); do
        if [ -d "/proc/$pid" ]; then
            # Get the actual executable path and command
            local exe=$(readlink /proc/$pid/exe 2>/dev/null)
            local cmd=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
            local cmd_path=$(echo "$cmd" | awk '{print $1}')
            
            # Check if it's an actual Java process
            if [[ "$exe" == *"/bin/java"* ]] || [[ "$exe" == *"/jre/bin/java"* ]] || [[ "$cmd" =~ ^[[:space:]]*java[[:space:]] ]] || [[ "$cmd" == java* ]]; then
                # Exclude IDE/editor Java processes
                if [[ "$cmd_path" != *".windsurf"* ]] && [[ "$cmd_path" != *".idea"* ]] && [[ "$cmd_path" != *".vscode"* ]] && [[ "$cmd" != *".windsurf"* ]]; then
                    # Apply user filter if specified
                    if [ -n "$FILTER_USER" ]; then
                        local user=$(ps -p $pid -o user= 2>/dev/null | tr -d ' ')
                        if [ "$user" != "$FILTER_USER" ]; then
                            continue
                        fi
                    fi
                    
                    # Apply pattern filter if specified
                    if [ -n "$FILTER_PATTERN" ]; then
                        if [[ "$cmd" != *"$FILTER_PATTERN"* ]]; then
                            continue
                        fi
                    fi
                    
                    if [ -z "$java_pids" ]; then
                        java_pids="$pid"
                    else
                        java_pids="$java_pids $pid"
                    fi
                fi
            fi
        fi
    done
    
    echo "$java_pids"
}

# Instantaneous per-process CPU percent (ps %cpu convention: 100.0 = 1 core fully used).
# Computed from the delta of (utime + stime) in /proc/<pid>/stat between consecutive
# refresh ticks. Returns "0.0" on the first sample for a given PID (no baseline yet).
get_proc_cpu_pct() {
    local pid=$1
    local stat
    stat=$(cat /proc/$pid/stat 2>/dev/null) || { echo "0.0"; return; }

    # /proc/<pid>/stat field 2 is "(comm)" which may contain spaces/parens.
    # Split on the *last* ')' to isolate the post-comm fields reliably.
    local rest=${stat##*) }
    local -a f
    read -ra f <<< "$rest"
    # After the stripped "pid (comm) " prefix, f[0]=state, ..., f[11]=utime, f[12]=stime
    local utime=${f[11]:-0}
    local stime=${f[12]:-0}
    local total=$((utime + stime))

    local now_ms
    now_ms=$(date +%s%3N)

    # File-backed state so the baseline survives the subshells used in JSON mode.
    local state_file="$CPU_STATE_DIR/$pid"
    local prev_total="" prev_ms=""
    if [ -n "$CPU_STATE_DIR" ] && [ -r "$state_file" ]; then
        read -r prev_total prev_ms < "$state_file"
    fi
    if [ -n "$CPU_STATE_DIR" ]; then
        printf "%s %s\n" "$total" "$now_ms" > "$state_file"
    fi

    if [ -z "$prev_total" ] || [ -z "$prev_ms" ]; then
        echo "0.0"
        return
    fi

    local dticks=$((total - prev_total))
    local dms=$((now_ms - prev_ms))
    [ $dms -le 0 ] && dms=1
    awk -v dt="$dticks" -v dms="$dms" -v tck="$CLK_TCK" 'BEGIN {
        secs = dms / 1000.0
        cpu_sec = dt / tck
        pct = (cpu_sec / secs) * 100.0
        if (pct < 0) pct = 0
        printf "%.1f", pct
    }'
}

# Get CPU usage from /proc/stat (more efficient than top)
get_cpu_usage() {
    local cpu_line=$(grep '^cpu ' /proc/stat 2>/dev/null)
    if [ -z "$cpu_line" ]; then
        echo "0.0"
        return
    fi
    
    # Calculate CPU usage from /proc/stat
    echo "$cpu_line" | awk '{idle=$4; total=$2+$3+$4+$5+$6+$7+$8; printf "%.1f", (1-idle/total)*100}'
}

# Safe arithmetic comparison without bc
compare_float() {
    local value1=$1
    local operator=$2
    local value2=$3
    
    # Use awk for reliable floating-point comparison
    awk -v v1="$value1" -v op="$operator" -v v2="$value2" 'BEGIN {
        if (op == ">") exit (v1 > v2) ? 0 : 1
        if (op == "<") exit (v1 < v2) ? 0 : 1
        if (op == ">=") exit (v1 >= v2) ? 0 : 1
        if (op == "<=") exit (v1 <= v2) ? 0 : 1
        exit 1
    }'
}

# Improved system stats function
get_system_stats() {
    if [ "$OUTPUT_FORMAT" = "json" ]; then
        echo '{"system": {'
        echo "  \"cpu_cores\": $TOTAL_CORES,"
        echo "  \"total_memory_gb\": $TOTAL_MEM_GB,"
        echo "  \"total_memory_mb\": $TOTAL_MEM_MB,"
        
        # Load average
        local load_avg=$(uptime | awk -F'load average:' '{print $2}' | tr -d ' ')
        echo "  \"load_average\": \"$load_avg\","
        
        # Memory usage
        local mem_available=$(grep MemAvailable /proc/meminfo 2>/dev/null | awk '{print $2}')
        local mem_used_kb=$((TOTAL_MEM_KB - mem_available))
        local mem_used_mb=$((mem_used_kb / 1024))
        local mem_used_pct=$(awk "BEGIN {printf \"%.1f\", ($mem_used_kb/$TOTAL_MEM_KB)*100}")
        local mem_used_bytes=$((mem_used_kb * 1024))
        
        echo "  \"memory_used_mb\": $mem_used_mb,"
        echo "  \"memory_used_pct\": $mem_used_pct,"
        echo "  \"memory_used_bytes\": $mem_used_bytes,"
        
        # CPU usage (using efficient method)
        local cpu_usage=$(get_cpu_usage)
        echo "  \"cpu_usage_pct\": $cpu_usage"
        echo '}}'
    else
        local load_avg=$(uptime | awk -F'load average:' '{print $2}' | tr -d ' ')
        local mem_available=$(grep MemAvailable /proc/meminfo 2>/dev/null | awk '{print $2}')
        local mem_used_kb=$((TOTAL_MEM_KB - mem_available))
        local mem_used_pct=$(awk "BEGIN {printf \"%.1f\", ($mem_used_kb/$TOTAL_MEM_KB)*100}")
        echo -e "${CYAN}System:${NC} CPU $(get_cpu_usage)% | Mem $(format_bytes $((mem_used_kb * 1024)))/${TOTAL_MEM_GB} GB (${mem_used_pct}%) | Load $load_avg"
    fi
}

# Improved Java process statistics function
get_java_stats() {
    local java_pids=$(get_java_pids)
    
    if [ -z "$java_pids" ]; then
        if [ "$OUTPUT_FORMAT" = "json" ]; then
            echo '{"java_processes": {"processes": [], "total_cpu_pct": 0.0, "total_memory_pct": 0.0, "process_count": 0}}'
        else
            echo -e "${YELLOW}No Java processes found${NC}"
        fi
        return
    fi
    
    # Count Java processes efficiently
    local java_count=$(echo "$java_pids" | wc -w)
    
    if [ "$OUTPUT_FORMAT" = "json" ]; then
        echo '{"java_processes": {'
        echo "  \"process_count\": $java_count,"
        echo "  \"pids\": [$(echo "$java_pids" | tr ' ' ',' | sed 's/,$//')],"
        echo '  "processes": ['
    else
        echo -e "${CYAN}=== JAVA PROCESSES DETAILS ===${NC}"
        echo "Active Java Instances: $java_count"
        echo "Java PIDs: $(echo "$java_pids" | tr ' ' ',')"
        echo ""
        
        # Header
        printf "%-8s %-10s %8s %12s %12s %10s %8s %-14s %-20s\n" \
            "PID" "USER" "CPU%" "CPU Cores" "MEM%" "RSS" "VSZ" "PORTS" "COMMAND"
        printf "%-8s %-10s %8s %12s %12s %10s %8s %-14s %-20s\n" \
            "--------" "----------" "--------" "------------" "------------" "----------" "--------" "--------------" "--------------------"
    fi
    
    # Listening TCP ports per PID, from a single ss call. ss only annotates
    # sockets the caller owns unless run as root, so ports show as "-" for
    # other users' processes.
    local ports_map
    ports_map=$(ss -tlnpH 2>/dev/null | awk '{
        n = split($4, a, ":"); port = a[n]
        s = $0
        while (match(s, /pid=[0-9]+/)) {
            print substr(s, RSTART + 4, RLENGTH - 4), port
            s = substr(s, RSTART + RLENGTH)
        }
    }' | sort -u -k1,1n -k2,2n)

    # Process tracking variables
    local total_cpu_raw=0.0
    local total_mem_pct=0.0
    local total_rss=0
    local first_process=true
    
    # Process each Java instance with single ps call per PID
    for pid in $java_pids; do
        if [ -d "/proc/$pid" ]; then
            # Get all process info in one ps call
            local ps_info=$(ps -p $pid -o user=,%cpu=,%mem=,rss=,vsz=,comm= 2>/dev/null)
            if [ -z "$ps_info" ]; then
                continue
            fi
            
            # Parse ps output efficiently
            read -r user _ps_cpu_ignored mem_pct rss_kb vsz_kb comm <<< "$ps_info"
            
            # Clean up values
            user=$(echo "$user" | tr -d ' ')
            mem_pct=$(echo "$mem_pct" | tr -d ' ')
            rss_kb=$(echo "$rss_kb" | tr -d ' ')
            vsz_kb=$(echo "$vsz_kb" | tr -d ' ')

            # Instantaneous CPU from /proc (ps %cpu is a lifetime average and
            # misleadingly stays high long after a process goes idle).
            local cpu_pct_raw
            cpu_pct_raw=$(get_proc_cpu_pct "$pid")

            # Set defaults if empty
            [ -z "$cpu_pct_raw" ] && cpu_pct_raw="0.0"
            [ -z "$mem_pct" ] && mem_pct="0.0"
            [ -z "$rss_kb" ] && rss_kb="0"
            [ -z "$vsz_kb" ] && vsz_kb="0"
            
            # Get full command safely
            local full_cmd=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ' | cut -c1-20 | sed 's/[^a-zA-Z0-9._-]/ /g')
            
            # CPU calculations
            local max_cpu=$((TOTAL_CORES * 100))
            local cpu_pct_normalized=$(awk -v raw="$cpu_pct_raw" -v max="$max_cpu" 'BEGIN {printf "%.1f", (raw / max) * 100}')
            local cpu_cores=$(awk -v raw="$cpu_pct_raw" 'BEGIN {printf "%.1f", raw/100}')
            
            # Memory formatting
            local rss_bytes=$((rss_kb * 1024))
            local vsz_bytes=$((vsz_kb * 1024))
            local rss_fmt=$(format_bytes $rss_bytes)
            local vsz_fmt=$(format_bytes $vsz_bytes)
            
            # Color coding for high resource usage (terminal only)
            local ports=$(awk -v p="$pid" '$1 == p {printf "%s%s", sep, $2; sep=","}' <<< "$ports_map")
            [ -z "$ports" ] && ports="-"

            local cpu_color=$GREEN mem_color=$GREEN
            if [ "$OUTPUT_FORMAT" != "json" ]; then
                if compare_float "$cpu_pct_normalized" ">" "$CPU_HIGH_THRESHOLD"; then
                    cpu_color=$RED
                elif compare_float "$cpu_pct_normalized" ">" "$CPU_MEDIUM_THRESHOLD"; then
                    cpu_color=$YELLOW
                fi
                
                if compare_float "$mem_pct" ">" "$MEM_HIGH_THRESHOLD"; then
                    mem_color=$RED
                elif compare_float "$mem_pct" ">" "$MEM_MEDIUM_THRESHOLD"; then
                    mem_color=$YELLOW
                fi
            fi
            
            # Output format
            if [ "$OUTPUT_FORMAT" = "json" ]; then
                [ "$first_process" = true ] && first_process=false || echo ','
                echo '    {'
                echo "      \"pid\": $pid,"
                echo "      \"user\": \"$user\","
                echo "      \"cpu_pct\": $cpu_pct_normalized,"
                echo "      \"cpu_cores\": \"$cpu_cores/$TOTAL_CORES\","
                echo "      \"memory_pct\": $mem_pct,"
                echo "      \"rss_bytes\": $rss_bytes,"
                echo "      \"rss_formatted\": \"$rss_fmt\","
                echo "      \"vsz_bytes\": $vsz_bytes,"
                echo "      \"vsz_formatted\": \"$vsz_fmt\","
                echo "      \"command\": \"$full_cmd\""
                echo -n '    }'
            else
                printf "%-8s %-10s ${cpu_color}%8.1f${NC} %12s ${mem_color}%12.1f${NC} %10s %8s %-14.14s %-20s\n" \
                    "$pid" "$user" "$cpu_pct_normalized" "${cpu_cores}/${TOTAL_CORES}" "$mem_pct" "$rss_fmt" "$vsz_fmt" "$ports" "$full_cmd"
            fi
            
            # Accumulate totals
            total_cpu_raw=$(awk -v total="$total_cpu_raw" -v current="$cpu_pct_raw" 'BEGIN {printf "%.1f", total + current}')
            total_mem_pct=$(awk -v total="$total_mem_pct" -v current="$mem_pct" 'BEGIN {printf "%.1f", total + current}')
            total_rss=$((total_rss + rss_kb))
        fi
    done
    
    # Calculate aggregate stats
    local total_cpu_normalized="0.0"
    local cpu_cores_used="0.0"
    
    if [ "$total_cpu_raw" != "0.0" ] && [ "$total_cpu_raw" != "0" ]; then
        total_cpu_normalized=$(awk -v raw="$total_cpu_raw" -v max="$max_cpu" 'BEGIN {printf "%.1f", (raw / max) * 100}')
        cpu_cores_used=$(awk -v raw="$total_cpu_raw" 'BEGIN {printf "%.1f", raw / 100}')
    fi
    
    local total_rss_bytes=$((total_rss * 1024))
    local total_mem_formatted=$(format_bytes $total_rss_bytes)
    
    if [ "$OUTPUT_FORMAT" = "json" ]; then
        echo ''
        echo '  ],'
        echo "  \"total_cpu_pct\": $total_cpu_normalized,"
        echo "  \"total_cpu_cores\": \"$cpu_cores_used/$TOTAL_CORES\","
        echo "  \"total_memory_pct\": $total_mem_pct,"
        echo "  \"total_memory_bytes\": $total_rss_bytes,"
        echo "  \"total_memory_formatted\": \"$total_mem_formatted\""
        echo '}}'
    else
        echo ""
        echo -e "${CYAN}=== JAVA AGGREGATE STATS ===${NC}"
        echo "Total Java CPU: ${total_cpu_normalized}% (${cpu_cores_used}/${TOTAL_CORES} cores)"
        echo "Total Java Memory: ${total_mem_pct}% ($total_mem_formatted)"
        echo ""
    fi
}

# Improved detailed process info function
get_proc_details() {
    local java_pids=$(get_java_pids)
    
    if [ -z "$java_pids" ]; then
        return
    fi
    
    if [ "$OUTPUT_FORMAT" = "json" ]; then
        echo '{"detailed_process_info": ['
        local first_process=true
    else
        echo -e "${CYAN}=== DETAILED JAVA PROCESS INFO ===${NC}"
    fi
    
    for pid in $java_pids; do
        if [ -d "/proc/$pid" ]; then
            if [ "$OUTPUT_FORMAT" = "json" ]; then
                [ "$first_process" = true ] && first_process=false || echo ','
                echo "  {\"pid\": $pid,"
            else
                echo "--- PID $pid ---"
            fi
            
            # Status file details with error handling
            if [ -f "/proc/$pid/status" ]; then
                local threads=$(grep Threads /proc/$pid/status 2>/dev/null | awk '{print $2}' || echo "N/A")
                local fd_count=$(ls /proc/$pid/fd/ 2>/dev/null | wc -l)
                
                # Memory details with error handling
                local vmpeak=$(grep VmPeak /proc/$pid/status 2>/dev/null | awk '{print $2}' || echo "N/A")
                local vmsize=$(grep VmSize /proc/$pid/status 2>/dev/null | awk '{print $2}' || echo "N/A")
                local vmrss=$(grep VmRSS /proc/$pid/status 2>/dev/null | awk '{print $2}' || echo "N/A")
                local vmhwm=$(grep VmHWM /proc/$pid/status 2>/dev/null | awk '{print $2}' || echo "N/A")
                
                if [ "$OUTPUT_FORMAT" = "json" ]; then
                    echo "    \"threads\": $threads,"
                    echo "    \"file_descriptors\": $fd_count,"
                    if [ "$vmpeak" != "N/A" ]; then
                        local vmpeak_bytes=$((vmpeak * 1024))
                        local vmsize_bytes=$((vmsize * 1024))
                        local vmrss_bytes=$((vmrss * 1024))
                        local vmhwm_bytes=$((vmhwm * 1024))
                        echo "    \"vm_peak_bytes\": $vmpeak_bytes,"
                        echo "    \"vm_size_bytes\": $vmsize_bytes,"
                        echo "    \"vm_rss_bytes\": $vmrss_bytes,"
                        echo "    \"vm_hwm_bytes\": $vmhwm_bytes,"
                        echo "    \"vm_peak_formatted\": \"$(format_bytes $vmpeak_bytes)\","
                        echo "    \"vm_size_formatted\": \"$(format_bytes $vmsize_bytes)\","
                        echo "    \"vm_rss_formatted\": \"$(format_bytes $vmrss_bytes)\","
                        echo "    \"vm_hwm_formatted\": \"$(format_bytes $vmhwm_bytes)\""
                    else
                        echo "    \"memory_details\": \"N/A\""
                    fi
                else
                    echo "Threads: $threads"
                    echo "File Descriptors: $fd_count"
                    
                    if [ "$vmpeak" != "N/A" ]; then
                        local vmpeak_bytes=$((vmpeak * 1024))
                        local vmsize_bytes=$((vmsize * 1024))
                        local vmrss_bytes=$((vmrss * 1024))
                        local vmhwm_bytes=$((vmhwm * 1024))
                        echo "VmPeak (peak virtual): $(format_bytes $vmpeak_bytes)"
                        echo "VmSize (virtual): $(format_bytes $vmsize_bytes)"
                        echo "VmRSS (resident): $(format_bytes $vmrss_bytes)"
                        echo "VmHWM (peak resident): $(format_bytes $vmhwm_bytes)"
                    fi
                fi
            fi
            
            # IO stats with error handling
            if [ -f "/proc/$pid/io" ]; then
                local read_bytes=$(grep read_bytes /proc/$pid/io 2>/dev/null | awk '{print $2}' || echo "0")
                local write_bytes=$(grep write_bytes /proc/$pid/io 2>/dev/null | awk '{print $2}' || echo "0")
                
                if [ "$OUTPUT_FORMAT" = "json" ]; then
                    echo "    \"io_read_bytes\": $read_bytes,"
                    echo "    \"io_write_bytes\": $write_bytes,"
                    echo "    \"io_read_formatted\": \"$(format_bytes $read_bytes)\","
                    echo "    \"io_write_formatted\": \"$(format_bytes $write_bytes)\""
                else
                    echo "IO Read: $(format_bytes $read_bytes)"
                    echo "IO Write: $(format_bytes $write_bytes)"
                fi
            fi
            
            if [ "$OUTPUT_FORMAT" = "json" ]; then
                echo -n "  }"
            else
                echo ""
            fi
        fi
    done
    
    if [ "$OUTPUT_FORMAT" = "json" ]; then
        echo ''
        echo ']}'
    fi
}

# Output function with file support
output_data() {
    local data="$1"
    
    if [ -n "$OUTPUT_FILE" ]; then
        echo "$data" >> "$OUTPUT_FILE"
    else
        echo "$data"
    fi
}

cleanup_and_exit() {
    rm -rf "$CPU_STATE_DIR" 2>/dev/null
    printf '\033[?25h'
    echo -e "\n${GREEN}Monitoring stopped.${NC}"
    exit 0
}

# Prompt for a PID and SIGKILL it. Only PIDs currently listed by the monitor
# are accepted, so a typo can't kill an unrelated process.
kill_prompt() {
    local pid
    printf '\033[?25h\n%bPID to kill -9 ("all" for every listed PID, Enter to cancel): %b' "$YELLOW" "$NC"
    read -r pid
    printf '\033[?25l'
    [ -z "$pid" ] && return 0

    if [ "$pid" = "all" ]; then
        local pids
        pids=$(get_java_pids)
        if [ -z "$pids" ]; then
            printf '%bNo monitored Java processes%b\n' "$RED" "$NC"
        else
            kill -9 $pids 2>/dev/null
            printf '%bSent SIGKILL to %s%b\n' "$GREEN" "$(echo $pids | tr '\n' ' ')" "$NC"
        fi
    elif [[ " $(get_java_pids) " != *" $pid "* ]]; then
        printf '%bNot a monitored Java PID: %s%b\n' "$RED" "$pid" "$NC"
    elif kill -9 "$pid" 2>/dev/null; then
        printf '%bSent SIGKILL to %s%b\n' "$GREEN" "$pid" "$NC"
    else
        printf '%bFailed to kill %s (permission denied or already gone)%b\n' "$RED" "$pid" "$NC"
    fi
    sleep 1
}

# Wait out the refresh interval, returning early on a keypress.
# Falls back to a plain sleep when stdin is not a terminal (piped/cron use).
wait_for_key() {
    if [ ! -t 0 ]; then
        sleep "$REFRESH_INTERVAL"
        return
    fi
    local key
    read -rsn 1 -t "$REFRESH_INTERVAL" key || return 0
    case "$key" in
        q|Q) cleanup_and_exit ;;
        k|K) kill_prompt ;;
    esac
}

# Detect the operating system and reject unsupported platforms early.
# This script relies on the Linux /proc filesystem and GNU coreutils;
# it will not function on macOS, Windows (native), or BSD variants.
detect_os() {
    local os_name
    os_name=$(uname -s 2>/dev/null)

    case "$os_name" in
        Linux)
            return 0
            ;;
        Darwin)
            echo "Error: macOS is not supported. This script requires the Linux /proc filesystem." >&2
            echo "       On macOS, consider using 'top' or 'Activity Monitor' for Java process monitoring." >&2
            exit 1
            ;;
        MINGW*|MSYS*|CYGWIN*)
            echo "Error: Windows (Git Bash / MSYS2 / Cygwin) is not fully supported." >&2
            echo "       The /proc filesystem is incomplete and many functions will fail." >&2
            echo "       Options:" >&2
            echo "         - Run inside WSL (Windows Subsystem for Linux)" >&2
            echo "         - Use PowerShell with Get-Process / Get-CimInstance instead" >&2
            exit 1
            ;;
        *)
            if [ -z "$os_name" ]; then
                echo "Error: Could not detect the operating system (uname -s failed)." >&2
            else
                echo "Error: Unsupported operating system: '$os_name'." >&2
                echo "       This script requires Linux with the /proc filesystem." >&2
            fi
            exit 1
            ;;
    esac
}

# Main monitoring loop with improvements
main() {
    # Abort immediately on non-Linux platforms before any other work
    detect_os

    # Parse command line arguments
    parse_args "$@"

    # Initialize system information
    init_system_info

    # Initialize file-backed CPU sampling state (used for instantaneous per-PID CPU).
    init_cpu_state_dir

    # Check for bc (optional now with improved compare_float)
    if ! command -v bc &> /dev/null; then
        echo "Note: 'bc' not installed. Some features may be limited." >&2
    fi
    
    # Terminal mode paints in place, so clear once up front and hide the
    # cursor (restored by the trap) instead of clearing every tick.
    if [ "$OUTPUT_FORMAT" != "json" ]; then
        clear
        printf '\033[?25l'
    fi

    # Trap to handle exit cleanly
    trap cleanup_and_exit INT TERM
    
    # Initialize output file if specified
    if [ -n "$OUTPUT_FILE" ]; then
        echo "# Java Monitor Log - $(date)" > "$OUTPUT_FILE"
        echo "# Generated by: $SCRIPT_NAME $*" >> "$OUTPUT_FILE"
        echo "# Refresh interval: ${REFRESH_INTERVAL}s" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    fi
    
    # Main monitoring loop
    while true; do
        if [ "$OUTPUT_FORMAT" = "json" ]; then
            local output="{"
            output+='"timestamp": "'$(date -Iseconds)'",'
            output+='"refresh_interval": '$REFRESH_INTERVAL','
            output+='"hostname": "'$(hostname)'",'
            
            # Get system stats
            local system_json=$(get_system_stats | tr -d '\n')
            output+="\"system\": $system_json,"
            
            # Get Java stats
            local java_json=$(get_java_stats | tr -d '\n')
            output+="\"java_processes\": $java_json,"
            
            # Get detailed info only if there's exactly 1 Java process
            local java_count=$(get_java_pids | wc -w)
            if [ "$java_count" -eq 1 ]; then
                local details_json=$(get_proc_details | tr -d '\n')
                output+="$details_json"
            else
                output+='"detailed_process_info": null'
            fi
            
            output+="}"
            output_data "$output"
        else
            # Terminal output: one compact header line plus the Java process
            # table. Rendered into a buffer, then painted from the home position
            # with a per-line erase-to-end-of-line and a final erase-to-end-of-
            # screen, so values update in place (no blank flash, no scroll).
            local frame
            frame=$(
                echo -e "${GREEN}Java Monitor${NC} | $(date '+%H:%M:%S') | every ${REFRESH_INTERVAL}s | $(hostname) | ${TOTAL_CORES} cores, ${TOTAL_MEM_GB} GB${FILTER_PATTERN:+ | pattern='$FILTER_PATTERN'}${FILTER_USER:+ | user='$FILTER_USER'}"
                get_system_stats
                get_java_stats
                echo -e "${YELLOW}[k] kill -9 a process (or all)  [q] quit${NC}"
            )
            # Truncate to the window so a long table can never push the frame
            # off the top and start scrolling.
            printf '\033[H%s\n\033[J' "$(printf '%s\n' "$frame" | head -n $(( $(tput lines 2>/dev/null || echo 24) - 1 )) | sed $'s/$/\033[K/')"
        fi
        
        if [ "$OUTPUT_FORMAT" = "json" ]; then
            sleep $REFRESH_INTERVAL
        else
            wait_for_key
        fi
    done
}

# Run main function with all arguments
main "$@"
