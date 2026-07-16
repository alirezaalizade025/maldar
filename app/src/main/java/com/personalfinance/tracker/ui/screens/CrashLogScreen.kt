package com.personalfinance.tracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.CrashLogger

@Composable
fun CrashLogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var log by remember { mutableStateOf("") }

    fun reload() { log = CrashLogger.deviceInfo + "\n---\n" + CrashLogger.getLogText() }
    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(AppStrings.crashLog, style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = { reload() }) { Icon(Icons.Filled.Refresh, contentDescription = AppStrings.refresh) }
                TextButton(onClick = onClose) { Text(AppStrings.close) }
            }
        }

        if (log.isBlank()) {
            Text(AppStrings.noCrashLog, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(log, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, log)
                    }
                    context.startActivity(Intent.createChooser(intent, AppStrings.sendLog))
                }
            ) { Text(AppStrings.sendLog) }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { CrashLogger.clear(); reload() }
            ) { Text(AppStrings.clearLog) }
        }
    }
}
