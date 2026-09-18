#!/data/data/com.termux/files/usr/bin/bash

set -u

PROJECT="$HOME/TIREXNET"
BRANCH="agent-checkpoints"
LOG="$PROJECT/.agents/CHECKPOINT.log"
LOCK="$PROJECT/.agents/.checkpoint.lock"

cd "$PROJECT" || exit 1
mkdir -p "$PROJECT/.agents"

if [ -e "$LOCK" ]; then
    exit 0
fi

touch "$LOCK"
trap 'rm -f "$LOCK" "$TMP_INDEX" 2>/dev/null || true' EXIT

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S %z')] $*" | tee -a "$LOG"
}

log "===== CHECKPOINT START ====="

CURRENT_BRANCH="$(git branch --show-current)"
CURRENT_HEAD="$(git rev-parse HEAD)"

TMP_INDEX="$(mktemp)"

# Build a temporary index. The real index and working tree are untouched.
export GIT_INDEX_FILE="$TMP_INDEX"

git read-tree "$CURRENT_HEAD" || {
    log "ERROR: could not prepare temporary index"
    exit 1
}

git add -A || {
    log "ERROR: git add failed"
    exit 1
}

if git diff --cached --quiet; then
    log "No changes detected."
    unset GIT_INDEX_FILE
    exit 0
fi

TREE="$(git write-tree)" || {
    log "ERROR: git write-tree failed"
    exit 1
}

COMMIT="$(
    printf 'TIREXNET automatic checkpoint\n\nTime: %s\nOriginal branch: %s\nOriginal HEAD: %s\n' \
        "$(date '+%Y-%m-%d %H:%M:%S %z')" \
        "$CURRENT_BRANCH" \
        "$CURRENT_HEAD" |
    git commit-tree "$TREE" -p "$CURRENT_HEAD"
)" || {
    log "ERROR: commit-tree failed"
    exit 1
}

if [ -z "$COMMIT" ]; then
    log "ERROR: empty checkpoint commit"
    exit 1
fi

unset GIT_INDEX_FILE

log "Checkpoint commit: $COMMIT"

# Update ONLY the checkpoint branch.
git update-ref "refs/heads/$BRANCH" "$COMMIT" || {
    log "ERROR: could not update checkpoint branch"
    exit 1
}

# Push ONLY agent-checkpoints.
git push origin "$BRANCH" --force-with-lease || {
    log "ERROR: checkpoint push failed"
    exit 1
}

log "Checkpoint pushed successfully: origin/$BRANCH"
log "Original branch preserved: $CURRENT_BRANCH"
log "Original HEAD preserved: $CURRENT_HEAD"
log "===== CHECKPOINT COMPLETE ====="
