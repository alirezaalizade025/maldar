package com.personalfinance.tracker.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
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

// Wraps an overlay screen (export/import/settings/crash log) in an opaque,
// full-screen surface with a top bar + close button so it always has a visible
// background and can be dismissed.
@Composable
private fun FullScreenSheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun NavGraph(
    viewModel: FinanceViewModel,
    startDestinationOverride: String? = null,
    importUri: String? = null
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showUpToDate by remember { mutableStateOf(false) }
    var showFailed by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCheckSms by remember { mutableStateOf(false) }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = AppStrings.menuUpdates)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
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
                            text = { Text(AppStrings.importData, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showImport = true }
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.checkSms, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showCheckSms = true }
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.settings, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showSettings = true }
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.about, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { menuExpanded = false; showAbout = true }
                        )
                    }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
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
            composable(
                route = "add_transaction?accountId={accountId}&smsDate={smsDate}",
                arguments = listOf(
                    androidx.navigation.navArgument("accountId") { type = androidx.navigation.NavType.LongType; defaultValue = 0L },
                    androidx.navigation.navArgument("smsDate") { type = androidx.navigation.NavType.LongType; defaultValue = 0L }
                )
            ) { backStackEntry ->
                val accId = backStackEntry.arguments?.getLong("accountId")?.takeIf { it != 0L }
                val smsDate = backStackEntry.arguments?.getLong("smsDate")?.takeIf { it != 0L }
                AddTransactionScreen(
                    viewModel = viewModel,
                    accountId = accId,
                    smsDate = smsDate,
                    onContinueToList = if (accId != null) ({ navController.popBackStack("account_sms/$accId", false) }) else null
                )
            }
            composable("confirm_sms_list") { SmsConfirmationScreen(viewModel) }
            composable("bank_accounts") { BankAccountsScreen(viewModel, navController = navController) }
            composable("loans") { LoansScreen(viewModel) }
            composable("reports") { ReportsScreen(viewModel) }
            composable("account_sms/{accountId}") { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: 0L
                val account = viewModel.bankAccounts.value.firstOrNull { it.id == accountId }
                if (account != null) {
                    AccountSmsScreen(
                        account = account,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSelectSms = { smsDateMillis -> navController.navigate("add_transaction?accountId=$accountId&smsDate=$smsDateMillis") }
                    )
                }
            }
        }
    }

    UpdateDialog(info = updateInfo, onDismiss = { updateInfo = null })
    if (showUpToDate) UpToDateDialog(onDismiss = { showUpToDate = false })
    if (showFailed) CheckFailedDialog(onDismiss = { showFailed = false })
    if (showCrashLog) FullScreenSheet(AppStrings.crashLog, { showCrashLog = false }) { CrashLogScreen(onClose = { showCrashLog = false }) }
    if (showExport) FullScreenSheet(AppStrings.exportData, { showExport = false }) { ExportScreen(viewModel, onClose = { showExport = false }) }
    if (showImport) {
        FullScreenSheet(AppStrings.importData, { showImport = false }) { ImportScreen(viewModel, initialUri = null, onClose = { showImport = false }) }
    }
    if (showSettings) {
        FullScreenSheet(AppStrings.settings, { showSettings = false }) { SettingsScreen(onClose = { showSettings = false }) }
    }
    if (showCheckSms) {
        FullScreenSheet(AppStrings.checkSms, { showCheckSms = false }) {
            SmsCheckScreen(
                viewModel = viewModel,
                onBack = { showCheckSms = false },
                onConfirmSms = { accountId, smsDateMillis ->
                    showCheckSms = false
                    navController.navigate("add_transaction?accountId=${accountId ?: 0L}&smsDate=$smsDateMillis")
                }
            )
        }
    }
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
