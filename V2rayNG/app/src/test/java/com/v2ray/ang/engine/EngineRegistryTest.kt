package com.v2ray.ang.engine

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the engine capability gate.
 *
 * The launcher consults [EngineRegistry.capabilityFor] BEFORE the address-validity
 * check. A profile that is supported but whose runtime engine is not bundled here
 * (SlipNet, Psiphon, obfuscated AmneziaWG) must report exact missing dependencies
 * instead of a misleading "invalid config" from the URL-validity check.
 */
class EngineRegistryTest {

    @Test
    fun xrayBackedProtocols_reportAvailable() {
        listOf(
            EConfigType.VLESS,
            EConfigType.VMESS,
            EConfigType.SHADOWSOCKS,
            EConfigType.TROJAN,
            EConfigType.SOCKS,
            EConfigType.HTTP,
            EConfigType.HYSTERIA2,
            EConfigType.WIREGUARD,
        ).forEach { type ->
            val capability = EngineRegistry.capabilityFor(ProfileItem.create(type))
            assertEquals("protocol ${type.name}", EngineId.XRAY, capability.engine)
            assertTrue("protocol ${type.name} must be available", capability.available)
            assertTrue("protocol ${type.name} must be supported", capability.supported)
            assertFalse("protocol ${type.name} must not be blocked", capability.isBlocked)
        }
    }

    @Test
    fun customAndGroupProfiles_reportAvailable() {
        listOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
            .forEach { type ->
                val capability = EngineRegistry.capabilityFor(ProfileItem.create(type))
                assertTrue("protocol ${type.name}", capability.available)
            }
    }

    @Test
    fun slipnet_reportsUnavailableWithMissingEngine() {
        val capability = EngineRegistry.capabilityFor(ProfileItem.create(EConfigType.SLIPNET))
        assertEquals(EngineId.SLIPNET, capability.engine)
        assertTrue(capability.supported)
        assertFalse(capability.available)
        assertTrue(capability.isBlocked)
        assertNotNull(capability.missingDependency)
        assertTrue(capability.missingDependency!!.contains("SlipNet", ignoreCase = true))
    }

    @Test
    fun psiphon_reportsUnavailableWithMissingEngine() {
        val capability = EngineRegistry.capabilityFor(ProfileItem.create(EConfigType.PSIPHON))
        assertEquals(EngineId.PSIPHON, capability.engine)
        assertTrue(capability.supported)
        assertFalse(capability.available)
        assertTrue(capability.isBlocked)
        assertNotNull(capability.missingDependency)
        assertTrue(capability.missingDependency!!.contains("Psiphon", ignoreCase = true))
    }

    @Test
    fun amneziaWithObfuscation_reportsUnavailable() {
        val obfuscated = ProfileItem.create(EConfigType.AMNEZIA_WG).apply {
            awgJunkPacketCount = 3
            awgInitPacketJunkSize = 8
        }
        val capability = EngineRegistry.capabilityFor(obfuscated)
        assertEquals(EngineId.AMNEZIA_WG, capability.engine)
        assertFalse(capability.available)
        assertTrue(capability.isBlocked)
    }

    @Test
    fun amneziaPlainWireguard_fallsBackToXray() {
        val plain = ProfileItem.create(EConfigType.AMNEZIA_WG)
        val capability = EngineRegistry.capabilityFor(plain)
        assertEquals(EngineId.XRAY, capability.engine)
        assertTrue(capability.available)
    }

    @Test
    fun dns_reportsAvailable() {
        val capability = EngineRegistry.capabilityFor(ProfileItem.create(EConfigType.DNS))
        assertEquals(EngineId.DNS, capability.engine)
        assertTrue(capability.available)
        assertFalse(capability.isBlocked)
    }

    @Test
    fun hasAmneziaObfuscation_reflectsJunkFields() {
        assertFalse(ProfileItem.create(EConfigType.AMNEZIA_WG).hasAmneziaObfuscation)
        assertTrue(
            ProfileItem.create(EConfigType.AMNEZIA_WG).apply {
                awgJunkPacketCount = 1
            }.hasAmneziaObfuscation
        )
    }
}