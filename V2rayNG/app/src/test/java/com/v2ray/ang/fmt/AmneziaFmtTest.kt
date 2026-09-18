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
 * Unit tests for AmneziaFmt.
 */
class AmneziaFmtTest {

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>

    private val sampleConfig = """
        [Interface]
        PrivateKey = abc123
        Address = 10.0.0.2/32
        Jc = 4
        Jmin = 100
        Jmax = 500
        S1 = 50
        S2 = 60
        H1 = 12345
        H2 = 23456
        H5 = 34567
        MTU = 1420

        [Peer]
        PublicKey = pub789
        Endpoint = server.example.com:443
        AllowedIPs = 0.0.0.0/0
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

    private fun configToAmneziaUri(configText: String, remark: String): String {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(configText.toByteArray(Charsets.UTF_8))
        val encodedRemark = java.net.URLEncoder.encode(remark, "UTF-8")
            .replace("+", "%20")
        return "amnezia://$encoded#$encodedRemark"
    }

    @Test
    fun parseConfigText_parsesAllFields() {
        val s = AmneziaFmt.parseConfigText(sampleConfig)
        assertNotNull(s)
        assertEquals("server.example.com", s?.server)
        assertEquals("443", s?.serverPort)
        assertEquals("abc123", s?.secretKey)
        assertEquals("pub789", s?.publicKey)
        assertEquals("10.0.0.2/32", s?.localAddress)
        assertEquals(4, s?.awgJunkPacketCount)
        assertEquals(100, s?.awgJunkPacketMinSize)
        assertEquals(500, s?.awgJunkPacketMaxSize)
        assertEquals(50, s?.awgInitPacketJunkSize)
        assertEquals(60, s?.awgResponsePacketJunkSize)
        assertEquals("12345", s?.awgInitPacketMagicHeader)
        assertEquals("23456", s?.awgResponsePacketMagicHeader)
        assertEquals("34567", s?.awgUnderloadPacketMagicHeader)
        assertEquals(1420, s?.mtu)
    }

    @Test
    fun parseConfigText_noJunkParams_defaultsNull() {
        val minimal = """
            [Interface]
            PrivateKey = xyz
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = pk123
            Endpoint = host:1194
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val s = AmneziaFmt.parseConfigText(minimal)
        assertNotNull(s)
        assertEquals("host", s?.server)
        assertEquals("1194", s?.serverPort)
        assertNull(s?.awgJunkPacketCount)
        assertNull(s?.awgInitPacketMagicHeader)
    }

    @Test
    fun parse_amneziaUri() {
        val uri = configToAmneziaUri(sampleConfig, "My Server")
        val s = AmneziaFmt.parse(uri)
        assertNotNull(s)
        assertEquals("My Server", s?.remarks)
        assertEquals("server.example.com", s?.server)
        assertEquals(4, s?.awgJunkPacketCount)
    }

    @Test
    fun parse_vpnScheme() {
        val uri = "vpn://" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(sampleConfig.toByteArray(Charsets.UTF_8)) + "#VpnServer"
        val s = AmneziaFmt.parse(uri)
        assertNotNull(s)
        assertEquals("VpnServer", s?.remarks)
    }

    @Test
    fun parse_invalidScheme_returnsNull() {
        assertNull(AmneziaFmt.parse("ss://notamnezia"))
    }

    @Test
    fun toUri_roundTrip() {
        val config = ProfileItem.create(EConfigType.AMNEZIA_WG).apply {
            remarks = "Round Trip"
            server = "rt.example.com"
            serverPort = "8443"
            secretKey = "sk_rt"
            publicKey = "pk_rt"
            localAddress = "10.0.0.5/32"
            mtu = 1410
            awgJunkPacketCount = 10
            awgJunkPacketMinSize = 200
            awgJunkPacketMaxSize = 800
            awgInitPacketJunkSize = 100
            awgResponsePacketJunkSize = 110
            awgInitPacketMagicHeader = "11111"
            awgResponsePacketMagicHeader = "22222"
            awgUnderloadPacketMagicHeader = "33333"
        }

        val uri = AppConfig.AMNEZIA + AmneziaFmt.toUri(config)
        assertTrue(uri.startsWith("amnezia://"))

        val reparsed = AmneziaFmt.parse(uri)
        assertNotNull(reparsed)
        assertEquals("Round Trip", reparsed?.remarks)
        assertEquals("rt.example.com", reparsed?.server)
        assertEquals("8443", reparsed?.serverPort)
        assertEquals("sk_rt", reparsed?.secretKey)
        assertEquals("pk_rt", reparsed?.publicKey)
        assertEquals(10, reparsed?.awgJunkPacketCount)
        assertEquals(200, reparsed?.awgJunkPacketMinSize)
        assertEquals("11111", reparsed?.awgInitPacketMagicHeader)
        assertEquals("33333", reparsed?.awgUnderloadPacketMagicHeader)
        assertEquals(1410, reparsed?.mtu)
    }

    @Test
    fun parse_emptySection_returnsNull() {
        assertNull(AmneziaFmt.parse("not-a-valid-amnezia://dGVzdA=="))
    }
}
