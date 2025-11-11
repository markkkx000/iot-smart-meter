package com.sabado.kuryentrol.ui.devicedetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.* // ktlint-disable no-wildcard-imports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.rememberLineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.diff.MutableChartEntryModelProducer
import com.sabado.kuryentrol.data.model.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    viewModel: DeviceDetailsViewModel,
    navController: NavController,
    clientId: String
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val energyReadings by viewModel.energyReadings.collectAsState()
    val selectedEnergyPeriod by viewModel.selectedEnergyPeriod.collectAsState()
    val totalEnergyConsumption by viewModel.totalEnergyConsumption.collectAsState()
    val pricePerKwh by viewModel.pricePerKwh.collectAsState()
    val estimatedEnergyBill by viewModel.estimatedEnergyBill.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val showScheduleDialog by viewModel.showScheduleDialog.collectAsState()
    val threshold by viewModel.threshold.collectAsState() // Observe threshold
    val showThresholdDialog by viewModel.showThresholdDialog.collectAsState() // Observe threshold dialog state

    val chartEntryModelProducer = remember { MutableChartEntryModelProducer() }

    // Convert EnergyReading data to ChartEntryModelProducer format
    chartEntryModelProducer.setEntries(
        listOf(energyReadings.mapIndexed { index, reading ->
            FloatEntry(index.toFloat(), reading.energyKwh)
        })
    )

    if (showScheduleDialog) {
        ScheduleDialog(viewModel = viewModel, onDismiss = { viewModel.dismissScheduleDialog() })
    }

    if (showThresholdDialog) {
        ThresholdDialog(viewModel = viewModel, onDismiss = { viewModel.dismissThresholdDialog() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device: $clientId") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            item {
                if (isLoading) {
                    CircularProgressIndicator()
                } else if (errorMessage != null) {
                    Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                } else {
                    // Historical Data Graphs
                    Text(
                        text = "Energy Consumption (kWh)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Period Selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        OutlinedButton(onClick = { viewModel.loadEnergyReadingsByPeriod("day") }, enabled = selectedEnergyPeriod != "day") { Text("Daily") }
                        OutlinedButton(onClick = { viewModel.loadEnergyReadingsByPeriod("week") }, enabled = selectedEnergyPeriod != "week") { Text("Weekly") }
                        OutlinedButton(onClick = { viewModel.loadEnergyReadingsByPeriod("month") }, enabled = selectedEnergyPeriod != "month") { Text("Monthly") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (energyReadings.isNotEmpty()) {
                        Chart( 
                            chart = rememberLineChart(),
                            chartModelProducer = chartEntryModelProducer,
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(valueFormatter = { value, _ ->
                                val dateFormat = when (selectedEnergyPeriod) {
                                    "day" -> SimpleDateFormat("HH:mm", Locale.getDefault())
                                    "week" -> SimpleDateFormat("EEE", Locale.getDefault())
                                    "month" -> SimpleDateFormat("MMM dd", Locale.getDefault())
                                    else -> SimpleDateFormat("MM/dd", Locale.getDefault())
                                }
                                val timestamp = energyReadings.getOrNull(value.toInt())?.timestamp ?: ""
                                try {
                                    dateFormat.format(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestamp) ?: Date())
                                } catch (e: Exception) {
                                    timestamp
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                        )
                    } else {
                        Text("No energy data available for this period.")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Energy Bill Computation
                    Text(
                        text = "Energy Bill Computation",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("Total Consumption (${selectedEnergyPeriod.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}): %.2f kWh".format(totalEnergyConsumption))
                    Spacer(modifier = Modifier.height(8.dp))

                    var priceInput by rememberSaveable { mutableStateOf(pricePerKwh.toString()) }
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = {
                            priceInput = it
                            val newPrice = it.toFloatOrNull()
                            if (newPrice != null) {
                                viewModel.setPricePerKwh(newPrice)
                            }
                        },
                        label = { Text("Price per kWh") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Estimated Bill: Php %.2f".format(estimatedEnergyBill))

                    Spacer(modifier = Modifier.height(16.dp))

                    // Schedule Management
                    Text(
                        text = "Schedule Management",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (schedules.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            schedules.forEach { schedule ->
                                ScheduleItem(
                                    schedule = schedule,
                                    onEditClick = { viewModel.showEditScheduleDialog(schedule) },
                                    onDeleteClick = { viewModel.deleteSchedule(schedule.id) }
                                )
                            }
                        }
                    } else {
                        Text("No schedules set for this device.")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.showAddScheduleDialog() }) {
                        Text("Add New Schedule")
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Threshold Management
                    Text(
                        text = "Threshold Management",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    threshold?.let {
                        Text("Limit: %.2f kWh".format(it.limitKwh))
                        Text("Reset Period: ${it.resetPeriod.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}")
                        Text("Enabled: ${if (it.enabled == 1) "Yes" else "No"}")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { viewModel.showSetThresholdDialog() }) {
                                Icon(Icons.Filled.Edit, "Edit Threshold")
                            }
                            IconButton(onClick = { viewModel.deleteThreshold() }) {
                                Icon(Icons.Filled.Delete, "Delete Threshold")
                            }
                        }
                    } ?: run {
                        Text("No threshold set.")
                        Button(onClick = { viewModel.showSetThresholdDialog() }) {
                            Text("Set Threshold")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItem(schedule: Schedule, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Type: ${schedule.scheduleType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}")
            if (schedule.scheduleType == "daily") {
                Text("Time: ${schedule.startTime} - ${schedule.endTime}")
                schedule.daysOfWeek?.let { days ->
                    Text("Days: $days")
                }
            } else if (schedule.scheduleType == "timer") {
                Text("Duration: N/A") // Placeholder
            }
            Text("Enabled: ${if (schedule.enabled == 1) "Yes" else "No"}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, "Edit Schedule")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, "Delete Schedule")
                }
            }
        }
    }
}
