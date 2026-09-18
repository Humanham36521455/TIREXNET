package com.v2ray.ang.testutil

import com.tencent.mmkv.MMKV
import com.v2ray.ang.handler.MmkvManager
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

/**
 * Shared MMKV mocks for JVM unit tests.
 *
 * MmkvManager's storage handles are `by lazy { MMKV.mmkvWithID(...) }` bindings that
 * the test JVM resolves exactly once. Every MMKV-mocking test class must therefore
 * hand back the same mock instance for a file id, otherwise the handles bound by the
 * first-running class are never refitted to later classes' mocks.
 */
object MmkvTestRuntime {

    private const val ID_MAIN = "MAIN"
    private const val ID_SUB = "SUB"
    private const val ID_SETTING = "SETTING"
    private const val ID_PROFILE_FULL = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_ASSET = "ASSET"

    private val instances = mutableMapOf<String, MMKV>()
    val store = mutableMapOf<String, String>()

    private fun instance(fileId: String): MMKV = instances.getOrPut(fileId) { mock() }

    fun handle(fileId: String): MMKV = instance(fileId)

    fun key(fileId: String, key: String): String = "$fileId\u0000$key"

    fun getKeys(fileId: String): List<String> =
        store.keys.filter { it.startsWith("$fileId\u0000") }
            .map { it.removePrefix("$fileId\u0000") }

    /** Binds every relevant MMKV file id to a shared in-memory stateless mock. */
    fun bindAll() {
        MmkvTestRuntime.store.clear()
        Mockito.mockStatic(MMKV::class.java).use {
            listOf(ID_MAIN, ID_SUB, ID_SETTING, ID_PROFILE_FULL, ID_SERVER_RAW, ID_ASSET).forEach { fileId ->
                it.`when`<MMKV> { MMKV.mmkvWithID(fileId, MMKV.MULTI_PROCESS_MODE) }
                    .thenReturn(instance(fileId))
            }
            MmkvManager.decodeSettingsString("mmkv-test-runtime-init")
            MmkvManager.decodeSubscriptions()
            MmkvManager.decodeSubsList()
        }
    }

    /** Points every bound handle at [store], clearing prior stubbings first. */
    fun bindStoreBehavior() {
        MmkvTestRuntime.store.clear()
        instances.values.forEach { reset(it) }
        instances.forEach { (fileId, mmkv) ->
            whenever(mmkv.decodeString(any())).thenAnswer {
                store[key(fileId, it.getArgument<String>(0))]
            }
            whenever(mmkv.decodeString(any(), any())).thenAnswer {
                store[key(fileId, it.getArgument<String>(0))] ?: it.getArgument(1)
            }
            whenever(mmkv.decodeInt(any(), Mockito.anyInt())).thenAnswer {
                store[key(fileId, it.getArgument<String>(0))]?.toIntOrNull() ?: it.getArgument<Int>(1)
            }
            whenever(mmkv.decodeBool(any(), Mockito.anyBoolean())).thenAnswer {
                store[key(fileId, it.getArgument<String>(0))]?.let(String::toBooleanStrictOrNull)
                    ?: it.getArgument<Boolean>(1)
            }
            whenever(mmkv.encode(any<String>(), any<String>())).thenAnswer {
                store[key(fileId, it.getArgument(0))] = it.getArgument(1)
                true
            }
            whenever(mmkv.encode(any<String>(), Mockito.anyInt())).thenAnswer {
                store[key(fileId, it.getArgument(0))] = it.getArgument<Int>(1).toString()
                true
            }
            whenever(mmkv.encode(any<String>(), Mockito.anyBoolean())).thenAnswer {
                store[key(fileId, it.getArgument(0))] = it.getArgument<Boolean>(1).toString()
                true
            }
            whenever(mmkv.allKeys()).thenAnswer { getKeys(fileId).toTypedArray() }
        }
    }
}