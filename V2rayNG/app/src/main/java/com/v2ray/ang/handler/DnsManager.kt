package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS Changer configuration model and persistence.
 *
 * A DNS profile holds up to two IPv4 and two IPv6 resolver addresses. The active
 * profile's servers are stored separately so the VpnService can apply them to the
 * tun interface without parsing the profile list.
 */
data class DnsProfile(
    val id: String,
    val name: String,
    val ipV4Primary: String = "",
    val ipV4Secondary: String = "",
    val ipV6Primary: String = "",
    val ipV6Secondary: String = "",
) {
    val servers: List<String>
        get() = listOf(ipV4Primary, ipV4Secondary, ipV6Primary, ipV6Secondary)
            .filter { it.isNotBlank() }

    val isValid: Boolean
        get() = servers.isNotEmpty() && servers.all(Utils::isPureIpAddress)

    fun copyWithId(newId: String): DnsProfile = copy(id = newId)
}

object DnsManager {

    private fun profilesJson(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_CHANGER_CONFIGS)

    private fun store(json: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_DNS_CHANGER_CONFIGS, json)
    }

    /** Returns all stored DNS profiles. */
    fun getProfiles(): List<DnsProfile> {
        val json = profilesJson() ?: return emptyList()
        return try {
            JsonUtil.fromJson(json, Array<DnsProfile>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decode DNS profiles", e)
            emptyList()
        }
    }

    fun getProfile(id: String): DnsProfile? = getProfiles().firstOrNull { it.id == id }

    /** Adds or updates a DNS profile. Returns the stored id. */
    fun upsert(profile: DnsProfile): String {
        val profiles = getProfiles().toMutableList()
        val existing = profile.id.takeIf { id -> profiles.any { it.id == id } }
        val normalized = if (existing == null) profile.copyWithId(Utils.getUuid()) else profile

        val index = profiles.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            profiles[index] = normalized
        } else {
            profiles.add(normalized)
        }
        store(JsonUtil.toJson(profiles))
        return normalized.id
    }

    fun delete(id: String) {
        val profiles = getProfiles().filterNot { it.id == id }
        store(JsonUtil.toJson(profiles))
        if (getActiveId() == id) {
            setActive(null)
        }
    }

    fun setActive(id: String?) {
        MmkvManager.encodeSettings(AppConfig.PREF_DNS_CHANGER_ACTIVE_ID, id ?: "")
        val json = if (id == null) {
            ""
        } else {
            val servers = getProfile(id)?.servers.orEmpty().toTypedArray()
            JsonUtil.toJson(servers)
        }
        MmkvManager.encodeSettings(AppConfig.PREF_DNS_CHANGER_ACTIVE_SERVERS, json)
    }

    fun getActiveId(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_CHANGER_ACTIVE_ID).orEmpty()
            .takeIf { it.isNotBlank() }

    /** Returns the currently active DNS servers, or an empty list when none are set. */
    fun getActiveServers(): List<String> {
        val json = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_CHANGER_ACTIVE_SERVERS)
            ?: return emptyList()
        return try {
            JsonUtil.fromJson(json, Array<String>::class.java)?.toList().orEmpty()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decode active DNS servers", e)
            emptyList()
        }
    }

    /** True when a DNS profile is active with valid server addresses. */
    fun isActive(): Boolean {
        val id = getActiveId() ?: return false
        return getProfile(id)?.isValid == true
    }
}