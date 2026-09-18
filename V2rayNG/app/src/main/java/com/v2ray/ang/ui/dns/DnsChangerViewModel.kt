package com.v2ray.ang.ui.dns

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.DnsManager
import com.v2ray.ang.handler.DnsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DnsChangerUiState(
    val profiles: List<DnsProfile> = emptyList(),
    val activeId: String? = null,
    val editingProfile: DnsProfile? = null,
    val isEditing: Boolean = false,
    val validationError: Int? = null,
)

/**
 * Decides whether the in-dialog profile may be persisted.
 *
 * Returns the string resource describing why the profile is rejected, or null when
 * the profile is valid. A DNS profile must carry at least one pure IP resolver.
 */
internal fun dnsProfileValidationError(profile: DnsProfile?): Int? {
    if (profile == null) return null
    return if (profile.isValid) null else R.string.dns_title_no_valid_servers
}

class DnsChangerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DnsChangerUiState())
    val uiState: StateFlow<DnsChangerUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = DnsManager.getProfiles()
            val activeId = DnsManager.getActiveId()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(profiles = profiles, activeId = activeId)
                }
            }
        }
    }

    fun startNewProfile() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editingProfile = DnsProfile(
                    id = "",
                    name = "",
                    ipV4Primary = "",
                    ipV4Secondary = "",
                    ipV6Primary = "",
                    ipV6Secondary = ""
                ),
                validationError = null
            )
        }
    }

    fun editProfile(id: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(isEditing = true, editingProfile = profile, validationError = null)
        }
    }

    fun updateEditingField(
        name: String? = null,
        ipV4Primary: String? = null,
        ipV4Secondary: String? = null,
        ipV6Primary: String? = null,
        ipV6Secondary: String? = null,
    ) {
        _uiState.update {
            val current = it.editingProfile ?: return@update it
            it.copy(
                editingProfile = current.copy(
                    name = name ?: current.name,
                    ipV4Primary = ipV4Primary ?: current.ipV4Primary,
                    ipV4Secondary = ipV4Secondary ?: current.ipV4Secondary,
                    ipV6Primary = ipV6Primary ?: current.ipV6Primary,
                    ipV6Secondary = ipV6Secondary ?: current.ipV6Secondary,
                ),
                validationError = null
            )
        }
    }

    fun saveEditingProfile() {
        val profile = _uiState.value.editingProfile ?: return
        val error = dnsProfileValidationError(profile)
        if (error != null) {
            _uiState.update { it.copy(validationError = error) }
            return
        }
        _uiState.update { it.copy(validationError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            DnsManager.upsert(profile)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isEditing = false, editingProfile = null) }
                loadProfiles()
            }
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingProfile = null, validationError = null) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DnsManager.delete(id)
            loadProfiles()
        }
    }

    fun activateProfile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DnsManager.setActive(id)
            loadProfiles()
        }
    }

    fun deactivateProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            DnsManager.setActive(null)
            loadProfiles()
        }
    }
}