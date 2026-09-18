package com.v2ray.ang.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

object LauncherManager {

    fun startServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
            return false
        }
        return true
    }

    fun startService(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
        }
    }

    fun stopService(context: Context) {
        //context.toast(R.string.toast_services_stop)
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /** Restarts the active daemon without starting a stopped service. */
    fun restartService(context: Context) {
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_RESTART, "")
    }

    /** Restarts the active daemon, or delegates to the caller's permission-aware start flow. */
    fun restartServiceOrStart(context: Context, startIfStopped: () -> Unit) {
        MessageHelper.sendMsg2ServiceForResult(context, AppConfig.MSG_STATE_RESTART, "") { handled ->
            if (!handled) startIfStopped()
        }
    }

    @Throws(Exception::class)
    private fun startContextService(context: Context) {
        // Note: isRunning check is removed here to avoid loading Native libraries in the UI process.
        // The check is performed in CoreServiceManager when the service starts in the daemon process.

        val guid = MmkvManager.getSelectServer()
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        // Engine capability gate: a supported profile whose runtime engine is not
        // bundled must report the exact missing dependency, never a misleading
        // "invalid config" from the later address-validity check.
        val capability = com.v2ray.ang.engine.EngineRegistry.capabilityFor(config)
        when {
            capability.engine == com.v2ray.ang.engine.EngineId.SLIPNET -> {
                LogUtil.e(AppConfig.TAG, "LauncherManager: SlipNet transports are not supported by the bundled core")
                error(context.getString(R.string.toast_slipnet_unsupported))
            }

            capability.engine == com.v2ray.ang.engine.EngineId.PSIPHON -> {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Psiphon protocol is not supported by the bundled core")
                error(context.getString(R.string.toast_psiphon_unsupported))
            }

            capability.engine == com.v2ray.ang.engine.EngineId.AMNEZIA_WG && config.hasAmneziaObfuscation -> {
                LogUtil.e(AppConfig.TAG, "LauncherManager: AmneziaWG obfuscation is not supported by the bundled core")
                error(context.getString(R.string.toast_amnezia_unsupported))
            }
        }

        if (!config.configType.isComplexType()
            && config.configType != EConfigType.DNS
            && config.configType != EConfigType.SLIPNET
            && config.configType != EConfigType.PSIPHON
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        if (config.configType == EConfigType.DNS && !SettingsManager.isVpnMode()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: DNS profiles require VPN mode")
            error(context.getString(R.string.toast_dns_requires_vpn))
        }

        SettingsManager.refreshRuntimeSocksPort()

        // DNS profiles apply their resolvers to the tun through the DNS Changer
        // active-servers slot, so CoreVpnService.configureNetworkSettings() hands
        // them to the platform resolver alongside the runtime DNS block.
        if (config.configType == EConfigType.DNS) {
            val servers = (config.dnsServers ?: "")
                .split(',')
                .map { it.trim() }
                .filter { Utils.isPureIpAddress(it) }
            if (servers.isNotEmpty()) {
                val dnsProfile = com.v2ray.ang.handler.DnsManager.getProfiles()
                    .firstOrNull { it.name == config.remarks }
                    ?: com.v2ray.ang.handler.DnsProfile(
                        id = "",
                        name = config.remarks.orEmpty().ifBlank { "DNS profile" },
                        ipV4Primary = servers.getOrNull(0).orEmpty()
                    )
                com.v2ray.ang.handler.DnsManager.setActive(
                    com.v2ray.ang.handler.DnsManager.upsert(
                        dnsProfile.copy(
                            ipV4Primary = servers.getOrNull(0).orEmpty(),
                            ipV4Secondary = servers.getOrNull(1).orEmpty(),
                            ipV6Primary = servers.getOrNull(2).orEmpty(),
                            ipV6Secondary = servers.getOrNull(3).orEmpty()
                        )
                    )
                )
            }
        }

        // Track the launch attempt in the engine state machine. The capability gate
        // above guarantees this profile can actually run.
        com.v2ray.ang.engine.ConnectionManager.onLaunchAttempt(config)

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.toastError(R.string.toast_allow_insecure_deprecated)
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }

        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        val intent = if (isRootMode) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Root service")
            Intent(context.applicationContext, CoreRootService::class.java)
        } else if (SettingsManager.isVpnMode()) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }
}
