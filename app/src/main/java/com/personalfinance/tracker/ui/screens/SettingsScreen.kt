package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import com.personalfinance.tracker.ui.theme.AppCard
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.Digits
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Settings
import com.personalfinance.tracker.worker.DailyReminderScheduler

@Composable
private fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = content
    )
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Settings.dailyReminderEnabled) }
    var hour by remember { mutableStateOf(Settings.dailyReminderHour) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = 0)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    Settings.dailyReminderHour = hour
                    if (enabled) DailyReminderScheduler.scheduleNext(context)
                    showTimePicker = false
                }) { Text(AppStrings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(AppStrings.cancel) }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TimePicker(state = state)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(AppStrings.settings, style = MaterialTheme.typography.headlineMedium)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(AppStrings.dailyReminder, style = MaterialTheme.typography.titleMedium)
                        Text(AppStrings.dailyReminderHint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        Settings.dailyReminderEnabled = it
                        if (it) DailyReminderScheduler.scheduleNext(context)
                        else DailyReminderScheduler.cancel(context)
                    })
                }

                if (enabled) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { showTimePicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(AppStrings.dailyReminderTime, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Digits.toPersian("%02d:00".format(java.util.Locale.US, hour)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.close) }
    }
}
