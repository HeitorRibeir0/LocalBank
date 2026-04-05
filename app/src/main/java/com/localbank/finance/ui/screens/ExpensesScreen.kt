package com.localbank.finance.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import com.localbank.finance.ui.components.CurrencyField
import com.localbank.finance.ui.components.DropdownSelector
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
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    val appColors = LocalAppColors.current
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteTx by remember { mutableStateOf<Transaction?>(null) }
    var showEditTx by remember { mutableStateOf<Transaction?>(null) }

    val pendingScheduled = scheduled.filter { !it.isPaid }
    val paidScheduled = scheduled.filter { it.isPaid }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = appColors.primary,
                contentColor = appColors.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova despesa")
            }
        }
    ) { padding ->
        if (transactions.isEmpty() && scheduled.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SwapVert, null,
                        Modifier.size(64.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nenhuma despesa ainda.\nToque no + para adicionar.",
                        color = OnDarkTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pendingScheduled.isNotEmpty()) {
                    item {
                        Text("Agendadas pendentes", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                    }
                    items(pendingScheduled) { expense ->
                        ScheduledCard(
                            expense = expense, currency = currency, sdf = sdf,
                            onPay = { viewModel.payScheduledExpense(expense) },
                            onDelete = { viewModel.deleteScheduledExpense(expense) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (paidScheduled.isNotEmpty()) {
                    item {
                        Text("Agendadas pagas", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkTextSecondary)
                    }
                    items(paidScheduled) { expense ->
                        ScheduledCard(
                            expense = expense, currency = currency, sdf = sdf,
                            onPay = {},
                            onDelete = { viewModel.deleteScheduledExpense(expense) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (transactions.isNotEmpty()) {
                    item {
                        Text("Histórico", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                    }
                    items(transactions) { tx ->
                        TransactionCard(
                            transaction = tx,
                            categoryName = categories.find { it.id == tx.categoryId }?.name,
                            currency = currency, sdf = sdf,
                            onDelete = { showDeleteTx = tx },
                            onEdit = { showEditTx = tx }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        AddExpenseDialog(
            accounts = accounts,
            categories = categories,
            onDismiss = { showDialog = false },
            onAddTransaction = { accountId, categoryId, desc, amount, type ->
                viewModel.addTransaction(accountId, categoryId, amount, type, desc)
                showDialog = false
            },
            onAddScheduled = { accountId, categoryId, desc, amount, dueDate, isRec, rule ->
                viewModel.addScheduledExpense(accountId, categoryId, amount, desc, dueDate, isRec, rule)
                showDialog = false
            },
            onAddCategory = { name, colorHex, type ->
                viewModel.addCategory(name, colorHex, type)
            }
        )
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
    val isIncome = transaction.type == TransactionType.INCOME

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onEdit
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Ícone com fundo colorido
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isIncome) IncomeGreen.copy(alpha = 0.12f)
                        else ExpenseRed.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    null,
                    tint = if (isIncome) IncomeGreen else ExpenseRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifBlank { if (isIncome) "Entrada" else "Saída" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnDarkText
                )
                Text(
                    text = buildString {
                        append(sdf.format(Date(transaction.date)))
                        if (categoryName != null) append(" • $categoryName")
                        if (transaction.createdBy != null) append(" • por ${transaction.createdBy}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkTextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncome) "+" else "-"} ${currency.format(transaction.amount)}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncome) IncomeGreen else ExpenseRed
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Deletar", modifier = Modifier.size(16.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ── Card de agendada ──
@Composable
fun ScheduledCard(
    expense: ScheduledExpense,
    currency: NumberFormat,
    sdf: SimpleDateFormat,
    onPay: () -> Unit,
    onDelete: () -> Unit
) {
    val appColors = LocalAppColors.current
    val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val isOverdue = daysLeft < 0

    val accentColor = when {
        isOverdue -> ExpenseRed
        daysLeft <= 7 -> WarningAmber
        else -> appColors.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Barra lateral de status
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description.ifBlank { "Despesa agendada" },
                    fontWeight = FontWeight.Medium, color = OnDarkText)
                Text(
                    text = when {
                        isOverdue     -> "Vencida há ${-daysLeft} dias"
                        daysLeft == 0 -> "Vence hoje"
                        else          -> "Vence ${sdf.format(Date(expense.dueDate))} ($daysLeft dias)"
                    },
                    fontSize = 12.sp,
                    color = if (isOverdue) ExpenseRed else OnDarkTextSecondary
                )
                if (expense.isRecurring && expense.recurrenceRule != null) {
                    Text("Recorrente: ${expense.recurrenceRule.name.lowercase()
                        .replaceFirstChar { it.uppercase() }}",
                        fontSize = 11.sp, color = OnDarkTextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    currency.format(expense.amount),
                    fontWeight = FontWeight.Bold,
                    color = if (expense.isPaid) OnDarkTextSecondary else ExpenseRed
                )
                if (expense.isPaid) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IncomeGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "PAGO",
                            color = IncomeGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            if (!expense.isPaid) {
                IconButton(onClick = onPay, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, "Pagar", tint = IncomeGreen, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Deletar", modifier = Modifier.size(16.dp),
                    tint = OnDarkTextSecondary.copy(alpha = 0.6f))
            }
        }
    }
}

// ── Dialog unificado com máscara R$ e + categoria ──
@Composable
fun AddExpenseDialog(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAddTransaction: (accountId: String, categoryId: String?, desc: String, amount: Double, type: TransactionType) -> Unit,
    onAddScheduled: (accountId: String, categoryId: String?, desc: String, amount: Double, dueDate: Long, isRecurring: Boolean, recurrenceRule: RecurrenceRule?) -> Unit,
    onAddCategory: (name: String, colorHex: String, type: TransactionType) -> Unit
) {
    val appColors = LocalAppColors.current
    var isIncome    by remember { mutableStateOf(false) }
    var isScheduled by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var amountRaw   by remember { mutableStateOf("") }
    var selectedAccount  by remember { mutableStateOf<Account?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showError by remember { mutableStateOf(false) }

    var dueDateText    by remember { mutableStateOf("") }
    var isRecurring    by remember { mutableStateOf(false) }
    var recurrenceRule by remember { mutableStateOf(RecurrenceRule.MONTHLY) }

    var showAddCategory by remember { mutableStateOf(false) }

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    LaunchedEffect(accounts) {
        if (selectedAccount == null && accounts.isNotEmpty()) selectedAccount = accounts.first()
    }

    val currentType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
    val filteredCategories = categories.filter { it.type == currentType }

    LaunchedEffect(isIncome) { selectedCategory = null }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nova despesa", color = OnDarkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Tipo
                Row {
                    FilterChip(selected = !isIncome, onClick = { isIncome = false },
                        label = { Text("Saída") }, modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                            selectedLabelColor = ExpenseRed
                        ))
                    FilterChip(selected = isIncome, onClick = { isIncome = true },
                        label = { Text("Entrada") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                            selectedLabelColor = IncomeGreen
                        ))
                }

                // Quando
                Row {
                    FilterChip(selected = !isScheduled, onClick = { isScheduled = false },
                        label = { Text("Agora") }, modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = appColors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = appColors.primary
                        ))
                    FilterChip(selected = isScheduled, onClick = { isScheduled = true },
                        label = { Text("Agendada") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = appColors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = appColors.primary
                        ))
                }

                DropdownSelector(
                    label = "Conta", items = accounts, selectedItem = selectedAccount,
                    onItemSelected = { selectedAccount = it }, itemLabel = { it.name }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        DropdownSelector(
                            label = "Categoria", items = filteredCategories,
                            selectedItem = selectedCategory,
                            onItemSelected = { selectedCategory = it }, itemLabel = { it.name },
                            allowNone = true, noneLabel = "Sem categoria",
                            onNoneSelected = { selectedCategory = null }, placeholder = "Sem categoria"
                        )
                    }
                    IconButton(onClick = { showAddCategory = true }) {
                        Icon(Icons.Default.Add, "Adicionar categoria", tint = appColors.primary)
                    }
                }

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

                if (isScheduled) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RecurrenceRule.entries.forEach { rule ->
                                FilterChip(
                                    selected = recurrenceRule == rule,
                                    onClick = { recurrenceRule = rule },
                                    label = {
                                        Text(rule.name.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 12.sp)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = appColors.primary.copy(alpha = 0.2f),
                                        selectedLabelColor = appColors.primary
                                    )
                                )
                            }
                        }
                    }
                }

                if (showError) {
                    Text(
                        when {
                            selectedAccount == null -> "Selecione uma conta"
                            amountRaw.isBlank() -> "Preencha o valor"
                            isScheduled && dueDateText.isBlank() -> "Preencha a data"
                            else -> "Verifique os campos"
                        },
                        color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val account = selectedAccount
                val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0

                if (account == null || amount <= 0) {
                    showError = true
                    return@TextButton
                }

                if (isScheduled) {
                    val dueDate = try { sdf.parse(dueDateText)?.time } catch (_: Exception) { null }
                    if (dueDate == null) { showError = true; return@TextButton }
                    onAddScheduled(
                        account.id, selectedCategory?.id, description, amount,
                        dueDate, isRecurring, if (isRecurring) recurrenceRule else null
                    )
                } else {
                    onAddTransaction(
                        account.id, selectedCategory?.id, description, amount, currentType
                    )
                }
            }) { Text("Adicionar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )

    if (showAddCategory) {
        AddCategoryDialog(
            type = currentType,
            onDismiss = { showAddCategory = false },
            onConfirm = { name, colorHex ->
                onAddCategory(name, colorHex, currentType)
                showAddCategory = false
            }
        )
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
    var selectedColor by remember { mutableStateOf("#FF5722") }
    var showError by remember { mutableStateOf(false) }

    val colors = listOf(
        "#FF5722", "#FF9800", "#FFC107", "#4CAF50", "#8BC34A",
        "#009688", "#00BCD4", "#2196F3", "#3F51B5", "#9C27B0",
        "#E91E63", "#F44336", "#795548", "#607D8B", "#9E9E9E"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nova categoria (${if (type == TransactionType.INCOME) "entrada" else "saída"})", color = OnDarkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit
) {
    val appColors = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    itemLabel = { it.name }
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
