package com.localbank.finance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.localbank.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localbank.finance.auth.AuthManager
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.sync.FirestoreSyncManager
import com.localbank.finance.sync.HouseholdManager
import com.localbank.finance.ui.MainNavigation
import com.localbank.finance.ui.screens.HouseholdScreen
import com.localbank.finance.ui.screens.LoginScreen
import com.localbank.ui.theme.LocalBankTheme
import com.localbank.ui.theme.*
import com.localbank.finance.ui.viewmodel.*
import com.localbank.finance.worker.NotificationScheduler
import com.localbank.finance.update.ApkInstaller
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) NotificationScheduler.schedule(this)
    }

    private suspend fun performLogout(syncManager: FirestoreSyncManager?) {
        syncManager?.close()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            AppDatabase.seedDefaults(db.openHelper.writableDatabase)
        }
        AuthManager.signOut(this@MainActivity)
        HouseholdManager.clearHouseholdId(this@MainActivity)
        runOnUiThread { recreate() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleNotifications()

        setContent {
            var currentTheme by remember {
                mutableStateOf(ThemeManager.getTheme(applicationContext))
            }

            LocalBankTheme(themeType = currentTheme) {
                AppRoot(
                    onThemeChanged = { newTheme ->
                        ThemeManager.setTheme(applicationContext, newTheme)
                        currentTheme = newTheme
                    }
                )
            }
        }
    }

    @Composable
    private fun AppRoot(
        onThemeChanged: (AppThemeType) -> Unit = {}
    ) {
        val appColors = LocalAppColors.current
        var updateInfo by remember { mutableStateOf<com.localbank.finance.update.UpdateInfo?>(null) }

        LaunchedEffect(Unit) {
            try {
                val info = com.localbank.finance.update.UpdateChecker.check(BuildConfig.VERSION_CODE)
                android.util.Log.d("MainActivity", "Update check: hasUpdate=${info.hasUpdate}, url=${info.downloadUrl}")
                if (info.hasUpdate && info.downloadUrl.isNotBlank()) {
                    updateInfo = info
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Update check failed: ${e.message}", e)
            }
        }

        updateInfo?.let { info ->
            var isDownloading by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isDownloading) updateInfo = null },
                containerColor = LocalAppColors.current.card,
                icon = {
                    Icon(Icons.Default.SystemUpdate, null,
                        tint = appColors.primary, modifier = Modifier.size(32.dp))
                },
                title = {
                    Text("Nova versão disponível", color = LocalAppColors.current.textPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("A versão ${info.latestVersion} está disponível.",
                            color = LocalAppColors.current.textSecondary)
                        if (isDownloading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = appColors.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Baixando...", color = LocalAppColors.current.textSecondary,
                                fontSize = androidx.compose.material3.MaterialTheme.typography.labelSmall.fontSize)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isDownloading = true
                            lifecycleScope.launch {
                                val result = ApkInstaller.downloadAndInstall(
                                    this@MainActivity,
                                    info.downloadUrl
                                )
                                if (result.isFailure) {
                                    isDownloading = false
                                    updateInfo = null
                                    // Erro: mostrar Toast ou log
                                }
                            }
                        },
                        enabled = !isDownloading
                    ) {
                        Text("Baixar", color = appColors.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { updateInfo = null },
                        enabled = !isDownloading
                    ) {
                        Text("Agora não", color = LocalAppColors.current.textSecondary)
                    }
                }
            )
        }

        // Estado de navegação: login → household → app
        var authState by rememberSaveable {
            mutableStateOf(
                if (AuthManager.isSignedIn) "check_household" else "login"
            )
        }
        var isLoggingOut by remember { mutableStateOf(false) }

        when (authState) {
            "login" -> {
                LoginScreen(
                    onLoginSuccess = { authState = "check_household" }
                )
            }

            "check_household" -> {
                var isChecking by remember { mutableStateOf(true) }
                var foundHousehold by remember { mutableStateOf(false) }

                // Tenta recuperar o lar do Firestore (caso reinstalou o app)
                LaunchedEffect(Unit) {
                    try {
                        val userId = AuthManager.userId
                        if (userId != null) {
                            val recovered = HouseholdManager.recoverHousehold(this@MainActivity, userId)
                            if (recovered != null) {
                                foundHousehold = true
                                authState = "app"
                            }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("MainActivity", "Recovery failed: ${e.javaClass.simpleName}", e)
                    }
                    isChecking = false
                }

                if (isChecking) {
                    Box(Modifier.fillMaxSize().background(LocalAppColors.current.background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = appColors.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Verificando seu lar...", color = LocalAppColors.current.textSecondary)
                        }
                    }
                } else if (!foundHousehold) {
                    // Não encontrou lar — mostrar tela para criar/entrar
                    HouseholdScreen(
                        onHouseholdReady = { authState = "app" }
                    )
                }
            }

            "app" -> {
                val householdId = HouseholdManager.getHouseholdId(this)

                if (householdId == null) {
                    // Sem household salvo — voltar para verificação
                    LaunchedEffect(Unit) {
                        authState = "check_household"
                    }
                    Box(Modifier.fillMaxSize().background(LocalAppColors.current.background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = appColors.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Carregando...", color = LocalAppColors.current.textSecondary)
                        }
                    }
                } else {
                    // Criar sync manager e repository com sync ativo
                    val syncManager = remember(householdId) { FirestoreSyncManager(db) }

                    val repository = remember(householdId) {
                        FinanceRepository(
                            accountDao = db.accountDao(),
                            categoryDao = db.categoryDao(),
                            transactionDao = db.transactionDao(),
                            scheduledExpenseDao = db.scheduledExpenseDao(),
                            budgetDao = db.budgetDao(),
                            savingsGoalDao = db.savingsGoalDao(),
                            pendingCaptureDao = db.pendingCaptureDao(),
                            syncManager = syncManager,
                            householdId = householdId,
                            database = db
                        )
                    }

                    val factory = remember(householdId) { ViewModelFactory(repository) }

                    // Só quem acabou de entrar no lar espera o download; o resto abre
                    // direto com os dados locais e sincroniza em segundo plano.
                    var isSyncing by remember(householdId) {
                        mutableStateOf(HouseholdManager.needsInitialDownload(this@MainActivity))
                    }

                    LaunchedEffect(householdId) {
                        val context = this@MainActivity
                        try {
                            if (HouseholdManager.needsInitialDownload(context)) {
                                syncManager.clearAndDownload(householdId)
                                HouseholdManager.clearNeedsInitialDownload(context)
                            } else if (!HouseholdManager.isInitialUploadDone(context)) {
                                // Não bloqueia: offline o Firestore enfileira e envia depois
                                syncManager.uploadAll(householdId) { ok ->
                                    if (ok) HouseholdManager.markInitialUploadDone(context)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Sync error: ${e.message}", e)
                        } finally {
                            syncManager.startListening(householdId)
                            // Recalcula saldos para corrigir divergências entre devices
                            try { syncManager.recalculateAllBalances() } catch (_: Exception) {}
                            isSyncing = false
                        }
                    }

                    // Cleanup listeners ao sair
                    DisposableEffect(syncManager) {
                        onDispose { syncManager.close() }
                    }

                    if (isLoggingOut) {
                        Box(Modifier.fillMaxSize().background(LocalAppColors.current.background), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = appColors.primary)
                                Spacer(Modifier.height(16.dp))
                                Text("Saindo da conta...", color = LocalAppColors.current.textSecondary)
                            }
                        }
                    } else if (isSyncing) {
                        Box(Modifier.fillMaxSize().background(LocalAppColors.current.background), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = appColors.primary)
                                Spacer(Modifier.height(16.dp))
                                Text("Sincronizando dados...", color = LocalAppColors.current.textSecondary)
                            }
                        }
                    } else {
                        val dashboardVm: DashboardViewModel = viewModel(key = "dashboard-$householdId", factory = factory)
                        val expenseVm: ExpenseViewModel = viewModel(key = "expense-$householdId", factory = factory)
                        val budgetVm: BudgetViewModel = viewModel(key = "budget-$householdId", factory = factory)
                        val reportVm: ReportViewModel = viewModel(key = "report-$householdId", factory = factory)
                        val savingsVm: com.localbank.finance.ui.viewmodel.SavingsGoalViewModel = viewModel(key = "savings-$householdId", factory = factory)

                        MainNavigation(
                            dashboardViewModel = dashboardVm,
                            expenseViewModel = expenseVm,
                            budgetViewModel = budgetVm,
                            reportViewModel = reportVm,
                            savingsGoalViewModel = savingsVm,
                            onLogout = {
                                isLoggingOut = true
                                lifecycleScope.launch {
                                    try {
                                        performLogout(syncManager)
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Logout error: ${e.message}", e)
                                    } finally {
                                        isLoggingOut = false
                                        authState = "login"
                                    }
                                }
                            },
                            onThemeChanged = onThemeChanged,
                            onClearData = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    // Remoto primeiro: os IDs locais ainda existem aqui
                                    try { syncManager.deleteHistoryRemote(householdId) } catch (_: Exception) {}
                                    db.transactionDao().deleteAll()
                                    db.scheduledExpenseDao().deleteAll()
                                    db.budgetDao().deleteAll()
                                    db.accountDao().resetAllBalances()
                                }
                            },
                            onClearAllData = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try { syncManager.deleteAllRemote(householdId) } catch (_: Exception) {}
                                    db.transactionDao().deleteAll()
                                    db.scheduledExpenseDao().deleteAll()
                                    db.budgetDao().deleteAll()
                                    db.savingsGoalDao().deleteAll()
                                    db.categoryDao().deleteAll()
                                    db.accountDao().deleteAll()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun scheduleNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    NotificationScheduler.schedule(this)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            NotificationScheduler.schedule(this)
        }
    }
}