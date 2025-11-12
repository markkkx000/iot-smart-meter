package com.sabado.kuryentrol.ui.devicedetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.Shape
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.util.*

/**
 * Device Details Screen with graph visualization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailsViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val graphData by viewModel.graphData.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val totalConsumption by viewModel.totalConsumption.collectAsState()
    val energyBill by viewModel.energyBill.collectAsState()
    val pricePerKwh by viewModel.pricePerKwh.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val threshold by viewModel.threshold.collectAsState()
    val clientId = viewModel.getClientId()

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog and state for time pickers
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleType by remember { mutableStateOf("daily") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("20:00") }
    var selectedDays by remember { mutableStateOf(setOf(0, 1, 2, 3, 4)) }
    var durationHours by remember { mutableStateOf("1") }
    var durationMinutes by remember { mutableStateOf("0") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Show error message
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device: $clientId") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Time Period Selector
                    item {
                        TimePeriodSelector(
                            selectedPeriod = selectedPeriod,
                            onPeriodSelected = { viewModel.selectPeriod(it) }
                        )
                    }

                    // Energy Graph Card
                    item {
                        EnergyGraphCard(
                            graphData = graphData,
                            totalConsumption = totalConsumption,
                            energyBill = energyBill,
                            pricePerKwh = pricePerKwh
                        )
                    }

                    // Threshold Card
                    item {
                        ThresholdCard(threshold)
                    }

                    // Schedules Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Schedules",
                                style = MaterialTheme.typography.titleMedium
                            )
                            FilledTonalButton(
                                onClick = { showScheduleDialog = true }
                            ) {
                                Icon(Icons.Default.Add, "Add", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }

                    if (schedules.isEmpty()) {
                        item {
                            Text(
                                text = "No schedules configured",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(schedules) { schedule ->
                            ScheduleCard(schedule)
                        }
                    }
                }
            }
        }

        // TimePickerDialogs for wheels
        if (showStartTimePicker) {
            val cal = Calendar.getInstance()
            val hour = startTime.substringBefore(":").toIntOrNull() ?: 8
            val minute = startTime.substringAfter(":").toIntOrNull() ?: 0
            TimePickerDialog(
                context,
                { _, h, m ->
                    startTime = String.format("%02d:%02d", h, m)
                    showStartTimePicker = false
                },
                hour,
                minute,
                true
            ).show()
        }
        if (showEndTimePicker) {
            val cal = Calendar.getInstance()
            val hour = endTime.substringBefore(":").toIntOrNull() ?: 20
            val minute = endTime.substringAfter(":").toIntOrNull() ?: 0
            TimePickerDialog(
                context,
                { _, h, m ->
                    endTime = String.format("%02d:%02d", h, m)
                    showEndTimePicker = false
                },
                hour,
                minute,
                true
            ).show()
        }

        // Schedule Dialog with wheel selectors
        if (showScheduleDialog) {
            AlertDialog(
                onDismissRequest = { showScheduleDialog = false },
                title = { Text("Create Schedule") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Schedule Type", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = scheduleType == "daily",
                                onClick = { scheduleType = "daily" },
                                label = { Text("Daily") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = scheduleType == "timer",
                                onClick = { scheduleType = "timer" },
                                label = { Text("Timer") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (scheduleType == "daily") {
                            OutlinedButton(
                                onClick = { showStartTimePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(startTime)
                            }
                            OutlinedButton(
                                onClick = { showEndTimePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(endTime)
                            }

                            Text("Days (0=Mon, 6=Sun)", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, day ->
                                    FilterChip(
                                        selected = selectedDays.contains(i),
                                        onClick = {
                                            selectedDays = if (selectedDays.contains(i)) {
                                                selectedDays - i
                                            } else {
                                                selectedDays + i
                                            }
                                        },
                                        label = { Text(day) }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = durationHours,
                                    onValueChange = { durationHours = it },
                                    label = { Text("Hours") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = durationMinutes,
                                    onValueChange = { durationMinutes = it },
                                    label = { Text("Minutes") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (scheduleType == "daily") {
                                val daysOfWeek = if (selectedDays.size == 7) null else selectedDays.sorted().joinToString(",")
                                viewModel.createSchedule(
                                    scheduleType = scheduleType,
                                    startTime = startTime,
                                    endTime = endTime,
                                    daysOfWeek = daysOfWeek
                                )
                            } else {
                                val hours = durationHours.toIntOrNull() ?: 0
                                val minutes = durationMinutes.toIntOrNull() ?: 0
                                val seconds = (hours * 3600) + (minutes * 60)
                                if (seconds > 0) {
                                    viewModel.createSchedule(
                                        scheduleType = scheduleType,
                                        durationSeconds = seconds
                                    )
                                }
                            }
                            showScheduleDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScheduleDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
// The rest of the file: TimePeriodSelector, EnergyGraphCard, EnergyChart, ThresholdCard, ScheduleCard, formatDaysOfWeek....
