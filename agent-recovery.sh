#!/data/data/com.termux/files/usr/bin/bash

set -u

PROJECT="$HOME/TIREXNET"
SESSION="tirexnet-agents"
PROMPT_FILE="$PROJECT/.agents/AUTONOMOUS_SHARED_PROMPT.md"
LOG="$PROJECT/.agents/RECOVERY.log"

AGENTS=("xray" "singbox" "engines" "core-ui")

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S %z')] $*" | tee -a "$LOG"
}

cd "$PROJECT" || exit 1

mkdir -p "$PROJECT/.agents/AGENTS" "$PROJECT/.agents/LOCKS"

log "===== RECOVERY START ====="

if ! tmux has-session -t "$SESSION" 2>/dev/null; then
    log "tmux session missing -> creating $SESSION"
    tmux new-session -d -s "$SESSION" -n "${AGENTS[0]}"
fi

for agent in "${AGENTS[@]}"; do

    if tmux list-windows -t "$SESSION" -F '#W' 2>/dev/null | grep -qx "$agent"; then
        cmd="$(tmux display-message -p -t "$SESSION:$agent" '#{pane_current_command}' 2>/dev/null || true)"

        case "$cmd" in
            opencode|node|bun|deno)
                log "$agent already appears alive ($cmd)"
                continue
                ;;
        esac

        log "$agent window exists but process is not alive -> restarting"
        tmux send-keys -t "$SESSION:$agent" C-c 2>/dev/null || true
        sleep 2
    else
        log "Creating missing window: $agent"
        tmux new-window -t "$SESSION" -n "$agent"
    fi

    tmux send-keys -t "$SESSION:$agent" \
        "cd '$PROJECT' && opencode --auto --continue --prompt \"\$(cat '$PROMPT_FILE')\"" C-m

    log "Started/recovered $agent"
    sleep 3
done

log "===== RECOVERY COMPLETE ====="
