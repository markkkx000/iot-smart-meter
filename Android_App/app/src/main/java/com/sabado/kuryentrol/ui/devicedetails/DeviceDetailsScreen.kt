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
                }
            }
        }
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
                        text = "₱${String.format("%.2f", energyBill)}", // Changed $ to ₱
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
        initialScroll = Scroll.Absolute.End,  // Start at the end (right side)
        autoScroll = Scroll.Absolute.End,      // Auto-scroll to end
        autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased // Auto-scroll when data changes
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
