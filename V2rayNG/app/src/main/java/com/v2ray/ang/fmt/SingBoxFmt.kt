package com.v2ray.ang.fmt

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.LogUtil

/**
 * Universal Import support for sing-box configuration documents.
 *
 * A sing-box config is JSON whose proxies live in the `outbounds` array and are
 * discriminated by the `type` field (e.g. `vless`, `vmess`, `trojan`). Xray configs
 * use `protocol` instead, so the two formats never collide during detection.
 *
 * Only outbound types that map onto an existing [EConfigType] are imported. Types
 * with no matching profile model (tuic, shadowtls, anytls, selector, ...) are skipped
 * rather than mis-mapped.
 */
object SingBoxFmt {

    private val SUPPORTED_OUTBOUND_TYPES = setOf(
        "vless",
        "vmess",
        "trojan",
        "shadowsocks",
        "socks",
        "http",
        "wireguard",
        "hysteria",
        "hysteria2",
    )

    /**
     * True when [text] is a sing-box JSON document that contains at least one
     * importable proxy outbound.
     */
    fun isSingBoxConfig(text: String?): Boolean {
        val root = parseRoot(text) ?: return false
        return when {
            root.isJsonObject -> outboundsHaveSupportedEntry(root.asJsonObject)
            root.isJsonArray -> root.asJsonArray.any { it.isJsonObject && isImportableOutbound(it.asJsonObject) }
            else -> false
        }
    }

    /**
     * Parses every importable proxy outbound out of a sing-box document. Returns an
     * empty list when the document is not sing-box JSON or has no importable proxies.
     */
    fun parse(text: String?): List<ProfileItem> {
        val root = parseRoot(text) ?: return emptyList()
        return when {
            root.isJsonObject -> parseOutbounds(root.asJsonObject.getAsJsonArray("outbounds"))
            root.isJsonArray -> root.asJsonArray.mapNotNull { toProfile(it) }
            else -> emptyList()
        }
    }

    /** Parses a top-level JSON array of sing-box outbounds. */
    fun parseOutboundsArray(text: String?): List<ProfileItem> {
        val root = parseRoot(text) ?: return emptyList()
        if (!root.isJsonArray) return emptyList()
        return root.asJsonArray.mapNotNull { toProfile(it) }
    }

    private fun parseRoot(text: String?): JsonElement? {
        val trimmed = text?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        if (trimmed.first() != '{' && trimmed.first() != '[') return null
        return try {
            JsonParser.parseString(trimmed)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse sing-box config JSON", e)
            null
        }
    }

    private fun outboundsHaveSupportedEntry(root: JsonObject): Boolean {
        val outbounds = root.getAsJsonArray("outbounds") ?: return false
        return outbounds.any { it.isJsonObject && isImportableOutbound(it.asJsonObject) }
    }

    private fun parseOutbounds(outbounds: JsonArray?): List<ProfileItem> {
        if (outbounds == null) return emptyList()
        return outbounds.mapNotNull { toProfile(it) }
    }

    private fun isImportableOutbound(outbound: JsonObject): Boolean {
        val type = outbound.string("type")?.lowercase() ?: return false
        return type in SUPPORTED_OUTBOUND_TYPES
    }

    private fun toProfile(element: JsonElement): ProfileItem? {
        if (!element.isJsonObject) return null
        val outbound = element.asJsonObject
        val type = outbound.string("type")?.lowercase() ?: return null
        if (type !in SUPPORTED_OUTBOUND_TYPES) return null

        val profile = when (type) {
            "vless" -> ProfileItem.create(EConfigType.VLESS).apply {
                password = outbound.string("uuid")
                method = "none"
                flow = outbound.string("flow")
            }

            "vmess" -> ProfileItem.create(EConfigType.VMESS).apply {
                password = outbound.string("uuid")
                method = outbound.string("security")?.nullIfBlank() ?: AppConfig.DEFAULT_SECURITY
            }

            "trojan" -> ProfileItem.create(EConfigType.TROJAN).apply {
                password = outbound.string("password")
            }

            "shadowsocks" -> ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
                method = outbound.string("method")
                password = outbound.string("password")
            }

            "socks" -> ProfileItem.create(EConfigType.SOCKS).apply {
                username = outbound.string("username")
                password = outbound.string("password")
            }

            "http" -> ProfileItem.create(EConfigType.HTTP).apply {
                username = outbound.string("username")
                password = outbound.string("password")
            }

            "wireguard" -> ProfileItem.create(EConfigType.WIREGUARD).apply {
                secretKey = outbound.string("private_key")
                publicKey = outbound.string("peer_public_key")
                preSharedKey = outbound.string("pre_shared_key")
                localAddress = outbound.stringArray("local_address")?.joinToString(",")
                reserved = outbound.intArray("reserved")?.joinToString(",")
                mtu = outbound.int("mtu")
            }

            "hysteria", "hysteria2" -> ProfileItem.create(EConfigType.HYSTERIA2).apply {
                password = outbound.string("password") ?: outbound.string("auth")
                security = AppConfig.TLS
                network = NetworkType.HYSTERIA.type
                obfsPassword = outbound.getAsJsonObject("obfs")?.string("password")
            }

            else -> return null
        }

