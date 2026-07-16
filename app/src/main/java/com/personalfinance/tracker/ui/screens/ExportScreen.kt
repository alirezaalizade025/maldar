package com.personalfinance.tracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.DataExport
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExportScreen(viewModel: FinanceViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun share(content: String, mime: String, title: String) {
        val file = File(context.cacheDir, title)
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, AppStrings.sendExport))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(AppStrings.exportData, style = MaterialTheme.typography.headlineMedium)
        Text(AppStrings.exportHint, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    runCatching {
                        val bundle = viewModel.exportAll()
                        share(DataExport.toCsv(bundle.transactions, bundle.accounts, bundle.loans, bundle.categories),
                            "text/csv", "maldar-export.csv")
                    }.onFailure { message = it.message }
                    busy = false
                }
            }
        ) { Text(AppStrings.exportCsv) }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    runCatching {
                        val bundle = viewModel.exportAll()
                        share(DataExport.toJson(bundle.transactions, bundle.accounts, bundle.loans, bundle.categories),
                            "application/json", "maldar-export.json")
                    }.onFailure { message = it.message }
                    busy = false
                }
            }
        ) { Text(AppStrings.exportJson) }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.close) }
    }
}
