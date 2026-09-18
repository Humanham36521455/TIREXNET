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
 * Regression tests for Trojan profile parsing and outbound config generation.
 *
 * Imported Trojan URIs may carry a "flow" parameter (legacy XTLS vision flows).
 * The bundled Xray core removed Trojan flow support entirely and rejects any
 * non-empty flow in the outbound settings, so the runtime outbound must never
 * serialize it, while the profile field keeps the value for URI export.
 */
class TrojanFmtTest {

    private lateinit var mockLog: MockedStatic<Log>

    private val flowUri =
        "trojan://password123@192.168.1.1:443" +
            "?security=tls&sni=cdn.example.com&type=tcp&flow=xtls-rprx-vision#Flow%20Test"

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
    fun parse_flowUri_preservesProfileFlow() {
        val config = TrojanFmt.parse(flowUri)

        assertEquals(EConfigType.TROJAN, config.configType)
        assertEquals("Flow Test", config.remarks)
        assertEquals("192.168.1.1", config.server)
        assertEquals("443", config.serverPort)
        assertEquals("password123", config.password)
        assertEquals("tcp", config.network)
        assertEquals("tls", config.security)
        assertEquals("cdn.example.com", config.sni)
        // Kept for URI round-trip even though the runtime core rejects Trojan flow.
        assertEquals("xtls-rprx-vision", config.flow)
    }

    @Test
    fun parse_noQuery_defaultsToTcpAndTls() {
        val config = TrojanFmt.parse("trojan://password123@example.com:443#Plain")
        assertEquals(EConfigType.TROJAN, config.configType)
        assertEquals("Plain", config.remarks)
        assertEquals("example.com", config.server)
        assertEquals("443", config.serverPort)
        assertEquals("tcp", config.network)
        assertEquals("tls", config.security)
        assertEquals(false, config.insecure)
        assertNull(config.flow)
    }

    @Test
    fun parse_wsTransportAndReality_arePreserved() {
        val ws = TrojanFmt.parse(
            "trojan://password123@example.com:443" +
                "?security=tls&type=ws&host=ws.example.com&path=%2Fws&sni=ws.example.com#Ws"
        )
        assertEquals("ws", ws.network)
        assertEquals("ws.example.com", ws.host)
        assertEquals("/ws", ws.path)

        val reality = TrojanFmt.parse(
            "trojan://password123@example.com:443" +
                "?security=reality&type=tcp&pbk=pubkey&sid=deadbeef&flow=xtls-rprx-vision#Reality"
        )
        assertEquals("reality", reality.security)
        assertEquals("pubkey", reality.publicKey)
        assertEquals("deadbeef", reality.shortId)
    }

    @Test
    fun parse_nonTlsSecurity_isNull() {
        val config = TrojanFmt.parse(
            "trojan://password123@example.com:443?security=none&type=tcp"
        )
        assertNull(config.security)
    }

    // ==================== Outbound generation ====================

    @Test
    fun convert_flowProfile_omitsRemovedFlowFromOutbound() {
        // The bundled Xray-core rejects any non-empty Trojan flow ("Flow for Trojan"
        // is a removed feature), so the runtime outbound settings must not contain it.
        val original = TrojanFmt.parse(flowUri)
        val persisted = JsonUtil.fromJsonSafe(JsonUtil.toJson(original), ProfileItem::class.java)!!

        val outbound = CoreOutboundBuilder.convert(persisted)
        assertNotNull(outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        assertEquals("trojan", json.get("protocol").asString)
        val settings = json.getAsJsonObject("settings")
        assertEquals("192.168.1.1", settings.get("address").asString)
        assertEquals(443, settings.get("port").asInt)
        assertEquals("password123", settings.get("password").asString)
        // Removed Trojan flow must not leak into the runtime config.
        assertNull(settings.get("flow"))
    }

    @Test
    fun convert_basicTrojan_generatesValidOutboundJson() {
        val profile = ProfileItem.create(EConfigType.TROJAN).apply {
            remarks = "Basic"
            server = "example.com"
            serverPort = "443"
            password = "password123"
            network = "tcp"
            security = "tls"
            sni = "example.com"
        }

        val outbound = CoreOutboundBuilder.convert(profile)
        assertNotNull(outbound)

        val json = JsonUtil.parseString(JsonUtil.toJsonPretty(outbound))!!
        val settings = json.getAsJsonObject("settings")
        assertEquals("example.com", settings.get("address").asString)
        assertEquals(443, settings.get("port").asInt)
        assertEquals("password123", settings.get("password").asString)
        assertNull(settings.get("flow"))

        val stream = json.getAsJsonObject("streamSettings")
        assertEquals("tcp", stream.get("network").asString)
        assertEquals("tls", stream.get("security").asString)
        assertNotNull(stream.get("tlsSettings"))
        assertNull(stream.get("realitySettings"))
    }

    // ==================== URI round trip ====================

    @Test
    fun toUri_roundTrip_keepsFlowAndFields() {
        val parsed = TrojanFmt.parse(flowUri)
        val regenerated = TrojanFmt.toUri(parsed)

        assertTrue(regenerated.contains("flow=xtls-rprx-vision"))

        val reparsed = TrojanFmt.parse("trojan://$regenerated")
        assertEquals(EConfigType.TROJAN, reparsed.configType)
        assertEquals("password123", reparsed.password)
        assertEquals("192.168.1.1", reparsed.server)
        assertEquals("443", reparsed.serverPort)
        assertEquals("tls", reparsed.security)
        assertEquals("xtls-rprx-vision", reparsed.flow)
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