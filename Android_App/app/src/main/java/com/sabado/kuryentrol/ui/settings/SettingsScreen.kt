package com.sabado.kuryentrol.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Settings Screen for configuring MQTT Broker and REST API URLs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val brokerUrl by viewModel.brokerUrl.collectAsState()
    val apiUrl by viewModel.apiUrl.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show success message
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar(
                message = "Settings saved successfully!",
                duration = SnackbarDuration.Short
            )
            viewModel.resetSaveSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connection Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            
            OutlinedTextField(
                value = brokerUrl,
                onValueChange = { viewModel.updateBrokerUrl(it) },
                label = { Text("MQTT Broker URL") },
                placeholder = { Text("tcp://mqttpi.local:1883") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Text(
                text = "Example: tcp://192.168.1.100:1883 or tcp://mqttpi.local:1883",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { viewModel.updateApiUrl(it) },
                label = { Text("REST API URL") },
                placeholder = { Text("http://mqttpi.local:5001/api") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Text(
                text = "Example: http://192.168.1.100:5001/api or http://mqttpi.local:5001/api",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
        }
    }
}
