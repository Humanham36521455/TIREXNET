package com.v2ray.ang.engine.singbox

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigBuilderTest {

    private fun outbound(profile: ProfileItem): JsonObject? =
        SingBoxConfigBuilder.convert(profile)

    private fun parse(json: JsonObject): JsonObject =
        JsonUtil.parseString(JsonUtil.toJsonPretty(json))!!

    private fun vlessProfile() = ProfileItem.create(EConfigType.VLESS).apply {
        remarks = "test-vless"
        server = "example.com"
        serverPort = "443"
        password = "11111111-2222-3333-4444-555555555555"
        method = "none"
        security = "tls"
        sni = "example.com"
        network = "ws"
        host = "ws.example.com"
        path = "/ws"
    }

    @Test
    fun vless_tlsWs_buildsSingBoxOutbound() {
        val json = parse(outbound(vlessProfile()))

        assertEquals("vless", json.get("type").asString)
        assertEquals("test-vless", json.get("tag").asString)
        assertEquals("example.com", json.get("server").asString)
        assertEquals(443, json.get("server_port").asInt)
        assertEquals("11111111-2222-3333-4444-555555555555", json.get("uuid").asString)

        val tls = json.getAsJsonObject("tls")
        assertTrue(tls.get("enabled").asBoolean)
        assertEquals("example.com", tls.get("server_name").asString)

        val transport = json.getAsJsonObject("transport")
        assertEquals("ws", transport.get("type").asString)
        assertEquals("/ws", transport.get("path").asString)
        assertEquals("ws.example.com", transport.getAsJsonObject("headers").get("Host").asString)
        assertNull(tls.get("reality"))
    }

    @Test
    fun vless_reality_emitsRealityBlock() {
        val profile = vlessProfile().apply {
            security = "reality"
            publicKey = "PUBKEY"
            shortId = "abc"
            fingerPrint = "chrome"
            network = "tcp"
            headerType = "none"
        }

        val json = parse(outbound(profile))
        val tls = json.getAsJsonObject("tls")
        assertEquals("reality", profile.security)
        val reality = tls.getAsJsonObject("reality")
        assertTrue(reality.get("enabled").asBoolean)
        assertEquals("PUBKEY", reality.get("public_key").asString)
        assertEquals("abc", reality.get("short_id").asString)
        assertTrue(profile.fingerPrint != null && json.getAsJsonObject("tls").getAsJsonObject("utls").get("fingerprint").asString == "chrome")
    }

    @Test
    fun vless_noSecurity_emitsNoTls() {
        val profile = vlessProfile().apply {
            security = null
            network = "tcp"
            headerType = "none"
        }

        val json = parse(outbound(profile))
        assertNull(json.get("tls"))
        assertNull(json.get("transport"))
    }

    @Test
    fun vmess_buildsWithDefaultSecurity() {
        val profile = ProfileItem.create(EConfigType.VMESS).apply {
            server = "vm.example.com"
            serverPort = "8443"
            password = "uu"
        }

        val json = parse(outbound(profile))
        assertEquals("vmess", json.get("type").asString)
        assertEquals("uu", json.get("uuid").asString)
        assertEquals("auto", json.get("security").asString)
        assertEquals(0, json.get("alter_id").asInt)
    }

    @Test
    fun trojan_buildsPassword() {
        val profile = ProfileItem.create(EConfigType.TROJAN).apply {
            server = "tr.example.com"
            serverPort = "443"
            password = "pw"
            security = "tls"
            sni = "tr.example.com"
            network = "ws"
            path = "/ws"
        }

        val json = parse(outbound(profile))
        assertEquals("trojan", json.get("type").asString)
        assertEquals("pw", json.get("password").asString)
        assertNotNull(json.getAsJsonObject("tls"))
        assertEquals("ws", json.getAsJsonObject("transport").get("type").asString)
    }

    @Test
    fun shadowsocks_buildsCipher() {
        val profile = ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            server = "ss.example.com"
            serverPort = "8388"
            method = "aes-128-gcm"
            password = "pw"
        }

        val json = parse(outbound(profile))
        assertEquals("shadowsocks", json.get("type").asString)
        assertEquals("aes-128-gcm", json.get("method").asString)
        assertEquals("pw", json.get("password").asString)
    }

    @Test
    fun hysteria2_buildsObfsAndTls() {
        val profile = ProfileItem.create(EConfigType.HYSTERIA2).apply {
            server = "hy.example.com"
            serverPort = "443"
            password = "pw"
            obfsPassword = "obfs-pw"
            security = "tls"
            sni = "hy.example.com"
        }

        val json = parse(outbound(profile))
        assertEquals("hysteria2", json.get("type").asString)
        assertEquals("pw", json.get("password").asString)
        assertEquals("salamander", json.getAsJsonObject("obfs").get("type").asString)
        assertEquals("obfs-pw", json.getAsJsonObject("obfs").get("password").asString)
        assertTrue(json.getAsJsonObject("tls").get("enabled").asBoolean)
        assertNull(json.get("transport"))
    }

    @Test
    fun wireguard_buildsKeysAndReserved() {
        val profile = ProfileItem.create(EConfigType.WIREGUARD).apply {
            server = "wg.example.com"
            serverPort = "51820"
            secretKey = "PRIV"
            publicKey = "PUB"
            preSharedKey = "PSK"
            localAddress = "172.16.0.2/32,fd00::2/128"
            reserved = "0,0,0"
            mtu = 1420
        }

        val json = parse(outbound(profile))
        assertEquals("wireguard", json.get("type").asString)
        assertEquals("PRIV", json.get("private_key").asString)
        assertEquals("PUB", json.get("peer_public_key").asString)
        assertEquals("PSK", json.get("pre_shared_key").asString)
        assertEquals(1420, json.get("mtu").asInt)
        assertEquals(2, json.getAsJsonArray("local_address").size())
        assertEquals("172.16.0.2/32", json.getAsJsonArray("local_address").get(0).asString)
        assertEquals(3, json.getAsJsonArray("reserved").size())
        assertEquals(0, json.getAsJsonArray("reserved").get(1).asInt)
    }

    @Test
    fun xhttpTransport_isUnsupported() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "example.com"
            serverPort = "443"
            password = "uuid"
            network = "xhttp"
            path = "/x"
        }

        val result = SingBoxConfigBuilder.build(profile)
        assertFalse(result.isSupported)
        assertNull(result.outbound)
        assertNotNull(result.unsupportedReason)
        assertTrue(result.unsupportedReason!!.contains("xhttp"))
    }

    @Test
    fun tcpWithHttpHeader_isUnsupported() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "example.com"
            serverPort = "443"
            password = "uuid"
            network = "tcp"
            headerType = "http"
        }

        assertFalse(SingBoxConfigBuilder.build(profile).isSupported)
    }

    @Test
    fun unresolvedProtocol_isUnsupported() {
        val profile = ProfileItem.create(EConfigType.POLICYGROUP)
        val result = SingBoxConfigBuilder.build(profile)
        assertFalse(result.isSupported)
        assertNotNull(result.unsupportedReason)
    }

    @Test
    fun grpcTransport_buildsServiceName() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            server = "example.com"
            serverPort = "443"
            password = "uuid"
            network = "grpc"
            serviceName = "grpc-name"
        }

        val json = parse(outbound(profile))
        assertEquals("grpc", json.getAsJsonObject("transport").get("type").asString)
        assertEquals("grpc-name", json.getAsJsonObject("transport").get("service_name").asString)
    }
}