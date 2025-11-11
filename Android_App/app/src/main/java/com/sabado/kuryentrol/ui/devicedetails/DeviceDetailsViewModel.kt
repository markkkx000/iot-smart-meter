package com.sabado.kuryentrol.ui.devicedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.DeviceRepository
import com.sabado.kuryentrol.data.Result
import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceDetailsViewModel(
    private val clientId: String,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _energyReadings = MutableStateFlow<List<EnergyReading>>(emptyList())
    val energyReadings: StateFlow<List<EnergyReading>> = _energyReadings.asStateFlow()

    private val _threshold = MutableStateFlow<Threshold?>(null)
    val threshold: StateFlow<Threshold?> = _threshold.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedEnergyPeriod = MutableStateFlow("day") // Default to daily
    val selectedEnergyPeriod: StateFlow<String> = _selectedEnergyPeriod.asStateFlow()

    // User defined price per kWh for bill computation
    private val _pricePerKwh = MutableStateFlow(0.20f) // Example default price
    val pricePerKwh: StateFlow<Float> = _pricePerKwh.asStateFlow()

    // Computed total energy consumption for the current period
    val totalEnergyConsumption: StateFlow<Float> = combine(
        energyReadings,
        selectedEnergyPeriod
    ) { readings, period ->
        if (readings.isEmpty()) 0f
        else {
            readings.last().energyKwh - (readings.firstOrNull()?.energyKwh ?: 0f)
        }
    }.asStateFlow()

    val estimatedEnergyBill: StateFlow<Float> = combine(
        totalEnergyConsumption,
        pricePerKwh
    ) { consumption, price ->
        consumption * price
    }.asStateFlow()

    // State for Schedule Dialog
    private val _showScheduleDialog = MutableStateFlow(false)
    val showScheduleDialog: StateFlow<Boolean> = _showScheduleDialog.asStateFlow()

    private val _editingSchedule = MutableStateFlow<Schedule?>(null)
    val editingSchedule: StateFlow<Schedule?> = _editingSchedule.asStateFlow()

    private val _newScheduleType = MutableStateFlow("daily") // "daily" or "timer"
    val newScheduleType: StateFlow<String> = _newScheduleType.asStateFlow()

    private val _newStartTime = MutableStateFlow("00:00")
    val newStartTime: StateFlow<String> = _newStartTime.asStateFlow()

    private val _newEndTime = MutableStateFlow("00:00")
    val newEndTime: StateFlow<String> = _newEndTime.asStateFlow()

    private val _newDurationSeconds = MutableStateFlow("3600") // Default to 1 hour for timer
    val newDurationSeconds: StateFlow<String> = _newDurationSeconds.asStateFlow()

    private val _newDaysOfWeek = MutableStateFlow<Set<Int>>(emptySet()) // 0=Mon, 6=Sun
    val newDaysOfWeek: StateFlow<Set<Int>> = _newDaysOfWeek.asStateFlow()

    // State for Threshold Dialog
    private val _showThresholdDialog = MutableStateFlow(false)
    val showThresholdDialog: StateFlow<Boolean> = _showThresholdDialog.asStateFlow()

    private val _newThresholdLimit = MutableStateFlow("")
    val newThresholdLimit: StateFlow<String> = _newThresholdLimit.asStateFlow()

    private val _newResetPeriod = MutableStateFlow("daily") // "daily", "weekly", "monthly"
    val newResetPeriod: StateFlow<String> = _newResetPeriod.asStateFlow()

    init {
        loadDeviceData()
        loadEnergyReadingsByPeriod("day") // Initial load for daily
    }

    fun loadDeviceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // Load schedules
            when (val result = deviceRepository.getSchedulesForDevice(clientId)) {
                is Result.Success -> _schedules.value = result.data
                is Result.Error -> _errorMessage.value = result.exception.message
            }

            // Load threshold
            when (val result = deviceRepository.getThreshold(clientId)) {
                is Result.Success -> _threshold.value = result.data
                is Result.Error -> _errorMessage.value = result.exception.message
            }

            _isLoading.value = false
        }
    }

    fun loadEnergyReadingsByPeriod(period: String) {
        _selectedEnergyPeriod.value = period
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = deviceRepository.getEnergyReadings(clientId, period)) {
                is Result.Success -> _energyReadings.value = result.data
                is Result.Error -> _errorMessage.value = result.exception.message
            }
            _isLoading.value = false
        }
    }

    fun setPricePerKwh(price: Float) {
        _pricePerKwh.value = price
    }

    // Schedule Dialog functions
    fun showAddScheduleDialog() {
        _editingSchedule.value = null // Clear any editing state
        _newScheduleType.value = "daily"
        _newStartTime.value = "00:00"
        _newEndTime.value = "00:00"
        _newDurationSeconds.value = "3600"
        _newDaysOfWeek.value = emptySet()
        _showScheduleDialog.value = true
    }

    fun showEditScheduleDialog(schedule: Schedule) {
        _editingSchedule.value = schedule
        _newScheduleType.value = schedule.scheduleType
        _newStartTime.value = schedule.startTime ?: "00:00"
        _newEndTime.value = schedule.endTime ?: "00:00"
        _newDurationSeconds.value = "3600" // API doesn't return duration, so default
        _newDaysOfWeek.value = schedule.daysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        _showScheduleDialog.value = true
    }

    fun dismissScheduleDialog() {
        _showScheduleDialog.value = false
    }

    fun setNewScheduleType(type: String) {
        _newScheduleType.value = type
    }

    fun setNewStartTime(time: String) {
        _newStartTime.value = time
    }

    fun setNewEndTime(time: String) {
        _newEndTime.value = time
    }

    fun setNewDurationSeconds(duration: String) {
        _newDurationSeconds.value = duration
    }

    fun toggleDayOfWeek(day: Int) {
        _newDaysOfWeek.update { currentDays ->
            if (currentDays.contains(day)) currentDays - day else currentDays + day
        }
    }

    fun saveSchedule() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val scheduleToSave = _editingSchedule.value?.copy(
                scheduleType = _newScheduleType.value,
                startTime = if (_newScheduleType.value == "daily") _newStartTime.value else null,
                endTime = if (_newScheduleType.value == "daily") _newEndTime.value else null,
                daysOfWeek = if (_newScheduleType.value == "daily" && _newDaysOfWeek.value.isNotEmpty()) _newDaysOfWeek.value.sorted().joinToString(",") else null,
                clientId = clientId
            ) ?: Schedule(
                id = 0, // Placeholder, API assigns ID
                clientId = clientId,
                scheduleType = _newScheduleType.value,
                startTime = if (_newScheduleType.value == "daily") _newStartTime.value else null,
                endTime = if (_newScheduleType.value == "daily") _newEndTime.value else null,
                daysOfWeek = if (_newScheduleType.value == "daily" && _newDaysOfWeek.value.isNotEmpty()) _newDaysOfWeek.value.sorted().joinToString(",") else null,
                enabled = 1, // Default to enabled
                createdAt = ""
            )

            val result = if (_editingSchedule.value == null) {
                deviceRepository.createSchedule(scheduleToSave)
            } else {
                deviceRepository.updateSchedule(_editingSchedule.value!!.id, scheduleToSave)
            }

            when (result) {
                is Result.Success -> {
                    dismissScheduleDialog()
                    loadDeviceData() // Refresh schedules after saving
                }
                is Result.Error -> _errorMessage.value = result.exception.message
            }
            _isLoading.value = false
        }
    }

    fun deleteSchedule(scheduleId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = deviceRepository.deleteSchedule(scheduleId)) {
                is Result.Success -> {
                    loadDeviceData() // Refresh schedules after deleting
                }
                is Result.Error -> _errorMessage.value = result.exception.message
            }
            _isLoading.value = false
        }
    }

    // Threshold Dialog functions
    fun showSetThresholdDialog() {
        _threshold.value?.let { currentThreshold ->
            _newThresholdLimit.value = currentThreshold.limitKwh.toString()
            _newResetPeriod.value = currentThreshold.resetPeriod
        } ?: run {
            _newThresholdLimit.value = ""
            _newResetPeriod.value = "daily"
        }
        _showThresholdDialog.value = true
    }

    fun dismissThresholdDialog() {
        _showThresholdDialog.value = false
    }

    fun setNewThresholdLimit(limit: String) {
        _newThresholdLimit.value = limit
    }

    fun setNewResetPeriod(period: String) {
        _newResetPeriod.value = period
    }

    fun saveThreshold() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val limit = _newThresholdLimit.value.toFloatOrNull()
            if (limit == null) {
                _errorMessage.value = "Invalid threshold limit"
                _isLoading.value = false
                return@launch
            }

            val thresholdToSave = Threshold(
                id = _threshold.value?.id ?: 0, // API assigns ID if new, otherwise use existing
                clientId = clientId,
                limitKwh = limit,
                resetPeriod = _newResetPeriod.value,
                enabled = 1, // Always enabled when setting
                lastReset = "", // Server handles this
                createdAt = "" // Server handles this
            )

            when (val result = deviceRepository.setThreshold(clientId, thresholdToSave)) {
                is Result.Success -> {
                    dismissThresholdDialog()
                    loadDeviceData() // Refresh threshold after saving
                }
                is Result.Error -> _errorMessage.value = result.exception.message
            }
            _isLoading.value = false
        }
    }

    fun deleteThreshold() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = deviceRepository.deleteThreshold(clientId)) {
                is Result.Success -> {
                    loadDeviceData() // Refresh threshold after deleting
                }
                is Result.Error -> _errorMessage.value = result.exception.message
            }
            _isLoading.value = false
        }
    }
}
