package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField

class ServerAmneziaActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.AMNEZIA_WG

    @Composable
    override fun ScreenContent() {
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.AMNEZIA_WG
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            WireguardProtocolFields(uiState)
        }
    }

    @Composable
    private fun WireguardProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_secret_key),
            state.secretKey,
            { state.secretKey = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_public_key),
            state.publicKey,
            { state.publicKey = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_preshared_key),
            state.preSharedKey,
            { state.preSharedKey = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_reserved),
            state.reserved,
            { state.reserved = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_local_address),
            state.localAddress,
            { state.localAddress = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_local_mtu),
            state.mtu,
            { state.mtu = it },
            keyboardType = KeyboardType.Number
        )

        FormTextField(
            stringResource(R.string.server_lab_final_mask),
            state.finalMask,
            { state.finalMask = it }
        )
        AmneziaJunkFields(state)
    }

    @Composable
    private fun AmneziaJunkFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_amnezia_jc),
            state.awgJunkPacketCount,
            { state.awgJunkPacketCount = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_jmin),
            state.awgJunkPacketMinSize,
            { state.awgJunkPacketMinSize = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_jmax),
            state.awgJunkPacketMaxSize,
            { state.awgJunkPacketMaxSize = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_s1),
            state.awgInitPacketJunkSize,
            { state.awgInitPacketJunkSize = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_s2),
            state.awgResponsePacketJunkSize,
            { state.awgResponsePacketJunkSize = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_h1),
            state.awgInitPacketMagicHeader,
            { state.awgInitPacketMagicHeader = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_h2),
            state.awgResponsePacketMagicHeader,
            { state.awgResponsePacketMagicHeader = it },
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            stringResource(R.string.server_lab_amnezia_h5),
            state.awgUnderloadPacketMagicHeader,
            { state.awgUnderloadPacketMagicHeader = it },
            keyboardType = KeyboardType.Number
        )
    }
}