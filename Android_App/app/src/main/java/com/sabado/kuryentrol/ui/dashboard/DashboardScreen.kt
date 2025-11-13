package com.sabado.kuryentrol.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sabado.kuryentrol.data.model.PzemMetrics
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * DashboardScreen: displays a real-time list of devices, their statuses, relay switches, and metrics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onDeviceClick: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val statuses by viewModel.statuses.collectAsState()
    val relays by viewModel.relays.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Device Dashboard") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = if (isConnected) "MQTT Connected" else "Connecting to MQTT...",
                color = if (isConnected) Color(0xFF388E3C) else Color(0xFFD32F2F),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (statuses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No devices found.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(statuses.keys.sorted()) { clientId ->
                        val status = statuses[clientId] ?: "Offline"
                        val relay = relays[clientId]?.trim() ?: "?"
                        val m = metrics[clientId]
                        DeviceCard(
                            clientId = clientId,
                            status = status,
                            relayState = relay == "1",
                            metrics = m,
                            onRelayToggle = { on -> viewModel.setRelay(clientId, on) },
                            onClick = { onDeviceClick(clientId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    clientId: String,
    status: String,
    relayState: Boolean,
    metrics: PzemMetrics?,
    onRelayToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val isOnline = status == "Online"
    val buttonColor = if (relayState && isOnline)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val glyphColor = if (relayState && isOnline)
        Color.Yellow
    else
        Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Left side: Device info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Client ID
                Text(
                    text = clientId,
                    style = MaterialTheme.typography.titleMedium
                )

                // Metrics
                metrics?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Voltage: ${it.voltage} V", style = MaterialTheme.typography.bodyMedium)
                    Text("Current: ${it.current} A", style = MaterialTheme.typography.bodyMedium)
                    Text("Power: ${it.power} W", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap for details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right side: Status and Power Button (vertical)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (isOnline) "●" else "○",
                        color = if (isOnline) Color(0xFF388E3C) else Color(0xFFD32F2F)
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Power Button
                FloatingActionButton(
                    onClick = {
                        if (isOnline) {
                            onRelayToggle(!relayState)
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .offset(y = 28.dp),
                    containerColor = buttonColor,
                    contentColor = glyphColor,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 10.dp
                    )
                ) {
                    PowerGlyph(color = glyphColor)
                }
            }
        }
    }
}

@Composable
private fun PowerGlyph(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.12f
        val cx = w / 2f
        val startY = h * 0.15f
        val midY = h * 0.55f

        drawLine(
            color = color,
            start = Offset(cx, startY),
            end = Offset(cx, midY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        val arcRadius = w * 0.32f
        val arcTop = h * 0.25f

        drawArc(
            color = color,
            startAngle = 305f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(cx - arcRadius, arcTop),
            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}
