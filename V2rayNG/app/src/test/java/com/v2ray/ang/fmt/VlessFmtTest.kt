package com.v2ray.ang.fmt

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

/**
 * Regression tests for VLESS profile parsing and outbound config generation.
 *
 * The bundled Xray core loads outbound settings through VLessOutboundConfig.Build(),
 * which rejects an outbound whose "encryption" value is empty and only accepts
 * "none" (or the MLKEM variant the app never writes). A VLESS profile that lacks
 * meaningful encryption must therefore always serialize "encryption": "none".
 */
class VlessFmtTest {

    private lateinit var mockLog: MockedStatic<Log>

    /**
     * Exact config reported failing on a real device with "start failed".
     * VLESS over TCP, no TLS, HTTP mask header, domain-only (no web socket), no flow.
     */
    private val failingUri =
        "vless://f9789e66-ef1d-4aae-a1d5-61863aeafa42@netvia2.extraservices.ir:62068" +
            "?security=&encryption=none&host=play-apps-features.googleusercontent.com" +
            "&headerType=http&type=tcp#Turmey%202%20%F0%9F%87%B9%F0%87%B7"

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)
    }

    @After
    fun tearDown() {
        mockLog.close()
    }

    // ==================== Parse ====================

    @Test
    fun parse_failingUri_parsesEveryField() {
        val config = VlessFmt.parse(failingUri)

        assertNotNull(config)
        assertEquals(EConfigType.VLESS, config?.configType)
        assertTrue(config?.remarks?.startsWith("Turmey 2") == true)
        assertEquals("netvia2.extraservices.ir", config?.server)
        assertEquals("62068", config?.serverPort)
        assertEquals("f9789e66-ef1d-4aae-a1d5-61863aeafa42", config?.password)
        // "encryption=none" must be preserved; the Xray core rejects empty encryption.
        assertEquals("none", config?.method)
        assertEquals("tcp", config?.network)
        assertEquals("http", config?.headerType)
        assertEquals("play-apps-features.googleusercontent.com", config?.host)
        assertNull(config?.path)
        assertNull(config?.flow)
    }

    @Test
    fun parse_emptyOrMissingSecurity_isNullNotTls() {
        val withEmptySecurity = VlessFmt.parse(failingUri)
        assertNull(withEmptySecurity?.security)

        val absent = "vless://f9789e66-ef1d-4aae-a1d5-61863aeafa42@netvia2.extraservices.ir:62068" +
            "?encryption=none&host=play-apps-features.googleusercontent.com&headerType=http&type=tcp"
        assertEquals(EConfigType.VLESS, VlessFmt.parse(absent)?.configType)
        assertNull(VlessFmt.parse(absent)?.security)
    }

    @Test
    fun parse_securityNone_isNullNotTls() {
        val config = VlessFmt.parse(
            "vless://f9789e66-ef1d-4aae-a1d5-61863aeafa42@netvia2.extraservices.ir:62068" +
                "?security=none&encryption=none&type=tcp"
        )
        assertNull(config?.security)
    }

    @Test
    fun parse_securityTlsAndReality_arePreserved() {
        val tls = VlessFmt.parse(
            "vless://f9789e66-ef1d-4aae-a1d5-61863aeafa42@netvia2.extraservices.ir:443" +
                "?security=tls&encryption=none&sni=example.com&type=tcp"
        )
        assertEquals("tls", tls?.security)

        val reality = VlessFmt.parse(
            "vless://f9789e66-ef1d-4aae-a1d5-61863aeafa42@netvia2.extraservices.ir:443" +
                "?security=reality&encryption=none&pbk=abc&sid=def&type=tcp"
        )
        assertEquals("reality", reality?.security)
    }

    @Test
    fun parse_missingEncryption_defaultsToNone() {
        val config = VlessFmt.parse(
            "vless://f9789e66-ef1d-4aae-a1d5-61863aeafa42@netvia2.extraservices.ir:62068" +
                "?security=&type=tcp"
        )
        assertEquals("none", config?.method)
    }

    // ==================== Outbound generation ====================

    @Test
    fun convert_failingUri_generatesValidVlessOutboundJson() {
        val original = VlessFmt.parse(failingUri)!!
        // Mimic the encode -> decode round trip the app performs between screens.
        val persisted = JsonUtil.fromJsonSafe(JsonUtil.toJson(original), ProfileItem::class.java)!!

        val outbound = CoreOutboundBuilder.convert(persisted)
        assertNotNull("convert() must build an outbound for no-security VLESS", outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        assertEquals("vless", json.get("protocol").asString)

        val settings = json.getAsJsonObject("settings")
        // Xray VLESS loader requires a valid non-empty encryption ("none").
        assertEquals("none", settings.get("encryption").asString)
        assertEquals("netvia2.extraservices.ir", settings.get("address").asString)
        assertEquals(62068, settings.get("port").asInt)
        assertEquals("f9789e66-ef1d-4aae-a1d5-61863aeafa42", settings.get("id").asString)

        val stream = json.getAsJsonObject("streamSettings")
        assertEquals("tcp", stream.get("network").asString)
        // Empty security must not turn into TLS/REALITY: no security keys are emitted.
        assertNull(stream.get("security"))
        assertNull(stream.get("tlsSettings"))
        assertNull(stream.get("realitySettings"))

        val tcp = stream.getAsJsonObject("tcpSettings")
        val header = tcp.getAsJsonObject("header")
        assertEquals("http", header.get("type").asString)
        val request = header.getAsJsonObject("request")
        assertEquals(listOf("/"), request.getAsJsonArray("path").map { it.asString })
        val headers = request.getAsJsonObject("headers")
        assertEquals(
            listOf("play-apps-features.googleusercontent.com"),
            headers.getAsJsonArray("Host").map { it.asString }
        )
    }

    @Test
    fun convert_blankMethod_isNormalizedToNone() {
        // A VLESS profile saved from the editor with the encryption field left empty
        // must still serialize "encryption":"none", otherwise the core reports
        // "VLESS users: please add/set encryption:none" and start fails.
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            remarks = "Blank encryption"
            server = "netvia2.extraservices.ir"
            serverPort = "62068"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = ""
            network = "tcp"
            headerType = "none"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        val settings = json.getAsJsonObject("settings")
        assertEquals("none", settings.get("encryption").asString)
    }

    @Test
    fun convert_nullMethod_isNormalizedToNone() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "62068"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = null
            network = "tcp"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        assertEquals("none", json.getAsJsonObject("settings").get("encryption").asString)
    }

    @Test
    fun convert_tlsKeepsTlsSettingsWithoutHttpHeaderLeak() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "443"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = "none"
            security = "tls"
            sni = "netvia2.extraservices.ir"
            network = "tcp"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        val stream = json.getAsJsonObject("streamSettings")
        assertEquals("tls", stream.get("security").asString)
        assertNotNull(stream.get("tlsSettings"))
        assertNull(stream.get("realitySettings"))
        assertEquals("none", json.getAsJsonObject("settings").get("encryption").asString)
    }

    @Test
    fun convert_noSecurityParam_hasNoTlsFields() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "62068"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = "none"
            security = null
            network = "tcp"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val stream = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!.getAsJsonObject("streamSettings")
        assertNull(stream.get("security"))
        assertNull(stream.get("tlsSettings"))
        assertNull(stream.get("realitySettings"))
    }

    @Test
    fun convert_securityNone_hasNoTlsFields() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "62068"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = "none"
            security = "none"
            network = "tcp"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val stream = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!.getAsJsonObject("streamSettings")
        // "none" is the core's no-TLS value; the requirement is that it never
        // turns into TLS or REALITY.
        val emittedSecurity = stream.get("security")?.takeIf { !it.isJsonNull }?.asString
        assertTrue(emittedSecurity == null || emittedSecurity == "none")
        assertNull(stream.get("tlsSettings"))
        assertNull(stream.get("realitySettings"))
    }

    @Test
    fun convert_reality_keepsRealitySettings() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "443"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = "none"
            security = "reality"
            publicKey = "publicKey"
            shortId = "shortId"
            network = "tcp"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val stream = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!.getAsJsonObject("streamSettings")
        assertEquals("reality", stream.get("security").asString)
        assertNull(stream.get("tlsSettings"))
        assertNotNull(stream.get("realitySettings"))
    }

    @Test
    fun convert_wsTransport_noSecurity() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "443"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = "none"
            network = "ws"
            host = "ws.example.com"
            path = "/path"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val stream = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!.getAsJsonObject("streamSettings")
        assertEquals("ws", stream.get("network").asString)
        assertNull(stream.get("security"))
        assertNotNull(stream.get("wsSettings"))
        assertEquals("/path", stream.getAsJsonObject("wsSettings").get("path").asString)
    }

    @Test
    fun convert_xhttpPreservedAsBefore() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "netvia2.extraservices.ir"
            serverPort = "443"
            password = "f9789e66-ef1d-4aae-a1d5-61863aeafa42"
            method = "none"
            network = "xhttp"
            host = "xhttp.example.com"
            path = "/x"
            xhttpMode = "auto"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val stream = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!.getAsJsonObject("streamSettings")
        assertEquals("xhttp", stream.get("network").asString)
        assertNotNull(stream.get("xhttpSettings"))
        assertEquals("none", JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
            .getAsJsonObject("settings").get("encryption").asString)
    }

    // ==================== URI round trip ====================

    @Test
    fun toUri_roundTrip_preservesNoSecurityVless() {
        val parsed = VlessFmt.parse(failingUri)!!
        val regenerated = VlessFmt.toUri(parsed)

        assertTrue(regenerated.contains("encryption=none"))
        assertTrue(regenerated.contains("security="))

        val reparsed = VlessFmt.parse("vless://$regenerated")!!
        assertEquals(EConfigType.VLESS, reparsed.configType)
        assertEquals("none", reparsed.method)
        assertNull(reparsed.security)
        assertEquals("netvia2.extraservices.ir", reparsed.server)
        assertEquals("62068", reparsed.serverPort)
        assertEquals("Turmey 2", reparsed.remarks.substringBeforeLast(" "))
        assertEquals("play-apps-features.googleusercontent.com", reparsed.host)
        assertEquals("http", reparsed.headerType)
        assertEquals("tcp", reparsed.network)
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