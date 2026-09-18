#!/data/data/com.termux/files/usr/bin/bash

ROOT="$HOME/TIREXNET"
SESSION="tirexnet-agents"
STATE="$ROOT/.agents"
LOG="$STATE/SUPERVISOR.log"

CHECK_INTERVAL=30
RATE_LIMIT_WAIT=300
CRASH_WAIT=60

mkdir -p "$STATE"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"
}

get_output() {
    local WINDOW="$1"

    tmux capture-pane \
        -t "$SESSION:$WINDOW" \
        -p \
        -S -120 \
        2>/dev/null || true
}

is_rate_limited() {
    local OUTPUT="$1"

    echo "$OUTPUT" | grep -Eiq \
        'rate.?limit|too many requests|429|quota exceeded|quota.*limit|resource exhausted|usage limit|limit reached|capacity'
}

is_real_error() {
    local OUTPUT="$1"

    echo "$OUTPUT" | grep -Eiq \
        'fatal error|uncaught exception|connection refused|network error|ECONNRESET|ECONNREFUSED'
}

restart_agent() {
    local WINDOW="$1"
    local REASON="$2"

    case "$WINDOW" in
        xray)
            TASK="Xray engine and protocols: VLESS, VMess, Trojan, XHTTP, TCP, WS, HTTP headers, TLS, Reality, SNI, Host, Path, Flow. Fix parser and runtime integration while preserving existing functionality."
            ;;
        singbox)
            TASK="Sing-box engine and protocol integration. Implement and verify Sing-box based configs, transports, protocol detection and runtime lifecycle. Integrate with Universal Import without breaking Xray."
            ;;
        engines)
            TASK="WireGuard, AmneziaWG, Psiphon, SlipNet, MTProto and Mirrly. Implement real runtime behavior, parsers, lifecycle, diagnostics and integration. Preserve working native components."
            ;;
        core-ui)
            TASK="Universal Import, Profile normalization, ConnectionManager, DNS/DNSTT/MasterDNS/VayDNS, real Ping/Stats, Simple UI, Professional UI and integration tests. Coordinate with all other agents."
            ;;
        *)
            return
            ;;
    esac

    log "Restarting $WINDOW because: $REASON"

    tmux send-keys -t "$SESSION:$WINDOW" \
        "cd '$ROOT' && export TIREXNET_AGENT='$WINDOW' && export TIREXNET_TASK='$TASK' && echo 'Supervisor: resuming $WINDOW...' && opencode --auto --continue --prompt \"\$(cat '.agents/AUTONOMOUS_SHARED_PROMPT.md')\"" \
        C-m
}

log "Supervisor started"

declare -A LAST_RESTART
declare -A BACKOFF

for W in xray singbox engines core-ui; do
    LAST_RESTART[$W]=0
    BACKOFF[$W]=$RATE_LIMIT_WAIT
done

while true; do

    if ! tmux has-session -t "$SESSION" 2>/dev/null; then
        log "tmux session disappeared; waiting."
        sleep 30
        continue
    fi

    NOW=$(date +%s)

    for WINDOW in xray singbox engines core-ui; do

        if ! tmux list-windows -t "$SESSION" \
            -F '#{window_name}' 2>/dev/null \
            | grep -Fxq "$WINDOW"; then
            log "Window missing: $WINDOW"
            continue
        fi

        OUTPUT="$(get_output "$WINDOW")"

        # ------------------------------------------
        # Rate limit detection
        # ------------------------------------------
        if is_rate_limited "$OUTPUT"; then

            LAST="${LAST_RESTART[$WINDOW]}"
            WAIT="${BACKOFF[$WINDOW]}"

            if [ $((NOW - LAST)) -ge "$WAIT" ]; then

                log "RATE LIMIT detected for $WINDOW"

                tmux send-keys -t "$SESSION:$WINDOW" \
                    C-c

                sleep 3

                restart_agent "$WINDOW" "rate limit detected"

                LAST_RESTART[$WINDOW]=$NOW

                # Exponential backoff, max 30 minutes
                NEXT=$((WAIT * 2))

                if [ "$NEXT" -gt 1800 ]; then
                    NEXT=1800
                fi

                BACKOFF[$WINDOW]=$NEXT
            fi

            continue
        fi

        # ------------------------------------------
        # Detect crashed OpenCode
        # ------------------------------------------
        CMD="$(tmux list-panes -t "$SESSION:$WINDOW" \
            -F '#{pane_current_command}' 2>/dev/null | head -1)"

        if [ "$CMD" = "bash" ] || [ "$CMD" = "sh" ] || [ -z "$CMD" ]; then

            LAST="${LAST_RESTART[$WINDOW]}"

            if [ $((NOW - LAST)) -ge "$CRASH_WAIT" ]; then

                log "OpenCode appears stopped for $WINDOW"

                restart_agent "$WINDOW" "OpenCode process stopped"

                LAST_RESTART[$WINDOW]=$NOW
                BACKOFF[$WINDOW]=$RATE_LIMIT_WAIT
            fi
        fi

    done

    sleep "$CHECK_INTERVAL"
done
