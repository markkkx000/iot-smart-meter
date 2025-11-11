package com.sabado.kuryentrol.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sabado.kuryentrol.data.model.DeviceStatus
import com.sabado.kuryentrol.data.model.RelayState
import com.sabado.kuryentrol.service.MqttConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, navController: NavController) {
    val devices by viewModel.devices.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val networkLatency by viewModel.networkLatency.collectAsState() // Observe network latency

    var showRenameDialog by remember { mutableStateOf<String?>(null) }

    if (showRenameDialog != null) {
        RenameDeviceDialog(
            clientId = showRenameDialog!!,
            onDismiss = { showRenameDialog = null },
            onConfirm = { clientId, newName ->
                viewModel.saveDeviceCustomName(clientId, newName)
                showRenameDialog = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kuryentrol Dashboard") },
                actions = {
                    // Pass networkLatency to the indicator
                    ConnectionStatusIndicator(status = connectionStatus, networkLatency = networkLatency)
                }
            )
        }
    ) { paddingValues ->
        if (devices.isEmpty()) {
            EmptyState(
                message = "No devices connected.\nPlease ensure your devices are online and connected to the MQTT broker.",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(
                        device = device,
                        onRelayToggle = { viewModel.toggleRelay(device.clientId, device.relayState) },
                        onCardClick = { navController.navigate("deviceDetails/${device.clientId}") },
                        onRenameClick = { showRenameDialog = device.clientId }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: DeviceUiState, onRelayToggle: () -> Unit, onCardClick: () -> Unit, onRenameClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.customName ?: device.clientId,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (device.customName != null) {
                        Text(
                            text = device.clientId,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                IconButton(onClick = { onRenameClick(device.clientId) }) {
                    Icon(Icons.Filled.Edit, "Rename Device")
                }
                Text(
                    text = device.status.name,
                    color = if (device.status == DeviceStatus.ONLINE) Color.Green else Color.Red,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Metrics
            device.metrics?.let {
                Text("Voltage: ${it.voltage} V")
                Text("Current: ${it.current} A")
                Text("Power: ${it.power} W")
            }
            device.energy?.let {
                Text("Energy: $it kWh")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Relay Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Relay")
                Switch(
                    checked = device.relayState == RelayState.ON,
                    onCheckedChange = { onRelayToggle() }
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusIndicator(status: MqttConnectionStatus, networkLatency: Long?) {
    val (text, color) = when (status) {
        MqttConnectionStatus.CONNECTED -> "Connected" to Color.Green
        MqttConnectionStatus.CONNECTING -> "Connecting" to Color.Yellow
        MqttConnectionStatus.DISCONNECTED -> "Disconnected" to Color.Red
        MqttConnectionStatus.ERROR -> "Error" to Color.Red
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Text(text, color = color)
        networkLatency?.let { latency ->
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ping: ${latency}ms", color = Color.Gray)
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameDeviceDialog(
    clientId: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Rename Device",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Client ID: $clientId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(clientId, newName) }, enabled = newName.isNotBlank()) {
                        Text("Rename")
                    }
                }
            }
        }
    }
}
