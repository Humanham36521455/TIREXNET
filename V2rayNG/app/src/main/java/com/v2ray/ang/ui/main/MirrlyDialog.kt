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
import androidx.compose.ui.unit.dp
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
        title = { Text(text = "Mirrly TG Proxy") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("آدرس (مثال: 1.2.3.4)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("پورت") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("سکرت (32 حرف، اختیاری)") },
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
                        Toast.makeText(ctx, "آدرس یا پورت نامعتبر است", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isConnecting = true
                    onConnectStarted?.invoke()

                    // اجرا در کوروتین ای‌او برای جلوگیری از بلاک UI
                    coroutineScope.launch {
                        val code = withContext(Dispatchers.IO) {
                            try {
                                // dcIps را خالی ارسال می‌کنیم (می‌توانید مقدار پیش‌فرض یا از تنظیمات بگیرید)
                                NativeProxy.startProxy(address, port, "", secret, 0)
                            } catch (t: Throwable) {
                                -1
                            }
                        }
                        isConnecting = false
                        if (code == 0) {
                            Toast.makeText(ctx, "اتصال شروع شد", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(ctx, "اتصال ناموفق (کد: $code)", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isConnecting
            ) {
                Text(if (isConnecting) "درحال اتصال…" else "اتصال")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isConnecting) onDismiss() }, enabled = !isConnecting) {
                Text("بستن")
            }
        }
    )
}
