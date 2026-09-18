package com.v2ray.ang.fmt

import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import java.net.URI
import java.util.Locale

/**
 * Parses Psiphon subscription server entries.
 *
 * Psiphon subscriptions and exported links commonly carry a Base64-encoded JSON
 * array of server entries, or a plain JSON array with the following shape:
 *
 * ```
 * [{
 *   "ipAddress": "1.2.3.4",
 *   "hostname": "host.example",
 *   "webServerPort": "443",
 *   "tunnelPort": "443",
 *   "region": "DE",
 *   "propagationChannelId": "...",
 *   "sponsorId": "...",
 *   ...
 * }]
 * ```
 *
 * Only parsing and country/metadata extraction are performed here. The bundled
 * Xray core cannot run the Psiphon protocol, so establishing a tunnel from these
 * entries is not technically possible in this build; connecting reports a clear
 * error instead of faking a connection.
 */
object PsiphonFmt {

    /**
     * Serializes a profile into a scheme-less `psiphon://` link payload.
     * Callers prepend [AppConfig.PSIPHON]; `shareConfig` relies on this shape.
     */
    fun toUri(config: ProfileItem): String {
        val envelope = linkedMapOf<String, Any>(
            PSIPHON_IP to (config.server.orEmpty()),
            PSIPHON_TUNNEL_PORT to (config.serverPort.orEmpty()),
        )
        config.psiphonRegion?.let { envelope[PSIPHON_REGION] = it }

        val json = JsonUtil.toJson(envelope) ?: return ""
        val encoded = try {
            Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
            )
        } catch (e: Exception) {
            return ""
        }
        return "$encoded#${Utils.encodeURIComponent(config.remarks.orEmpty())}"
    }

    /**
     * Parses a `psiphon://` link into a profile, or null when the payload is not
     * a recognized Psiphon server entry.
     */
    fun parse(str: String): ProfileItem? {
        if (str.isBlank()) return null
        val trimmed = str.trim()
        if (!trimmed.startsWith(AppConfig.PSIPHON, ignoreCase = true)) return null
        return parseUri(trimmed)
    }

    private fun parseUri(str: String): ProfileItem? {
        return try {
            val uri = URI(Utils.fixIllegalUrl(str))
            val payload = uri.rawSchemeSpecificPart.orEmpty()
                .removePrefix("//")
                .takeBefore('#')
            val decoded = Utils.safeDecodeBase64(payload)?.takeIf { it.isNotBlank() } ?: return null
            if (!decoded.trimStart().startsWith("{")) return null

            val config = parseEntry(decoded) ?: return null
            val remark = Utils.decodeURIComponent(uri.fragment.orEmpty())
            if (remark.isNotBlank()) config.remarks = remark
            config
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns a list of parsed profiles. A null/empty result means the input is
     * not a recognized Psiphon server list.
     */
    fun parseSubscription(str: String?): List<ProfileItem> {
        if (str.isNullOrBlank()) return emptyList()

        val json = decodePossiblyBase64(str)
        val entries = try {
            JsonUtil.fromJson(json, Array<Any>::class.java)
        } catch (e: Exception) {
            return emptyList()
        } ?: return emptyList()

        return entries.mapNotNull { entry ->
            val json = JsonUtil.toJson(entry) ?: return@mapNotNull null
            parseEntry(json)
        }
    }

    /** Builds a single profile from a raw JSON object string. */
    fun parseEntry(json: String): ProfileItem? {
        return try {
            val obj = JsonUtil.fromJson(json, Map::class.java) ?: return null
            val config = ProfileItem.create(EConfigType.PSIPHON)

            val region = obj[PSIPHON_REGION] as? String
            config.psiphonRegion = region
            config.server = (obj[PSIPHON_IP] as? String)
                ?: (obj[PSIPHON_HOSTNAME] as? String)
            config.serverPort = (obj[PSIPHON_TUNNEL_PORT] as? String)
                ?: (obj[PSIPHON_WEB_PORT] as? String)

            val tag = obj[PSIPHON_TAG] as? String
            val sponsor = obj[PSIPHON_SPONSOR_ID] as? String
            val remarkParts = buildList {
                region?.let { add(it.uppercase(Locale.ROOT)) }
                tag?.let { add(it) }
                sponsor?.take(8)?.let { add("sp:$it") }
            }
            config.remarks = if (remarkParts.isEmpty()) "psiphon" else remarkParts.joinToString(" ")

            config.description = buildString {
                region?.let { append(it.uppercase(Locale.ROOT)).append(' ') }
                append(config.server.orEmpty()).append(':').append(config.serverPort.orEmpty())
            }

            if (config.server.isNullOrBlank() || config.serverPort.isNullOrBlank()) {
                return null
            }
            config
        } catch (e: Exception) {
            null
        }
    }

    /** Detects whether a string is a Psiphon server list (JSON entries). */
    fun isPsiphonServerList(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        val json = decodePossiblyBase64(str)
        return try {
            val list = JsonUtil.fromJson(json, Array<Any>::class.java) ?: return false
            list.isNotEmpty() && list.any { entry ->
                val map = try {
                    val entryJson = JsonUtil.toJson(entry) ?: return@any false
                    JsonUtil.fromJson(entryJson, Map::class.java)
                } catch (e: Exception) {
                    null
                }
                map != null && (map.containsKey(PSIPHON_IP) || map.containsKey(PSIPHON_HOSTNAME))
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun String.takeBefore(ch: Char): String {
        val index = indexOf(ch)
        return if (index >= 0) substring(0, index) else this
    }

    private fun decodePossiblyBase64(str: String): String {
        if (str.trim().startsWith("[")) return str
        val decoded = Utils.safeDecodeBase64(str.trim())
        return decoded?.takeUnless { it.isBlank() } ?: str
    }

    private const val PSIPHON_IP = "ipAddress"
    private const val PSIPHON_HOSTNAME = "hostname"
    private const val PSIPHON_WEB_PORT = "webServerPort"
    private const val PSIPHON_TUNNEL_PORT = "tunnelPort"
    private const val PSIPHON_REGION = "region"
    private const val PSIPHON_TAG = "serverEntryTag"
    private const val PSIPHON_SPONSOR_ID = "sponsorId"
}