package com.v2ray.ang.engine.singbox

import com.google.gson.JsonObject
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.engine.VpnEngineState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Lifecycle tests for [SingBoxEngine].
 *
 * The engine must never report CONNECTED: without a real sing-box runtime its state
 * is UNAVAILABLE, and once a runtime is (test-only) registered the only honest
 * success is the CONNECTING handoff to the launch bridge.
 */
class SingBoxEngineTest {

    @Before
    fun setUp() {
        SingBoxRuntime.registerExecutable(null)
        SingBoxRuntime.registerLaunchBridge(null)
    }

    @After
    fun tearDown() {
        SingBoxRuntime.registerExecutable(null)
        SingBoxRuntime.registerLaunchBridge(null)
    }

    private fun vlessProfile() = ProfileItem.create(EConfigType.VLESS).apply {
        server = "example.com"
        serverPort = "443"
        password = "11111111-2222-3333-4444-555555555555"
        method = "none"
        network = "tcp"
    }

    @Test
    fun validate_noRuntime_isUnavailable() {
        val capability = SingBoxEngine.validate(vlessProfile())
        assertEquals(com.v2ray.ang.engine.EngineId.SINGBOX, capability.engine)
        assertTrue(capability.supported)
        assertFalse(capability.available)
        assertTrue(capability.isBlocked)
        assertNotNull(capability.missingDependency)
        assertTrue(capability.missingDependency!!.contains("sing-box", ignoreCase = true))
    }

    @Test
    fun validate_unsupportedProtocol_isNotSupported() {
        val capability = SingBoxEngine.validate(ProfileItem.create(EConfigType.CUSTOM))
        assertEquals(com.v2ray.ang.engine.EngineId.SINGBOX, capability.engine)
        assertFalse(capability.supported)
        assertFalse(capability.available)
    }

    @Test
    fun start_noRuntime_returnsUnavailableNotConnected() {
        val result = SingBoxEngine.start(vlessProfile())
        assertFalse(result.started)
        assertEquals(VpnEngineState.UNAVAILABLE, result.state)
        assertTrue(result.error?.contains("sing-box", ignoreCase = true) == true)
    }

    @Test
    fun start_bridgeWithoutExecutable_staysUnavailable() {
        val bridge = CapturingBridge()
        SingBoxRuntime.registerLaunchBridge(bridge)

        assertFalse("bridge alone must not make the runtime available", SingBoxRuntime.isAvailable())
        val result = SingBoxEngine.start(vlessProfile())
        assertFalse(result.started)
        assertEquals(VpnEngineState.UNAVAILABLE, result.state)
        assertFalse(bridge.started)
    }

    @Test
    fun start_withExecutableAndBridge_handsConfigToBridge() {
        val executable = File.createTempFile("singbox", "bin")
        try {
            executable.setExecutable(true)
            SingBoxRuntime.registerExecutable(executable.absolutePath)
            val bridge = CapturingBridge()
            SingBoxRuntime.registerLaunchBridge(bridge)

            assertTrue(SingBoxRuntime.isAvailable())
            val result = SingBoxEngine.start(vlessProfile())

            assertTrue(result.started)
            assertEquals(VpnEngineState.CONNECTING, result.state)
            assertNull(result.error)
            assertTrue(bridge.started)
            assertEquals("vless", bridge.received?.get("type")?.asString)
        } finally {
            executable.delete()
        }
    }

    @Test
    fun stop_forwardsToBridge() {
        val executable = File.createTempFile("singbox", "bin")
        try {
            executable.setExecutable(true)
            SingBoxRuntime.registerExecutable(executable.absolutePath)
            val bridge = CapturingBridge()
            SingBoxRuntime.registerLaunchBridge(bridge)

            SingBoxEngine.start(vlessProfile())
            SingBoxEngine.stop()
            assertTrue(bridge.stopped)
        } finally {
            executable.delete()
        }
    }

    @Test
    fun statusAndDiagnostics_delegateToCentralState() {
        assertEquals(VpnEngineState.IDLE, SingBoxEngine.status())
        assertNull(SingBoxEngine.diagnostics())
    }

    private class CapturingBridge : SingBoxLaunchBridge {
        var started = false
        var stopped = false
        var received: JsonObject? = null

        override fun start(config: JsonObject): Boolean {
            started = true
            received = config
            return true
        }

        override fun stop() {
            stopped = true
        }
    }
}