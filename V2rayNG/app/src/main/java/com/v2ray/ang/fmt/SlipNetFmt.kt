package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.Utils
import java.util.Locale

/**
 * Parses SlipNet `slipnet://` URIs into [ProfileItem].
 *
 * The SlipNet protocol (slip.network) shares tunnel configs as pipe-delimited
 * base64 payloads, matching the SlipGate server tool and the SlipNet Android app:
 *
 * ```
 * slipnet://<base64("17|dnstt|name|t.example.com|8.8.8.8:53:0|...")>[#fragment]
 * ```
 *
 * Field layout (v17-v22, see slipgate internal/clientcfg/fields.go):
 * index 0 version, 1 tunnel type, 2 profile name, 3 tunnel domain,
 * 4 resolvers, 5 auth mode, 6 keep-alive, 7 congestion control,
 * 8-10 TCP listen port/host/GSO, 11 Curve25519 public key, 12-13 SOCKS5
 * user/pass, 14-19 SSH fields, 20 use server DNS, 21 DoH URL,
 * 22 DNS transport, 23-26 SSH auth details, 27 DNSTT authoritative,
 * 28-30 NaiveProxy port/user/pass, 31-35 locking fields, 36-40 resolver
 * stealth/DNS payload/SOCKS5 server port, 41-49 VayDNS tuning,
 * 50-59 SSH-over-TLS/WS/HTTP-proxy and payload injection.
 *
 * Only parsing, metadata extraction, and persistence are performed here. The
 * bundled Xray core does not contain any SlipNet transport engine (DNSTT,
 * NoizDNS, VayDNS, Slipstream, NaiveProxy, or SSH tunneling), so establishing a
 * tunnel from these entries is not technically possible in this build; connecting
 * reports a clear error instead of faking a connection.
 */
object SlipNetFmt {

    // Pipe-delimited field positions (slipgate TotalFields layout).
    private const val FIELD_VERSION = 0
    private const val FIELD_TUNNEL_TYPE = 1
    private const val FIELD_NAME = 2
    private const val FIELD_DOMAIN = 3
    private const val FIELD_RESOLVERS = 4
    private const val FIELD_PUBLIC_KEY = 11
    private const val FIELD_TOR_BRIDGES = 26
    private const val FIELD_NAIVE_PORT = 28

    private const val TUNNEL_NAIVE = "naive"
    private const val TUNNEL_NAIVE_SSH = "naive_ssh"
    private const val TUNNEL_SS = "ss"
    private const val TUNNEL_SLIPSTREAM_SSH = "slipstream_ssh"
    private const val TUNNEL_TOR = "tor"
    private const val TUNNEL_SNOWFLAKE = "snowflake"
    private const val TUNNEL_OBFS4 = "obfs4"
    private const val TUNNEL_MEEK = "meek"

    /** Tunnel families that bridge through the Tor network instead of a slipgate server. */
    private val TOR_FAMILY = setOf(TUNNEL_TOR, TUNNEL_SNOWFLAKE, TUNNEL_OBFS4, TUNNEL_MEEK)

    fun isTorFamily(tunnelType: String?): Boolean =
        tunnelType?.lowercase(Locale.ROOT) in TOR_FAMILY

    /**
     * Parses a `slipnet://` URI into a profile, or null when the payload is not a
     * recognized SlipNet server entry.
     */
    fun parse(str: String): ProfileItem? {
        if (str.isBlank()) return null
        val trimmed = str.trim()
        if (!trimmed.startsWith(AppConfig.SLIPNET, ignoreCase = true)) return null
        return parseUri(trimmed)
    }

