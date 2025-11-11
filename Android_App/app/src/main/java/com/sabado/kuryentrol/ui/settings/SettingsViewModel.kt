package com.sabado.kuryentrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _brokerUrl = MutableStateFlow(SettingsRepository.DEFAULT_BROKER_URL)
    val brokerUrl: StateFlow<String> = _brokerUrl.asStateFlow()

    private val _apiUrl = MutableStateFlow(SettingsRepository.DEFAULT_API_URL)
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _brokerUrl.value = settingsRepository.brokerUrl.first()
            _apiUrl.value = settingsRepository.apiUrl.first()
            _isLoading.value = false
        }
    }

    fun updateBrokerUrl(url: String) {
        _brokerUrl.value = url
    }

    fun updateApiUrl(url: String) {
        _apiUrl.value = url
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveBrokerUrl(_brokerUrl.value)
            settingsRepository.saveApiUrl(_apiUrl.value)
            _saveSuccess.value = true
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
}
