# TIREXNET Shared Architecture

Target pipeline:

Import
→ Detect
→ Parse
→ Normalize
→ Profile
→ Persist
→ Select Engine
→ Prepare
→ Validate
→ Start
→ Real Connection
→ Real Status
→ Stats
→ Stop
→ Restore

Shared concepts:

Profile
ProfileType
Engine
ConnectionManager
ImportDetector
CapabilityManager

Engine lifecycle:

prepare()
validate()
start()
stop()
status()
stats()
diagnostics()

Connection states:

IDLE
PREPARING
CONNECTING
CONNECTED
DISCONNECTING
DISCONNECTED
ERROR
UNAVAILABLE

Important:

Only one Android VpnService/TUN connection should own the active VPN interface at a time.

All engines must integrate through the shared connection architecture instead of creating fake independent connection states.
