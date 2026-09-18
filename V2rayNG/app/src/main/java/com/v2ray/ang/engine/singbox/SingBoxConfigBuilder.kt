package com.v2ray.ang.engine.singbox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.removeWhiteSpace

/**
 * Builds a sing-box outbound object from a normalized [ProfileItem].
 *
 * This is the reverse of [com.v2ray.ang.fmt.SingBoxFmt]: it lets a profile that was
 * imported from any source be expressed in the sing-box config schema. It performs
 * pure JSON generation and never touches storage, IPC, or runtime state.
 *
 * Returns `null` when the profile uses a protocol or transport that sing-box cannot
 * represent (for example an Xray xhttp transport), so callers can report a real
 * unsupported/unavailable reason instead of emitting a broken config.
 */
object SingBoxConfigBuilder {

    /** Result of a conversion attempt, carrying the exact reason when unsupported. */
    data class Result(val outbound: JsonObject?, val unsupportedReason: String?) {
        val isSupported: Boolean get() = outbound != null
    }

    fun convert(profile: ProfileItem): JsonObject? = build(profile).outbound

    fun build(profile: ProfileItem): Result {
        val outbound = when (profile.configType) {
            EConfigType.VMESS -> vmess(profile)
            EConfigType.VLESS -> vless(profile)
            EConfigType.TROJAN -> trojan(profile)
            EConfigType.SHADOWSOCKS -> shadowsocks(profile)
            EConfigType.SOCKS -> socks(profile)
            EConfigType.HTTP -> http(profile)
            EConfigType.WIREGUARD -> wireguard(profile)
            EConfigType.HYSTERIA2 -> hysteria2(profile)
            else -> return Result(
                null,
                "Protocol ${profile.configType.name} cannot be expressed as a sing-box outbound",
            )
        }

        outbound.addProperty("tag", profile.remarks.nullIfBlank() ?: "proxy")
        applyTls(outbound, profile)

        if (!applyTransport(outbound, profile)) {
            val network = profile.network ?: NetworkType.TCP.type
            return Result(null, "sing-box does not support the '$network' transport")
        }

        return Result(outbound, null)
    }

