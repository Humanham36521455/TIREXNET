package com.v2ray.ang.ui.dns

import com.v2ray.ang.R
import com.v2ray.ang.handler.DnsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the DNS profile validation decision used by
 * [DnsChangerViewModel.saveEditingProfile].
 *
 * A profile is persistable only when it carries at least one pure IP resolver;
 * otherwise the edit dialog stays open with a localized error message.
 */
class DnsChangerViewModelTest {

    @Test
    fun validation_nullEditingProfile_hasNoError() {
        assertNull(dnsProfileValidationError(null))
    }

    @Test
    fun validation_validIpv4Profile_hasNoError() {
        val profile = DnsProfile("", "Home", ipV4Primary = "1.1.1.1")
        assertNull(dnsProfileValidationError(profile))
    }

    @Test
    fun validation_validIpv6OnlyProfile_hasNoError() {
        val profile = DnsProfile("", "V6", ipV6Primary = "2606:4700:4700::1111")
        assertNull(dnsProfileValidationError(profile))
    }

    @Test
    fun validation_multiServerProfile_hasNoError() {
        val profile = DnsProfile(
            "",
            "Both",
            ipV4Primary = "1.1.1.1",
            ipV4Secondary = "8.8.8.8",
            ipV6Primary = "2606:4700:4700::1111"
        )
        assertNull(dnsProfileValidationError(profile))
    }

    @Test
    fun validation_profileWithoutServers_reportsError() {
        assertEquals(
            R.string.dns_title_no_valid_servers,
            dnsProfileValidationError(DnsProfile("", "Empty"))
        )
    }

    @Test
    fun validation_profileWithInvalidAddress_reportsError() {
        assertEquals(
            R.string.dns_title_no_valid_servers,
            dnsProfileValidationError(DnsProfile("", "Bad", ipV4Primary = "not-an-ip"))
        )
    }

    @Test
    fun validation_partiallyInvalidProfile_reportsError() {
        assertEquals(
            R.string.dns_title_no_valid_servers,
            dnsProfileValidationError(DnsProfile("", "Mixed", ipV4Primary = "1.1.1.1", ipV6Primary = "bad"))
        )
    }

    @Test
    fun validation_hostnameResolver_reportsError() {
        // DNS Changer stores pure IP resolvers only; a hostname is rejected.
        assertEquals(
            R.string.dns_title_no_valid_servers,
            dnsProfileValidationError(DnsProfile("", "Host", ipV4Primary = "dns.google"))
        )
    }
}