package com.v2ray.ang.engine

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.engine.singbox.SingBoxConfigBuilder
import com.v2ray.ang.engine.singbox.SingBoxRuntime
import com.v2ray.ang.extension.isComplexType

/**
 * Runtime engines that a [ProfileItem] can map to.
 *
 * The component name is only used in diagnostics and capability checks; it is never
 * used as profile identity or in the persistence path.
 */
enum class EngineId(val displayName: String) {
    XRAY("Xray"),
    SINGBOX("Sing-box"),
    AMNEZIA_WG("AmneziaWG"),
    PSIPHON("Psiphon"),
    SLIPNET("SlipNet"),
    DNS("DNS"),
    MIRRLY("Mirrly"),
    MT_PROTO("MTProto"),
    CUSTOM("Custom"),
    UNKNOWN("Unknown");

    companion object {
        fun forType(configType: EConfigType): EngineId = when (configType) {
            EConfigType.VMESS,
            EConfigType.SHADOWSOCKS,
            EConfigType.SOCKS,
            EConfigType.VLESS,
            EConfigType.TROJAN,
            EConfigType.WIREGUARD,
            EConfigType.HYSTERIA2,
            EConfigType.HYSTERIA,
            EConfigType.HTTP,
            EConfigType.POLICYGROUP,
            EConfigType.PROXYCHAIN -> XRAY

            EConfigType.AMNEZIA_WG -> AMNEZIA_WG
            EConfigType.PSIPHON -> PSIPHON
            EConfigType.SLIPNET -> SLIPNET
            EConfigType.DNS -> DNS
            EConfigType.CUSTOM -> CUSTOM
        }

        /** Maps a native-library-backed feature to its engine id (used by Mirrly/MTProto). */
        fun forFeature(name: String): EngineId = when (name.lowercase()) {
            "mirrly" -> MIRRLY
            "mtproto" -> MT_PROTO
            "singbox", "sing-box" -> SINGBOX
            else -> UNKNOWN
        }
    }
}

/**
 * Availability of a runtime engine for a given profile.
 *
 * `supported` means the protocol/config format is understood. `available` means a
 * runtime engine that can honor the profile is present in this build. A profile can
 * be supported yet not available (e.g. SlipNet, Psiphon), in which case the UI must
 * report the exact missing dependency instead of faking a connection.
 */
data class EngineCapability(
    val engine: EngineId,
    val supported: Boolean,
    val available: Boolean,
    val requiresRoot: Boolean = false,
    val missingDependency: String? = null,
) {
    val isBlocked: Boolean get() = supported && !available
}

object EngineRegistry {

    /**
     * Maps a profile to the engine capability that would serve it.
     *
     * Xray-backed protocols (VLESS/VMess/SS/Trojan/SOCKS/HTTP/Hysteria2 and plain
     * WireGuard) are served by the bundled Xray core. AmneziaWG obfuscated mode,
     * Psiphon, and SlipNet are real config formats that this build cannot run
     * because the corresponding engine/transport is not bundled; they are reported
     * as supported-but-unavailable with the exact missing dependency.
     */
    fun capabilityFor(profile: ProfileItem): EngineCapability {
        val type = profile.configType
        return when (type) {
            EConfigType.VMESS,
            EConfigType.SHADOWSOCKS,
            EConfigType.SOCKS,
            EConfigType.VLESS,
            EConfigType.TROJAN,
            EConfigType.WIREGUARD,
            EConfigType.HYSTERIA2,
            EConfigType.HYSTERIA,
            EConfigType.HTTP,
            EConfigType.POLICYGROUP,
            EConfigType.PROXYCHAIN -> EngineCapability(EngineId.XRAY, supported = true, available = true)

            EConfigType.AMNEZIA_WG ->
                if (profile.hasAmneziaObfuscation) {
                    EngineCapability(
                        engine = EngineId.AMNEZIA_WG,
                        supported = true,
                        available = false,
                        missingDependency = "amnezia protocol transport"
                    )
                } else {
                    // Plain-WireGuard-compatible AmneziaWG configs connect through Xray's
                    // real WireGuard implementation.
                    EngineCapability(EngineId.XRAY, supported = true, available = true)
                }

            EConfigType.PSIPHON -> EngineCapability(
                engine = EngineId.PSIPHON,
                supported = true,
                available = false,
                missingDependency = "Psiphon engine (VPN SDK / libpsiphon)"
            )

            EConfigType.SLIPNET -> EngineCapability(
                engine = EngineId.SLIPNET,
                supported = true,
                available = false,
                missingDependency = "SlipNet engine (dnstt / noizdns / vaydns / slipstream client)"
            )

            EConfigType.CUSTOM -> EngineCapability(EngineId.CUSTOM, supported = true, available = true)

            EConfigType.DNS -> EngineCapability(EngineId.DNS, supported = true, available = true)

            else -> EngineCapability(EngineId.UNKNOWN, supported = true, available = false)
        }
    }

    /**
     * Capability of the sing-box engine for [profile]. This is distinct from
     * [capabilityFor]: it reports whether the profile could be expressed as a
     * sing-box outbound AND whether a sing-box runtime exists to honor it. Today no
     * runtime is bundled, so `available` is always false for supported profiles.
     */
    fun singBoxCapability(profile: ProfileItem): EngineCapability {
        val prepared = SingBoxConfigBuilder.build(profile)
        if (!prepared.isSupported) {
            return EngineCapability(
                engine = EngineId.SINGBOX,
                supported = false,
                available = false,
                missingDependency = prepared.unsupportedReason,
            )
        }
        val runtimeAvailable = SingBoxRuntime.isAvailable()
        return EngineCapability(
            engine = EngineId.SINGBOX,
            supported = true,
            available = runtimeAvailable,
            missingDependency = if (runtimeAvailable) null else SingBoxRuntime.missingDependency(),
        )
    }

    /**
     * Profiles that cannot expose a testable host (setup/debug, DNS, complex groups,
     * or any non-URL config) are excluded from the address-validity gate in the
     * launcher. This mirrors the old `isComplexType()` behavior but is explicit
     * about DNS-only and protocol-only entries.
     */
    fun hasConnectableAddress(profile: ProfileItem): Boolean {
        if (profile.configType.isComplexType()) return true
        return when (profile.configType) {
            EConfigType.DNS -> profile.dnsServers.isNotNullOrEmpty()
            EConfigType.SLIPNET -> !profile.slipNetResolvers.isNullOrBlank() && !profile.slipNetConfig.isNullOrBlank()
            EConfigType.PSIPHON -> !profile.slipNetConfig.isNullOrEmpty() && profile.server.isNotNullOrEmpty()
            else -> !profile.server.isNullOrBlank()
        }
    }

    private fun String?.isNotNullOrEmpty(): Boolean = !this.isNullOrEmpty()
}