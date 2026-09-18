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
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField

/**
 * View/edit screen for SlipNet server entries imported from a `slipnet://` link.
 *
 * The bundled Xray core has no SlipNet transport engine (DNSTT, NoizDNS, VayDNS,
 * Slipstream, NaiveProxy, or SSH tunneling), so these entries cannot establish a
 * tunnel. This screen lets the user edit metadata and inspect the parsed fields.
 */
class ServerSlipNetActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SLIPNET

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.SLIPNET
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_slipnet_24dp),
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
                stringResource(R.string.server_lab_address),
                uiState.address,
                { uiState.address = it },
            )
            FormTextField(
                stringResource(R.string.server_lab_port),
                uiState.port,
                { uiState.port = it },
                keyboardType = KeyboardType.Number
            )

            readonlyRow(
                R.string.slipnet_title_tunnel_type,
                initialConfig.slipNetTunnelType.orEmpty()
            )
            readonlyRow(
                R.string.slipnet_title_resolvers,
                initialConfig.slipNetResolvers.orEmpty()
            )
            readonlyRow(
                R.string.slipnet_title_public_key,
                initialConfig.slipNetPublicKey.orEmpty()
            )
            readonlyRow(
                R.string.slipnet_title_config,
                initialConfig.slipNetConfig.orEmpty(),
                maxLines = 3
            )
        }
    }

    @Composable
    private fun readonlyRow(titleRes: Int, value: String, maxLines: Int = 1) {
        if (value.isNotBlank()) {
            FormTextField(
                stringResource(titleRes),
                value,
                {},
                maxLines = maxLines
            )
        }
    }
}