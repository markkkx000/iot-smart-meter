package com.sabado.kuryentrol.ui.dashboard

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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clientId,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (status == "Online") "●" else "○",
                    color = if (status == "Online") Color(0xFF388E3C) else Color(0xFFD32F2F),
                    modifier = Modifier.padding(start = 4.dp)
                )
                Text(text = status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Relay:", Modifier.width(48.dp))
                Switch(
                    checked = relayState,
                    onCheckedChange = onRelayToggle,
                    enabled = (status == "Online")
                )
                Text(
                    text = if (relayState) "ON" else "OFF",
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            metrics?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Voltage: ${it.voltage} V")
                Text("Current: ${it.current} A")
                Text("Power: ${it.power} W")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap for details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
