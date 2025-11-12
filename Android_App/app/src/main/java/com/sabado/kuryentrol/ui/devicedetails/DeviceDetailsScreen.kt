package com.sabado.kuryentrol.ui.devicedetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold

/**
 * Device Details Screen showing energy readings, schedules, and thresholds
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailsViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val energyReadings by viewModel.energyReadings.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val threshold by viewModel.threshold.collectAsState()
    val clientId = viewModel.getClientId()

    val snackbarHostState = remember { SnackbarHostState() }

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
                    // Energy Summary Card
                    item {
                        EnergySummaryCard(energyReadings)
                    }

                    // Threshold Card
                    item {
                        ThresholdCard(threshold)
                    }

                    // Schedules Section
                    item {
                        Text(
                            text = "Schedules",
                            style = MaterialTheme.typography.titleMedium
                        )
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

                    // Energy Readings Section
                    item {
                        Text(
                            text = "Recent Energy Readings",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (energyReadings.isEmpty()) {
                        item {
                            Text(
                                text = "No energy readings available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(energyReadings.take(20)) { reading ->
                            EnergyReadingCard(reading)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnergySummaryCard(readings: List<EnergyReading>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Energy Summary",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (readings.isNotEmpty()) {
                val latestReading = readings.last()
                val firstReading = readings.first()
                val totalConsumption = latestReading.energyKwh - firstReading.energyKwh

                Text("Latest Reading: ${latestReading.energyKwh} kWh")
                Text("Total Consumption: ${"%%.3f".format(totalConsumption)} kWh")
                Text("Number of Readings: ${readings.size}")
            } else {
                Text(
                    text = "No data available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ThresholdCard(threshold: Threshold?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Energy Threshold",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (threshold != null) {
                Text("Limit: ${threshold.limitKwh} kWh")
                Text("Reset Period: ${threshold.resetPeriod}")
                Text("Status: ${if (threshold.enabled == 1) "Enabled" else "Disabled"}")
                Text("Last Reset: ${threshold.lastReset}")
            } else {
                Text(
                    text = "No threshold configured",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScheduleCard(schedule: Schedule) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = schedule.scheduleType.uppercase(),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (schedule.enabled == 1) "●" else "○",
                    color = if (schedule.enabled == 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            when (schedule.scheduleType) {
                "daily" -> {
                    Text("Time: ${schedule.startTime} - ${schedule.endTime}")
                    schedule.daysOfWeek?.let {
                        Text("Days: $it")
                    }
                }

                "timer" -> {
                    schedule.durationSeconds?.let {
                        val hours = it / 3600
                        val minutes = (it % 3600) / 60
                        Text("Duration: ${hours}h ${minutes}m")
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyReadingCard(reading: EnergyReading) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = reading.timestamp,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${reading.energyKwh} kWh",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
