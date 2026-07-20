package com.personalfinance.tracker.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
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

    // Write the backup directly to the device's Downloads folder (no share sheet),
    // so the user gets a local file they can open, copy, or send later.
    fun saveToDevice(content: String, mime: String, displayName: String) {
        val resolver = context.contentResolver
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val inserted = resolver.insert(coll, values)
                ?: throw Exception("cannot create file in Downloads")
            resolver.openOutputStream(inserted)?.use { it.write(content.toByteArray()) }
                ?: throw Exception("cannot write file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(inserted, values, null, null)
            inserted
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, displayName)
            file.writeText(content)
        }
        // On pre-Q we already wrote the file; just confirm it exists.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                displayName
            ).exists()
        ) throw Exception("cannot write file")
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
                        share(DataExport.toCsv(bundle.transactions, bundle.accounts, bundle.loans, bundle.categories, bundle.smsSenders),
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
                        share(DataExport.toJson(bundle.transactions, bundle.accounts, bundle.loans, bundle.categories, bundle.smsSenders),
                            "application/json", "maldar-export.json")
                    }.onFailure { message = it.message }
                    busy = false
                }
            }
        ) { Text(AppStrings.exportJson) }

        HorizontalDivider()
        Text(AppStrings.saveToDevice, style = MaterialTheme.typography.titleMedium)

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    runCatching {
                        val bundle = viewModel.exportAll()
                        saveToDevice(DataExport.toCsv(bundle.transactions, bundle.accounts, bundle.loans, bundle.categories, bundle.smsSenders),
                            "text/csv", "maldar-export.csv")
                        message = AppStrings.savedToDownloads
                    }.onFailure { message = it.message }
                    busy = false
                }
            }
        ) { Text("${AppStrings.exportCsv} → ${AppStrings.saveToDevice}") }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    runCatching {
                        val bundle = viewModel.exportAll()
                        saveToDevice(DataExport.toJson(bundle.transactions, bundle.accounts, bundle.loans, bundle.categories, bundle.smsSenders),
                            "application/json", "maldar-export.json")
                        message = AppStrings.savedToDownloads
                    }.onFailure { message = it.message }
                    busy = false
                }
            }
        ) { Text("${AppStrings.exportJson} → ${AppStrings.saveToDevice}") }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.close) }
    }
}
