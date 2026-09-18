# TIREXNET Multi-Agent Project State

Project: TIREXNET
Repository: ~/TIREXNET

IMPORTANT:
- Existing uncommitted changes are intentional.
- Do NOT reset, clean, stash, checkout, or overwrite existing work.
- Do NOT commit or push unless explicitly authorized by the owner.

## Current architecture

Base:
- V2rayNG Android application

Active areas:
- Xray / VLESS / VMess / Trojan
- Sing-box
- WireGuard
- AmneziaWG
- Psiphon
- SlipNet
- DNS
- MTProto
- Mirrly
- Android VPN/TUN
- Simple UI
- Professional UI
- Import / Parser system
- Testing

## Multi-Agent Rule

Every agent MUST:
1. Read this file before starting.
2. Read ARCHITECTURE.md and TASKS.md.
3. Check AGENTS/ for active work.
4. Check LOCKS/ before modifying shared files.
5. Record what it is doing in its own AGENTS/<agent>.md.
6. Avoid files currently locked by another agent.
7. Never delete or revert another agent's work.
8. Run relevant tests after changes.
9. Update its status when finished.
10. Never claim a feature works without testing it.

## Connection rule

Never fake CONNECTED state.

If an engine is unavailable:
ENGINE UNAVAILABLE

If connection fails:
ERROR

Only report CONNECTED after a real successful connection.
