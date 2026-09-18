package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.fmt.DnsFmt
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.FormTextField

/**
 * View/edit screen for first-class DNS profiles (`dns://`).
 *
 * A DNS profile has no tunnel server. It carries the resolver addresses that are
 * handed to the platform through the VPN interface when the profile is connected.
 * Connecting requires VPN mode; proxy-only mode reports a clear error.
 */
class ServerDnsActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.DNS

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.DNS
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { dnsSaveServer(uiState) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_dns_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            FormTextField(
                stringResource(R.string.server_lab_remarks),
                uiState.remarks,
                { uiState.remarks = it }
            )
            FormTextField(
                stringResource(R.string.dns_lab_servers),
                uiState.dnsServers,
                { uiState.dnsServers = it }
            )
        }
    }

    private fun dnsSaveServer(state: ServerUiState) {
        if (state.remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return
        }
        if (!DnsFmt.isAddressList(state.dnsServers)) {
            toast(R.string.dns_lab_servers)
            return
        }
        val config = state.toProfileItem(initialConfig).apply {
            server = null
            serverPort = null
            dnsServers = DnsFmt.parseAddresses(state.dnsServers).joinToString(",")
            description = AngConfigManager.generateDescription(this)
        }
        if (config.subscriptionId.isNullOrEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        val savedGuid = MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        ProfileEditorResult.run {
            finishSaved(savedGuid, isRunning)
        }
    }
}