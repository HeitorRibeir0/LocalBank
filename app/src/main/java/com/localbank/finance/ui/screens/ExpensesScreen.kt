package com.localbank.finance.ui.screens

import com.localbank.finance.util.DateRanges
import com.localbank.finance.ui.util.rememberCurrencyFormat
import com.localbank.finance.ui.util.rememberDateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import com.localbank.finance.ui.util.label
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.*
import com.localbank.finance.ui.components.*
import com.localbank.finance.ui.util.CategoryColorPalette
import com.localbank.finance.ui.util.normalizeCategoryName
import com.localbank.finance.ui.viewmodel.ExpenseViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpensesScreen(viewModel: ExpenseViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val scheduled    by viewModel.scheduledExpenses.collectAsState()
    val categories   by viewModel.categories.collectAsState()
    val accounts     by viewModel.accounts.collectAsState()
    val currency = rememberCurrencyFormat()
    val sdf = rememberDateFormat("dd/MM/yyyy")

    val appColors = LocalAppColors.current
    var showDeleteTx by remember { mutableStateOf<Transaction?>(null) }
    var showEditTx by remember { mutableStateOf<Transaction?>(null) }
    var showEditScheduled by remember { mutableStateOf<ScheduledExpense?>(null) }

    // Seletor de mês
    val selectedCal = remember { mutableStateOf(Calendar.getInstance()) }
    val monthSdf = rememberDateFormat("MMMM yyyy")
    val isCurrentMonth = remember(selectedCal.value) {
        DateRanges.isSameMonth(selectedCal.value, Calendar.getInstance())
    }
    val (monthFrom, monthTo) = remember(selectedCal.value) { DateRanges.month(selectedCal.value) }

    val filteredTransactions = remember(transactions, monthFrom, monthTo) {
        transactions.filter { it.date in monthFrom..monthTo }
    }
    val pendingScheduled = remember(scheduled, monthFrom, monthTo) {
        scheduled.filter { !it.isPaid && it.dueDate in monthFrom..monthTo }
    }
    val paidScheduled = remember(scheduled, monthFrom, monthTo) {
        scheduled.filter { it.isPaid && it.dueDate in monthFrom..monthTo }
    }

    val categoryNames = remember(categories) { categories.associate { it.id to it.displayName } }

    Scaffold(
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Seletor de mês (sempre visível) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = {
                        val c = selectedCal.value.clone() as Calendar
                        c.add(Calendar.MONTH, -1)
                        selectedCal.value = c
                    }) {
                        Icon(Icons.Default.ChevronLeft, "Mês anterior", tint = OnDarkTextSecondary)
                    }
                    Text(
                        text = monthSdf.format(selectedCal.value.time)
                            .replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = if (isCurrentMonth) appColors.primary else OnDarkText,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            val c = selectedCal.value.clone() as Calendar
                            c.add(Calendar.MONTH, 1)
                            selectedCal.value = c
                        },
                        enabled = !isCurrentMonth
                    ) {
                        Icon(Icons.Default.ChevronRight, "Próximo mês",
                            tint = if (isCurrentMonth) OnDarkTextSecondary.copy(alpha = 0.3f)
                                   else OnDarkTextSecondary)
                    }
                }
            }

            // ── Estado vazio ──
            if (filteredTransactions.isEmpty() && pendingScheduled.isEmpty() && paidScheduled.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SwapVert, null,
                                Modifier.size(64.dp),
                                tint = OnDarkTextSecondary.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Nenhum lançamento neste mês.\nToque em Lançar para adicionar.",
                                color = OnDarkTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

                if (pendingScheduled.isNotEmpty()) {
                    item {
                        Text("Agendadas pendentes", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                    }
                    items(pendingScheduled, key = { "scheduled-${it.id}" }) { expense ->
                        ScheduledCard(
                            expense = expense, currency = currency, sdf = sdf,
                            onPay = { viewModel.payScheduledExpense(expense) },
                            onDelete = { viewModel.deleteScheduledExpense(expense) },
                            onEdit = { showEditScheduled = expense }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (paidScheduled.isNotEmpty()) {
                    item {
                        Text("Agendadas pagas", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkTextSecondary)
                    }
                    items(paidScheduled, key = { "scheduled-${it.id}" }) { expense ->
                        ScheduledCard(
                            expense = expense, currency = currency, sdf = sdf,
                            onPay = {},
                            onDelete = { viewModel.deleteScheduledExpense(expense) },
                            onEdit = { showEditScheduled = expense }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (filteredTransactions.isNotEmpty()) {
                    item {
                        Text("Histórico", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                    }
                    items(filteredTransactions, key = { "tx-${it.id}" }) { tx ->
                        TransactionCard(
                            transaction = tx,
                            categoryName = tx.categoryId?.let { categoryNames[it] },
                            currency = currency, sdf = sdf,
                            onDelete = { showDeleteTx = tx },
                            onEdit = { showEditTx = tx }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
    }

    showDeleteTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { showDeleteTx = null },
            containerColor = DarkCard,
            title = { Text("Excluir transação?", color = OnDarkText) },
            text = { Text("\"${tx.description.ifBlank { "Sem descrição" }}\" será removida.", color = OnDarkTextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTransaction(tx); showDeleteTx = null }) {
                    Text("Excluir", color = ExpenseRed)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteTx = null }) { Text("Cancelar", color = OnDarkTextSecondary) } }
        )
    }

    showEditTx?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            accounts = accounts,
            categories = categories,
            onDismiss = { showEditTx = null },
            onConfirm = { updated ->
                viewModel.updateTransaction(tx, updated)
                showEditTx = null
            }
        )
    }

    showEditScheduled?.let { expense ->
        EditScheduledDialog(
            expense = expense,
            accounts = accounts,
            categories = categories,
            onDismiss = { showEditScheduled = null },
            onConfirm = { updated ->
                viewModel.updateScheduledExpense(updated)
                showEditScheduled = null
            }
        )
    }
}

// ── Card de transação ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionCard(
    transaction: Transaction,
    categoryName: String?,
    currency: NumberFormat,
    sdf: SimpleDateFormat,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) c.success else c.textPrimary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onEdit),
        shape = RoundedCornerShape(CardTokens.radiusCompact),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(CardTokens.elevation)
    ) {
        Row(
            modifier = Modifier.padding(CardTokens.paddingInner),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemIconBox(
                icon = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                tint = if (isIncome) c.success else c.textSecondary,
                background = if (isIncome) c.success.copy(alpha = 0.12f)
                             else c.surfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            ItemLeading(
                title = transaction.description.ifBlank { if (isIncome) "Entrada" else "Saída" },
                metadata = buildString {
                    append(sdf.format(Date(transaction.date)))
                    if (categoryName != null) append(" · $categoryName")
                    if (transaction.createdBy != null) append(" · ${transaction.createdBy}")
                }
            )
            ItemTrailing(
                value = "${if (isIncome) "+" else "−"} ${currency.format(transaction.amount)}",
                valueColor = amountColor
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Deletar", modifier = Modifier.size(14.dp),
                        tint = c.textSecondary.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── Card de agendada ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledCard(
    expense: ScheduledExpense,
    currency: NumberFormat,
    sdf: SimpleDateFormat,
    onPay: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val isOverdue = daysLeft < 0

    val accentColor = when {
        isOverdue    -> c.error
        daysLeft <= 7 -> c.warning
        else          -> c.brandPrimary
    }
    val metaText = when {
        isOverdue     -> "Vencida há ${-daysLeft} dias"
        daysLeft == 0 -> "Vence hoje"
        else          -> "Vence ${sdf.format(Date(expense.dueDate))} · $daysLeft dias"
    }.let { if (expense.isRecurring && expense.recurrenceRule != null)
        "$it · ${expense.recurrenceRule.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }}"
    else it }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onEdit),
        shape = RoundedCornerShape(CardTokens.radiusCompact),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(CardTokens.elevation)
    ) {
        Row(
            modifier = Modifier.padding(CardTokens.paddingInner),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentBar(color = accentColor)
            Spacer(Modifier.width(12.dp))
            ItemLeading(
                title = expense.description.ifBlank { "Despesa agendada" },
                metadata = metaText,
                metaColor = if (isOverdue) c.error else c.textSecondary
            )
            ItemTrailing(
                value = currency.format(expense.amount),
                valueColor = if (expense.isPaid) c.textSecondary else c.error
            ) {
                if (expense.isPaid) {
                    StatusChip("Pago", ChipVariant.SUCCESS)
                } else if (isOverdue) {
                    StatusChip("Vencida", ChipVariant.ERROR)
                } else if (daysLeft == 0) {
                    StatusChip("Hoje", ChipVariant.WARNING)
                } else if (daysLeft <= 3) {
                    StatusChip("Em $daysLeft dias", ChipVariant.WARNING)
                }
            }
            Spacer(Modifier.width(4.dp))
            Spacer(Modifier.width(6.dp))
            if (!expense.isPaid) {
                ItemActionButton(
                    label = "Pagar",
                    icon = Icons.Default.Check,
                    onClick = onPay
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Deletar", modifier = Modifier.size(14.dp),
                    tint = c.textSecondary.copy(alpha = 0.5f))
            }
        }
    }
}

// ── Dialog para criar categoria ──
@Composable
fun AddCategoryDialog(
    type: TransactionType,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String) -> Unit
) {
    val appColors = LocalAppColors.current
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(CategoryColorPalette.first()) }
    var showError by remember { mutableStateOf(false) }

    val colors = CategoryColorPalette

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nova categoria (${if (type == TransactionType.INCOME) "entrada" else "saída"})", color = OnDarkText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; showError = false },
                    label = { Text("Nome") }, singleLine = true,
                    isError = showError && name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )
                Text("Cor:", fontSize = 13.sp, color = OnDarkTextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(colors) { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) }
                                    catch (_: Exception) { Color.Gray }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selectedColor == hex)
                                        Modifier.border(3.dp, appColors.primary, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                if (showError && name.isBlank()) {
                    Text("Preencha o nome", color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { showError = true; return@TextButton }
                onConfirm(name.trim(), selectedColor)
            }) { Text("Criar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScheduledDialog(
    expense: ScheduledExpense,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (ScheduledExpense) -> Unit
) {
    val appColors = LocalAppColors.current
    val sdf = rememberDateFormat("dd/MM/yyyy")

    var description by remember { mutableStateOf(expense.description) }
    var amountRaw by remember { mutableStateOf((expense.amount * 100).toLong().toString()) }
    var dueDateText by remember { mutableStateOf(sdf.format(Date(expense.dueDate))) }
    var isRecurring by remember { mutableStateOf(expense.isRecurring) }
    var recurrenceRule by remember { mutableStateOf(expense.recurrenceRule ?: RecurrenceRule.MONTHLY) }
    var selectedAccount by remember { mutableStateOf(accounts.find { it.id == expense.accountId }) }
    var selectedCategory by remember { mutableStateOf(categories.find { it.id == expense.categoryId }) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Editar agendada", color = OnDarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownSelector(
                    label = "Conta", items = accounts, selectedItem = selectedAccount,
                    onItemSelected = { selectedAccount = it }, itemLabel = { it.name }
                )

                val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
                DropdownSelector(
                    label = "Categoria (opcional)", items = expenseCategories,
                    selectedItem = selectedCategory?.takeIf { expenseCategories.contains(it) },
                    onItemSelected = { selectedCategory = it }, itemLabel = { it.displayName },
                    allowNone = true, noneLabel = "Sem categoria",
                    onNoneSelected = { selectedCategory = null }, placeholder = "Sem categoria"
                )

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrição") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )

                CurrencyField(
                    rawValue = amountRaw,
                    onRawValueChange = { amountRaw = it; showError = false },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueDateText, onValueChange = { dueDateText = it; showError = false },
                    label = { Text("Vencimento (dd/MM/yyyy)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isRecurring, onCheckedChange = { isRecurring = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = appColors.primary,
                            checkmarkColor = appColors.onPrimary
                        )
                    )
                    Text("Recorrente", color = OnDarkText)
                }

                if (isRecurring) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurrenceRule.entries.forEach { rule ->
                            FilterChip(
                                selected = recurrenceRule == rule,
                                onClick = { recurrenceRule = rule },
                                label = { Text(rule.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = appColors.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = appColors.primary
                                )
                            )
                        }
                    }
                }

                if (showError) {
                    Text("Verifique os campos", color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val account = selectedAccount
                val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0
                val dueDate = try { sdf.parse(dueDateText)?.time } catch (_: Exception) { null }
                if (account == null || amount <= 0 || dueDate == null) { showError = true; return@TextButton }
                onConfirm(
                    expense.copy(
                        accountId = account.id,
                        categoryId = selectedCategory?.id,
                        description = description.trim(),
                        amount = amount,
                        dueDate = dueDate,
                        isRecurring = isRecurring,
                        recurrenceRule = if (isRecurring) recurrenceRule else null
                    )
                )
            }) { Text("Salvar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )
}

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit
) {
    val appColors = LocalAppColors.current
    val currency = rememberCurrencyFormat()

    var description by remember { mutableStateOf(transaction.description) }
    var amountRaw by remember {
        mutableStateOf((transaction.amount * 100).toLong().toString())
    }
    var isIncome by remember { mutableStateOf(transaction.type == TransactionType.INCOME) }
    var selectedAccount by remember { mutableStateOf(accounts.find { it.id == transaction.accountId }) }
    var selectedCategory by remember { mutableStateOf(categories.find { it.id == transaction.categoryId }) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Editar transação", color = OnDarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tipo: Entrada / Saída
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isIncome, onClick = { isIncome = false },
                        label = { Text("Saída") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                            selectedLabelColor = ExpenseRed
                        )
                    )
                    FilterChip(
                        selected = isIncome, onClick = { isIncome = true },
                        label = { Text("Entrada") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                            selectedLabelColor = IncomeGreen
                        )
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )

                CurrencyField(
                    rawValue = amountRaw,
                    onRawValueChange = { amountRaw = it; showError = false },
                    label = { Text("Valor") },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownSelector(
                    label = "Conta",
                    items = accounts,
                    selectedItem = selectedAccount,
                    onItemSelected = { selectedAccount = it },
                    itemLabel = { it.name }
                )

                val filteredCategories = categories.filter {
                    it.type == if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
                }
                DropdownSelector(
                    label = "Categoria (opcional)",
                    items = filteredCategories,
                    selectedItem = selectedCategory?.takeIf { filteredCategories.contains(it) },
                    onItemSelected = { selectedCategory = it },
                    itemLabel = { it.displayName }
                )

                if (showError) {
                    Text("Preencha conta e valor", color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val account = selectedAccount
                val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0
                if (account == null || amount <= 0) { showError = true; return@TextButton }
                onConfirm(
                    transaction.copy(
                        accountId   = account.id,
                        categoryId  = selectedCategory?.id,
                        amount      = amount,
                        type        = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                        description = description.trim()
                    )
                )
            }) { Text("Salvar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )
}
