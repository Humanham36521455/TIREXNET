package com.v2ray.ang.fmt

import android.util.Base64
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic

/**
 * Unit tests for PsiphonFmt.
 */
class PsiphonFmtTest {

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>

    private val sampleEntryJson = """
        {
            "ipAddress": "1.2.3.4",
            "hostname": "host.example.com",
            "webServerPort": "443",
            "tunnelPort": "1194",
            "region": "DE",
            "serverEntryTag": "mytag",
            "propagationChannelId": "pc1",
            "sponsorId": "sponsor12345"
        }
    """.trimIndent()

    private val sampleEntryJsonMinimal = """
        {
            "ipAddress": "5.6.7.8",
            "tunnelPort": "8443"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)
        mockBase64 = mockStatic(Base64::class.java)

        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as String
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val decoder = if (isUrlSafe) {
                java.util.Base64.getUrlDecoder()
            } else {
                java.util.Base64.getDecoder()
            }
            decoder.decode(input)
        }

        mockBase64.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as ByteArray
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val noPadding = (flags and Base64.NO_PADDING) != 0
            var encoder = if (isUrlSafe) {
                java.util.Base64.getUrlEncoder()
            } else {
                java.util.Base64.getEncoder()
            }
            if (noPadding) encoder = encoder.withoutPadding()
            encoder.encodeToString(input)
        }
    }

    @After
    fun tearDown() {
        mockLog.close()
        mockBase64.close()
    }

    @Test
    fun parseEntry_validServer() {
        val s = PsiphonFmt.parseEntry(sampleEntryJson)
        assertNotNull(s)
        assertEquals(EConfigType.PSIPHON, s?.configType)
        assertEquals("1.2.3.4", s?.server)
        assertEquals("1194", s?.serverPort)
        assertEquals("DE", s?.psiphonRegion)
        // remarks: region("DE") + tag("mytag") + sponsor.take(8)("sponsor1") → "DE mytag sp:sponsor1"
        assertEquals("DE mytag sp:sponsor1", s?.remarks)
        assertEquals("DE 1.2.3.4:1194", s?.description)
    }

    @Test
    fun parseEntry_minimal_noRegion_noTag() {
        val s = PsiphonFmt.parseEntry(sampleEntryJsonMinimal)
        assertNotNull(s)
        assertEquals("5.6.7.8", s?.server)
        assertEquals("8443", s?.serverPort)
        assertNull(s?.psiphonRegion)
        assertEquals("5.6.7.8:8443", s?.description)
    }

    @Test
    fun parseEntry_noServer_returnsNull() {
        val json = """{"region": "US"}"""
        assertNull(PsiphonFmt.parseEntry(json))
    }

    @Test
    fun parseEntry_invalidJson_returnsNull() {
        assertNull(PsiphonFmt.parseEntry("not json"))
    }

    @Test
    fun parseSubscription_plainJsonArray() {
        val arrayJson = "[$sampleEntryJson, $sampleEntryJsonMinimal]"
        val list = PsiphonFmt.parseSubscription(arrayJson)
        assertEquals(2, list.size)
        assertEquals("1.2.3.4", list[0].server)
        assertEquals("5.6.7.8", list[1].server)
    }

    @Test
    fun parseSubscription_base64Encoded() {
        val arrayJson = "[$sampleEntryJson]"
        val encoded = java.util.Base64.getEncoder().encodeToString(arrayJson.toByteArray(Charsets.UTF_8))
        val list = PsiphonFmt.parseSubscription(encoded)
        assertEquals(1, list.size)
        assertEquals("1.2.3.4", list[0].server)
    }

    @Test
    fun parseSubscription_blankInput_returnsEmpty() {
        assertEquals(emptyList<Any>(), PsiphonFmt.parseSubscription(""))
        assertEquals(emptyList<Any>(), PsiphonFmt.parseSubscription(null))
    }

    @Test
    fun isPsiphonServerList_valid() {
        assertTrue(PsiphonFmt.isPsiphonServerList("[$sampleEntryJson]"))
    }

    @Test
    fun isPsiphonServerList_notJson() {
        assertFalse(PsiphonFmt.isPsiphonServerList("randomtext"))
    }

    @Test
    fun isPsiphonServerList_emptyArray() {
        assertFalse(PsiphonFmt.isPsiphonServerList("[]"))
    }

    @Test
    fun toUri_roundTrip() {
        val config = ProfileItem.create(EConfigType.PSIPHON).apply {
            server = "1.2.3.4"
            serverPort = "1194"
            psiphonRegion = "DE"
            remarks = "My Psiphon"
        }

        val url = AppConfig.PSIPHON + PsiphonFmt.toUri(config)
        assertTrue(url.startsWith("psiphon://"))

        val reparsed = PsiphonFmt.parse(url)
        assertNotNull(reparsed)
        assertEquals("1.2.3.4", reparsed?.server)
        assertEquals("1194", reparsed?.serverPort)
        assertEquals("DE", reparsed?.psiphonRegion)
        assertEquals("My Psiphon", reparsed?.remarks)
    }

    @Test
    fun toUri_minimal_withoutRegion() {
        val config = ProfileItem.create(EConfigType.PSIPHON).apply {
            server = "5.6.7.8"
            serverPort = "8443"
        }
        val payload = PsiphonFmt.toUri(config)
        assertFalse(payload.contains("region"))

        val reparsed = PsiphonFmt.parse(AppConfig.PSIPHON + payload)
        assertNotNull(reparsed)
        assertEquals("5.6.7.8", reparsed?.server)
        assertEquals("8443", reparsed?.serverPort)
        assertNull(reparsed?.psiphonRegion)
    }

    @Test
    fun parse_nonPsiphonLink_returnsNull() {
        assertNull(PsiphonFmt.parse("ss://Y2hhY2hhMjAtexample"))
        assertNull(PsiphonFmt.parse(""))
        assertNull(PsiphonFmt.parse("psiphon://notbase64"))
    }

    @Test
    fun isPsiphonServerList_entriesWithoutIpOrHostname() {
        val json = """[{"region": "US", "tunnelPort": "443"}]"""
        assertFalse(PsiphonFmt.isPsiphonServerList(json))
    }
}
