package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.ui.components.DropdownSelector
import com.localbank.finance.ui.viewmodel.ExpenseViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val appColors = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Minhas contas", color = OnDarkText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = OnDarkTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg
                )
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = appColors.brandPrimaryDark,
                contentColor = appColors.textPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, "Nova conta")
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountBalance, null, Modifier.size(64.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhuma conta cadastrada.\nToque no + para adicionar.",
                        color = OnDarkTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { account ->
                    AccountCard(account = account, currency = currency,
                        onDelete = { showDeleteDialog = account })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type ->
                viewModel.addAccount(name, type)
                showAddDialog = false
            }
        )
    }

    showDeleteDialog?.let { account ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DarkCard,
            title = { Text("Excluir conta?", color = OnDarkText) },
            text = { Text("A conta \"${account.name}\" será excluída.", color = OnDarkTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(account)
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
fun AccountCard(account: Account, currency: NumberFormat, onDelete: () -> Unit) {
    val appColors = LocalAppColors.current
    val icon = when (account.type) {
        AccountType.WALLET   -> Icons.Default.AccountBalanceWallet
        AccountType.CHECKING -> Icons.Default.AccountBalance
        AccountType.SAVINGS  -> Icons.Default.Savings
    }
    val typeLabel = when (account.type) {
        AccountType.WALLET   -> "Carteira"
        AccountType.CHECKING -> "Conta corrente"
        AccountType.SAVINGS  -> "Poupança"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = appColors.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = OnDarkText)
                Text(typeLabel, fontSize = 12.sp, color = OnDarkTextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    currency.format(account.balance),
                    fontWeight = FontWeight.Bold,
                    color = if (account.balance >= 0) IncomeGreen else ExpenseRed
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(16.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: AccountType) -> Unit
) {
    val appColors = LocalAppColors.current
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.CHECKING) }
    var showError by remember { mutableStateOf(false) }

    val typeLabel: (AccountType) -> String = { t ->
        when (t) {
            AccountType.WALLET   -> "Carteira"
            AccountType.CHECKING -> "Conta corrente"
            AccountType.SAVINGS  -> "Poupança"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nova conta", color = OnDarkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; showError = false },
                    label = { Text("Nome (ex: Nubank, Itaú)") }, singleLine = true,
                    isError = showError && name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        focusedLabelColor = appColors.primary,
                        cursorColor = appColors.primary
                    )
                )
                DropdownSelector(
                    label = "Tipo",
                    items = AccountType.entries.toList(),
                    selectedItem = selectedType,
                    onItemSelected = { selectedType = it },
                    itemLabel = typeLabel
                )
                if (showError) {
                    Text("Preencha o nome", color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { showError = true; return@TextButton }
                onConfirm(name.trim(), selectedType)
            }) { Text("Criar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )
}
