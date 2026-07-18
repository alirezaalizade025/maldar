package com.personalfinance.tracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personalfinance.tracker.ui.screens.*
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.UpdateChecker
import com.personalfinance.tracker.BuildConfig
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomItems = listOf(
    NavItem("dashboard", AppStrings.navHome, Icons.Filled.Home),
    NavItem("add_transaction", AppStrings.navAdd, Icons.Filled.Add),
    NavItem("loans", AppStrings.navLoans, Icons.Filled.Payments),
    NavItem("reports", AppStrings.navReports, Icons.Filled.Assessment),
    NavItem("bank_accounts", AppStrings.navAccounts, Icons.Filled.AccountBalance),
)

@Composable
fun NavGraph(viewModel: FinanceViewModel, startDestinationOverride: String? = null) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showUpToDate by remember { mutableStateOf(false) }
    var showFailed by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    // Avoid showing the startup prompt more than once per session.
    var startupChecked by rememberSaveable { mutableStateOf(false) }

    fun runCheck(auto: Boolean) {
        scope.launch {
            when (val result = UpdateChecker.checkForUpdate()) {
                is UpdateChecker.UpdateResult.Available -> updateInfo = result.info
                is UpdateChecker.UpdateResult.UpToDate -> if (!auto) showUpToDate = true
                is UpdateChecker.UpdateResult.Error -> if (!auto) showFailed = true
            }
        }
    }

    // Automatic check on startup (once per session).
    LaunchedEffect(Unit) {
        if (!startupChecked) {
            startupChecked = true
            runCheck(auto = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.appName) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = AppStrings.menuUpdates)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        DropdownMenuItem(
                            text = { Text(AppStrings.menuUpdates, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; runCheck(auto = false) }
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.crashLog, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showCrashLog = true }
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.exportData, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showExport = true }
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.about, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showAbout = true }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestinationOverride ?: "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel, onGoToConfirm = { navController.navigate("confirm_sms_list") }, onGoToReports = { navController.navigate("reports") }) }
            composable("add_transaction") { AddTransactionScreen(viewModel) }
            composable("confirm_sms_list") { SmsConfirmationScreen(viewModel) }
            composable("bank_accounts") { BankAccountsScreen(viewModel) }
            composable("loans") { LoansScreen(viewModel) }
            composable("reports") { ReportsScreen(viewModel) }
        }
    }

    UpdateDialog(info = updateInfo, onDismiss = { updateInfo = null })
    if (showUpToDate) UpToDateDialog(onDismiss = { showUpToDate = false })
    if (showFailed) CheckFailedDialog(onDismiss = { showFailed = false })
    if (showCrashLog) CrashLogScreen(onClose = { showCrashLog = false })
    if (showExport) ExportScreen(viewModel, onClose = { showExport = false })
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(AppStrings.about) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(AppStrings.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(AppStrings.versionLabel.format(BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(AppStrings.close) } }
        )
    }
}
