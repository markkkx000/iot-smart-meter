package com.sabado.kuryentrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val brokerIp: String = "",
    val brokerPort: String = "",
    val apiIp: String = "",
    val apiPort: String = "",
    val pricePerKwh: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository // Implement repository to save/load DataStore or SharedPreferences
) : ViewModel() {

    private val _saveSuccessMessage = MutableStateFlow<String?>(null)
    val saveSuccessMessage: StateFlow<String?> = _saveSuccessMessage.asStateFlow()


    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val settings = settingsRepository.getSettings() // suspend function returning data class or map

            _uiState.update {
                it.copy(
                    brokerIp = settings.brokerIp,
                    brokerPort = settings.brokerPort,
                    apiIp = settings.apiIp,
                    apiPort = settings.apiPort,
                    pricePerKwh = settings.pricePerKwh.toString(),
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    fun onBrokerIpChanged(newIp: String) {
        _uiState.update { it.copy(brokerIp = newIp) }
    }

    fun onBrokerPortChanged(newPort: String) {
        _uiState.update { it.copy(brokerPort = newPort) }
    }

    fun onApiIpChanged(newIp: String) {
        _uiState.update { it.copy(apiIp = newIp) }
    }

    fun onApiPortChanged(newPort: String) {
        _uiState.update { it.copy(apiPort = newPort) }
    }

    fun onPricePerKwhChanged(newPrice: String) {
        _uiState.update { it.copy(pricePerKwh = newPrice) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val priceFloat = _uiState.value.pricePerKwh.toFloatOrNull() ?: 10f
                settingsRepository.saveSettings(
                    brokerIp = _uiState.value.brokerIp,
                    brokerPort = _uiState.value.brokerPort,
                    apiIp = _uiState.value.apiIp,
                    apiPort = _uiState.value.apiPort,
                    pricePerKwh = priceFloat
                )
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                _saveSuccessMessage.value = "Settings saved successfully"
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun clearSaveSuccessMessage() {
        _saveSuccessMessage.value = null
    }
}
