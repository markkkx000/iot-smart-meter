package com.sabado.kuryentrol.ui.devicedetails

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.*

/**
 * Dialog for creating schedules (Daily or Timer)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    clientId: String,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?, Int?) -> Unit
) {
    var scheduleType by remember { mutableStateOf("daily") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("20:00") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Schedule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Schedule Type Selector
                Text(
                    text = "Schedule Type",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scheduleType == "daily",
                        onClick = { scheduleType = "daily" },
                        label = { Text("Daily") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = scheduleType == "timer",
                        onClick = { scheduleType = "timer" },
                        label = { Text("Timer") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Daily Schedule Form
                if (scheduleType == "daily") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Start Time
                        OutlinedButton(
                            onClick = {
                                val timeParts = startTime.split(":")
                                val hour = timeParts[0].toInt()
                                val minute = timeParts[1].toInt()

                                TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        startTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                                    },
                                    hour,
                                    minute,
                                    true // 24-hour format
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Time: $startTime")
                        }

                        // End Time
                        OutlinedButton(
                            onClick = {
                                val timeParts = endTime.split(":")
                                val hour = timeParts[0].toInt()
                                val minute = timeParts[1].toInt()

                                TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        endTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                                    },
                                    hour,
                                    minute,
                                    true // 24-hour format
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("End Time: $endTime")
                        }

                        // Days of Week
                        Text(
                            text = "Days of Week (0=Monday, 6=Sunday)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            dayLabels.forEachIndexed { index, label ->
                                FilterChip(
                                    selected = selectedDays.contains(index),
                                    onClick = {
                                        selectedDays = if (selectedDays.contains(index)) {
                                            selectedDays - index
                                        } else {
                                            selectedDays + index
                                        }
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Timer Schedule Form
                if (scheduleType == "timer") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter { char -> char.isDigit() } },
                            label = { Text("Hours") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter { char -> char.isDigit() } },
                            label = { Text("Minutes") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
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
                    
                    if (scheduleType == "daily") {
                        val daysOfWeek = if (selectedDays.isEmpty()) {
                            null // All days
                        } else {
                            selectedDays.sorted().joinToString(",")
                        }
                        onSave(scheduleType, startTime, endTime, daysOfWeek, null)
                        onDismiss()
                    } else {
                        // Timer validation
                        val h = hours.toIntOrNull() ?: 0
                        val m = minutes.toIntOrNull() ?: 0
                        val totalSeconds = h * 3600 + m * 60
                        
                        if (totalSeconds <= 0) {
                            errorMessage = "Duration must be greater than 0"
                        } else {
                            onSave(scheduleType, null, null, null, totalSeconds)
                            onDismiss()
                        }
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
