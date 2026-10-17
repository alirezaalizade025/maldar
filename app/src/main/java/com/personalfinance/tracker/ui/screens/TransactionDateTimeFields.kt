package com.personalfinance.tracker.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.Digits
import com.personalfinance.tracker.util.JalaliCalendar
import java.util.Calendar
import java.util.Locale

@Composable
internal fun TransactionDateTimeFields(value: Long, onValueChange: (Long) -> Unit) {
    val context = LocalContext.current
    val time = Calendar.getInstance().apply { timeInMillis = value }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showTransactionDatePicker(context, value, onValueChange) }, modifier = Modifier.weight(1.4f)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text("تاریخ", style = MaterialTheme.typography.labelSmall)
                Text(JalaliCalendar.formatDate(value), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { showTransactionTimePicker(context, value, onValueChange) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text("زمان", style = MaterialTheme.typography.labelSmall)
                Text(Digits.toPersian(String.format(Locale.US, "%02d:%02d", time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE))))
            }
        }
    }
}

internal fun showTransactionDatePicker(context: Context, value: Long, onValueChange: (Long) -> Unit) {
    val current = Calendar.getInstance().apply { timeInMillis = value }
    DatePickerDialog(context, { _, year, month, day ->
        val selected = Calendar.getInstance().apply {
            timeInMillis = value
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        onValueChange(selected.timeInMillis)
    }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
}

internal fun showTransactionTimePicker(context: Context, value: Long, onValueChange: (Long) -> Unit) {
    val current = Calendar.getInstance().apply { timeInMillis = value }
    TimePickerDialog(context, { _, hour, minute ->
        val selected = Calendar.getInstance().apply {
            timeInMillis = value
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        onValueChange(selected.timeInMillis)
    }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
}
