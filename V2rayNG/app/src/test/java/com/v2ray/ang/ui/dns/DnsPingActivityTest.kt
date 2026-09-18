package com.v2ray.ang.ui.dns

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsPingActivityTest {

    @Test
    fun parseDnsServerList_commaSeparated() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            parseDnsServerList("1.1.1.1, 8.8.8.8 , 9.9.9.9")
        )
    }

    @Test
    fun parseDnsServerList_mixedSeparators() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            parseDnsServerList("1.1.1.1;8.8.8.8\n9.9.9.9")
        )
    }

    @Test
    fun parseDnsServerList_deduplicatesAndTrims() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            parseDnsServerList(" 1.1.1.1 ,8.8.8.8,1.1.1.1,")
        )
    }

    @Test
    fun parseDnsServerList_blankInput() {
        assertEquals(emptyList<String>(), parseDnsServerList(""))
        assertEquals(emptyList<String>(), parseDnsServerList(",,  ,"))
    }

    @Test
    fun parseDnsServerList_acceptedHostnameAndPortStyle() {
        assertEquals(
            listOf("dns.google", "1.0.0.1"),
            parseDnsServerList("dns.google, 1.0.0.1")
        )
    }

    @Test
    fun parseDnsServerList_ipv6LiteralAndPortStyle() {
        // IPv6 literals contain colons; the splitter must not treat them as delimiters
        // and an IPv6-with-port resolver must be kept intact rather than being split.
        assertEquals(
            listOf("2001:4860:4860::8888"),
            parseDnsServerList("2001:4860:4860::8888")
        )
        assertEquals(
            listOf("[2001:4860:4860::8888]:53"),
            parseDnsServerList("[2001:4860:4860::8888]:53")
        )
    }

    @Test
    fun parseDnsServerList_invalidEntriesKeepButBlankRemoved() {
        // The checker probes whatever is listed; blank segments are the only input that
        // is dropped here. An invalid address fails at probe time, not at parse time.
        assertEquals(
            listOf("1.1.1.1", "bogus-host"),
            parseDnsServerList("1.1.1.1,  , bogus-host")
        )
    }
}