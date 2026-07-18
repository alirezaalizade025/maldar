package com.personalfinance.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.navigation.NavGraph
import com.personalfinance.tracker.ui.theme.PersonalFinanceTheme
import com.personalfinance.tracker.util.CrashLogger
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import com.personalfinance.tracker.viewmodel.FinanceViewModelFactory
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory((application as PersonalFinanceApp).repository)
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results not critical - screens re-check permission state as needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Whole-startup guard: if anything on the startup path throws, log it and
        // show an on-screen error instead of crashing to the launcher, so the
        // actual cause is visible. Remove/relax once the startup crash is fixed.
        try {
            // Force right-to-left layout for the Persian (Farsi) UI. Must run AFTER
            // super.onCreate(): touching window.decorView earlier materializes the
            // decor view before the framework has initialized the activity/window.
            try {
                window.decorView.layoutDirection = android.util.LayoutDirection.RTL
            } catch (t: Throwable) {
                CrashLogger.log("startup: setting RTL layoutDirection failed", t)
            }

            try {
                val permissionsNeeded = mutableListOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                requestPermissions.launch(permissionsNeeded.toTypedArray())
            } catch (t: Throwable) {
                CrashLogger.log("startup: requesting permissions failed", t)
            }

            val openScreen = intent.getStringExtra("open_screen")
            // Opening a backup file (e.g. from a file manager) hands us its content URI.
            val importUri = if (intent.action == android.content.Intent.ACTION_VIEW) {
                intent.data?.toString()
            } else null

            // If the Application failed to initialize the database/repository, show
            // that real cause rather than a generic uninitialized-property error.
            (application as? PersonalFinanceApp)?.startupError?.let { throw it }

            // Touch the ViewModel here (inside the guard) so a repository/database
            // initialization failure is caught and reported rather than crashing.
            val vm: FinanceViewModel = viewModel

            setContent {
                PersonalFinanceTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NavGraph(
                            viewModel = vm,
                            startDestinationOverride = when (openScreen) {
                                "confirm_sms" -> "confirm_sms_list"
                                "loans" -> "loans"
                                else -> null
                            },
                            importUri = importUri
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            CrashLogger.log("FATAL startup error in MainActivity.onCreate", t)
            showStartupError(t)
        }
    }

    // Renders a minimal, dependency-free error screen with the full stack trace so
    // the startup failure is visible on-device (and saved to the crash log).
    private fun showStartupError(t: Throwable) {
        val trace = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()
        val details = buildString {
            append(CrashLogger.deviceInfo)
            append("\n")
            append(t.toString())
            append("\n\n")
            append(trace)
        }
        try {
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        StartupErrorContent(details)
                    }
                }
            }
        } catch (inner: Throwable) {
            // If even Compose can't come up, at least keep the log written.
            CrashLogger.log("startup: failed to render error screen", inner)
        }
    }

    // Re-deliver a backup file opened while the app was already running.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val ctx = this
                // Recreate the UI with the new import URI by relaunching the activity.
                val relaunch = android.content.Intent(ctx, MainActivity::class.java).apply {
                    action = android.content.Intent.ACTION_VIEW
                    data = uri
                    addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(relaunch)
                finish()
            }
        }
    }
}

@Composable
private fun StartupErrorContent(details: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Startup error",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
