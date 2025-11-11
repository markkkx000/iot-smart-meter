package com.sabado.kuryentrol.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val mqttBrokerAddress by viewModel.mqttBrokerAddress.collectAsState()
    val restApiUrl by viewModel.restApiUrl.collectAsState()

    var tempMqttBrokerAddress by remember(mqttBrokerAddress) { mutableStateOf(mqttBrokerAddress) }
    var tempRestApiUrl by remember(restApiUrl) { mutableStateOf(restApiUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = tempMqttBrokerAddress,
            onValueChange = { tempMqttBrokerAddress = it },
            label = { Text("MQTT Broker Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = tempRestApiUrl,
            onValueChange = { tempRestApiUrl = it },
            label = { Text("REST API URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.saveMqttBrokerAddress(tempMqttBrokerAddress)
                viewModel.saveRestApiUrl(tempRestApiUrl)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}
