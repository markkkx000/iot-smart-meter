package com.sabado.kuryentrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val mqttBrokerAddress = settingsRepository.mqttBrokerAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val restApiUrl = settingsRepository.restApiUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun saveMqttBrokerAddress(address: String) {
        viewModelScope.launch {
            settingsRepository.saveMqttBrokerAddress(address)
        }
    }

    fun saveRestApiUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.saveRestApiUrl(url)
        }
    }
}
