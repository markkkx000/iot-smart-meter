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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class GraphDataPoint(
    val label: String,
    val value: Float
)

enum class TimePeriod {
    DAILY, WEEKLY, MONTHLY
}

/**
 * ViewModel for device details screen with graph data processing
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

    private val _allReadings = MutableStateFlow<List<EnergyReading>>(emptyList())
    private val _selectedPeriod = MutableStateFlow(TimePeriod.DAILY)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _threshold = MutableStateFlow<Threshold?>(null)
    val threshold: StateFlow<Threshold?> = _threshold.asStateFlow()

    // Price per kWh for bill calculation (make this configurable in settings later)
    private val _pricePerKwh = MutableStateFlow(0.12f) // Default $0.12 per kWh
    val pricePerKwh: StateFlow<Float> = _pricePerKwh.asStateFlow()

    // Combined graph data based on readings and selected period
    val graphData: StateFlow<List<GraphDataPoint>> = combine(
        _allReadings,
        _selectedPeriod
    ) { readings, period ->
        processReadingsForGraph(readings, period)
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Total consumption for selected period
    val totalConsumption: StateFlow<Float> = combine(
        _allReadings,
        _selectedPeriod
    ) { readings, period ->
        calculateTotalConsumption(readings, period)
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    // Energy bill for selected period
    val energyBill: StateFlow<Float> = combine(
        totalConsumption,
        pricePerKwh
    ) { consumption, price ->
        consumption * price
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        loadDeviceData()
    }

    fun loadDeviceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // Load energy readings
            deviceRepository.getEnergyReadings(clientId, limit = 500).fold(
                onSuccess = { readings ->
                    _allReadings.value = readings.reversed() // Chronological order (oldest first)
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

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    private fun processReadingsForGraph(
        readings: List<EnergyReading>,
        period: TimePeriod
    ): List<GraphDataPoint> {
        if (readings.isEmpty()) return emptyList()

        // Filter readings based on period
        val now = LocalDateTime.now()
        val filteredReadings = readings.filter { reading ->
            try {
                val timestamp = LocalDateTime.parse(reading.timestamp, dateFormatter)
                when (period) {
                    TimePeriod.DAILY -> timestamp.isAfter(now.minusDays(1))
                    TimePeriod.WEEKLY -> timestamp.isAfter(now.minusWeeks(1))
                    TimePeriod.MONTHLY -> timestamp.isAfter(now.minusMonths(1))
                }
            } catch (e: Exception) {
                false
            }
        }

        if (filteredReadings.isEmpty()) return emptyList()

        // Group readings and calculate consumption deltas
        return when (period) {
            TimePeriod.DAILY -> groupByHour(filteredReadings)
            TimePeriod.WEEKLY -> groupByDay(filteredReadings)
            TimePeriod.MONTHLY -> groupByDay(filteredReadings)
        }
    }

    private fun groupByHour(readings: List<EnergyReading>): List<GraphDataPoint> {
        val grouped = readings.groupBy { reading ->
            try {
                val timestamp = LocalDateTime.parse(reading.timestamp, dateFormatter)
                timestamp.hour
            } catch (e: Exception) {
                -1
            }
        }.filter { it.key != -1 }

        return grouped.mapNotNull { (hour, hourReadings) ->
            if (hourReadings.size < 2) return@mapNotNull null
            val first = hourReadings.first()
            val last = hourReadings.last()
            val consumption = last.energy_kwh - first.energy_kwh
            GraphDataPoint(
                label = "${hour}h",
                value = maxOf(consumption, 0f)
            )
        }.sortedBy { it.label }
    }

    private fun groupByDay(readings: List<EnergyReading>): List<GraphDataPoint> {
        val grouped = readings.groupBy { reading ->
            try {
                val timestamp = LocalDateTime.parse(reading.timestamp, dateFormatter)
                timestamp.toLocalDate()
            } catch (e: Exception) {
                null
            }
        }.filter { it.key != null }

        return grouped.mapNotNull { (date, dayReadings) ->
            if (dayReadings.size < 2 || date == null) return@mapNotNull null
            val first = dayReadings.first()
            val last = dayReadings.last()
            val consumption = last.energy_kwh - first.energy_kwh
            GraphDataPoint(
                label = "${date.monthValue}/${date.dayOfMonth}",
                value = maxOf(consumption, 0f)
            )
        }.sortedBy { it.label }
    }

    private fun calculateTotalConsumption(
        readings: List<EnergyReading>,
        period: TimePeriod
    ): Float {
        if (readings.isEmpty()) return 0f

        val now = LocalDateTime.now()
        val filteredReadings = readings.filter { reading ->
            try {
                val timestamp = LocalDateTime.parse(reading.timestamp, dateFormatter)
                when (period) {
                    TimePeriod.DAILY -> timestamp.isAfter(now.minusDays(1))
                    TimePeriod.WEEKLY -> timestamp.isAfter(now.minusWeeks(1))
                    TimePeriod.MONTHLY -> timestamp.isAfter(now.minusMonths(1))
                }
            } catch (e: Exception) {
                false
            }
        }

        if (filteredReadings.size < 2) return 0f

        val first = filteredReadings.first()
        val last = filteredReadings.last()
        return maxOf(last.energy_kwh - first.energy_kwh, 0f)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getClientId(): String = clientId
}
