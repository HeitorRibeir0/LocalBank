package com.localbank.finance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.map
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.database.AppDatabase
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
    // rememberSaveable: sobrevive à rotação e a outras recriações da Activity
    var selectedRoute by rememberSaveable { mutableStateOf(Screen.Dashboard.route) }
    val selectedScreen = screens.firstOrNull { it.route == selectedRoute } ?: Screen.Dashboard
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var showCaptures by rememberSaveable { mutableStateOf(false) }

    // Contagem de compras capturadas aguardando confirmação (fila local deste aparelho)
    val context = LocalContext.current
    val pendingDao = remember { AppDatabase.getInstance(context).pendingCaptureDao() }
    val pendingCountFlow = remember(pendingDao) { pendingDao.observeAll().map { it.size } }
    val pendingCount by pendingCountFlow.collectAsState(initial = 0)

    var showAddEntry by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val accounts by expenseViewModel.accounts.collectAsState()
    val categories by expenseViewModel.categories.collectAsState()

    // Alerta de orçamento vale para lançamentos feitos de qualquer aba
    LaunchedEffect(Unit) {
        expenseViewModel.budgetAlert.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    // Tablet, dobrável aberto ou paisagem: trilho lateral em vez da barra inferior
    val useRail = LocalConfiguration.current.screenWidthDp >= 600

    // Voltar fecha a tela sobreposta ou retorna ao Início antes de sair do app
    BackHandler(enabled = showCaptures || showAccounts || showProfile || selectedScreen != Screen.Dashboard) {
        when {
            showCaptures -> showCaptures = false
            showAccounts -> showAccounts = false
            showProfile  -> showProfile = false
            else         -> selectedRoute = Screen.Dashboard.route
        }
    }

    if (showCaptures) {
        PendingCapturesScreen(
            viewModel = expenseViewModel,
            onBack = { showCaptures = false }
        )
    } else if (showAccounts) {
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
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(snackbarData = data, containerColor = appColors.surfaceVariant,
                        contentColor = appColors.textPrimary)
                }
            },
            floatingActionButton = {
                // Lançar é a ação principal do app: disponível em todas as abas
                ExtendedFloatingActionButton(
                    onClick = { showAddEntry = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Lançar", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    containerColor = appColors.brandPrimary,
                    contentColor = appColors.onBrandPrimary,
                    shape = RoundedCornerShape(28.dp)
                )
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "LocalBank",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            color = appColors.textPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg,
                        titleContentColor = appColors.textPrimary,
                        actionIconContentColor = OnDarkTextSecondary
                    ),
                    actions = {
                        BadgedBox(badge = {
                            if (pendingCount > 0) Badge { Text("$pendingCount") }
                        }) {
                            IconButton(onClick = { showCaptures = true }) {
                                Icon(Icons.Default.Notifications, "Compras capturadas")
                            }
                        }
                        IconButton(onClick = { showProfile = true }) {
                            Icon(Icons.Default.AccountCircle, "Perfil")
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = OnDarkText,
                        tonalElevation = 0.dp
                    ) {
                        screens.forEach { screen ->
                            val selected = selectedScreen == screen
                            NavigationBarItem(
                                selected = selected,
                                onClick  = { selectedRoute = screen.route },
                                icon     = { Icon(screen.icon, contentDescription = screen.label) },
                                label    = { NavLabel(screen.label, selected) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = appColors.primary,
                                    selectedTextColor   = appColors.primary,
                                    unselectedIconColor = OnDarkTextSecondary,
                                    unselectedTextColor = OnDarkTextSecondary,
                                    // pill quase invisível — a cor do ícone já comunica seleção
                                    indicatorColor      = appColors.brandPrimary.copy(alpha = 0.10f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail) {
                    NavigationRail(containerColor = DarkSurface, contentColor = OnDarkText) {
                        Spacer(Modifier.weight(1f))
                        screens.forEach { screen ->
                            val selected = selectedScreen == screen
                            NavigationRailItem(
                                selected = selected,
                                onClick  = { selectedRoute = screen.route },
                                icon     = { Icon(screen.icon, contentDescription = screen.label) },
                                label    = { NavLabel(screen.label, selected) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor   = appColors.primary,
                                    selectedTextColor   = appColors.primary,
                                    unselectedIconColor = OnDarkTextSecondary,
                                    unselectedTextColor = OnDarkTextSecondary,
                                    indicatorColor      = appColors.brandPrimary.copy(alpha = 0.10f)
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = DarkBg
                ) {
                    // Em telas largas o conteúdo fica centralizado com largura legível
                    Box(contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
                            when (selectedScreen) {
                                Screen.Dashboard -> DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    onPayScheduled = expenseViewModel::payScheduledExpense,
                                    onOpenReport = { selectedRoute = Screen.Report.route }
                                )
                                Screen.Expenses  -> ExpensesScreen(viewModel = expenseViewModel)
                                Screen.Budget    -> BudgetScreen(viewModel = budgetViewModel)
                                Screen.Goals     -> SavingsGoalScreen(viewModel = savingsGoalViewModel)
                                Screen.Report    -> ReportScreen(viewModel = reportViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEntry) {
        AddEntrySheet(
            accounts = accounts,
            categories = categories,
            onDismiss = { showAddEntry = false },
            onAddTransaction = { accountId, categoryId, desc, amount, type, date ->
                expenseViewModel.addTransaction(accountId, categoryId, amount, type, desc, date)
                showAddEntry = false
            },
            onAddScheduled = { accountId, categoryId, desc, amount, dueDate, isRec, rule ->
                expenseViewModel.addScheduledExpense(accountId, categoryId, amount, desc, dueDate, isRec, rule)
                showAddEntry = false
            },
            onAddCategory = { name, colorHex, type -> expenseViewModel.addCategory(name, colorHex, type) }
        )
    }
}

private val MaxContentWidth = 840.dp

@Composable
private fun NavLabel(text: String, selected: Boolean) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1
    )
}
