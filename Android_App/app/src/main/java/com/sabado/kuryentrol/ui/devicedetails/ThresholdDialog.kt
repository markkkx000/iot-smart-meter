package com.sabado.kuryentrol.ui.devicedetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdDialog(
    viewModel: DeviceDetailsViewModel,
    onDismiss: () -> Unit
) {
    val newThresholdLimit by viewModel.newThresholdLimit.collectAsState()
    val newResetPeriod by viewModel.newResetPeriod.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentThreshold by viewModel.threshold.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (currentThreshold == null) "Set Energy Threshold" else "Edit Energy Threshold",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = newThresholdLimit,
                    onValueChange = { viewModel.setNewThresholdLimit(it) },
                    label = { Text("Threshold Limit (kWh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Reset Period:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("daily", "weekly", "monthly").forEach { period ->
                        FilterChip(
                            selected = newResetPeriod == period,
                            onClick = { viewModel.setNewResetPeriod(period) },
                            label = { Text(period.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.saveThreshold() },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text(if (currentThreshold == null) "Set Threshold" else "Update Threshold")
                        }
                    }
                }
            }
        }
    }
}
