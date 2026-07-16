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

        setContent {
            PersonalFinanceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(
                        viewModel = viewModel,
                        startDestinationOverride = when (openScreen) {
                            "confirm_sms" -> "confirm_sms_list"
                            "loans" -> "loans"
                            else -> null
                        }
                    )
                }
            }
        }
    }
}