    private fun parseUri(str: String): ProfileItem? {
        return try {
            val payload = str.trim()
                .removePrefix(AppConfig.SLIPNET)
                .takeBefore('#')
                .trim()
            val decoded = Utils.safeDecodeBase64(payload) ?: return null
            val fields = decoded.split("|", limit = 60)

            val version = fields.getOrNull(FIELD_VERSION).orEmpty()
            val tunnelType = fields.getOrNull(FIELD_TUNNEL_TYPE).orEmpty()
            if (tunnelType.isBlank()) return null
            if (version.isNotBlank() && version.toIntOrNull() == null) return null

            val config = ProfileItem.create(EConfigType.SLIPNET)
            config.slipNetVersion = version.takeUnless { it.isBlank() }
            config.slipNetTunnelType = tunnelType.takeUnless { it.isBlank() }
            config.slipNetResolvers = fields.getOrNull(FIELD_RESOLVERS)
                ?.takeUnless { it.isBlank() || it == "0" }
            config.slipNetPublicKey = fields.getOrNull(FIELD_PUBLIC_KEY)
                ?.takeUnless { it.isBlank() || it == "0" }
            config.slipNetTorBridgeLines = fields.getOrNull(FIELD_TOR_BRIDGES)
                ?.takeUnless { it.isBlank() || it == "0" }

            // The full link is preserved so toUri() can re-export it losslessly.
            config.slipNetConfig = str.trim()

            val name = fields.getOrNull(FIELD_NAME).orEmpty()
            config.remarks = name.takeUnless { it.isBlank() }
                ?: "slipnet-${tunnelType.takeUnless { it.isBlank() } ?: "tunnel"}"

            // Tor-family configs legitimately have no slipgate server/domain;
            // they bridge through the Tor network via the bridge lines.
            if (!isTorFamily(tunnelType)) {
                config.server = fields.getOrNull(FIELD_DOMAIN).orEmpty().takeUnless { it.isBlank() }
                config.serverPort = derivePort(tunnelType, fields)
            }
            config.description = buildString {
                tunnelType.uppercase(Locale.ROOT).let {
                    if (it.isNotBlank()) append(it).append(' ')
                }
                if (config.server.isNullOrBlank()) {
                    append("tor-network")
                } else {
                    append(config.server).append(':').append(config.serverPort.orEmpty())
                }
            }

            config
        } catch (e: Exception) {
            null
        }
    }

    private fun derivePort(tunnelType: String, fields: List<String>): String {
        return when (tunnelType) {
            TUNNEL_NAIVE, TUNNEL_NAIVE_SSH ->
                fields.getOrNull(FIELD_NAIVE_PORT)?.takeUnless { it.isBlank() || it == "0" }
                    ?: "443"
            TUNNEL_SS, TUNNEL_SLIPSTREAM_SSH -> "53"
            else -> "53"
        }
    }

    /**
     * Structural validation used by the editor screen and import pipeline.
     * Returns an exact, user-facing reason the entry cannot be used, or null when
     * the entry is structurally complete for its tunnel family.
     */
    fun validate(config: ProfileItem): String? {
        val tunnelType = config.slipNetTunnelType.orEmpty()
        if (tunnelType.isBlank()) return "slipNetTunnelType is required"
        if (isTorFamily(tunnelType)) {
            if (config.slipNetTorBridgeLines.isNullOrBlank()) {
                return "Tor-family tunnels require bridge lines"
            }
            return null
        }
        if (config.slipNetResolvers.isNullOrBlank()) {
            return "slipNetResolvers is required"
        }
        if (config.server != null && config.server!!.isNotBlank()) {
            return null
        }
        val domain = config.slipNetConfig?.substringAfter("#", "")
            .orEmpty()
            .trim()
        if (domain.isNotBlank()) return null
        return "tunnel domain is required"
    }

    /**
     * Serializes a profile back into a `slipnet://` payload (without the scheme).
     * [AngConfigManager.shareConfig] prepends `protocolScheme`, so this returns
     * the raw payload only, matching the PsiphonFmt contract. The payload is the
     * original link's payload when it was preserved at import time (lossless
     * round trip); otherwise a minimal entry is rebuilt from the parsed fields.
     */
    fun toUri(config: ProfileItem): String {
        config.slipNetConfig?.let {
            if (it.startsWith(AppConfig.SLIPNET, ignoreCase = true)) {
                return it.trim().removePrefix(AppConfig.SLIPNET)
            }
        }

        val fields = arrayOfNulls<String>(60)
        fields[FIELD_VERSION] = config.slipNetVersion ?: "17"
        fields[FIELD_TUNNEL_TYPE] = config.slipNetTunnelType ?: "dnstt"
        fields[FIELD_NAME] = config.remarks
        fields[FIELD_DOMAIN] = config.server ?: ""
        fields[FIELD_RESOLVERS] = config.slipNetResolvers ?: ""
        fields[FIELD_PUBLIC_KEY] = config.slipNetPublicKey ?: ""
        fields[FIELD_TOR_BRIDGES] = config.slipNetTorBridgeLines ?: ""

        return Utils.encode(fields.joinToString("|", transform = { it.orEmpty() }))
    }

    /**
     * Detects whether a string is a SlipNet server list (one or more `slipnet://`
     * links). A non-empty result means genuine SlipNet links were found.
     */
    fun isSlipNetServerList(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        return str.lines().any { line ->
            line.trim().startsWith(AppConfig.SLIPNET, ignoreCase = true)
        }
    }

    private fun String.takeBefore(ch: Char): String {
        val index = indexOf(ch)
        return if (index >= 0) substring(0, index) else this
    }
}