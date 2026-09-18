package com.v2ray.ang.dto.entities

import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ProfileItem's AmneziaWG obfuscation detection.
 */
class ProfileItemTest {

    private fun profile() = ProfileItem.create(EConfigType.AMNEZIA_WG)

    @Test
    fun noObfuscationFields_returnsFalse() {
        assertFalse(profile().hasAmneziaObfuscation)
    }

    @Test
    fun onlyJunkPacketCounts_returnsFalse() {
        val item = profile().apply {
            awgJunkPacketCount = 70
            awgJunkPacketMinSize = 50
            awgJunkPacketMaxSize = 1000
        }
        assertFalse(item.hasAmneziaObfuscation)
    }

    @Test
    fun initPacketJunkSize_returnsTrue() {
        val item = profile().apply { awgInitPacketJunkSize = 500 }
        assertTrue(item.hasAmneziaObfuscation)
    }

    @Test
    fun responsePacketJunkSize_returnsTrue() {
        val item = profile().apply { awgResponsePacketJunkSize = 500 }
        assertTrue(item.hasAmneziaObfuscation)
    }

    @Test
    fun magicHeaders_returnsTrue() {
        assertTrue(profile().apply { awgInitPacketMagicHeader = "938700" }.hasAmneziaObfuscation)
        assertTrue(profile().apply { awgResponsePacketMagicHeader = "827631" }.hasAmneziaObfuscation)
        assertTrue(profile().apply { awgUnderloadPacketMagicHeader = "883131" }.hasAmneziaObfuscation)
    }

    @Test
    fun blankMagicHeaders_returnsFalse() {
        val item = profile().apply {
            awgInitPacketJunkSize = 0
            awgResponsePacketJunkSize = 0
            awgInitPacketMagicHeader = ""
            awgResponsePacketMagicHeader = " "
            awgUnderloadPacketMagicHeader = null
        }
        assertFalse(item.hasAmneziaObfuscation)
    }

    @Test
    fun psiphonProfile_returnsFalse() {
        val item = ProfileItem.create(EConfigType.PSIPHON).apply {
            psiphonRegion = "US"
        }
        assertFalse(item.hasAmneziaObfuscation)
    }

    @Test
    fun createDefaultsAware() {
        val item = ProfileItem.create(EConfigType.AMNEZIA_WG)
        assertEquals(EConfigType.AMNEZIA_WG, item.configType)
        assertEquals(null, item.awgJunkPacketCount)
    }
}