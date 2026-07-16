package com.personalfinance.tracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personalfinance.tracker.ui.screens.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomItems = listOf(
    NavItem("dashboard", "Home", Icons.Filled.Home),
    NavItem("add_transaction", "Add", Icons.Filled.Add),
    NavItem("loans", "Loans", Icons.Filled.Payments),
    NavItem("reports", "Reports", Icons.Filled.Assessment),
    NavItem("bank_accounts", "Accounts", Icons.Filled.AccountBalance),
)

@Composable
fun NavGraph(viewModel: FinanceViewModel, startDestinationOverride: String? = null) {
    val navController = rememberNavController()

    Scaffold(
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
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel, onGoToConfirm = { navController.navigate("confirm_sms_list") }) }
            composable("add_transaction") { AddTransactionScreen(viewModel) }
            composable("confirm_sms_list") { SmsConfirmationScreen(viewModel) }
            composable("bank_accounts") { BankAccountsScreen(viewModel) }
            composable("loans") { LoansScreen(viewModel) }
            composable("reports") { ReportsScreen(viewModel) }
        }
    }
}
