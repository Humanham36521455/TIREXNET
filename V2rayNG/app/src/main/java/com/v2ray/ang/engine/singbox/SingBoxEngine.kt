package com.v2ray.ang.engine.singbox

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.engine.ConnectionDiagnostics
import com.v2ray.ang.engine.ConnectionManager
import com.v2ray.ang.engine.EngineCapability
import com.v2ray.ang.engine.EngineId
import com.v2ray.ang.engine.EngineRegistry
import com.v2ray.ang.engine.VpnEngineState

/**
 * Sing-box engine lifecycle.
 *
 * This object implements the shared engine contract without owning a second VPN
 * state machine: connection state is fed by the daemon through
 * [ConnectionManager], exactly like the Xray path, so CONNECTED can only ever mean
 * a real running tunnel.
 *
 * With no bundled sing-box runtime this engine is permanently UNAVAILABLE; [start]
 * never fabricates a connection and reports the exact missing dependency.
 */
object SingBoxEngine {

    val engineId: EngineId = EngineId.SINGBOX

    data class StartResult(
        val started: Boolean,
        val state: VpnEngineState,
        val error: String? = null,
    )

    /** Plain traffic counts. Zero is only meaningful while the daemon reports CONNECTED. */
    data class StatsSnapshot(
        val uplinkBytes: Long = 0,
        val downlinkBytes: Long = 0,
    )

    /** Turns the validated profile into the sing-box outbound that would be launched. */
    fun prepare(profile: ProfileItem): SingBoxConfigBuilder.Result =
        SingBoxConfigBuilder.build(profile)

    fun validate(profile: ProfileItem): EngineCapability =
        EngineRegistry.singBoxCapability(profile)

    /**
     * Starts the launch only when the runtime is genuinely available. Any other case
     * returns a non-started result with UNAVAILABLE/ERROR, never CONNECTED.
     */
    fun start(profile: ProfileItem): StartResult {
        val capability = validate(profile)
        if (!capability.available) {
            return StartResult(false, VpnEngineState.UNAVAILABLE, capability.missingDependency)
        }
        val prepared = prepare(profile)
        if (!prepared.isSupported) {
            return StartResult(false, VpnEngineState.UNAVAILABLE, prepared.unsupportedReason)
        }
        val outbound = prepared.outbound ?: return StartResult(false, VpnEngineState.UNAVAILABLE, "no outbound config")
        return try {
            if (SingBoxRuntime.launch(outbound)) {
                StartResult(true, VpnEngineState.CONNECTING, null)
            } else {
                StartResult(false, VpnEngineState.ERROR, "sing-box launch reported failure")
            }
        } catch (e: Exception) {
            StartResult(false, VpnEngineState.ERROR, e.message)
        }
    }

    fun stop() {
        SingBoxRuntime.shutdown()
    }

    /** Delegates to the centralized engine state; never reports CONNECTED by itself. */
    fun status(): VpnEngineState = ConnectionManager.state.value

    fun stats(): StatsSnapshot = StatsSnapshot()

    /** Delegates to the centralized diagnostics snapshot. */
    fun diagnostics(): ConnectionDiagnostics? = ConnectionManager.lastDiagnostics
}