        profile.server = outbound.string("server")
        profile.serverPort = outbound.int("server_port")?.toString()
        profile.remarks = outbound.string("tag")?.nullIfBlank()
            ?: buildString {
                append(type)
                profile.server?.nullIfBlank()?.let { append(" ").append(it) }
            }

        applyTls(profile, outbound.getAsJsonObject("tls"))
        applyTransport(profile, outbound.getAsJsonObject("transport"))

        if (profile.server.isNullOrBlank() || profile.serverPort.isNullOrBlank()) {
            return null
        }
        return profile
    }

    private fun applyTls(profile: ProfileItem, tls: JsonObject?) {
        if (tls == null || !tls.bool("enabled", false)) return

        val reality = tls.getAsJsonObject("reality")
        val realityEnabled = reality != null && reality.bool("enabled", false)
        profile.security = if (realityEnabled) AppConfig.REALITY else AppConfig.TLS
        profile.sni = tls.string("server_name")
        profile.insecure = tls.bool("insecure", false)
        profile.alpn = tls.stringArray("alpn")?.joinToString(",")

        tls.getAsJsonObject("utls")
            ?.takeIf { it.bool("enabled", false) }
            ?.string("fingerprint")
            ?.nullIfBlank()
            ?.let { profile.fingerPrint = it }

        if (realityEnabled) {
            reality.string("public_key")?.nullIfBlank()?.let { profile.publicKey = it }
            reality.string("short_id")?.nullIfBlank()?.let { profile.shortId = it }
        }
    }

    private fun applyTransport(profile: ProfileItem, transport: JsonObject?) {
        if (transport == null) return
        when (transport.string("type")?.lowercase()) {
            "ws" -> {
                profile.network = NetworkType.WS.type
                profile.path = transport.string("path")
                profile.host = transport.headerHost()
            }

            "http" -> {
                profile.network = NetworkType.H2.type
                profile.path = transport.string("path")
                profile.host = transport.stringArray("host")?.joinToString(",") ?: transport.headerHost()
            }

            "httpupgrade" -> {
                profile.network = NetworkType.HTTP_UPGRADE.type
                profile.path = transport.string("path")
                profile.host = transport.string("host") ?: transport.headerHost()
            }

            "grpc" -> {
                profile.network = NetworkType.GRPC.type
                profile.serviceName = transport.string("service_name")
            }
        }
    }

    private fun JsonObject.headerHost(): String? {
        val headers = getAsJsonObject("headers") ?: return null
        for ((key, value) in headers.entrySet()) {
            if (!key.equals("host", ignoreCase = true)) continue
            return when {
                value.isJsonArray -> value.asJsonArray.mapNotNull { it.stringOrNull() }.joinToString(",")
                else -> value.stringOrNull()
            }
        }
        return null
    }

    private fun JsonElement.stringOrNull(): String? =
        if (isJsonPrimitive && asJsonPrimitive.isString) asString else null

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

    private fun JsonObject.stringArray(key: String): List<String>? {
        val array = getAsJsonArray(key) ?: return null
        return array.mapNotNull { it.stringOrNull() }
    }

    private fun JsonObject.intArray(key: String): List<Int>? {
        val array = getAsJsonArray(key) ?: return null
        return array.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        }
    }
}
