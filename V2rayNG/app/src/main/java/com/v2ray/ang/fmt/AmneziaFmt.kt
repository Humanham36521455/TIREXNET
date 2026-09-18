package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils
import android.util.Base64
import java.net.URI

/**
 * Parses AmneziaWG configuration formats into [ProfileItem].
 *
 * AmneziaWG is a WireGuard fork that adds optional packet-junk obfuscation
 * (Jc / Jmin / Jmax and the magic headers S1/S2/H1/H2). The wire format of an
 * outbound built from these profiles is a WireGuard outbound: Xray-core cannot
 * interpret the AmneziaWG obfuscation layer, so connections succeed only when
 * the server has the obfuscation disabled (Jc <= 0). The junk parameters are
 * still persisted so that re-exporting the profile keeps the original data.
 *
 * Supported inputs:
 * - [Interface]/[Peer] text config (pasted or file content)
 * - `amnezia://` URI whose payload is a Base64-encoded config
 * - legacy `vpn://` URI whose Base64 payload contains AmneziaWG sections
 */
object AmneziaFmt {

    fun parse(str: String): ProfileItem? {
        val trimmed = str.trim()
        if (trimmed.startsWith("[Interface]", ignoreCase = true)) {
            return parseConfigText(trimmed)
        }
        return when {
            trimmed.startsWith(AppConfig.AMNEZIA, ignoreCase = true) ->
                parseUri(trimmed, AppConfig.AMNEZIA)
            trimmed.startsWith(AppConfig.V2RAYS_AMNEZIA_VPN, ignoreCase = true) ->
                parseUri(trimmed, AppConfig.V2RAYS_AMNEZIA_VPN)
            else -> null
        }
    }

    private fun parseUri(str: String, prefix: String): ProfileItem? {
        return try {
            val uri = URI(Utils.fixIllegalUrl(str))
            val payload = uri.rawSchemeSpecificPart.orEmpty()
                .removePrefix(prefix)
                .removePrefix("//")
                .takeBefore('#')
            val decoded = decodePayload(payload) ?: return null
            val config = parseConfigText(decoded) ?: return null
            val remark = Utils.decodeURIComponent(uri.fragment.orEmpty())
            if (remark.isNotBlank()) {
                config.remarks = remark
            }
            config
        } catch (_: Exception) {
            null
        }
    }

    private fun String.takeBefore(ch: Char): String {
        val index = indexOf(ch)
        return if (index >= 0) substring(0, index) else this
    }

    private fun decodePayload(payload: String): String? {
        val decoded = Utils.safeDecodeBase64(payload)
        return decoded?.takeIf { it.isNotBlank() }
    }

    /**
     * Parses an AmneziaWG `[Interface]` / `[Peer]` text config.
     */
    fun parseConfigText(str: String): ProfileItem? {
        val interfaceParams = mutableMapOf<String, String>()
        val peerParams = mutableMapOf<String, String>()
        var currentSection: String? = null

        str.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@forEach
            when {
                trimmedLine.startsWith("[Interface]", ignoreCase = true) -> currentSection = "Interface"
                trimmedLine.startsWith("[Peer]", ignoreCase = true) -> currentSection = "Peer"
                else -> {
                    if (currentSection != null) {
                        val parts = trimmedLine.split("=", limit = 2).map { it.trim() }
                        if (parts.size == 2) {
                            when (currentSection) {
                                "Interface" -> interfaceParams[parts[0].lowercase()] = parts[1]
                                "Peer" -> peerParams[parts[0].lowercase()] = parts[1]
                            }
                        }
                    }
                }
            }
        }

        val peerPublicKey = peerParams["publickey"].orEmpty()
        if (interfaceParams.isEmpty() && peerPublicKey.isEmpty()) {
            return null
        }

        val config = ProfileItem.create(EConfigType.AMNEZIA_WG)
        config.secretKey = interfaceParams["privatekey"].orEmpty()
        config.localAddress = interfaceParams["address"] ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
        config.mtu = Utils.parseInt(interfaceParams["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
        config.publicKey = peerPublicKey
        config.preSharedKey = peerParams["presharedkey"]?.nullIfBlank()

        val endpoint = peerParams["endpoint"].orEmpty()
        val endpointParts = endpoint.split(":", limit = 2)
        config.server = endpointParts.getOrNull(0)?.trim().orEmpty()
        config.serverPort = endpointParts.getOrNull(1)?.trim().orEmpty()
        config.reserved = peerParams["reserved"] ?: "0,0,0"

        config.awgJunkPacketCount = interfaceParams["jc"]?.toIntOrNull()
        config.awgJunkPacketMinSize = interfaceParams["jmin"]?.toIntOrNull()
        config.awgJunkPacketMaxSize = interfaceParams["jmax"]?.toIntOrNull()
        config.awgInitPacketJunkSize = interfaceParams["s1"]?.toIntOrNull()
        config.awgResponsePacketJunkSize = interfaceParams["s2"]?.toIntOrNull()
        config.awgInitPacketMagicHeader = interfaceParams["h1"]
        config.awgResponsePacketMagicHeader = interfaceParams["h2"]
        config.awgUnderloadPacketMagicHeader = interfaceParams["h5"] ?: interfaceParams["h4"]

        config.remarks = interfaceParams["remarks"]
            ?: peerParams["remarks"]
            ?: "amnezia-${System.currentTimeMillis()}"
        return config
    }

    /**
     * Converts a profile back to the `amnezia://` URI payload for export / QR sharing.
     *
     * Like the other format objects, the returned string does not include the
     * `amnezia://` scheme; callers prepend [AppConfig.AMNEZIA].
     */
    fun toUri(config: ProfileItem): String {
        val sb = StringBuilder()
        sb.append("[Interface]\n")
        config.secretKey?.let { sb.append("PrivateKey = ").append(it).append('\n') }
        config.localAddress?.let { sb.append("Address = ").append(it).append('\n') }
        config.awgJunkPacketCount?.let { sb.append("Jc = ").append(it).append('\n') }
        config.awgJunkPacketMinSize?.let { sb.append("Jmin = ").append(it).append('\n') }
        config.awgJunkPacketMaxSize?.let { sb.append("Jmax = ").append(it).append('\n') }
        config.awgInitPacketJunkSize?.let { sb.append("S1 = ").append(it).append('\n') }
        config.awgResponsePacketJunkSize?.let { sb.append("S2 = ").append(it).append('\n') }
        config.awgInitPacketMagicHeader?.let { sb.append("H1 = ").append(it).append('\n') }
        config.awgResponsePacketMagicHeader?.let { sb.append("H2 = ").append(it).append('\n') }
        config.awgUnderloadPacketMagicHeader?.let { sb.append("H5 = ").append(it).append('\n') }
        config.mtu?.let { sb.append("MTU = ").append(it).append('\n') }
        sb.append('\n')
        sb.append("[Peer]\n")
        config.publicKey?.let { sb.append("PublicKey = ").append(it).append('\n') }
        config.preSharedKey?.let { sb.append("PresharedKey = ").append(it).append('\n') }
        sb.append("AllowedIPs = 0.0.0.0/0, ::/0\n")
        config.server?.let { server ->
            sb.append("Endpoint = ")
                .append(Utils.getIpv6Address(server))
                .append(':')
                .append(config.serverPort)
                .append('\n')
        }

        val encoded = try {
            Base64.encodeToString(
                sb.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
            )
        } catch (e: Exception) {
            return ""
        }
        return "$encoded#${Utils.encodeURIComponent(config.remarks)}"
    }
}