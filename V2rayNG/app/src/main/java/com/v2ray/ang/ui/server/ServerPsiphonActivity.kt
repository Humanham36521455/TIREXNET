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
import com.v2ray.ang.ui.compose.FormTextField

/**
 * View/edit screen for Psiphon server entries imported from a subscription.
 *
 * The bundled core cannot run the Psiphon protocol; this screen only lets the user
 * edit metadata (remarks) and see the parsed server entry values.
 */
class ServerPsiphonActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.PSIPHON

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.PSIPHON
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
                    painterResource(R.drawable.ic_psiphon_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            FormTextField(
                stringResource(R.string.server_lab_remarks),
                uiState.remarks,
                { uiState.remarks = it }
            )
            val region = initialConfig.psiphonRegion.orEmpty()
            if (region.isNotBlank()) {
                FormTextField(
                    stringResource(R.string.dns_title_psiphon_region),
                    region,
                    {}
                )
            }
        }
    }
}