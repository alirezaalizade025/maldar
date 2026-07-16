package com.personalfinance.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.personalfinance.tracker.util.AppStrings
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
        text = { Text(AppStrings.updateAvailableBody.format(info.version)) },
        confirmButton = {
            TextButton(onClick = {
                info.downloadUrl?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                onDismiss()
            }) { Text(AppStrings.download) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.later) } }
    )
}

@Composable
fun UpToDateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.upToDate) },
        text = { Text(AppStrings.upToDateBody.format(UpdateChecker.CURRENT_VERSION)) },
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
