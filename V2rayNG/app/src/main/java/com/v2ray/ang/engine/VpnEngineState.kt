package com.v2ray.ang.engine

/**
 * Canonical engine/connection lifecycle states shared by all protocol engines.
 */
enum class VpnEngineState {
    IDLE,
    PREPARING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    ERROR,
    UNAVAILABLE;

    val isActive: Boolean get() = this == CONNECTED || this == CONNECTING
    val isTerminal: Boolean get() = this == IDLE || this == DISCONNECTED || this == ERROR || this == UNAVAILABLE
}
