package com.personalfinance.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.personalfinance.tracker.ui.navigation.NavGraph
import com.personalfinance.tracker.ui.theme.PersonalFinanceTheme
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import com.personalfinance.tracker.viewmodel.FinanceViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory((application as PersonalFinanceApp).repository)
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results not critical - screens re-check permission state as needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force right-to-left layout for the Persian (Farsi) UI. Must run AFTER
        // super.onCreate(): touching window.decorView earlier materializes the
        // decor view before the framework has initialized the activity/window,
        // which crashes on entry (right after the splash window).
        window.decorView.layoutDirection = android.util.LayoutDirection.RTL

        val permissionsNeeded = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions.launch(permissionsNeeded.toTypedArray())

        val openScreen = intent.getStringExtra("open_screen")
        val pendingId = intent.getLongExtra("pending_id", -1L)
        // Opening a backup file (e.g. from a file manager) hands us its content URI.
        val importUri = if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.toString()
        } else null

        setContent {
            PersonalFinanceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(
                        viewModel = viewModel,
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
