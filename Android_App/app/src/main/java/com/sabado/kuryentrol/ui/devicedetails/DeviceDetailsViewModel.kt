package com.sabado.kuryentrol.ui.devicedetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import com.sabado.kuryentrol.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for device details screen
 */
@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _energyReadings = MutableStateFlow<List<EnergyReading>>(emptyList())
    val energyReadings: StateFlow<List<EnergyReading>> = _energyReadings.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _threshold = MutableStateFlow<Threshold?>(null)
    val threshold: StateFlow<Threshold?> = _threshold.asStateFlow()

    init {
        loadDeviceData()
    }

    fun loadDeviceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // Load energy readings
            deviceRepository.getEnergyReadings(clientId, limit = 100).fold(
                onSuccess = { readings ->
                    _energyReadings.value = readings.reversed() // Chronological order
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load energy readings: ${error.message}"
                }
            )

            // Load schedules
            deviceRepository.getSchedules(clientId).fold(
                onSuccess = { schedules ->
                    _schedules.value = schedules
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load schedules: ${error.message}"
                }
            )

            // Load threshold
            deviceRepository.getThreshold(clientId).fold(
                onSuccess = { threshold ->
                    _threshold.value = threshold
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load threshold: ${error.message}"
                }
            )

            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getClientId(): String = clientId
}
