package com.v2ray.ang.fmt

import android.util.Log
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic

class SingBoxFmtTest {

    private lateinit var mockLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)
    }

    @After
    fun tearDown() {
        mockLog.close()
    }

    private val vlessRealityConfig = """
        {
          "outbounds": [
            {
              "type": "vless",
              "tag": "my-vless",
              "server": "example.com",
              "server_port": 443,
              "uuid": "11111111-2222-3333-4444-555555555555",
              "flow": "xtls-rprx-vision",
              "tls": {
                "enabled": true,
                "server_name": "example.com",
                "utls": { "enabled": true, "fingerprint": "chrome" },
                "reality": { "enabled": true, "public_key": "PUBKEY", "short_id": "abc" }
              },
              "transport": { "type": "ws", "path": "/ws", "headers": { "Host": "ws.example.com" } }
            },
            { "type": "direct", "tag": "direct" }
          ]
        }
    """.trimIndent()

    private val xrayConfig = """
        {
          "inbounds": [ { "protocol": "socks", "port": 10808 } ],
          "outbounds": [ { "protocol": "vmess", "tag": "proxy" } ],
          "routing": {}
        }
    """.trimIndent()

    // ==================== Detection ====================

    @Test
    fun isSingBoxConfig_singBoxJson_returnsTrue() {
        assertTrue(SingBoxFmt.isSingBoxConfig(vlessRealityConfig))
    }

    @Test
    fun isSingBoxConfig_xrayJson_returnsFalse() {
        assertFalse(SingBoxFmt.isSingBoxConfig(xrayConfig))
    }

    @Test
    fun isSingBoxConfig_shareLinkOrGarbage_returnsFalse() {
        assertFalse(SingBoxFmt.isSingBoxConfig("vless://uuid@host:443?encryption=none"))
        assertFalse(SingBoxFmt.isSingBoxConfig("not json at all"))
        assertFalse(SingBoxFmt.isSingBoxConfig(null))
    }

    @Test
    fun isSingBoxConfig_outboundsArrayRoot_returnsTrue() {
        val array = """[ {"type":"trojan","server":"h","server_port":443,"password":"p"} ]"""
        assertTrue(SingBoxFmt.isSingBoxConfig(array))
    }

    // ==================== Parsing ====================

    @Test
    fun parse_vlessReality_parsesEveryField() {
        val configs = SingBoxFmt.parse(vlessRealityConfig)

        assertEquals(1, configs.size)
        val profile = configs.first()
        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals("my-vless", profile.remarks)
        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
        assertEquals("11111111-2222-3333-4444-555555555555", profile.password)
        assertEquals("none", profile.method)
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("reality", profile.security)
        assertEquals("example.com", profile.sni)
        assertEquals("chrome", profile.fingerPrint)
        assertEquals("PUBKEY", profile.publicKey)
        assertEquals("abc", profile.shortId)
        assertEquals("ws", profile.network)
        assertEquals("/ws", profile.path)
        assertEquals("ws.example.com", profile.host)
    }

    @Test
    fun parse_vmessTls_parsesTlsBlock() {
        val config = """{"outbounds":[{"type":"vmess","tag":"my-vmess","server":"vm.example.com",
            "server_port":8443,"uuid":"uu","security":"auto",
            "tls":{"enabled":true,"server_name":"vm.example.com","insecure":true,"alpn":["h2","http/1.1"]}}]}"""

        val profile = SingBoxFmt.parse(config).first()
        assertEquals(EConfigType.VMESS, profile.configType)
        assertEquals("vm.example.com", profile.server)
        assertEquals("8443", profile.serverPort)
        assertEquals("uu", profile.password)
        assertEquals("auto", profile.method)
        assertEquals("tls", profile.security)
        assertEquals("vm.example.com", profile.sni)
        assertEquals(true, profile.insecure)
        assertEquals("h2,http/1.1", profile.alpn)
    }

    @Test
    fun parse_trojan_parsesPasswordAndTls() {
        val config = """{"outbounds":[{"type":"trojan","tag":"tr","server":"tr.example.com",
            "server_port":443,"password":"pw","tls":{"enabled":true,"server_name":"tr.example.com"}}]}"""

        val profile = SingBoxFmt.parse(config).first()
        assertEquals(EConfigType.TROJAN, profile.configType)
        assertEquals("tr.example.com", profile.server)
        assertEquals("443", profile.serverPort)
        assertEquals("pw", profile.password)
        assertEquals("tls", profile.security)
    }

    @Test
    fun parse_shadowsocks_parsesCipherAndPassword() {
        val config = """{"outbounds":[{"type":"shadowsocks","tag":"ss","server":"ss.example.com",
            "server_port":8388,"method":"aes-128-gcm","password":"pw"}]}"""

        val profile = SingBoxFmt.parse(config).first()
        assertEquals(EConfigType.SHADOWSOCKS, profile.configType)
        assertEquals("ss.example.com", profile.server)
        assertEquals("8388", profile.serverPort)
        assertEquals("aes-128-gcm", profile.method)
        assertEquals("pw", profile.password)
        assertNull(profile.security)
    }

    @Test
    fun parse_hysteria2_parsesObfsAndTls() {
        val config = """{"outbounds":[{"type":"hysteria2","tag":"hy2","server":"hy.example.com",
            "server_port":443,"password":"pw","obfs":{"type":"salamander","password":"obfs-pw"},
            "tls":{"enabled":true,"server_name":"hy.example.com"}}]}"""

        val profile = SingBoxFmt.parse(config).first()
        assertEquals(EConfigType.HYSTERIA2, profile.configType)
        assertEquals("hy.example.com", profile.server)
        assertEquals("443", profile.serverPort)
        assertEquals("pw", profile.password)
        assertEquals("obfs-pw", profile.obfsPassword)
        assertEquals("tls", profile.security)
        assertEquals(NetworkType.HYSTERIA.type, profile.network)
    }

    @Test
    fun parse_wireguard_parsesKeysAndReserved() {
        val config = """{"outbounds":[{"type":"wireguard","tag":"wg","server":"wg.example.com",
            "server_port":51820,"private_key":"PRIV","peer_public_key":"PUB","pre_shared_key":"PSK",
            "local_address":["172.16.0.2/32"],"reserved":[0,0,0],"mtu":1420}]}"""

        val profile = SingBoxFmt.parse(config).first()
        assertEquals(EConfigType.WIREGUARD, profile.configType)
        assertEquals("wg.example.com", profile.server)
        assertEquals("51820", profile.serverPort)
        assertEquals("PRIV", profile.secretKey)
        assertEquals("PUB", profile.publicKey)
        assertEquals("PSK", profile.preSharedKey)
        assertEquals("172.16.0.2/32", profile.localAddress)
        assertEquals("0,0,0", profile.reserved)
        assertEquals(1420, profile.mtu)
    }

    @Test
    fun parse_unsupportedTypes_areSkipped() {
        val config = """{"outbounds":[
            {"type":"tuic","tag":"t"},
            {"type":"direct","tag":"d"},
            {"type":"block","tag":"b"},
            {"type":"shadowsocks","tag":"ok","server":"s.example.com","server_port":1,"method":"aes","password":"p"}
        ]}"""

        val configs = SingBoxFmt.parse(config)
        assertEquals(1, configs.size)
        assertEquals("ok", configs.first().remarks)
    }

    @Test
    fun parse_noServerAddress_isRejected() {
        val config = """{"outbounds":[{"type":"vless","tag":"broken","server_port":443,"uuid":"u"}]}"""
        assertTrue(SingBoxFmt.parse(config).isEmpty())
    }

    @Test
    fun parse_notJson_returnsEmpty() {
        assertTrue(SingBoxFmt.parse("vless://x").isEmpty())
        assertTrue(SingBoxFmt.parse(null).isEmpty())
    }
}