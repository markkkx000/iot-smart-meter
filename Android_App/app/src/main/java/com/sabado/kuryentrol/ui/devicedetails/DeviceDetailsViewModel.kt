package com.sabado.kuryentrol.ui.devicedetails

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import com.sabado.kuryentrol.data.repository.DeviceRepository
import com.sabado.kuryentrol.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
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
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    // Loading states - granular for specific sections
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingGraph = MutableStateFlow(false)
    val isLoadingGraph: StateFlow<Boolean> = _isLoadingGraph.asStateFlow()

    private val _isLoadingSchedules = MutableStateFlow(false)
    val isLoadingSchedules: StateFlow<Boolean> = _isLoadingSchedules.asStateFlow()

    private val _isLoadingThreshold = MutableStateFlow(false)
    val isLoadingThreshold: StateFlow<Boolean> = _isLoadingThreshold.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _allReadings = MutableStateFlow<List<EnergyReading>>(emptyList())
    private val _selectedPeriod = MutableStateFlow(TimePeriod.DAILY)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _threshold = MutableStateFlow<Threshold?>(null)
    val threshold: StateFlow<Threshold?> = _threshold.asStateFlow()

    // Price per kWh for bill calculation
    private val _pricePerKwh = MutableStateFlow(10f)
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

    // Aggregate consumption states
    private val _totalConsumption = MutableStateFlow(0f)
    private val _dailyConsumption = MutableStateFlow(0f)
    private val _weeklyConsumption = MutableStateFlow(0f)
    private val _monthlyConsumption = MutableStateFlow(0f)
    val totalConsumption: StateFlow<Float> = combine(
        _selectedPeriod,
        _dailyConsumption,
        _weeklyConsumption,
        _monthlyConsumption
    ) { period, daily, weekly, monthly ->
        when (period) {
            TimePeriod.DAILY -> daily
            TimePeriod.WEEKLY -> weekly
            TimePeriod.MONTHLY -> monthly
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val dailyConsumption: StateFlow<Float> = _dailyConsumption.asStateFlow()
    val weeklyConsumption: StateFlow<Float> = _weeklyConsumption.asStateFlow()
    val monthlyConsumption: StateFlow<Float> = _monthlyConsumption.asStateFlow()

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
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                _pricePerKwh.value = settings.pricePerKwh
            }
            refreshAllAggregates()
        }

        loadDeviceData()
        loadAggregateConsumption(TimePeriod.DAILY)
    }

    fun loadDeviceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            coroutineScope {
                launch { loadEnergyReadings() }
                launch { loadSchedules() }
                launch { loadThresholds() }
                launch { refreshAllAggregates() }
            }

            _isLoading.value = false
        }
    }

    private suspend fun loadEnergyReadings() {
        _isLoadingGraph.value = true

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Calculate time range based on selected period
        val nowUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            when (_selectedPeriod.value) {
                TimePeriod.DAILY -> add(Calendar.DAY_OF_YEAR, -1)
                TimePeriod.WEEKLY -> add(Calendar.DAY_OF_YEAR, -7)
                TimePeriod.MONTHLY -> add(Calendar.DAY_OF_YEAR, -30)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val start = dateFormat.format(startTime.time)
        val end = dateFormat.format(nowUtc.time)

        deviceRepository.getEnergyReadingsByRange(clientId, start, end).fold(
            onSuccess = { readings ->
                _allReadings.value = readings.sortedBy {
                    dateFormat.parse(it.timestamp)
                }
            },
            onFailure = { error ->
                _errorMessage.value = "Failed to load energy readings: ${error.message}"
            }
        )

        _isLoadingGraph.value = false
    }

    private suspend fun loadSchedules() {
        _isLoadingSchedules.value = true
        deviceRepository.getSchedules(clientId).fold(
            onSuccess = { schedules ->
                _schedules.value = schedules
            },
            onFailure = { error ->
                _errorMessage.value = "Failed to load schedules: ${error.message}"
            }
        )
        _isLoadingSchedules.value = false
    }

    private suspend fun loadThresholds() {
        _isLoadingThreshold.value = true
        deviceRepository.getThreshold(clientId).fold(
            onSuccess = { threshold ->
                _threshold.value = threshold
            },
            onFailure = { error ->
                if (error.message?.contains("404") != true) {
                    _errorMessage.value = "Failed to load threshold: ${error.message}"
                }
            }
        )
        _isLoadingThreshold.value = false
    }

    private suspend fun refreshAllAggregates() {
        deviceRepository.getAggregateConsumption(clientId, "day").onSuccess {
            _dailyConsumption.value = it
        }
        deviceRepository.getAggregateConsumption(clientId, "week").onSuccess {
            _weeklyConsumption.value = it
        }
        deviceRepository.getAggregateConsumption(clientId, "month").onSuccess {
            _monthlyConsumption.value = it
        }
    }

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        viewModelScope.launch {
            loadEnergyReadings()
            _totalConsumption.value = when (period) {
                TimePeriod.DAILY -> _dailyConsumption.value
                TimePeriod.WEEKLY -> _weeklyConsumption.value
                TimePeriod.MONTHLY -> _monthlyConsumption.value
            }
        }
    }

    private fun loadAggregateConsumption(period: TimePeriod) {
        viewModelScope.launch {
            val periodString = when (period) {
                TimePeriod.DAILY -> "day"
                TimePeriod.WEEKLY -> "week"
                TimePeriod.MONTHLY -> "month"
            }

            deviceRepository.getAggregateConsumption(clientId, periodString).fold(
                onSuccess = { consumption ->
                    _totalConsumption.value = consumption
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load consumption: ${error.message}"
                }
            )
        }
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
            _isLoadingSchedules.value = true

            val scheduleData = buildMap {
                put("client_id", clientId)
                put("schedule_type", scheduleType)

                if (scheduleType == "daily") {
                    startTime?.let { put("start_time", it) }
                    endTime?.let { put("end_time", it) }
                    daysOfWeek?.let { put("days_of_week", it) }
                } else if (scheduleType == "timer") {
                    durationSeconds?.let { put("duration_seconds", it.toString()) }
                }
            }

            deviceRepository.createSchedule(scheduleData).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadSchedules()
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to create schedule: ${error.message}"
                    _isLoadingSchedules.value = false
                }
            )
        }
    }

    fun editSchedule(
        scheduleId: Int,
        scheduleType: String,
        startTime: String? = null,
        endTime: String? = null,
        daysOfWeek: String? = null,
        durationSeconds: Int? = null
    ) {
        viewModelScope.launch {
            _isLoadingSchedules.value = true

            val scheduleData = buildMap {
                put("schedule_type", scheduleType)

                if (scheduleType == "daily") {
                    startTime?.let { put("start_time", it) }
                    endTime?.let { put("end_time", it) }
                    daysOfWeek?.let { put("days_of_week", it) }
                } else if (scheduleType == "timer") {
                    durationSeconds?.let { put("duration_seconds", it.toString()) }
                }
            }

            deviceRepository.updateSchedule(scheduleId, scheduleData).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadSchedules()
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to update schedule: ${error.message}"
                    _isLoadingSchedules.value = false
                }
            )
        }
    }

    fun toggleSchedule(scheduleId: Int, enabled: Boolean) {
        viewModelScope.launch {
            val updates = mapOf("enabled" to if (enabled) "1" else "0")

            deviceRepository.updateSchedule(scheduleId, updates).fold(
                onSuccess = { message ->
                    clearError()
                    loadSchedules()
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
                    loadSchedules()
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
            _isLoadingThreshold.value = true

            val thresholdData = mapOf(
                "limit_kwh" to limitKwh.toString(),
                "reset_period" to resetPeriod
            )

            deviceRepository.setThreshold(clientId, thresholdData).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    loadThresholds()
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to set threshold: ${error.message}"
                    _isLoadingThreshold.value = false
                }
            )
        }
    }

    fun deleteThreshold() {
        viewModelScope.launch {
            _isLoadingThreshold.value = true
            deviceRepository.deleteThreshold(clientId).fold(
                onSuccess = { message ->
                    _errorMessage.value = message
                    _threshold.value = null
                    loadThresholds()
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to delete threshold: ${error.message}"
                    _isLoadingThreshold.value = false
                }
            )
        }
    }

    private fun processReadingsForGraph(
        readings: List<EnergyReading>,
        period: TimePeriod
    ): List<GraphDataPoint> {
        if (readings.isEmpty()) return emptyList()

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

        return when (period) {
            TimePeriod.DAILY -> groupByHourWithEmptySlots(filteredReadings)
            TimePeriod.WEEKLY -> groupByDayOfWeek(filteredReadings)
            TimePeriod.MONTHLY -> groupByDateInMonth(filteredReadings)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun groupByHourWithEmptySlots(readings: List<EnergyReading>): List<GraphDataPoint> {
        if (readings.isEmpty()) return emptyList()

        val sortedReadings = readings.sortedBy { dateFormat.parse(it.timestamp) }

        val utcTimeZone = TimeZone.getTimeZone("UTC")
        val localTimeZone = TimeZone.getDefault()
        val nowUtc = Calendar.getInstance(utcTimeZone)

        // Start from 24 hours ago
        val startTime = nowUtc.clone() as Calendar
        startTime.add(Calendar.HOUR_OF_DAY, -24)

        // Create 24 hour boundaries (24 hours ago to now)
        val hourMarks = (0..24).map { h ->
            val cal = startTime.clone() as Calendar
            cal.add(Calendar.HOUR_OF_DAY, h)
            cal
        }

        // Find the last reading before or at each hour boundary
        val hourStartIndices = hourMarks.map { hourCal ->
            sortedReadings.indexOfLast {
                dateFormat.parse(it.timestamp)!! <= hourCal.time
            }
        }

        val result = mutableListOf<GraphDataPoint>()

        for (i in 0 until 24) {
            val startIdx = hourStartIndices[i]
            val endIdx = hourStartIndices[i + 1]

            // Convert the hour mark to local time for the label
            val localCal = Calendar.getInstance(localTimeZone).apply {
                timeInMillis = hourMarks[i].timeInMillis
            }
            val localHour = localCal.get(Calendar.HOUR_OF_DAY)

            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val first = sortedReadings[startIdx]
                val last = sortedReadings[endIdx]
                val consumption = last.energyKwh - first.energyKwh

                result.add(
                    GraphDataPoint(
                        label = String.format("%02d:00", localHour),
                        value = maxOf(consumption, 0f)
                    )
                )
            } else {
                result.add(
                    GraphDataPoint(
                        label = String.format("%02d:00", localHour),
                        value = 0f
                    )
                )
            }
        }

        return result
    }


    private fun groupByDayOfWeek(readings: List<EnergyReading>): List<GraphDataPoint> {
        val daysMap = mutableMapOf<String, MutableList<EnergyReading>>()
        val dayLabels = mutableListOf<Pair<String, String>>() // (localLabel, dayKey)

        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val localTimeZone = TimeZone.getDefault()

        for (i in 6 downTo 0) {
            // Create UTC day bucket
            val dayCalUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = utcCalendar.time
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayKey = "${dayCalUtc.get(Calendar.YEAR)}-${dayCalUtc.get(Calendar.DAY_OF_YEAR)}"

            // Convert to local time for label
            val dayCalLocal = Calendar.getInstance(localTimeZone).apply {
                timeInMillis = dayCalUtc.timeInMillis
            }
            val dayName = dayCalLocal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""

            daysMap[dayKey] = mutableListOf()
            dayLabels.add(dayName to dayKey)
        }

        // Group readings by UTC day
        readings.forEach { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@forEach
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    time = timestamp
                }
                val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                daysMap[dayKey]?.add(reading)
            } catch (_: Exception) {}
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

    private fun groupByDateInMonth(readings: List<EnergyReading>): List<GraphDataPoint> {
        val daysMap = mutableMapOf<String, MutableList<EnergyReading>>()
        val dayLabels = mutableListOf<Pair<String, String>>() // (localLabel, dayKey)

        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val localTimeZone = TimeZone.getDefault()

        for (i in 29 downTo 0) {
            // Create UTC day bucket
            val dayCalUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = utcCalendar.time
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayKey = "${dayCalUtc.get(Calendar.YEAR)}-${dayCalUtc.get(Calendar.DAY_OF_YEAR)}"

            // Convert to local time for label
            val dayCalLocal = Calendar.getInstance(localTimeZone).apply {
                timeInMillis = dayCalUtc.timeInMillis
            }
            val label = "${dayCalLocal.get(Calendar.MONTH) + 1}/${dayCalLocal.get(Calendar.DAY_OF_MONTH)}"

            daysMap[dayKey] = mutableListOf()
            dayLabels.add(label to dayKey)
        }

        // Group readings by UTC day
        readings.forEach { reading ->
            try {
                val timestamp = dateFormat.parse(reading.timestamp) ?: return@forEach
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    time = timestamp
                }
                val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                daysMap[dayKey]?.add(reading)
            } catch (_: Exception) {}
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

    fun clearError() {
        _errorMessage.value = null
    }

    fun getClientId(): String = clientId
}