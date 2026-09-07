package com.v2ray.ang.mtproto

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

private interface ProxyLibrary : Library {
    companion object {
        val INSTANCE: ProxyLibrary by lazy {
            Native.load("mirrlyengine", ProxyLibrary::class.java) as ProxyLibrary
        }
    }

    fun StartProxy(host: String, port: Int, dcIps: String, secret: String, verbose: Int): Int
    fun StartSocks5Proxy(host: String, port: Int, verbose: Int): Int
    fun StopProxy(): Int
    fun ResetNetworkSockets()
    fun SetPoolSize(size: Int)
    fun SetTcpNoDelay(enabled: Int)
    fun SetCfProxyCacheDir(cacheDir: String)
    fun SetCfProxyConfig(enabled: Int, userDomain: String)
    fun SetSecret(secret: String)
    fun SetSocks5Auth(username: String, password: String)
    fun GetSecretWithPrefix(): Pointer?
    fun GetStats(): Pointer?
    fun FreeString(p: Pointer)
}

/**
 * Kotlin bridge to the native `mirrlyengine` Rust MTProto/Cloudflare proxy core.
 * Ported from Mirrly TG Proxy (GPLv3) — https://github.com/joycecurcirt539-dot/Mirrly-TG-Proxy
 */
object NativeProxy {
    private const val TAG = "NativeProxy"

    @Volatile
    var isStarted: Boolean = false
        private set

    fun startProxy(host: String, port: Int, dcIps: String, secret: String, verbose: Int): Int {
        return try {
            val code = ProxyLibrary.INSTANCE.StartProxy(host, port, dcIps, secret, verbose)
            if (code == 0) {
                isStarted = true
            }
            code
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [startProxy]: ${t.message}", t)
            -1
        }
    }

    fun startSocks5Proxy(host: String, port: Int, verbose: Int): Int {
        return try {
            val code = ProxyLibrary.INSTANCE.StartSocks5Proxy(host, port, verbose)
            if (code == 0) {
                isStarted = true
            }
            code
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [startSocks5Proxy]: ${t.message}", t)
            -1
        }
    }

    fun stopProxy(): Int {
        if (!isStarted) {
            return 0
        }
        isStarted = false
        return try {
            ProxyLibrary.INSTANCE.StopProxy()
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [stopProxy]: ${t.message}", t)
            -1
        }
    }

    fun resetNetworkSockets() {
        if (!isStarted) return
        try {
            ProxyLibrary.INSTANCE.ResetNetworkSockets()
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [resetNetworkSockets]: ${t.message}", t)
        }
    }

    fun setPoolSize(size: Int) {
        if (!isStarted) return
        try {
            ProxyLibrary.INSTANCE.SetPoolSize(size)
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [setPoolSize]: ${t.message}", t)
        }
    }

    fun setTcpNoDelay(enabled: Boolean) {
        try {
            ProxyLibrary.INSTANCE.SetTcpNoDelay(if (enabled) 1 else 0)
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [setTcpNoDelay]: ${t.message}", t)
        }
    }

    fun setCfProxyCacheDir(cacheDir: String) {
        try {
            ProxyLibrary.INSTANCE.SetCfProxyCacheDir(cacheDir)
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [setCfProxyCacheDir]: ${t.message}", t)
        }
    }

    fun setCfProxyConfig(enabled: Boolean, userDomain: String) {
        try {
            ProxyLibrary.INSTANCE.SetCfProxyConfig(
                if (enabled) 1 else 0,
                userDomain
            )
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [setCfProxyConfig]: ${t.message}", t)
        }
    }

    fun setSecret(secret: String) {
        if (!isStarted) return
        try {
            ProxyLibrary.INSTANCE.SetSecret(secret)
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [setSecret]: ${t.message}", t)
        }
    }

    fun setSocks5Auth(username: String, password: String) {
        try {
            ProxyLibrary.INSTANCE.SetSocks5Auth(username, password)
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [setSocks5Auth]: ${t.message}", t)
        }
    }

    fun getSecretWithPrefix(): String? {
        if (!isStarted) return null
        return try {
            val ptr = ProxyLibrary.INSTANCE.GetSecretWithPrefix() ?: return null
            val res = ptr.getString(0)
            ProxyLibrary.INSTANCE.FreeString(ptr)
            res
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [getSecretWithPrefix]: ${t.message}", t)
            null
        }
    }

    fun getStats(): String? {
        if (!isStarted) return null
        return try {
            val ptr = ProxyLibrary.INSTANCE.GetStats() ?: return null
            val res = ptr.getString(0)
            ProxyLibrary.INSTANCE.FreeString(ptr)
            res
        } catch (t: Throwable) {
            Log.e(TAG, "FFI call failed [getStats]: ${t.message}", t)
            null
        }
    }
}
