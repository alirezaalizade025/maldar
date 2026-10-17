package com.personalfinance.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.fa
import com.personalfinance.tracker.util.DataExport
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.personalfinance.tracker.data.FinanceRepository
import java.io.BufferedReader

@Composable
fun ImportScreen(
    viewModel: FinanceViewModel,
    initialUri: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingBundle by remember { mutableStateOf<FinanceRepository.ExportBundle?>(null) }

    fun readUri(uri: Uri) {
        scope.launch {
            busy = true
            message = null
            runCatching {
                pendingBundle = withContext(Dispatchers.IO) {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use(BufferedReader::readText)
                        ?: throw Exception("cannot read file")
                    if (content.trimStart().startsWith("{")) DataExport.fromJson(content)
                    else DataExport.fromCsv(content)
                }
            }.onFailure {
                if (it is CancellationException) throw it
                message = if (it is Exception) AppStrings.importFailed.fa(it.message ?: "")
                else AppStrings.importInvalid
            }
            busy = false
        }
    }

    // When opened from a file (e.g. tapping a backup in a file manager), import it
    // for review once the screen mounts.
    LaunchedEffect(initialUri) {
        if (initialUri != null) readUri(Uri.parse(initialUri))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) readUri(uri) else message = AppStrings.importFailed.fa("")
    }

    pendingBundle?.let { bundle ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingBundle = null },
            title = { Text(AppStrings.importData) },
            text = { Text(AppStrings.importHint) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { viewModel.importBundle(bundle) }
                            pendingBundle = null
                            message = AppStrings.importSuccess
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) {
                            message = AppStrings.importFailed.fa(e.message ?: "")
                            pendingBundle = null
                        } finally { busy = false }
                    }
                }) { Text(AppStrings.confirm) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingBundle = null }) { Text(AppStrings.cancel) } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(AppStrings.importData, style = MaterialTheme.typography.headlineMedium)
        Text(
            AppStrings.importHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(AppStrings.importing, style = MaterialTheme.typography.labelSmall)
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = { picker.launch("*/*") }
        ) { Text(AppStrings.importChooseFile) }

        message?.let {
            val isError = it != AppStrings.importSuccess
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.close) }
    }
}
