package com.localbank.finance.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.localbank.finance.capture.CaptureSources
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.PendingCapture
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.model.displayName
import com.localbank.finance.ui.components.DropdownSelector
import com.localbank.finance.ui.util.rememberCurrencyFormat
import com.localbank.finance.ui.util.rememberDateFormat
import com.localbank.finance.ui.viewmodel.ExpenseViewModel
import com.localbank.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/** Compras detectadas nas notificações, para você confirmar, editar ou descartar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingCapturesScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val dao = remember { AppDatabase.getInstance(context).pendingCaptureDao() }
    val capturesFlow = remember(dao) { dao.observeAll() }
    val captures by capturesFlow.collectAsState(initial = emptyList())

    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val expenseCategories = remember(categories) {
        categories.filter { it.type == TransactionType.EXPENSE }
    }

    // Atualiza ao voltar das configurações de notificação
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Compras capturadas", color = appColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = OnDarkTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!listenerEnabled) {
                item(key = "setup") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.card)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Captura automática desligada",
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.textPrimary
                            )
                            Text(
                                "Para o LocalBank ler as compras de " +
                                    "${CaptureSources.labels().joinToString(", ")}, " +
                                    "ative o acesso às notificações.",
                                fontSize = 13.sp,
                                color = appColors.textSecondary
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.brandPrimary,
                                    contentColor = appColors.onBrandPrimary
                                )
                            ) { Text("Abrir configurações") }
                        }
                    }
                }
            }

            if (captures.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nenhuma compra para confirmar.\n" +
                                "Quando um desses apps notificar uma compra, ela aparece aqui.",
                            color = appColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(captures, key = { it.id }) { capture ->
                CaptureCard(
                    capture = capture,
                    accounts = accounts,
                    expenseCategories = expenseCategories,
                    onConfirm = { accountId, categoryId, description ->
                        // Lança como saída normal: passa pelo mesmo fluxo de sync e de alerta de orçamento
                        viewModel.addTransaction(
                            accountId = accountId,
                            categoryId = categoryId,
                            amount = capture.amount,
                            type = TransactionType.EXPENSE,
                            description = description,
                            date = capture.detectedAt
                        )
                        scope.launch(Dispatchers.IO) { dao.delete(capture.id) }
                    },
                    onDiscard = {
                        scope.launch(Dispatchers.IO) { dao.delete(capture.id) }
                    }
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(
    capture: PendingCapture,
    accounts: List<Account>,
    expenseCategories: List<Category>,
    onConfirm: (accountId: String, categoryId: String?, description: String) -> Unit,
    onDiscard: () -> Unit
) {
    val appColors = LocalAppColors.current
    val currency = rememberCurrencyFormat()
    val dateFmt = rememberDateFormat("dd/MM HH:mm")

    var description by remember(capture.id) { mutableStateOf(capture.merchant) }
    var account by remember(capture.id, accounts) { mutableStateOf(accounts.firstOrNull()) }
    var category by remember(capture.id) { mutableStateOf<Category?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.card)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currency.format(capture.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = appColors.textPrimary
                )
                Text(
                    "${capture.sourceLabel} · ${dateFmt.format(Date(capture.detectedAt))}",
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            DropdownSelector(
                label = "Conta",
                items = accounts,
                selectedItem = account,
                onItemSelected = { account = it },
                itemLabel = { it.name }
            )

            DropdownSelector(
                label = "Categoria (opcional)",
                items = expenseCategories,
                selectedItem = category,
                onItemSelected = { category = it },
                itemLabel = { it.displayName },
                allowNone = true,
                noneLabel = "Sem categoria",
                onNoneSelected = { category = null },
                placeholder = "Sem categoria"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDiscard) {
                    Text("Descartar", color = OnDarkTextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val selected = account ?: return@Button
                        onConfirm(
                            selected.id,
                            category?.id,
                            description.trim().ifBlank { capture.merchant }
                        )
                    },
                    enabled = account != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.brandPrimary,
                        contentColor = appColors.onBrandPrimary
                    )
                ) { Text("Confirmar") }
            }
        }
    }
}

/** true quando o LocalBank tem acesso às notificações neste aparelho. */
private fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
