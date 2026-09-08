package com.v2ray.ang.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.mtproto.NativeProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MirrlyDialog(
    initialAddress: String = "",
    initialPort: Int? = null,
    initialSecret: String = "",
    onDismiss: () -> Unit,
    onConnectStarted: (() -> Unit)? = null,
    coroutineScope: CoroutineScope
) {
    val ctx = LocalContext.current
    var address by remember { mutableStateOf(initialAddress) }
    var portText by remember { mutableStateOf(initialPort?.toString() ?: "") }
    var secret by remember { mutableStateOf(initialSecret) }
    var isConnecting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text(text = stringResource(R.string.title_mirrly_dialog)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.mirrly_label_address)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.mirrly_label_port)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(R.string.mirrly_label_secret)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull()
                    if (address.isBlank() || port == null || port <= 0) {
                        Toast.makeText(ctx, stringResource(R.string.mirrly_toast_invalid_addr_port), Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isConnecting = true
                    onConnectStarted?.invoke()

                    // Capture strings in composable context before launching coroutine
                    val successMsg = stringResource(R.string.mirrly_toast_connect_started)
                    val failedMsgPrefix = stringResource(R.string.mirrly_toast_connect_failed)

                    // execute in coroutine IO to avoid blocking UI
                    coroutineScope.launch {
                        val code = withContext(Dispatchers.IO) {
                            try {
                                NativeProxy.startProxy(address, port, "", secret, 0)
                            } catch (t: Throwable) {
                                -1
                            }
                        }
                        isConnecting = false
                        if (code == 0) {
                            Toast.makeText(ctx, successMsg, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(ctx, "$failedMsgPrefix $code", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isConnecting
            ) {
                Text(if (isConnecting) stringResource(R.string.mirrly_button_connecting) else stringResource(R.string.mirrly_button_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isConnecting) onDismiss() }, enabled = !isConnecting) {
                Text(stringResource(R.string.mirrly_button_close))
            }
        }
    )
}
