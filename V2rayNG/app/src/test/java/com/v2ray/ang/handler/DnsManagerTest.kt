package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.testutil.MmkvTestRuntime
import org.junit.After
import org.junit.Assert.*
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
 * Unit tests for DnsManager persistence and DnsProfile behavior.
 */
class DnsManagerTest {

    private lateinit var mockLog: MockedStatic<Log>

    private val store = mutableMapOf<String, String>()
    private val settings = MmkvTestRuntime.handle("SETTING")
    private val configs = MmkvTestRuntime.handle("PREF_DNS_CHANGER_CONFIGS")
    private val activeId = MmkvTestRuntime.handle("PREF_DNS_CHANGER_ACTIVE_ID")
    private val activeServers = MmkvTestRuntime.handle("PREF_DNS_CHANGER_ACTIVE_SERVERS")

    @Before
    fun setUp() {
        store.clear()
        reset(settings, configs, activeId, activeServers)
        whenever(settings.decodeString(any())).thenAnswer { store[it.getArgument<String>(0)] }
        whenever(settings.encode(any<String>(), any<String>())).thenAnswer {
            store[it.getArgument(0)] = it.getArgument(1)
            true
        }
        whenever(configs.decodeString(any())).thenAnswer { store[it.getArgument<String>(0)] }
        whenever(configs.encode(any<String>(), any<String>())).thenAnswer {
            store[it.getArgument(0)] = it.getArgument(1)
            true
        }
        whenever(activeId.decodeString(any())).thenAnswer { store[it.getArgument<String>(0)] }
        whenever(activeId.encode(any<String>(), any<String>())).thenAnswer {
            store[it.getArgument(0)] = it.getArgument(1)
            true
        }
        whenever(activeServers.decodeString(any())).thenAnswer { store[it.getArgument<String>(0)] }
        whenever(activeServers.encode(any<String>(), any<String>())).thenAnswer {
            store[it.getArgument(0)] = it.getArgument(1)
            true
        }
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)
    }

    @After
    fun tearDown() {
        mockLog.close()
    }

    @Test
    fun getProfiles_emptyWhenNothingStored() {
        assertEquals(emptyList<DnsProfile>(), DnsManager.getProfiles())
    }

    @Test
    fun upsert_newProfile_generatesId() {
        val profile = DnsProfile("", "Home", ipV4Primary = "1.1.1.1")
        val id = DnsManager.upsert(profile)
        assertTrue(id.isNotBlank())
        assertEquals(1, DnsManager.getProfiles().size)
        assertNotNull(DnsManager.getProfile(id))
    }

    @Test
    fun upsert_andGetProfile() {
        val id = DnsManager.upsert(DnsProfile("", "Office", ipV4Primary = "8.8.8.8"))
        val loaded = DnsManager.getProfile(id)
        assertNotNull(loaded)
        assertEquals("Office", loaded?.name)
        assertEquals(listOf("8.8.8.8"), loaded?.servers)
    }

    @Test
    fun upsert_existingId_updatesInPlace() {
        val id = DnsManager.upsert(DnsProfile("", "A", ipV4Primary = "1.1.1.1"))
        val updated = DnsManager.upsert(DnsProfile(id, "A2", ipV4Primary = "2.2.2.2", ipV4Secondary = "3.3.3.3"))
        assertEquals(id, updated)
        assertEquals(1, DnsManager.getProfiles().size)
        assertEquals("A2", DnsManager.getProfile(id)?.name)
    }

    @Test
    fun delete_removesProfile() {
        val id = DnsManager.upsert(DnsProfile("", "A", ipV4Primary = "1.1.1.1"))
        DnsManager.delete(id)
        assertEquals(emptyList<DnsProfile>(), DnsManager.getProfiles())
        assertNull(DnsManager.getProfile(id))
    }

    @Test
    fun setActiveAndGetActiveServers() {
        val id = DnsManager.upsert(DnsProfile("", "A", ipV4Primary = "1.1.1.1", ipV6Primary = "2606:4700:4700::1001"))
        DnsManager.setActive(id)
        assertEquals(id, DnsManager.getActiveId())
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1001"), DnsManager.getActiveServers())
        assertTrue(DnsManager.isActive())
    }

    @Test
    fun clearActive() {
        val id = DnsManager.upsert(DnsProfile("", "A", ipV4Primary = "1.1.1.1"))
        DnsManager.setActive(id)
        DnsManager.setActive(null)
        assertNull(DnsManager.getActiveId())
        assertEquals(emptyList<String>(), DnsManager.getActiveServers())
        assertFalse(DnsManager.isActive())
    }

    @Test
    fun delete_activeProfileClearsActiveState() {
        val id = DnsManager.upsert(DnsProfile("", "A", ipV4Primary = "1.1.1.1"))
        DnsManager.setActive(id)
        DnsManager.delete(id)
        assertNull(DnsManager.getActiveId())
        assertFalse(DnsManager.isActive())
    }

    @Test
    fun isActive_falseWhenProfileMissing() {
        DnsManager.upsert(DnsProfile("", "A", ipV4Primary = "1.1.1.1"))
        DnsManager.setActive("does-not-exist")
        assertFalse(DnsManager.isActive())
    }

    @Test
    fun profileConfiguration_isValid() {
        assertTrue(DnsProfile("", "A", ipV4Primary = "1.1.1.1").isValid)
        assertTrue(DnsProfile("", "A", ipV4Primary = "1.1.1.1", ipV4Secondary = "8.8.8.8").isValid)
        assertTrue(DnsProfile("", "A", ipV6Primary = "2606:4700:4700::1111").isValid)
        assertFalse(DnsProfile("", "A").isValid)
        assertFalse(DnsProfile("", "A", ipV4Primary = "not-an-ip").isValid)
        assertFalse(DnsProfile("", "A", ipV4Primary = "1.1.1.1", ipV6Primary = "bad").isValid)
    }

    @Test
    fun getActiveServers_ignoresBlankServers() {
        val id = DnsManager.upsert(
            DnsProfile(
                "",
                "A",
                ipV4Primary = "1.1.1.1",
                ipV4Secondary = "",
                ipV6Primary = "2606:4700:4700::1001",
                ipV6Secondary = ""
            )
        )
        DnsManager.setActive(id)
        assertEquals(2, DnsManager.getActiveServers().size)
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1001"), DnsManager.getActiveServers())
    }

    @Test
    fun delete_nonExistentProfile_isNoOp() {
        DnsManager.delete("nope")
        assertEquals(emptyList<DnsProfile>(), DnsManager.getProfiles())
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeSettingsStorage() {
            MmkvTestRuntime.bindAll()
        }
    }
}