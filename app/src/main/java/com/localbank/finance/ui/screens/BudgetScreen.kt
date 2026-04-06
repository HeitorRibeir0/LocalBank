package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
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
                Icon(Icons.Default.Add, contentDescription = "Novo orçamento")
            }
        }
    ) { padding ->
        if (state.budgets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PieChart, null, Modifier.size(64.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhum orçamento definido", color = OnDarkTextSecondary)
                    Text("Defina limites por categoria para controlar seus gastos",
                        fontSize = 13.sp, color = OnDarkTextSecondary.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Text("Orçamentos do mês", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OnDarkText) }
                items(state.budgets) { item ->
                    BudgetCard(item, currency) { viewModel.deleteBudget(item.budget) }
                }
                item { Spacer(Modifier.height(80.dp)) }
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
    val color = when {
        item.percentage >= 1.0f -> ExpenseRed
        item.percentage >= 0.8f -> WarningAmber
        else                    -> IncomeGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
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
                        Text(item.categoryName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OnDarkText)
                        Text("${currency.format(item.spent)} / ${currency.format(item.budget.limitAmount)}",
                            fontSize = 13.sp, color = OnDarkTextSecondary)
                    }
                    Text("${(item.percentage * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(16.dp),
                            tint = OnDarkTextSecondary.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.percentage.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = DarkSurfaceVariant
                )
                if (item.percentage >= 1.0f) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠ Estourado em ${currency.format(item.spent - item.budget.limitAmount)}",
                        fontSize = 12.sp, color = ExpenseRed, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun AddBudgetDialog(
    categories: List<Category>, onDismiss: () -> Unit,
    onConfirm: (categoryId: String, limitAmount: Double) -> Unit
) {
    val appColors = LocalAppColors.current
    var selected by remember { mutableStateOf<Category?>(null) }
    var amountRaw by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Novo orçamento", color = OnDarkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cat = selected
                val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0
                if (cat != null && amount > 0) onConfirm(cat.id, amount)
                else showError = true
            }) { Text("Criar", color = appColors.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) } }
    )
}
