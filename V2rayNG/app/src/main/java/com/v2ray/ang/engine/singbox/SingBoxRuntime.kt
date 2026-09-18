package com.v2ray.ang.engine.singbox

import com.google.gson.JsonObject
import java.io.File

/**
 * Bridge to whatever actually executes a sing-box instance (a bundled `sing-box`
 * executable or an in-process native library).
 *
 * The app ships no such runtime today, so the default state is "no bridge, no
 * executable", which makes [SingBoxRuntime.isAvailable] return false and forces the
 * engine to report UNAVAILABLE. A future native integration registers a bridge and
 * executable path; until then nothing here can be mistaken for a live connection.
 */
interface SingBoxLaunchBridge {
    fun start(config: JsonObject): Boolean
    fun stop()
}

/**
 * Availability probe for the sing-box runtime.
 *
 * [isAvailable] is only true when BOTH an executable that exists and is executable
 * AND a [SingBoxLaunchBridge] have been registered. Registering only one of them
 * keeps the runtime unavailable, so the engine never claims it can connect.
 */
object SingBoxRuntime {

    const val MISSING_DEPENDENCY =
        "sing-box runtime (sing-box executable / libsingbox) is not bundled in this build"

    @Volatile
    private var executablePath: String? = null

    @Volatile
    private var launchBridge: SingBoxLaunchBridge? = null

    fun registerExecutable(path: String?) {
        executablePath = path
    }

    fun registerLaunchBridge(bridge: SingBoxLaunchBridge?) {
        launchBridge = bridge
    }

    fun isAvailable(): Boolean = executableFile()?.canExecute() == true && launchBridge != null

    fun missingDependency(): String = MISSING_DEPENDENCY

    fun launch(config: JsonObject): Boolean = launchBridge?.start(config) ?: false

    fun shutdown() {
        launchBridge?.stop()
    }

    private fun executableFile(): File? = executablePath?.let(::File)
}
