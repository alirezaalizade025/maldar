package com.personalfinance.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.fa
import com.personalfinance.tracker.util.UpdateChecker

@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo?,
    onDismiss: () -> Unit
) {
    if (info == null) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.updateAvailable) },
        text = { Text(AppStrings.updateAvailableBody.fa(info.version)) },
        confirmButton = {
            TextButton(onClick = {
                info.downloadUrl?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                onDismiss()
            }) { Text(AppStrings.download) }
        },
        dismissButton = {
            TextButton(onClick = {
                UpdateChecker.markSkipped(info.tag)
                onDismiss()
            }) { Text(AppStrings.skipVersion) }
        }
    )
}

@Composable
fun UpToDateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.upToDate) },
        text = { Text(AppStrings.upToDateBody.fa(UpdateChecker.CURRENT_VERSION_NAME)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}

@Composable
fun CheckFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.checkFailed) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
