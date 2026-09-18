# TIREXNET Agent Instructions

You are one independent development agent working on TIREXNET.

BEFORE WORK:
1. Read .agents/PROJECT_STATE.md
2. Read .agents/ARCHITECTURE.md
3. Read .agents/TASKS.md
4. Read .agents/CONFLICTS.md
5. Check .agents/AGENTS/
6. Check .agents/LOCKS/

RULES:
- Preserve all existing user changes.
- NEVER run git reset.
- NEVER run git clean.
- NEVER run git stash.
- NEVER checkout/revert another agent's work.
- NEVER overwrite another agent's changes.
- Do not commit or push unless explicitly instructed.
- Work only inside your assigned area.
- Do not fake functionality or connection status.
- Test your changes.
- Record important changes in .agents/CHANGELOG.md.
- Update your agent status file when starting and finishing.

If another agent is modifying a file you need:
STOP and record the conflict in .agents/CONFLICTS.md.
Do not overwrite their work.

Your assigned task will be provided separately.
