package com.v2ray.ang.fmt

import android.util.Base64
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic

/**
 * Unit tests for SlipNetFmt.
 *
 * SlipNet shares its config as a pipe-delimited base64 payload behind a
 * `slipnet://` scheme (see slipgate internal/clientcfg/fields.go). The codec
 * under test reuses the Android `Base64` helper exactly like the other *Fmt
 * parsers do, so the codec is mocked with the JDK implementation here.
 */
class SlipNetFmtTest {

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>

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

    private fun slipnetUri(fields: Array<String?>): String {
        val joined = fields.joinToString("|", transform = { it.orEmpty() })
        val payload = java.util.Base64.getEncoder()
            .encodeToString(joined.toByteArray(Charsets.UTF_8))
        return "slipnet://$payload#Tunnel"
    }

    private fun slipnetUri(rawFields: List<String>): String =
        slipnetUri(rawFields.toTypedArray())

    @Test
    fun parse_validDnsttProfile() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "17"
        fields[1] = "dnstt"
        fields[2] = "My Tunnel"
        fields[3] = "t.example.com"
        fields[4] = "8.8.8.8:53:0,1.1.1.1:53:0"
        fields[11] = "aabbccddeeff0011"

        val uri = slipnetUri(fields)
        val config = SlipNetFmt.parse(uri)
        assertNotNull(config)
        assertEquals(EConfigType.SLIPNET, config?.configType)
        assertEquals("17", config?.slipNetVersion)
        assertEquals("dnstt", config?.slipNetTunnelType)
        assertEquals("My Tunnel", config?.remarks)
        assertEquals("t.example.com", config?.server)
        assertEquals("53", config?.serverPort)
        assertEquals("8.8.8.8:53:0,1.1.1.1:53:0", config?.slipNetResolvers)
        assertEquals("aabbccddeeff0011", config?.slipNetPublicKey)
        assertEquals(uri, config?.slipNetConfig)
        assertEquals("DNSTT t.example.com:53", config?.description)
    }

    @Test
    fun parse_naiveProfile_usesNaivePort() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "naive"
        fields[2] = "Naive"
        fields[3] = "n.example.com"
        fields[28] = "8080"

        val config = SlipNetFmt.parse(slipnetUri(fields))
        assertNotNull(config)
        assertEquals("naive", config?.slipNetTunnelType)
        assertEquals("n.example.com", config?.server)
        assertEquals("8080", config?.serverPort)
    }

    @Test
    fun parse_naiveProfile_defaultPortWhenMissing() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "naive_ssh"
        fields[2] = "Naive SSH"
        fields[3] = "n.example.com"
        fields[28] = "0"

        val config = SlipNetFmt.parse(slipnetUri(fields))
        assertNotNull(config)
        assertEquals("443", config?.serverPort)
    }

    @Test
    fun parse_ssProfile_defaultPort53() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "ss"
        fields[2] = "SS"
        fields[3] = "s.example.com"

        val config = SlipNetFmt.parse(slipnetUri(fields))
        assertNotNull(config)
        assertEquals("s.example.com", config?.server)
        assertEquals("53", config?.serverPort)
    }

    @Test
    fun parse_urlSafeBase64_roundTrips() {
        val joined = listOf("17", "sayedns", "Say", "y.example.com")
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(joined.joinToString("|").toByteArray(Charsets.UTF_8))
        val config = SlipNetFmt.parse("slipnet://$payload")
        assertNotNull(config)
        assertEquals("sayedns", config?.slipNetTunnelType)
        assertEquals("y.example.com", config?.server)
    }

    @Test
    fun parse_invalidPayload_returnsNull() {
        assertNull(SlipNetFmt.parse("slipnet://!!!!not-base64!!!!"))
        assertNull(SlipNetFmt.parse("slipnet://"))
    }

    @Test
    fun parse_nonSlipnetLink_returnsNull() {
        assertNull(SlipNetFmt.parse(""))
        assertNull(SlipNetFmt.parse("  "))
        assertNull(SlipNetFmt.parse("vmess://eyJhIjoiYiJ9"))
        assertNull(SlipNetFmt.parse("ssh://whatever"))
    }

    @Test
    fun parse_garbageButDecodable_returnsNullWhenNoRecognizedFields() {
        val payload = java.util.Base64.getEncoder()
            .encodeToString("hello world".toByteArray(Charsets.UTF_8))
        assertNull(SlipNetFmt.parse("slipnet://$payload"))
    }

    @Test
    fun toUri_roundTrip_preservesOriginalLink() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "dnstt_ssh"
        fields[2] = "Round Trip"
        fields[3] = "r.example.com"
        fields[4] = "9.9.9.9:53:0"
        fields[11] = "f00d"
        val uri = slipnetUri(fields)

        val parsed = SlipNetFmt.parse(uri)
        assertNotNull(parsed)

        val reencoded = AppConfig.SLIPNET + SlipNetFmt.toUri(parsed!!)
        assertEquals(uri, reencoded)

        val reparsed = SlipNetFmt.parse(reencoded)
        assertNotNull(reparsed)
        assertEquals("22", reparsed?.slipNetVersion)
        assertEquals("dnstt_ssh", reparsed?.slipNetTunnelType)
        assertEquals("Round Trip", reparsed?.remarks)
        assertEquals("r.example.com", reparsed?.server)
        assertEquals("9.9.9.9:53:0", reparsed?.slipNetResolvers)
        assertEquals("f00d", reparsed?.slipNetPublicKey)
    }

    @Test
    fun toUri_rebuildsFromStoredFieldsWithoutOriginalLink() {
        val config = ProfileItem.create(EConfigType.SLIPNET).apply {
            remarks = "Manual"
            server = "m.example.com"
            slipNetVersion = "17"
            slipNetTunnelType = "dnstt"
            slipNetResolvers = "8.8.8.8:53:0"
            slipNetPublicKey = "beef"
        }
        val payload = SlipNetFmt.toUri(config)
        assertTrue(payload.isNotBlank())

        val reparsed = SlipNetFmt.parse(AppConfig.SLIPNET + payload)
        assertNotNull(reparsed)
        assertEquals("Manual", reparsed?.remarks)
        assertEquals("m.example.com", reparsed?.server)
        assertEquals("dnstt", reparsed?.slipNetTunnelType)
    }

    @Test
    fun isSlipNetServerList_multiLineLinks() {
        val fields1 = arrayOfNulls<String>(60)
        fields1[0] = "17"
        fields1[1] = "dnstt"
        fields1[3] = "a.example.com"
        val uri1 = slipnetUri(fields1)

        val uri2 = slipnetUri(listOf("22", "naive", "B", "b.example.com"))

        assertTrue(SlipNetFmt.isSlipNetServerList("$uri1\n$uri2"))
    }

    @Test
    fun isSlipNetServerList_notASlipNetList() {
        assertFalse(SlipNetFmt.isSlipNetServerList(""))
        assertFalse(SlipNetFmt.isSlipNetServerList("some plain text"))
        assertFalse(SlipNetFmt.isSlipNetServerList("vmess://eyJhIjoiYiJ9"))
    }

    @Test
    fun parse_snowflake_torFamily_legitimateEmptyDomain() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "snowflake"
        fields[2] = "Snowflake"
        fields[4] = "8.8.8.8:53:0"
        fields[26] = "snowflake.example.net:443 fingerprint"
        // SlipNet's Tor-family tunnels (tor/snowflake/obfs4/meek) legitimately
        // carry no slipgate server/domain; an empty domain must stay empty and the
        // profile must remain structurally valid for its family.
        val uri = slipnetUri(fields)

        val config = SlipNetFmt.parse(uri)
        assertNotNull(config)
        assertTrue(SlipNetFmt.isTorFamily(config?.slipNetTunnelType))
        assertNull(config?.server)
        assertNull(config?.serverPort)
        assertEquals("snowflake.example.net:443 fingerprint", config?.slipNetTorBridgeLines)
        assertEquals("SNOWFLAKE tor-network", config?.description)
        assertNull(SlipNetFmt.validate(config!!))
    }

    @Test
    fun parse_torFamily_routesThroughTorNetwork() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "obfs4"
        fields[2] = "Obfs4"
        fields[4] = "0"
        fields[26] = "obfs4 bridge:443 cert"
        val config = SlipNetFmt.parse(slipnetUri(fields))
        assertNotNull(config)
        assertEquals("obfs4", config?.slipNetTunnelType)
        assertNull(config?.server)
        assertEquals("obfs4 bridge:443 cert", config?.slipNetTorBridgeLines)
    }

    @Test
    fun validate_torFamily_requiresBridgeLines() {
        val config = ProfileItem.create(EConfigType.SLIPNET).apply {
            slipNetTunnelType = "tor"
            slipNetResolvers = "8.8.8.8:53:0"
        }
        val reason = SlipNetFmt.validate(config)
        assertTrue(reason?.contains("bridge", ignoreCase = true) == true)
    }

    @Test
    fun validate_slipgateFamily_requiresResolversAndEndpoint() {
        val noResolvers = ProfileItem.create(EConfigType.SLIPNET).apply {
            slipNetTunnelType = "dnstt"
            server = "t.example.com"
        }
        assertEquals("slipNetResolvers is required", SlipNetFmt.validate(noResolvers))

        val noEndpoint = ProfileItem.create(EConfigType.SLIPNET).apply {
            slipNetTunnelType = "dnstt"
            slipNetResolvers = "8.8.8.8:53:0"
        }
        val reason = SlipNetFmt.validate(noEndpoint)
        assertTrue(reason?.contains("domain", ignoreCase = true) == true)
    }

    @Test
    fun validate_missingTunnelType_rejected() {
        val config = ProfileItem.create(EConfigType.SLIPNET).apply {
            slipNetTunnelType = ""
        }
        val reason = SlipNetFmt.validate(config)
        assertTrue(reason?.contains("tunnelType", ignoreCase = true) == true)
    }

    @Test
    fun toUri_roundTrip_torFamily_preservesBridgeLines() {
        val fields = arrayOfNulls<String>(60)
        fields[0] = "22"
        fields[1] = "meek"
        fields[2] = "Meek"
        fields[4] = "0"
        fields[26] = "meek bridge:80"
        val uri = slipnetUri(fields)

        val parsed = SlipNetFmt.parse(uri)
        assertNotNull(parsed)

        val reencoded = AppConfig.SLIPNET + SlipNetFmt.toUri(parsed!!)
        assertEquals(uri, reencoded)

        val reparsed = SlipNetFmt.parse(reencoded)
        assertNotNull(reparsed)
        assertEquals("meek", reparsed?.slipNetTunnelType)
        assertNull(reparsed?.server)
        assertEquals("meek bridge:80", reparsed?.slipNetTorBridgeLines)
    }
}