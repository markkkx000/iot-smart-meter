package com.sabado.kuryentrol.ui.devicedetails

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sabado.kuryentrol.data.model.Threshold

/**
 * Dialog for setting energy consumption threshold
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdDialog(
    onDismiss: () -> Unit,
    existingThreshold: Threshold? = null,
    onSave: (Float, String) -> Unit
) {
    var limit by remember { mutableStateOf(existingThreshold?.limitKwh?.toString() ?: "") }
    var resetPeriod by remember { mutableStateOf(existingThreshold?.resetPeriod ?: "daily") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isEditing = existingThreshold != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Threshold" else "Set Energy Threshold") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning Card
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
                            text = "⚠️ Once triggered, threshold auto-disables and will NOT re-enable on next reset period. You must delete and recreate to restore monitoring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Limit Input
                OutlinedTextField(
                    value = limit,
                    onValueChange = { 
                        limit = it.filter { char -> char.isDigit() || char == '.' }
                    },
                    label = { Text("Limit (kWh)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Energy consumption limit in kilowatt-hours") }
                )

                // Reset Period Selector
                Text(
                    text = "Reset Period",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = resetPeriod == "daily",
                        onClick = { resetPeriod = "daily" },
                        label = { Text("Daily") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = resetPeriod == "weekly",
                        onClick = { resetPeriod = "weekly" },
                        label = { Text("Weekly") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = resetPeriod == "monthly",
                        onClick = { resetPeriod = "monthly" },
                        label = { Text("Monthly") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Error message
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    errorMessage = null
                    val limitValue = limit.toFloatOrNull()
                    
                    if (limitValue == null || limitValue <= 0) {
                        errorMessage = "Limit must be greater than 0"
                    } else {
                        onSave(limitValue, resetPeriod)
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
