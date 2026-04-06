package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.RecurrenceRule
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.ui.components.DropdownSelector
import com.localbank.finance.ui.viewmodel.ExpenseViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduledScreen(viewModel: ExpenseViewModel) {
    val scheduled  by viewModel.scheduledExpenses.collectAsState()
    val accounts   by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val appColors = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showDialog = true },
                containerColor = appColors.brandPrimaryDark,
                contentColor = appColors.textPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova despesa agendada")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pending = scheduled.filter { !it.isPaid }
            val paid    = scheduled.filter { it.isPaid }

            if (pending.isNotEmpty()) {
                item { Text("Pendentes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OnDarkText) }
                items(pending) { expense ->
                    ScheduledExpenseCard(
                        expense = expense, currency = currency, sdf = sdf,
                        onPay = { viewModel.payScheduledExpense(expense) },
                        onDelete = { viewModel.deleteScheduledExpense(expense) }
                    )
                }
            }

            if (paid.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pagas", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        color = OnDarkTextSecondary)
                }
                items(paid) { expense ->
                    ScheduledExpenseCard(
                        expense = expense, currency = currency, sdf = sdf,
                        onPay = {}, onDelete = { viewModel.deleteScheduledExpense(expense) }
                    )
                }
            }

            if (scheduled.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Schedule, null, Modifier.size(64.dp),
                                tint = OnDarkTextSecondary.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))
                            Text("Nenhuma despesa agendada", color = OnDarkTextSecondary)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showDialog) {
        AddScheduledDialog(
            accounts = accounts, categories = categories,
            onDismiss = { showDialog = false },
            onConfirm = { accountId, categoryId, desc, amount, dueDate, isRec, rule ->
                viewModel.addScheduledExpense(accountId, categoryId, amount, desc, dueDate, isRec, rule)
                showDialog = false
            }
        )
    }
}

@Composable
fun ScheduledExpenseCard(
    expense: ScheduledExpense, currency: NumberFormat, sdf: SimpleDateFormat,
    onPay: () -> Unit, onDelete: () -> Unit
) {
    val c = LocalAppColors.current
    val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val isUrgent = !expense.isPaid && daysLeft in 0..7
    val isOverdue = !expense.isPaid && daysLeft < 0

    // Barra lateral: cor só quando há problema
    val barColor = when {
        expense.isPaid -> c.surfaceVariant
        isOverdue      -> c.error
        isUrgent       -> c.warning
        else           -> c.surfaceVariant
    }

    val metaColor = when {
        expense.isPaid -> c.textSecondary
        isOverdue      -> c.error
        isUrgent       -> c.warning
        else           -> c.textSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description.ifBlank { "Despesa agendada" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (expense.isPaid) c.textSecondary else c.textPrimary
                )
                Text(
                    text = when {
                        expense.isPaid -> "Pago"
                        isOverdue      -> "Vencida há ${-daysLeft} dias"
                        daysLeft == 0  -> "Vence hoje"
                        else           -> "Vence ${sdf.format(Date(expense.dueDate))} · $daysLeft dias"
                    },
                    fontSize = 12.sp,
                    color = metaColor
                )
                if (expense.isRecurring && expense.recurrenceRule != null) {
                    Text(
                        "Recorrente · ${expense.recurrenceRule.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        fontSize = 11.sp,
                        color = c.textSecondary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Valor: neutro por padrão, vermelho só se vencida
                Text(
                    currency.format(expense.amount),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = when {
                        expense.isPaid -> c.textSecondary
                        isOverdue      -> c.error
                        else           -> c.textPrimary
                    }
                )
                if (expense.isPaid) {
                    com.localbank.finance.ui.components.StatusChip(
                        "Pago",
                        com.localbank.finance.ui.components.ChipVariant.SUCCESS
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            if (!expense.isPaid) {
                com.localbank.finance.ui.components.ItemActionButton(
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

@Composable
fun AddScheduledDialog(
    accounts: List<Account>, categories: List<Category>, onDismiss: () -> Unit,
    onConfirm: (String, String?, String, Double, Long, Boolean, RecurrenceRule?) -> Unit
) {
    val appColors = LocalAppColors.current
    var description    by remember { mutableStateOf("") }
    var amountText     by remember { mutableStateOf("") }
    var dueDateText    by remember { mutableStateOf("") }
    var isRecurring    by remember { mutableStateOf(false) }
    var recurrenceRule by remember { mutableStateOf(RecurrenceRule.MONTHLY) }
    var selectedAccount  by remember { mutableStateOf<Account?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showError by remember { mutableStateOf(false) }

    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    LaunchedEffect(accounts) {
        if (selectedAccount == null && accounts.isNotEmpty()) selectedAccount = accounts.first()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nova despesa agendada", color = OnDarkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSelector(
                    label = "Conta", items = accounts, selectedItem = selectedAccount,
                    onItemSelected = { selectedAccount = it }, itemLabel = { it.name }
                )
                DropdownSelector(
                    label = "Categoria", items = expenseCategories, selectedItem = selectedCategory,
                    onItemSelected = { selectedCategory = it }, itemLabel = { it.name },
                    allowNone = true, noneLabel = "Sem categoria",
                    onNoneSelected = { selectedCategory = null }, placeholder = "Sem categoria"
                )
                OutlinedTextField(value = description, onValueChange = { description = it; showError = false },
                    label = { Text("Descrição (ex: Aluguel)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary, focusedLabelColor = appColors.primary, cursorColor = appColors.primary
                    ))
                OutlinedTextField(value = amountText, onValueChange = { amountText = it; showError = false },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary, focusedLabelColor = appColors.primary, cursorColor = appColors.primary
                    ))
                OutlinedTextField(value = dueDateText, onValueChange = { dueDateText = it; showError = false },
                    label = { Text("Vencimento (dd/MM/yyyy)") }, singleLine = true,
                    isError = showError && dueDateText.isNotBlank() && try { sdf.parse(dueDateText); false } catch (_: Exception) { true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary, focusedLabelColor = appColors.primary, cursorColor = appColors.primary
                    ))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it },
                        colors = CheckboxDefaults.colors(checkedColor = appColors.primary, checkmarkColor = appColors.onPrimary))
                    Text("Despesa recorrente", color = OnDarkText)
                }
                if (isRecurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceRule.entries.forEach { rule ->
                            FilterChip(selected = recurrenceRule == rule, onClick = { recurrenceRule = rule },
                                label = { Text(rule.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = appColors.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = appColors.primary
                                ))
                        }
                    }
                }
                if (showError) {
                    Text(when {
                        selectedAccount == null -> "Selecione uma conta"
                        amountText.isBlank() -> "Preencha o valor"
                        else -> "Verifique a data (dd/MM/yyyy)"
                    }, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val account = selectedAccount
                val amount = amountText.replace(",", ".").toDoubleOrNull()
                val dueDate = try { sdf.parse(dueDateText)?.time } catch (_: Exception) { null }
                if (account != null && amount != null && amount > 0 && dueDate != null) {
                    onConfirm(account.id, selectedCategory?.id, description, amount, dueDate,
                        isRecurring, if (isRecurring) recurrenceRule else null)
                } else { showError = true }
            }) { Text("Adicionar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )
}