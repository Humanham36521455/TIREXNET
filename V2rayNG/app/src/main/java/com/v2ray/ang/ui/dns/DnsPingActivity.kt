package com.v2ray.ang.ui.dns

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsPingActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        DnsPingScreen(onBackClick = { finish() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsPingScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dnsServer by rememberSaveable { mutableStateOf("1.1.1.1, 8.8.8.8") }
    var domain by rememberSaveable { mutableStateOf("google.com") }
    var timeout by rememberSaveable { mutableStateOf("3000") }
    var resultLines by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var isLoading by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_dns_ping_checker),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormTextField(
                label = stringResource(R.string.dns_title_server),
                value = dnsServer,
                onValueChange = { dnsServer = it },
                placeholder = stringResource(R.string.dns_hint_multi_server),
                keyboardType = KeyboardType.Uri
            )
            FormTextField(
                label = stringResource(R.string.dns_title_domain),
                value = domain,
                onValueChange = { domain = it },
                keyboardType = KeyboardType.Uri
            )
            FormTextField(
                label = stringResource(R.string.dns_title_timeout),
                value = timeout,
                onValueChange = { timeout = it },
                keyboardType = KeyboardType.Number
            )
            val latencyTitle = stringResource(R.string.dns_title_latency)
            val noResultTitle = stringResource(R.string.dns_title_no_result)
            val servers = parseDnsServerList(dnsServer)
            Button(
                onClick = {
                    isLoading = true
                    resultLines = arrayListOf()
                    val host = domain.trim().ifEmpty { "google.com" }
                    val timeoutMs = timeout.trim().toIntOrNull() ?: 3000
                    scope.launch {
                        val results = withContext(Dispatchers.IO) {
                            servers.map { server ->
                                server to dnsPingUdp53(server, host, timeoutMs)
                            }
                        }
                        resultLines = ArrayList(
                            results.map { (server, ms) ->
                                val value = ms?.let { latencyTitle.format(it.toString()) }
                                    ?: noResultTitle
                                "$server  $value"
                            }
                        )
                        isLoading = false
                    }
                },
                enabled = !isLoading && servers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dns_title_ping))
            }

            if (resultLines.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    resultLines.forEach { line ->
                        Text(line)
                    }
                }
            }

            NavigationBarsSpacer()
        }
    }
}

/**
 * Splits a user-entered DNS server field into a deduplicated, non-empty list.
 * Servers may be separated by commas, semicolons, or newlines.
 */
internal fun parseDnsServerList(input: String): List<String> =
    input.split(',', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Sends a minimal UDP DNS A-record query and measures round-trip time in milliseconds.
 *
 * @return Latency in ms, or null on timeout/failure.
 */
private fun dnsPingUdp53(dnsServer: String, domain: String, timeoutMs: Int): Long? {
    val socket = DatagramSocket()
    socket.soTimeout = timeoutMs
    return try {
        val host = InetAddress.getByName(dnsServer)
        // Minimal DNS A query: 12-byte header + 1 query
        val txId = (Math.random() * 65535).toInt() and 0xFFFF
        val queryBytes = buildDnsQuery(txId, domain)
        val packet = DatagramPacket(queryBytes, queryBytes.size, host, 53)
        val start = System.currentTimeMillis()
        socket.send(packet)
        val buf = ByteArray(512)
        val response = DatagramPacket(buf, buf.size)
        socket.receive(response)
        System.currentTimeMillis() - start
    } catch (_: Exception) {
        null
    } finally {
        socket.close()
    }
}

/**
 * Builds a minimal DNS query for an A record (type 1) of [domain].
 */
private fun buildDnsQuery(txId: Int, domain: String): ByteArray {
    val baos = java.io.ByteArrayOutputStream()
    val dos = java.io.DataOutputStream(baos)
    // Header
    dos.writeShort(txId)
    dos.writeShort(0x0100) // Standard query, recursion desired
    dos.writeShort(1)     // QDCOUNT = 1
    dos.writeShort(0)     // ANCOUNT
    dos.writeShort(0)     // NSCOUNT
    dos.writeShort(0)     // ARCOUNT
    // Question section
    domain.split(".").filter { it.isNotEmpty() }.forEach { label ->
        dos.writeByte(label.length)
        dos.writeBytes(label)
    }
    dos.writeByte(0) // root label terminator
    dos.writeShort(1)  // QTYPE = A
    dos.writeShort(1)  // QCLASS = IN
    dos.flush()
    return baos.toByteArray()
}