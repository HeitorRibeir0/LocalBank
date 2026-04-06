package com.localbank.finance.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.ui.screens.*
import com.localbank.finance.ui.viewmodel.BudgetViewModel
import com.localbank.finance.ui.viewmodel.DashboardViewModel
import com.localbank.finance.ui.viewmodel.ExpenseViewModel
import com.localbank.finance.ui.viewmodel.ReportViewModel
import com.localbank.finance.ui.viewmodel.SavingsGoalViewModel
import com.localbank.ui.theme.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Início",     Icons.Default.Dashboard)
    object Expenses  : Screen("expenses",  "Despesas",   Icons.Default.SwapVert)
    object Budget    : Screen("budget",    "Orçamento",  Icons.Default.PieChart)
    object Goals     : Screen("goals",     "Metas",      Icons.Default.Savings)
    object Report    : Screen("report",    "Relatório",  Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    dashboardViewModel: DashboardViewModel,
    expenseViewModel: ExpenseViewModel,
    budgetViewModel: BudgetViewModel,
    reportViewModel: ReportViewModel,
    savingsGoalViewModel: SavingsGoalViewModel,
    onLogout: () -> Unit = {},
    onThemeChanged: (AppThemeType) -> Unit = {},
    onClearData: () -> Unit = {},
    onClearAllData: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val screens = listOf(Screen.Dashboard, Screen.Expenses, Screen.Budget, Screen.Goals, Screen.Report)
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var showProfile by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }

    if (showAccounts) {
        AccountsScreen(
            viewModel = expenseViewModel,
            onBack = { showAccounts = false }
        )
    } else if (showProfile) {
        ProfileScreen(
            onBack = { showProfile = false },
            onLogout = onLogout,
            onManageAccounts = { showProfile = false; showAccounts = true },
            onThemeChanged = onThemeChanged,
            onClearData = onClearData,
            onClearAllData = onClearAllData
        )
    } else {
        Scaffold(
            containerColor = DarkBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "LocalBank",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = appColors.primary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg,
                        titleContentColor = appColors.primary,
                        actionIconContentColor = OnDarkTextSecondary
                    ),
                    actions = {
                        IconButton(onClick = { showProfile = true }) {
                            Icon(Icons.Default.AccountCircle, "Perfil")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = OnDarkText,
                    tonalElevation = 0.dp
                ) {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick  = { selectedScreen = screen },
                            icon     = { Icon(screen.icon, contentDescription = screen.label) },
                            label    = { Text(screen.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = appColors.primary,
                                selectedTextColor = appColors.primary,
                                unselectedIconColor = OnDarkTextSecondary,
                                unselectedTextColor = OnDarkTextSecondary,
                                indicatorColor = appColors.primarySurface
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.padding(innerPadding),
                color = DarkBg
            ) {
                when (selectedScreen) {
                    Screen.Dashboard -> DashboardScreen(viewModel = dashboardViewModel)
                    Screen.Expenses  -> ExpensesScreen(viewModel = expenseViewModel)
                    Screen.Budget    -> BudgetScreen(viewModel = budgetViewModel)
                    Screen.Goals     -> SavingsGoalScreen(viewModel = savingsGoalViewModel)
                    Screen.Report    -> ReportScreen(viewModel = reportViewModel)
                }
            }
        }
    }
}