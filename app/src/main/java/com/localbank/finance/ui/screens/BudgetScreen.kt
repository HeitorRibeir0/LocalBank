package com.localbank.finance.ui.screens

import com.localbank.finance.ui.util.cents
import com.localbank.finance.ui.util.rememberCurrencyFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.ui.components.DropdownSelector
import com.localbank.finance.ui.viewmodel.BudgetViewModel
import com.localbank.finance.ui.viewmodel.BudgetWithProgress
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.util.*

@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val state by viewModel.uiState.collectAsState()
    val appColors = LocalAppColors.current
    val currency = rememberCurrencyFormat()
    var showDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(appColors.background),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Orçamento do mês", style = MaterialTheme.typography.headlineMedium, color = appColors.textPrimary)
                OutlinedButton(
                    onClick = { showDialog = true },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, appColors.surfaceVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary)
                ) { Text("Novo limite", fontWeight = FontWeight.SemiBold) }
            }
        }
        if (state.budgets.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.PieChart, null, Modifier.size(56.dp),
                        tint = appColors.textMuted.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhum limite definido", color = appColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Defina quanto o lar pode gastar por categoria. O app avisa em 80% e 100%.",
                        fontSize = 14.sp, color = appColors.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            items(state.budgets, key = { it.budget.id }) { item ->
                BudgetCard(item, currency) { viewModel.deleteBudget(item.budget) }
            }
        }
    }

    if (showDialog) {
        AddBudgetDialog(
            categories = state.categories.filter { it.type == TransactionType.EXPENSE },
            onDismiss = { showDialog = false },
            onConfirm = { catId, amount -> viewModel.addBudget(catId, amount); showDialog = false }
        )
    }
}

@Composable
fun BudgetCard(item: BudgetWithProgress, currency: NumberFormat, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val color = when {
        item.percentage >= 1.0f -> LocalAppColors.current.error
        item.percentage >= 0.8f -> LocalAppColors.current.warning
        else                    -> LocalAppColors.current.success
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Barra lateral de progresso
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.categoryName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LocalAppColors.current.textPrimary)
                        Text("${currency.cents(item.spentCents)} / ${currency.cents(item.budget.limitCents)}",
                            fontSize = 13.sp, color = LocalAppColors.current.textSecondary)
                    }
                    Text("${(item.percentage * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(20.dp),
                            tint = LocalAppColors.current.textSecondary.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.percentage.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = LocalAppColors.current.surfaceVariant
                )
                if (item.percentage >= 1.0f) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠ Estourado em ${currency.cents(item.spentCents - item.budget.limitCents)}",
                        fontSize = 12.sp, color = LocalAppColors.current.error, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = LocalAppColors.current.card,
            title = { Text("Excluir orçamento?", color = LocalAppColors.current.textPrimary) },
            text = { Text("Orçamento de \"${item.categoryName}\" será removido.", color = LocalAppColors.current.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("Excluir", color = LocalAppColors.current.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar", color = LocalAppColors.current.textSecondary) }
            }
        )
    }
}

@Composable
fun AddBudgetDialog(
    categories: List<Category>, onDismiss: () -> Unit,
    onConfirm: (categoryId: String, limitCents: Long) -> Unit
) {
    val appColors = LocalAppColors.current
    var selected by remember { mutableStateOf<Category?>(null) }
    var amountRaw by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.card,
        title = { Text("Novo orçamento", color = LocalAppColors.current.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSelector(
                    label = "Categoria", items = categories, selectedItem = selected,
                    onItemSelected = { selected = it; showError = false }, itemLabel = { it.name }
                )
                com.localbank.finance.ui.components.CurrencyField(
                    rawValue = amountRaw,
                    onRawValueChange = { amountRaw = it; showError = false },
                    label = { Text("Limite mensal") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Text(if (selected == null) "Selecione uma categoria" else "Preencha o valor",
                        color = LocalAppColors.current.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cat = selected
                val amount = amountRaw.toLongOrNull() ?: 0L
                if (cat != null && amount > 0) onConfirm(cat.id, amount)
                else showError = true
            }) { Text("Criar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = LocalAppColors.current.textSecondary) } }
    )
}
