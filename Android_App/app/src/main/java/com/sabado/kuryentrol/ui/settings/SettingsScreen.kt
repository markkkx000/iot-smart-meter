package com.sabado.kuryentrol.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isLoading = state.isLoading
    val saveSuccessMessage by viewModel.saveSuccessMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(saveSuccessMessage) {
        saveSuccessMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short)
            viewModel.clearSaveSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Broker Settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.brokerIp,
                    onValueChange = viewModel::onBrokerIpChanged,
                    label = { Text("Broker IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = state.brokerPort,
                    onValueChange = viewModel::onBrokerPortChanged,
                    label = { Text("Broker Port") },
                    placeholder = { Text("1883") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )

//                Spacer(modifier = Modifier.height(16.dp))

                Text("API Settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.apiIp,
                    onValueChange = viewModel::onApiIpChanged,
                    label = { Text("API IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = state.apiPort,
                    onValueChange = viewModel::onApiPortChanged,
                    label = { Text("API Port") },
                    placeholder = { Text("5001") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )

//                Spacer(modifier = Modifier.height(16.dp))

                Text("Energy Price", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.pricePerKwh,
                    onValueChange = viewModel::onPricePerKwhChanged,
                    label = { Text("Price per kWh (₱)") },
                    placeholder = { Text("10.00") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )

//                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.saveSettings()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings")
                }

//                Spacer(Modifier.height(12.dp))

                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
