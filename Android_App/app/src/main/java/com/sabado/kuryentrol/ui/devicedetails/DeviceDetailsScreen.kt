package com.sabado.kuryentrol.ui.devicedetails

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    
    // Dialog states
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }
    var editingThreshold by remember { mutableStateOf(false) }

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
                        ThresholdCard(
                            threshold = threshold,
                            currentConsumption = totalConsumption,
                            onAddClick = { showThresholdDialog = true },
                            onEditClick = {
                                editingThreshold = true
                                showThresholdDialog = true
                            },
                            onDeleteClick = { viewModel.deleteThreshold() }
                        )
                    }

                    // Schedules Section Header
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
                                onClick = { showScheduleDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Schedule",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Schedule")
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
                            ScheduleCard(
                                schedule = schedule,
                                onToggleClick = { 
                                    viewModel.toggleSchedule(schedule.id, schedule.enabled == 0)
                                },
                                onEditClick = {
                                    editingSchedule = schedule
                                    showScheduleDialog = true
                                },
                                onDeleteClick = { viewModel.deleteSchedule(schedule.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Dialogs
    if (showScheduleDialog) {
        ScheduleDialog(
            existingSchedule = editingSchedule,
            onDismiss = {
                showScheduleDialog = false
                editingSchedule = null
            },
            onSave = { type, start, end, days, duration ->
                if (editingSchedule != null) {
                    viewModel.editSchedule(editingSchedule!!.id, type, start, end, days, duration)
                } else {
                    viewModel.createSchedule(type, start, end, days, duration)
                }
                editingSchedule = null
            }
        )
    }

    if (showThresholdDialog) {
        ThresholdDialog(
            existingThreshold = if (editingThreshold) threshold else null,
            onDismiss = {
                showThresholdDialog = false
                editingThreshold = false
            },
            onSave = { limit, period ->
                viewModel.setThreshold(limit, period)
            }
        )
    }
}

@Composable
fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePeriod.entries.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun EnergyGraphCard(
    graphData: List<GraphDataPoint>,
    totalConsumption: Float,
    energyBill: Float,
    pricePerKwh: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Energy Consumption",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Total Consumption",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.3f kWh", totalConsumption),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Estimated Bill",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₱${String.format("%.2f", energyBill)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = "(Rate: ₱${String.format("%.2f", pricePerKwh)}/kWh)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Graph
            if (graphData.isNotEmpty()) {
                EnergyChart(
                    data = graphData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data available for this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EnergyChart(
    data: List<GraphDataPoint>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val scrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End,
        autoScroll = Scroll.Absolute.End,
        autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased
    )

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnSeries {
                series(data.map { it.value })
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        color = primaryColor,
                        thickness = 16.dp,
                        shape = Shape.rounded(allPercent = 40)
                    )
                )
            ),
            startAxis = rememberStartAxis(
                label = rememberTextComponent(
                    color = onSurfaceColor
                )
            ),
            bottomAxis = rememberBottomAxis(
                label = rememberTextComponent(
                    color = onSurfaceColor
                ),
                valueFormatter = { value, _, _ ->
                    data.getOrNull(value.toInt())?.label ?: ""
                }
            )
        ),
        modelProducer = modelProducer,
        scrollState = scrollState,
        modifier = modifier
    )
}

@SuppressLint("DefaultLocale")
@Composable
fun ThresholdCard(
    threshold: Threshold?,
    currentConsumption: Float,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit,  // Add this
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Energy Threshold",
                    style = MaterialTheme.typography.titleMedium
                )

                if (threshold == null) {
                    FilledTonalButton(
                        onClick = onAddClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Set")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Threshold",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Threshold",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (threshold != null) {
                Text(
                    text = "Limit: ${threshold.limitKwh} kWh",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Reset: ${threshold.resetPeriod.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Status: ${if (threshold.enabled == 1) "Active" else "Disabled"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (threshold.enabled == 1) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Progress Bar
                Text(
                    text = "Current Usage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val progress = (currentConsumption / threshold.limitKwh).coerceIn(0f, 1f)
                val progressColor = when {
                    progress > 1.0f -> MaterialTheme.colorScheme.error
                    progress > 0.7f -> Color(0xFFFF9800) // Orange
                    else -> MaterialTheme.colorScheme.primary
                }
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = progressColor,
                )
                
                Text(
                    text = String.format("%.2f / %.2f kWh", currentConsumption, threshold.limitKwh),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Warning if disabled
                if (threshold.enabled == 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Threshold triggered and disabled. It will NOT re-enable on next reset period. Delete and recreate to re-enable monitoring.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No threshold configured. Set a threshold to automatically disable the device when energy consumption exceeds a limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScheduleCard(
    schedule: Schedule,
    onToggleClick: (Schedule) -> Unit,
    onEditClick: (Schedule) -> Unit,  // Add this
    onDeleteClick: (Schedule) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = schedule.scheduleType.uppercase(),
                            style = MaterialTheme.typography.titleSmall
                        )
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = if (schedule.enabled == 1) "Active" else "Disabled",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (schedule.enabled == 1)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    when (schedule.scheduleType) {
                        "daily" -> {
                            Text(
                                text = "${schedule.startTime} - ${schedule.endTime}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            schedule.daysOfWeek?.let {
                                Text(
                                    text = formatDaysOfWeek(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } ?: Text(
                                text = "Every Day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        "timer" -> {
                            schedule.durationSeconds?.let {
                                val hours = it / 3600
                                val minutes = (it % 3600) / 60
                                Text(
                                    text = "Duration: ${hours}h ${minutes}m",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Only show switch for daily schedules
                    if (schedule.scheduleType == "daily") {
                        Switch(
                            checked = schedule.enabled == 1,
                            onCheckedChange = { onToggleClick(schedule) }
                        )
                    }

                    // Edit button
                    IconButton(onClick = { onEditClick(schedule) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Schedule",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Delete button
                    IconButton(onClick = { onDeleteClick(schedule) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Schedule",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}


/**
 * Helper function to format days of week from "0,1,2,3,4" to "Mon, Tue, Wed, Thu, Fri"
 */
fun formatDaysOfWeek(daysString: String): String {
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return daysString.split(",")
        .mapNotNull { it.toIntOrNull() }
        .filter { it in 0..6 }.joinToString(", ") { dayLabels[it] }
}
