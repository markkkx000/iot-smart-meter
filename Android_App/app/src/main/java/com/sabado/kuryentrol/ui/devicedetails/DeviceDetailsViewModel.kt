package com.sabado.kuryentrol.ui.devicedetails

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import com.sabado.kuryentrol.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    private val _pricePerKwh = MutableStateFlow(10.00f) // Default ₱10.00 per kWh
    val pricePerKwh: StateFlow<Float> = _pricePerKwh.asStateFlow()

    // Date format for parsing timestamps
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Combined graph data based on readings and selected period
    val graphData: StateFlow<List<GraphDataPoint>> = combine(
        _allReadings,
        _selectedPeriod
    ) { readings, period ->
        processReadingsForGraph(readings, period)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
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
        started = SharingStarted.WhileSubscribed(5000),
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
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

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

    // Write Methods for Schedules
    fun createSchedule(
        scheduleType: String,
        startTime: String? = null,
        endTime: String? = null,
        daysOfWeek: String? = null,
        durationSeconds: Int? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            val scheduleData = buildMap {
                put("client_id", clientId)
                put("schedule_type", scheduleType)

                if (scheduleType == "daily") {
                    startTime?.let { put("start_time", it) }
                    endTime?.let { put("end_time", it) }
                    daysOfWeek?.let { put("days_of_week", it) }
                } else if (scheduleType == "timer") {
                    durationSeconds?.let { put("duration_seconds", it.toString()) } // Convert to String
                }
            }

            deviceRepository.createSchedule(scheduleData).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadDeviceData() // Refresh
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to create schedule: ${error.message}"
                }
            )

            _isLoading.value = false
        }
    }

    fun updateSchedule(scheduleId: Int, enabled: Boolean) {
        viewModelScope.launch {
            val updates = mapOf("enabled" to (if (enabled) "1" else "0"))

            deviceRepository.updateSchedule(scheduleId, updates).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadDeviceData() // Refresh
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to update schedule: ${error.message}"
                }
            )
        }
    }

    fun deleteSchedule(scheduleId: Int) {
        viewModelScope.launch {
            deviceRepository.deleteSchedule(scheduleId).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadDeviceData() // Refresh
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to delete schedule: ${error.message}"
                }
            )
        }
    }

    // Write Methods for Thresholds
    fun setThreshold(limitKwh: Float, resetPeriod: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val thresholdData = mapOf(
                "limit_kwh" to limitKwh.toString(),
                "reset_period" to resetPeriod
            )

            deviceRepository.setThreshold(clientId, thresholdData).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadDeviceData() // Refresh
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to set threshold: ${error.message}"
                }
            )

            _isLoading.value = false
        }
    }

    fun deleteThreshold() {
        viewModelScope.launch {
            deviceRepository.deleteThreshold(clientId).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadDeviceData() // Refresh
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to delete threshold: ${error.message}"
                }
            )
        }
    }

    private fun processReadingsForGraph(
        readings: List<EnergyReading>,
        period: TimePeriod
    ): List<GraphDataPoint> {
        if (readings.isEmpty()) return emptyList()

        // Filter readings based on period
        val filteredReadings = readings.filter { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@filter false
                val readingCal = Calendar.getInstance().apply { time = timestamp }

                when (period) {
                    TimePeriod.DAILY -> {
                        val oneDayAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                        readingCal.after(oneDayAgo)
                    }
                    TimePeriod.WEEKLY -> {
                        val oneWeekAgo = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -1) }
                        readingCal.after(oneWeekAgo)
                    }
                    TimePeriod.MONTHLY -> {
                        val oneMonthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                        readingCal.after(oneMonthAgo)
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        // Group readings and calculate consumption deltas
        return when (period) {
            TimePeriod.DAILY -> groupByHourWithEmptySlots(filteredReadings)
            TimePeriod.WEEKLY -> groupByDayOfWeek(filteredReadings)
            TimePeriod.MONTHLY -> groupByDateInMonth(filteredReadings)
        }
    }


    @SuppressLint("DefaultLocale")
    private fun groupByHourWithEmptySlots(readings: List<EnergyReading>): List<GraphDataPoint> {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)

        // Create all 24 hour slots
        val allHours = (0..23).associateWith { mutableListOf<EnergyReading>() }.toMutableMap()

        // Group readings by hour
        readings.forEach { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@forEach
                val cal = Calendar.getInstance().apply { time = timestamp }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                allHours[hour]?.add(reading)
            } catch (_: Exception) {
                // Skip invalid readings
            }
        }

        // Calculate consumption for each hour, in order from 8 hours ago to now
        val result = mutableListOf<GraphDataPoint>()
        for (i in 0..23) {
            val hour = (currentHour - 23 + i + 24) % 24
            val hourReadings = allHours[hour] ?: continue

            if (hourReadings.size >= 2) {
                val first = hourReadings.first()
                val last = hourReadings.last()
                val consumption = last.energyKwh - first.energyKwh
                result.add(GraphDataPoint(
                    label = String.format("%02d:00", hour),
                    value = maxOf(consumption, 0f)
                ))
            } else {
                // Add zero for missing data
                result.add(GraphDataPoint(
                    label = String.format("%02d:00", hour),
                    value = 0f
                ))
            }
        }

        return result
    }

    private fun groupByDayOfWeek(readings: List<EnergyReading>): List<GraphDataPoint> {

        // Create map for 7 days (today back to 6 days ago)
        val daysMap = mutableMapOf<String, MutableList<EnergyReading>>()
        val dayLabels = mutableListOf<Pair<String, Int>>() // (label, dayOffset)

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dayName = dayCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US) ?: ""
            val dayKey = "${dayCal.get(Calendar.YEAR)}-${dayCal.get(Calendar.DAY_OF_YEAR)}"
            daysMap[dayKey] = mutableListOf()
            dayLabels.add(dayName to -i)
        }

        // Group readings by day
        readings.forEach { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@forEach
                val cal = Calendar.getInstance().apply { time = timestamp }
                val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                daysMap[dayKey]?.add(reading)
            } catch (_: Exception) {
                // Skip
            }
        }

        // Calculate consumption for each day
        return dayLabels.map { (label, offset) ->
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dayKey = "${dayCal.get(Calendar.YEAR)}-${dayCal.get(Calendar.DAY_OF_YEAR)}"
            val dayReadings = daysMap[dayKey] ?: emptyList()

            if (dayReadings.size >= 2) {
                val first = dayReadings.first()
                val last = dayReadings.last()
                val consumption = last.energyKwh - first.energyKwh
                GraphDataPoint(label = label, value = maxOf(consumption, 0f))
            } else {
                GraphDataPoint(label = label, value = 0f)
            }
        }
    }

    private fun groupByDateInMonth(readings: List<EnergyReading>): List<GraphDataPoint> {
        // Create map for last 30 days
        val daysMap = mutableMapOf<String, MutableList<EnergyReading>>()
        val dayLabels = mutableListOf<Pair<String, String>>() // (label, dayKey)

        for (i in 29 downTo 0) {
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val label = "${dayCal.get(Calendar.MONTH) + 1}/${dayCal.get(Calendar.DAY_OF_MONTH)}"
            val dayKey = "${dayCal.get(Calendar.YEAR)}-${dayCal.get(Calendar.DAY_OF_YEAR)}"
            daysMap[dayKey] = mutableListOf()
            dayLabels.add(label to dayKey)
        }

        // Group readings by day
        readings.forEach { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@forEach
                val cal = Calendar.getInstance().apply { time = timestamp }
                val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                daysMap[dayKey]?.add(reading)
            } catch (_: Exception) {
                // Skip
            }
        }

        // Calculate consumption for each day
        return dayLabels.map { (label, dayKey) ->
            val dayReadings = daysMap[dayKey] ?: emptyList()

            if (dayReadings.size >= 2) {
                val first = dayReadings.first()
                val last = dayReadings.last()
                val consumption = last.energyKwh - first.energyKwh
                GraphDataPoint(label = label, value = maxOf(consumption, 0f))
            } else {
                GraphDataPoint(label = label, value = 0f)
            }
        }
    }


    private fun calculateTotalConsumption(
        readings: List<EnergyReading>,
        period: TimePeriod
    ): Float {
        if (readings.isEmpty()) return 0f

        val filteredReadings = readings.filter { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@filter false
                val readingCal = Calendar.getInstance().apply { time = timestamp }

                when (period) {
                    TimePeriod.DAILY -> {
                        val oneDayAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                        readingCal.after(oneDayAgo)
                    }
                    TimePeriod.WEEKLY -> {
                        val oneWeekAgo = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -1) }
                        readingCal.after(oneWeekAgo)
                    }
                    TimePeriod.MONTHLY -> {
                        val oneMonthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                        readingCal.after(oneMonthAgo)
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        if (filteredReadings.size < 2) return 0f

        val first = filteredReadings.first()
        val last = filteredReadings.last()
        return maxOf(last.energyKwh - first.energyKwh, 0f)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getClientId(): String = clientId
}