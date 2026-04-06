package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
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
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.model.displayName
import androidx.compose.foundation.BorderStroke
import com.localbank.finance.ui.components.CardTokens
import com.localbank.finance.ui.components.DropdownSelector
import com.localbank.finance.ui.viewmodel.ExpenseViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionsScreen(viewModel: ExpenseViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val categories   by viewModel.categories.collectAsState()
    val accounts     by viewModel.accounts.collectAsState()
    val appColors = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    var showDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<com.localbank.finance.data.model.Transaction?>(null) }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showDialog = true },
                containerColor = appColors.brandPrimaryDark,
                contentColor = appColors.textPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova transação")
            }
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhuma transação ainda.\nToque no + para adicionar.",
                    color = OnDarkTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    val isIncome = transaction.type == TransactionType.INCOME
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CardTokens.radiusCompact),
                        colors = CardDefaults.cardColors(containerColor = appColors.card),
                        elevation = CardDefaults.cardElevation(CardTokens.elevation),
                        border = CardTokens.border
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ícone: neutro para saída, sutil verde para entrada
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isIncome) appColors.success.copy(alpha = 0.12f)
                                        else appColors.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    null,
                                    tint = if (isIncome) appColors.success else appColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = transaction.description.ifBlank { if (isIncome) "Entrada" else "Saída" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = appColors.textPrimary
                                )
                                val catName = categories.find { it.id == transaction.categoryId }?.displayName
                                Text(
                                    text = buildString {
                                        append(sdf.format(Date(transaction.date)))
                                        if (catName != null) append(" · $catName")
                                    },
                                    fontSize = 12.sp,
                                    color = appColors.textSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                // Valor: verde para entrada, neutro para saída
                                Text(
                                    text = "${if (isIncome) "+" else "−"} ${currency.format(transaction.amount)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = if (isIncome) appColors.success else appColors.textPrimary
                                )
                                IconButton(
                                    onClick = { showDeleteDialog = transaction },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Deletar",
                                        modifier = Modifier.size(14.dp),
                                        tint = appColors.textSecondary.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        AddTransactionDialog(
            accounts   = accounts,
            categories = categories,
            onDismiss  = { showDialog = false },
            onConfirm  = { accountId, categoryId, description, amount, type ->
                viewModel.addTransaction(
                    accountId   = accountId,
                    categoryId  = categoryId,
                    amount      = amount,
                    type        = type,
                    description = description
                )
                showDialog = false
            }
        )
    }

    showDeleteDialog?.let { transaction ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DarkCard,
            title = { Text("Excluir transação?", color = OnDarkText) },
            text  = { Text("\"${transaction.description}\" será removida e o saldo da conta será ajustado.",
                color = OnDarkTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(transaction)
                    showDeleteDialog = null
                }) { Text("Excluir", color = ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar", color = OnDarkTextSecondary) }
            }
        )
    }
}

@Composable
fun AddTransactionDialog(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: String, categoryId: String?, description: String, amount: Double, type: TransactionType) -> Unit
) {
    val appColors = LocalAppColors.current
    var description by remember { mutableStateOf("") }
    var amountText  by remember { mutableStateOf("") }
    var isIncome    by remember { mutableStateOf(false) }
    var selectedAccount  by remember { mutableStateOf<Account?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(accounts) {
        if (selectedAccount == null && accounts.isNotEmpty()) {
            selectedAccount = accounts.first()
        }
    }

    val filteredCategories = categories.filter {
        it.type == if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
    }

    LaunchedEffect(isIncome) { selectedCategory = null }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nova transação", color = OnDarkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    FilterChip(
                        selected = !isIncome,
                        onClick  = { isIncome = false },
                        label    = { Text("Saída") },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                            selectedLabelColor = ExpenseRed
                        )
                    )
                    FilterChip(
                        selected = isIncome,
                        onClick  = { isIncome = true },
                        label    = { Text("Entrada") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                            selectedLabelColor = IncomeGreen
                        )
                    )
                }

                DropdownSelector(
                    label = "Conta",
                    items = accounts,
                    selectedItem = selectedAccount,
                    onItemSelected = { selectedAccount = it },
                    itemLabel = { it.name }
                )

                DropdownSelector(
                    label = "Categoria",
                    items = filteredCategories,
                    selectedItem = selectedCategory,
                    onItemSelected = { selectedCategory = it },
                    itemLabel = { it.name },
                    allowNone = true,
                    noneLabel = "Sem categoria",
                    onNoneSelected = { selectedCategory = null },
                    placeholder = "Sem categoria"
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; showError = false },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; showError = false },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = showError && (amountText.isBlank() || amountText.replace(",", ".").toDoubleOrNull() == null),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )

                if (showError) {
                    Text(
                        text = when {
                            selectedAccount == null -> "Selecione uma conta"
                            amountText.isBlank() -> "Preencha o valor"
                            else -> "Valor inválido"
                        },
                        color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val account = selectedAccount
                    val amount = amountText.replace(",", ".").toDoubleOrNull()
                    if (account != null && amount != null && amount > 0) {
                        onConfirm(
                            account.id,
                            selectedCategory?.id,
                            description,
                            amount,
                            if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
                        )
                    } else {
                        showError = true
                    }
                }
            ) { Text("Adicionar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) }
        }
    )
}