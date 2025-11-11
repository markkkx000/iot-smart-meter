package com.sabado.kuryentrol.ui.devicedetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sabado.kuryentrol.data.model.Schedule
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    viewModel: DeviceDetailsViewModel,
    onDismiss: () -> Unit
) {
    val editingSchedule by viewModel.editingSchedule.collectAsState()
    val newScheduleType by viewModel.newScheduleType.collectAsState()
    val newStartTime by viewModel.newStartTime.collectAsState()
    val newEndTime by viewModel.newEndTime.collectAsState()
    val newDurationSeconds by viewModel.newDurationSeconds.collectAsState()
    val newDaysOfWeek by viewModel.newDaysOfWeek.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
                    text = if (editingSchedule == null) "Add New Schedule" else "Edit Schedule",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Schedule Type Selection
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    FilterChip(
                        selected = newScheduleType == "daily",
                        onClick = { viewModel.setNewScheduleType("daily") },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = newScheduleType == "timer",
                        onClick = { viewModel.setNewScheduleType("timer") },
                        label = { Text("Timer") }
                    )
                }

                if (newScheduleType == "daily") {
                    // Daily Schedule Inputs
                    OutlinedTextField(
                        value = newStartTime,
                        onValueChange = { viewModel.setNewStartTime(it) },
                        label = { Text("Start Time (HH:MM)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newEndTime,
                        onValueChange = { viewModel.setNewEndTime(it) },
                        label = { Text("End Time (HH:MM)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Days of Week Selector
                    Text("Days of Week:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        days.forEachIndexed { index, dayName ->
                            val isSelected = newDaysOfWeek.contains(index)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleDayOfWeek(index) },
                                label = { Text(dayName) }
                            )
                        }
                    }
                } else {
                    // Timer Schedule Input
                    OutlinedTextField(
                        value = newDurationSeconds,
                        onValueChange = { viewModel.setNewDurationSeconds(it) },
                        label = { Text("Duration (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        onClick = { viewModel.saveSchedule() },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
