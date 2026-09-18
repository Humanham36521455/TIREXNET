#!/data/data/com.termux/files/usr/bin/bash

PROJECT="$HOME/TIREXNET"
LOG="$PROJECT/.agents/BOOT.log"

mkdir -p "$PROJECT/.agents"

echo "[$(date '+%Y-%m-%d %H:%M:%S %z')] Phone boot recovery started" >> "$LOG"

termux-wake-lock 2>/dev/null || true

cd "$PROJECT" || exit 1

sleep 10

./agent-recovery.sh >> "$LOG" 2>&1
