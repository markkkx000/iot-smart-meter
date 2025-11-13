package com.sabado.kuryentrol.ui.devicedetails

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sabado.kuryentrol.data.model.Schedule

/**
 * Dialog for creating schedules (Daily or Timer)
 */
@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    existingSchedule: Schedule? = null,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?, Int?) -> Unit
) {
    var scheduleType by remember { mutableStateOf("daily") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("20:00") }
    var selectedDays by remember {
        mutableStateOf(
            existingSchedule?.daysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf()
        )
    }
    var hours by remember {
        mutableStateOf(
            existingSchedule?.durationSeconds?.let { (it / 3600).toString() } ?: ""
        )
    }
    var minutes by remember {
        mutableStateOf(
            existingSchedule?.durationSeconds?.let { ((it % 3600) / 60).toString() } ?: ""
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val isEditing = existingSchedule != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Schedule" else "Create Schedule") },
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
                            text = "Days of Week",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val displayOrder = listOf(6, 0, 1, 2, 3, 4, 5)  // Sunday first
                            val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

                            displayOrder.forEachIndexed { displayIndex, actualIndex ->
                                val isSelected = selectedDays.contains(actualIndex)

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),  // Makes it square, then circle shape makes it circular
                                    shape = CircleShape,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = {
                                        selectedDays = if (selectedDays.contains(actualIndex)) {
                                            selectedDays - actualIndex
                                        } else {
                                            selectedDays + actualIndex
                                        }
                                    }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = dayLabels[displayIndex],
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
