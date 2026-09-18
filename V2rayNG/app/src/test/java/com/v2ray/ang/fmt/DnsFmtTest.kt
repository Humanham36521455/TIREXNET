package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for first-class DNS profile parsing and serialization.
 *
 * A DNS profile (`dns://resolver1,resolver2#name`) has no tunnel endpoint; it
 * carries the resolver addresses handed to the platform through the VPN
 * interface. The parser must reject payloads without a usable address and round
 * trip through [DnsFmt.toUri] losslessly.
 */
class DnsFmtTest {

    @Test
    fun parse_validProfile() {
        val config = DnsFmt.parse("dns://1.1.1.1,8.8.8.8#Cloudflare")
        assertNotNull(config)
        assertEquals(EConfigType.DNS, config?.configType)
        assertEquals("1.1.1.1,8.8.8.8", config?.dnsServers)
        assertEquals("Cloudflare", config?.remarks)
        assertNull(config?.server)
        assertNull(config?.serverPort)
    }

    @Test
    fun parse_ipv6Resolvers_supported() {
        val config = DnsFmt.parse("dns://2606:4700:4700::1111,2001:4860:4860::8888#Google")
        assertNotNull(config)
        assertEquals("2606:4700:4700::1111,2001:4860:4860::8888", config?.dnsServers)
        assertEquals("Google", config?.remarks)
    }

    @Test
    fun parse_mixedIpv4AndIpv6Resolvers() {
        val config = DnsFmt.parse("dns://1.1.1.1,2606:4700:4700::1111")
        assertNotNull(config)
        assertEquals("1.1.1.1,2606:4700:4700::1111", config?.dnsServers)
    }

    @Test
    fun parse_resolverWithPort_supported() {
        val config = DnsFmt.parse("dns://1.1.1.1:53,9.9.9.9:5353")
        assertNotNull(config)
        assertEquals("1.1.1.1:53,9.9.9.9:5353", config?.dnsServers)
    }

    @Test
    fun parse_defaultRemarks_whenNoFragment() {
        val config = DnsFmt.parse("dns://1.1.1.1")
        assertNotNull(config)
        assertEquals("dns-1.1.1.1", config?.remarks)
    }

    @Test
    fun parse_filtersInvalidEntries() {
        val config = DnsFmt.parse("dns://not-a-host,1.1.1.1,999.1.2.3")
        assertNotNull(config)
        assertEquals("1.1.1.1", config?.dnsServers)
    }

    @Test
    fun parse_noUsableAddress_returnsNull() {
        assertNull(DnsFmt.parse("dns://not-a-host"))
        assertNull(DnsFmt.parse("dns://"))
        assertNull(DnsFmt.parse("dns://999.1.2.3"))
        assertNull(DnsFmt.parse(""))
        assertNull(DnsFmt.parse("vmess://x"))
    }

    @Test
    fun toUri_roundTrip_preservesServersAndRemarks() {
        val parsed = DnsFmt.parse("dns://1.1.1.1,8.8.8.8#Cloudflare")!!
        val reencoded = AppConfig.DNS + DnsFmt.toUri(parsed)
        assertEquals("dns://1.1.1.1,8.8.8.8#Cloudflare", reencoded)
        assertEquals(parsed, DnsFmt.parse(reencoded))
    }

    @Test
    fun toUri_rebuildsFromStoredProfile() {
        val config = ProfileItem.create(EConfigType.DNS).apply {
            remarks = "Manual DNS"
            dnsServers = "1.1.1.1, 8.8.8.8"
        }
        assertEquals("1.1.1.1,8.8.8.8#Manual DNS", DnsFmt.toUri(config))
    }

    @Test
    fun toUri_ignoresInvalidStoredAddresses() {
        val config = ProfileItem.create(EConfigType.DNS).apply {
            remarks = "Broken"
            dnsServers = "not-an-ip,1.1.1.1"
        }
        assertEquals("1.1.1.1#Broken", DnsFmt.toUri(config))
    }

    @Test
    fun parseAddresses_acceptsIpv4Ipv6AndPort() {
        assertTrue(DnsFmt.isAddressList("1.1.1.1,8.8.8.8"))
        assertTrue(DnsFmt.isAddressList("2606:4700:4700::1111"))
        assertTrue(DnsFmt.isAddressList("1.1.1.1:53"))
        assertFalse(DnsFmt.isAddressList(""))
        assertFalse(DnsFmt.isAddressList("host.example"))
        assertFalse(DnsFmt.isAddressList("999.1.2.3"))
    }

    @Test
    fun parse_rejectsMalformedIpv6() {
        assertNull(DnsFmt.parse("dns://::::"))
        assertNull(DnsFmt.parse("dns://12345::6789"))
        assertNull(DnsFmt.parse("dns://abcd::efgh"))
        assertNull(DnsFmt.parse("dns://1:2:3:4:5:6:7:8:9"))
        assertNotNull(DnsFmt.parse("dns://2001:db8::1"))
    }
}