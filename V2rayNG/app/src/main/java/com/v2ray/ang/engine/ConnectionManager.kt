package com.v2ray.ang.engine

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last diagnostic snapshot for the active (or last) engine attempt.
 * Contents are safe to show in a diagnostics page and never include secrets.
 */
data class ConnectionDiagnostics(
    val engine: String,
    val protocol: String,
    val endpoint: String?,
    val dnsServers: List<String> = emptyList(),
    val connectionState: VpnEngineState,
    val lastError: String? = null,
    val connectedAtMillis: Long? = null,
)

/**
 * Raised when a profile is supported but its runtime engine is not present in this
 * build. Carries the exact missing dependency so the UI can show a real reason.
 */
class EngineUnavailableException(val capability: EngineCapability) :
    IllegalArgumentException("Required engine '${capability.engine.displayName}' is not available" +
        (capability.missingDependency?.let { ": $it" } ?: ""))

/**
 * Central connection coordinator.
 *
 * It is the single place that decides whether a profile can run (capability gate),
 * that keeps the engine state machine, and that routes start/stop/switch through the
 * existing launcher so only one VPN engine is ever handed the interface.
 *
 * The state machine is fed by the daemon's own messages (see
 * [com.v2ray.ang.service.CoreServiceManager]), so "CONNECTED" only ever means the
 * native core actually started successfully.
 */
object ConnectionManager {
    private val _state = MutableStateFlow(VpnEngineState.IDLE)
    val state: StateFlow<VpnEngineState> = _state.asStateFlow()

    @Volatile
    private var activeProfile: ProfileItem? = null

    @Volatile
    var lastDiagnostics: ConnectionDiagnostics? = null
        private set

    fun activeProfileSnapshot(): ProfileItem? = activeProfile

    /** True when the native core reports running. */
    val isConnected: Boolean get() = _state.value == VpnEngineState.CONNECTED

    fun capabilityFor(profile: ProfileItem): EngineCapability =
        EngineRegistry.capabilityFor(profile)

    /**
     * Resolves the engine capability for the selected profile that is about to start.
     * Returns a capability gate result; the caller (launcher) aborts when unavailable.
     */
    fun validate(profile: ProfileItem): EngineCapability {
        val capability = EngineRegistry.capabilityFor(profile)
        if (!capability.available) {
            _state.value = VpnEngineState.UNAVAILABLE
            lastDiagnostics = ConnectionDiagnostics(
                engine = capability.engine.displayName,
                protocol = profile.configType.name,
                endpoint = profile.getServerAddressAndPort().takeIf { it != "null:null" },
                connectionState = VpnEngineState.UNAVAILABLE,
                lastError = capability.missingDependency,
            )
        }
        return capability
    }

    /** Marks the point at which a launch attempt for [profile] begins. */
    fun onLaunchAttempt(profile: ProfileItem) {
        activeProfile = profile
        _state.value = VpnEngineState.CONNECTING
        lastDiagnostics = ConnectionDiagnostics(
            engine = EngineRegistry.capabilityFor(profile).engine.displayName,
            protocol = profile.configType.name,
            endpoint = profile.getServerAddressAndPort().takeIf { it != "null:null" },
            dnsServers = if (profile.configType == com.v2ray.ang.enums.EConfigType.DNS) {
                profile.dnsServers?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            } else {
                emptyList()
            },
            connectionState = VpnEngineState.CONNECTING,
        )
    }

    /** Feeds daemon messages into the engine state machine. */
    fun onServiceCommand(msgId: Int) {
        when (msgId) {
            AppConfig.MSG_STATE_RUNNING,
            AppConfig.MSG_STATE_START_SUCCESS -> {
                _state.value = VpnEngineState.CONNECTED
                lastDiagnostics = lastDiagnostics?.copy(
                    connectionState = VpnEngineState.CONNECTED,
                    connectedAtMillis = System.currentTimeMillis(),
                    lastError = null,
                )
            }

            AppConfig.MSG_STATE_STOP_SUCCESS,
            AppConfig.MSG_STATE_NOT_RUNNING -> {
                _state.value = VpnEngineState.DISCONNECTED
                lastDiagnostics = lastDiagnostics?.copy(
                    connectionState = VpnEngineState.DISCONNECTED,
                    connectedAtMillis = null,
                )
                activeProfile = null
            }

            AppConfig.MSG_STATE_START_FAILURE -> {
                _state.value = VpnEngineState.ERROR
            }
        }
    }

    fun onStartFailure(detail: String?) {
        _state.value = VpnEngineState.ERROR
        lastDiagnostics = lastDiagnostics?.copy(
            connectionState = VpnEngineState.ERROR,
            lastError = detail,
            connectedAtMillis = null,
        )
    }

    fun reset() {
        _state.value = VpnEngineState.IDLE
        activeProfile = null
        lastDiagnostics = null
    }
}