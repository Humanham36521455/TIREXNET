package com.v2ray.ang.ui.dns

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.verticalScrollbar

class DnsChangerActivity : BaseComponentActivity() {
    private val viewModel: DnsChangerViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        DnsChangerScreen(
            viewModel = viewModel,
            onBackClick = { finish() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsChangerScreen(
    viewModel: DnsChangerViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    if (uiState.isEditing) {
        DnsEditDialog(
            profile = uiState.editingProfile,
            validationError = uiState.validationError?.let { stringResource(it) },
            onDismiss = { viewModel.cancelEditing() },
            onConfirm = { viewModel.saveEditingProfile() },
            onNameChange = { viewModel.updateEditingField(name = it) },
            onV4PrimaryChange = { viewModel.updateEditingField(ipV4Primary = it) },
            onV4SecondaryChange = { viewModel.updateEditingField(ipV4Secondary = it) },
            onV6PrimaryChange = { viewModel.updateEditingField(ipV6Primary = it) },
            onV6SecondaryChange = { viewModel.updateEditingField(ipV6Secondary = it) }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_dns_changer),
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { viewModel.startNewProfile() }) {
                        Icon(
                            painterResource(R.drawable.ic_add_24dp),
                            contentDescription = null
                        )
                    }
                }
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
            if (uiState.activeId != null) {
                val active = uiState.profiles.firstOrNull { it.id == uiState.activeId }
                Text(
                    text = stringResource(R.string.dns_title_profile_activated) +
                        (active?.let { ": ${it.name.ifBlank { it.ipV4Primary }}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.dns_title_no_profiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.profiles.forEach { profile ->
                val isActive = profile.id == uiState.activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.editProfile(profile.id) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name.ifBlank { profile.ipV4Primary },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = buildString {
                                append(profile.ipV4Primary)
                                if (profile.ipV4Secondary.isNotBlank()) append(", ").append(profile.ipV4Secondary)
                                if (profile.ipV6Primary.isNotBlank()) append(" | ").append(profile.ipV6Primary)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            if (isActive) viewModel.deactivateProfile() else viewModel.activateProfile(profile.id)
                        }) {
                            Icon(
                                painterResource(if (isActive) R.drawable.ic_restore_24dp else R.drawable.ic_restore_24dp),
                                contentDescription = stringResource(
                                    if (isActive) R.string.dns_title_deactivate else R.string.dns_title_activate
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { viewModel.deleteProfile(profile.id) }) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                contentDescription = stringResource(R.string.dns_title_delete_profile)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun DnsEditDialog(
    profile: com.v2ray.ang.handler.DnsProfile?,
    validationError: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onNameChange: (String) -> Unit,
    onV4PrimaryChange: (String) -> Unit,
    onV4SecondaryChange: (String) -> Unit,
    onV6PrimaryChange: (String) -> Unit,
    onV6SecondaryChange: (String) -> Unit,
) {
    if (profile == null) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dns_title_save_profile))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.title_dns_changer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    label = stringResource(R.string.dns_title_name),
                    value = profile.name,
                    onValueChange = onNameChange
                )
                FormTextField(
                    label = stringResource(R.string.dns_title_ipv4_primary),
                    value = profile.ipV4Primary,
                    onValueChange = onV4PrimaryChange,
                    keyboardType = KeyboardType.Uri
                )
                FormTextField(
                    label = stringResource(R.string.dns_title_ipv4_secondary),
                    value = profile.ipV4Secondary,
                    onValueChange = onV4SecondaryChange,
                    keyboardType = KeyboardType.Uri
                )
                FormTextField(
                    label = stringResource(R.string.dns_title_ipv6_primary),
                    value = profile.ipV6Primary,
                    onValueChange = onV6PrimaryChange,
                    keyboardType = KeyboardType.Uri
                )
                FormTextField(
                    label = stringResource(R.string.dns_title_ipv6_secondary),
                    value = profile.ipV6Secondary,
                    onValueChange = onV6SecondaryChange,
                    keyboardType = KeyboardType.Uri
                )
                if (validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}