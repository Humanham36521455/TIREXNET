package com.v2ray.ang.fmt

import android.util.Base64
import android.util.Log
import com.tencent.mmkv.MMKV
import com.v2ray.ang.core.CoreOutboundBuilder
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.testutil.MmkvTestRuntime
import com.v2ray.ang.util.JsonUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.util.Base64 as JavaBase64

/**
 * Unit tests for VmessFmt parsing, URI conversion, and outbound generation.
 *
 * Covers the legacy base64-JSON QR code format and the standard vmess:// URI
 * format, plus verification that CoreOutboundBuilder emits a flat VMess outbound
 * the bundled Xray core accepts.
 */
class VmessFmtTest {

    private lateinit var mockLog: MockedStatic<Log>
    private lateinit var mockBase64: MockedStatic<Base64>

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
            val decoder = if (isUrlSafe) JavaBase64.getUrlDecoder() else JavaBase64.getDecoder()
            decoder.decode(input)
        }
        mockBase64.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as ByteArray
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val noPadding = (flags and Base64.NO_PADDING) != 0
            var encoder = if (isUrlSafe) JavaBase64.getUrlEncoder() else JavaBase64.getEncoder()
            if (noPadding) {
                encoder = encoder.withoutPadding()
            }
            encoder.encodeToString(input)
        }
    }

    @After
    fun tearDown() {
        mockLog.close()
        mockBase64.close()
    }

    // ==================== Legacy base64 JSON parse ====================

    private fun legacyVmessUri(
        ps: String = "Legacy Server",
        add: String = "example.com",
        port: String = "443",
        id: String = "9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862",
        scy: String = "auto",
        net: String = "tcp",
        type: String = "none",
        host: String = "",
        path: String = "/",
        tls: String = ""
    ): String {
        val json = JsonUtil.toJson(
            mapOf(
                "v" to "2",
                "ps" to ps,
                "add" to add,
                "port" to port,
                "id" to id,
                "aid" to "0",
                "scy" to scy,
                "net" to net,
                "type" to type,
                "host" to host,
                "path" to path,
                "tls" to tls
            )
        )
        val base64 = JavaBase64.getEncoder().encodeToString(json.toByteArray())
        return "vmess://$base64"
    }

    @Test
    fun parseLegacy_base64Json_parsesEveryField() {
        val uri = legacyVmessUri(
            ps = "Legacy Server",
            add = "example.com",
            port = "443",
            scy = "aes-128-gcm",
            net = "tcp",
            type = "http",
            host = "h1.example.com",
            tls = "tls"
        )
        val config = VmessFmt.parse(uri)

        assertNotNull(config)
        assertEquals(EConfigType.VMESS, config?.configType)
        assertEquals("Legacy Server", config?.remarks)
        assertEquals("example.com", config?.server)
        assertEquals("443", config?.serverPort)
        assertEquals("9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862", config?.password)
        assertEquals("aes-128-gcm", config?.method)
        assertEquals("tcp", config?.network)
        assertEquals("http", config?.headerType)
        assertEquals("h1.example.com", config?.host)
        assertEquals("tls", config?.security)
    }

    @Test
    fun parseLegacy_emptyScy_defaultsToAuto() {
        val config = VmessFmt.parse(legacyVmessUri(scy = ""))
        assertEquals("auto", config?.method)
    }

    @Test
    fun parseLegacy_tcpEmptyIsNormalizedToTcp() {
        val config = VmessFmt.parse(legacyVmessUri(net = ""))
        assertEquals("tcp", config?.network)
    }

    @Test
    fun toUri_legacy_roundTrip_preservesFields() {
        val parsed = VmessFmt.parse(legacyVmessUri(tls = "tls"))!!
        val regenerated = VmessFmt.toUri(parsed)

        val reparsed = VmessFmt.parse("vmess://$regenerated")
        assertNotNull(reparsed)
        assertEquals(EConfigType.VMESS, reparsed?.configType)
        assertEquals("Legacy Server", reparsed?.remarks)
        assertEquals("example.com", reparsed?.server)
        assertEquals("443", reparsed?.serverPort)
        assertEquals("9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862", reparsed?.password)
        assertEquals("auto", reparsed?.method)
        assertEquals("tcp", reparsed?.network)
        assertEquals("tls", reparsed?.security)
    }

    // ==================== Standard vmess:// URI parse ====================

    private val stdUri =
        "vmess://9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862@example.com:443" +
            "?type=ws&path=%2Fws&host=cdn.example.com&security=tls&sni=example.com" +
            "&fp=chrome&alpn=http%2F1.1&insecure=1#Standard%20Server"

    @Test
    fun parseVmessStd_parsesEveryField() {
        val config = VmessFmt.parse(stdUri)

        assertNotNull(config)
        assertEquals(EConfigType.VMESS, config?.configType)
        assertEquals("Standard Server", config?.remarks)
        assertEquals("example.com", config?.server)
        assertEquals("443", config?.serverPort)
        assertEquals("9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862", config?.password)
        assertEquals("auto", config?.method)
        assertEquals("ws", config?.network)
        assertEquals("/ws", config?.path)
        assertEquals("cdn.example.com", config?.host)
        assertEquals("tls", config?.security)
        assertEquals("example.com", config?.sni)
        assertEquals("chrome", config?.fingerPrint)
        assertEquals(true, config?.insecure)
    }

    @Test
    fun parseVmessStd_wsTls_outboundSerialsVmessConfig() {
        val original = VmessFmt.parse(stdUri)!!
        val persisted = JsonUtil.fromJsonSafe(JsonUtil.toJson(original), ProfileItem::class.java)!!

        val outbound = CoreOutboundBuilder.convert(persisted)
        assertNotNull(outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        assertEquals("vmess", json.get("protocol").asString)

        val settings = json.getAsJsonObject("settings")
        assertEquals("example.com", settings.get("address").asString)
        assertEquals(443, settings.get("port").asInt)
        assertEquals("9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862", settings.get("id").asString)
        assertEquals("auto", settings.get("security").asString)

        val stream = json.getAsJsonObject("streamSettings")
        assertEquals("ws", stream.get("network").asString)
        assertEquals("tls", stream.get("security").asString)
        val ws = stream.getAsJsonObject("wsSettings")
        assertEquals("cdn.example.com", ws.get("host").asString)
        assertEquals("/ws", ws.get("path").asString)
        val tls = stream.getAsJsonObject("tlsSettings")
        assertEquals("example.com", tls.get("serverName").asString)
        assertTrue(tls.get("allowInsecure").asBoolean)
    }

    @Test
    fun parseVmessStd_tlsOnly_firstValuePreserved() {
        val config = VmessFmt.parse(
            "vmess://9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862@example.com:443" +
                "?security=tls&sni=example.com"
        )
        assertEquals("tls", config?.security)
        assertEquals("example.com", config?.sni)
    }

    @Test
    fun parse_nonTlsSecurity_isNull() {
        val config = VmessFmt.parse(
            "vmess://9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862@example.com:443" +
                "?security=none&type=tcp"
        )
        assertNull(config?.security)
    }

    @Test
    fun parse_noQuery_returnsNull_noUserInfoOrStructuredFields() {
        // A bare UUID@server:port without a query cannot be a standard vmess URI;
        // only the base64 legacy QR format is valid on the non-query path.
        assertNull(VmessFmt.parse("vmess://9b18b6c3-2f3c-4a92-b1ae-9a7b07c91862@example.com:443"))
    }

    companion object {
        private val settingsStorage: MMKV = MmkvTestRuntime.handle("SETTING")

        @BeforeClass
        @JvmStatic
        fun initializeSettingsStorage() {
            MmkvTestRuntime.bindAll()
            reset(settingsStorage)
            whenever(settingsStorage.decodeString(any<String>())).thenReturn(null)
            whenever(settingsStorage.decodeString(any<String>(), any<String>()))
                .thenAnswer { arg -> arg.getArgument(1) }
            whenever(settingsStorage.decodeBool(any<String>())).thenReturn(false)
            whenever(settingsStorage.decodeBool(any<String>(), any<Boolean>()))
                .thenAnswer { arg -> arg.getArgument(1) }
        }
    }
}