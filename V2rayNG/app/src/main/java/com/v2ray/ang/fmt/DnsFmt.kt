package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * Parses DNS profile links for first-class DNS entries.
 *
 * ```
 * dns://1.1.1.1,8.8.8.8#Cloudflare
 * ```
 *
 * The payload is a comma-separated list of IPv4/IPv6 resolver addresses (primary,
 * secondary). The optional `#fragment` becomes the profile remarks. A DNS profile
 * does not own a tunnel server; it is a system DNS override run through the VPN
 * interface.
 */
object DnsFmt {

    /** Extracts the usable resolver addresses from a comma-separated raw input. */
    fun parseAddresses(raw: String?): List<String> =
        (raw ?: "").split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() && isUsableAddress(it) }

    /** True when [raw] contains at least one usable resolver address. */
    fun isAddressList(raw: String?): Boolean = parseAddresses(raw).isNotEmpty()

    /**
     * Parses a `dns://` URI into a profile, or null when the payload does not
     * contain at least one usable resolver address.
     */
    fun parse(str: String): ProfileItem? {
        if (str.isBlank()) return null
        val trimmed = str.trim()
        if (!trimmed.startsWith(AppConfig.DNS, ignoreCase = true)) return null

        val body = trimmed.removePrefix(AppConfig.DNS).trim()
        val host = body.takeBefore('#').trim()
        val frag = body.substringAfter('#', "").trim()

        val servers = parseAddresses(host)
        if (servers.isEmpty()) return null

        val config = ProfileItem.create(EConfigType.DNS)
        config.dnsServers = servers.joinToString(",")
        config.remarks = frag.takeUnless { it.isBlank() }
            ?: "dns-${servers.first()}"
        return config
    }

    /**
     * Serializes a DNS profile back into the `dns://` payload (without the scheme).
     * [AngConfigManager.shareConfig] prepends `protocolScheme`.
     */
    fun toUri(config: ProfileItem): String {
        val servers = parseAddresses(config.dnsServers)
        val host = servers.joinToString(",")
        val remarks = config.remarks.orEmpty().trim().takeUnless { it.isBlank() }
        return if (remarks != null) "$host#$remarks" else host
    }

    /** Accepts IPv4 addresses, bare IPv6 addresses, and resolvers with a DNS port. */
    private fun isUsableAddress(value: String): Boolean {
        var address = value.trim()
        // Optional [v6]:port style not used for DNS; strip a bracketed form if present.
        if (address.startsWith("[")) address = address.substring(1)
        val core = address.removeSuffix("]").trim()
        return isIpV4(core) || isIpV4WithPort(core) || isIpV6(core)
    }

    private fun isIpV4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.length <= 3 &&
                part.all { it.isDigit() } &&
                part.toIntOrNull() in 0..255
        }
    }

    private fun isIpV4WithPort(value: String): Boolean {
        val index = value.lastIndexOf(':')
        if (index <= 0) return false
        val port = value.substring(index + 1)
        return isIpV4(value.substring(0, index)) &&
            port.isNotEmpty() && port.all { it.isDigit() } && port.toIntOrNull() in 0..65535
    }

    /** Validates IPv6 without a zone id; compression (`::`) is allowed exactly once. */
    private fun isIpV6(value: String): Boolean {
        if (!value.contains(':')) return false
        if (value.any { !it.isDigit() && it !in 'A'..'F' && it !in 'a'..'f' && it != ':' }) return false

        val doubleColon = value.indexOf("::")
        if (doubleColon >= 0 && value.indexOf("::", doubleColon + 1) >= 0) return false

        val groups = value.split(':').filter { it.isNotEmpty() }
        if (groups.isEmpty()) return false
        if (groups.any { it.length > 4 }) return false
        if (doubleColon >= 0) {
            // With "::", any number of implicit zero groups may be omitted.
            return groups.size <= 7
        }
        return groups.size == 8
    }

    private fun String.takeBefore(ch: Char): String {
        val index = indexOf(ch)
        return if (index >= 0) substring(0, index) else this
    }
}