    private fun vless(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "vless")
        addEndpoint(this, profile)
        addProperty("uuid", profile.password.orEmpty())
        profile.flow?.nullIfBlank()?.let { addProperty("flow", it) }
    }

    private fun vmess(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "vmess")
        addEndpoint(this, profile)
        addProperty("uuid", profile.password.orEmpty())
        addProperty("security", profile.method?.nullIfBlank() ?: AppConfig.DEFAULT_SECURITY)
        addProperty("alter_id", 0)
    }

    private fun trojan(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "trojan")
        addEndpoint(this, profile)
        addProperty("password", profile.password.orEmpty())
    }

    private fun shadowsocks(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "shadowsocks")
        addEndpoint(this, profile)
        addProperty("method", profile.method.orEmpty())
        addProperty("password", profile.password.orEmpty())
    }

    private fun socks(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "socks")
        addEndpoint(this, profile)
        addProperty("version", "5")
        profile.username?.nullIfBlank()?.let { addProperty("username", it) }
        profile.password?.nullIfBlank()?.let { addProperty("password", it) }
    }

    private fun http(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "http")
        addEndpoint(this, profile)
        profile.username?.nullIfBlank()?.let { addProperty("username", it) }
        profile.password?.nullIfBlank()?.let { addProperty("password", it) }
    }

    private fun wireguard(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "wireguard")
        addEndpoint(this, profile)
        addProperty("private_key", profile.secretKey.orEmpty())
        addProperty("peer_public_key", profile.publicKey.orEmpty())
        profile.preSharedKey?.nullIfBlank()?.let { addProperty("pre_shared_key", it) }

        val localAddress = profile.localAddress
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (localAddress.isNotEmpty()) {
            add("local_address", JsonArray().apply { localAddress.forEach { add(it) } })
        }

        val reserved = profile.reserved
            ?.removeWhiteSpace()
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        if (reserved.isNotEmpty()) {
            add("reserved", JsonArray().apply { reserved.forEach { add(it) } })
        }

        profile.mtu?.let { addProperty("mtu", it) }
    }

    private fun hysteria2(profile: ProfileItem): JsonObject = JsonObject().apply {
        addProperty("type", "hysteria2")
        addEndpoint(this, profile)
        addProperty("password", profile.password.orEmpty())
        profile.obfsPassword?.nullIfBlank()?.let { obfsPassword ->
            add("obfs", JsonObject().apply {
                addProperty("type", "salamander")
                addProperty("password", obfsPassword)
            })
        }
    }

    private fun addEndpoint(outbound: JsonObject, profile: ProfileItem) {
        outbound.addProperty("server", profile.server.orEmpty())
        profile.serverPort?.toIntOrNull()?.let { outbound.addProperty("server_port", it) }
    }

    private fun applyTls(outbound: JsonObject, profile: ProfileItem) {
        val security = profile.security
        if (security != AppConfig.TLS && security != AppConfig.REALITY) return

        val tls = JsonObject().apply {
            addProperty("enabled", true)
            profile.sni?.nullIfBlank()?.let { addProperty("server_name", it) }
            if (profile.insecure == true) addProperty("insecure", true)
            profile.alpn?.nullIfBlank()?.let { alpn ->
                add("alpn", JsonArray().apply {
                    alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                })
            }
            profile.fingerPrint?.nullIfBlank()?.let { fingerprint ->
                add("utls", JsonObject().apply {
                    addProperty("enabled", true)
                    addProperty("fingerprint", fingerprint)
                })
            }
            if (security == AppConfig.REALITY) {
                add("reality", JsonObject().apply {
                    addProperty("enabled", true)
                    profile.publicKey?.nullIfBlank()?.let { addProperty("public_key", it) }
                    profile.shortId?.nullIfBlank()?.let { addProperty("short_id", it) }
                })
            }
        }
        outbound.add("tls", tls)
    }

    /**
     * Emits the sing-box `transport` object. Returns false when [ProfileItem.network]
     * names a transport that has no sing-box equivalent, so the caller can surface an
     * explicit unsupported result rather than silently dropping the transport.
     */
    private fun applyTransport(outbound: JsonObject, profile: ProfileItem): Boolean {
        val network = profile.network?.nullIfBlank() ?: NetworkType.TCP.type
        return when (network) {
            NetworkType.TCP.type ->
                // Xray's TCP "http" header obfuscation has no sing-box equivalent.
                !profile.headerType.equals(AppConfig.HEADER_TYPE_HTTP, ignoreCase = true)

            NetworkType.WS.type -> {
                outbound.add("transport", JsonObject().apply {
                    addProperty("type", "ws")
                    profile.path?.nullIfBlank()?.let { addProperty("path", it) }
                    profile.host?.nullIfBlank()?.let { host ->
                        add("headers", JsonObject().apply { addProperty("Host", host) })
                    }
                })
                true
            }

            NetworkType.HTTP_UPGRADE.type -> {
                outbound.add("transport", JsonObject().apply {
                    addProperty("type", "httpupgrade")
                    profile.path?.nullIfBlank()?.let { addProperty("path", it) }
                    profile.host?.nullIfBlank()?.let { addProperty("host", it) }
                })
                true
            }

            NetworkType.GRPC.type -> {
                outbound.add("transport", JsonObject().apply {
                    addProperty("type", "grpc")
                    profile.serviceName?.nullIfBlank()?.let { addProperty("service_name", it) }
                })
                true
            }

            NetworkType.H2.type, NetworkType.HTTP.type -> {
                outbound.add("transport", JsonObject().apply {
                    addProperty("type", "http")
                    profile.host?.nullIfBlank()?.let { host ->
                        add("host", JsonArray().apply {
                            host.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                        })
                    }
                    profile.path?.nullIfBlank()?.let { addProperty("path", it) }
                })
                true
            }

            else -> false
        }
    }
}
