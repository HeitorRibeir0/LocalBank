package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.localbank.finance.data.model.SavingsGoal
import androidx.compose.foundation.BorderStroke
import com.localbank.finance.ui.components.CardTokens
import com.localbank.finance.ui.components.ChipVariant
import com.localbank.finance.ui.util.CategoryColorPalette
import com.localbank.finance.ui.components.CurrencyField
import com.localbank.finance.ui.components.ItemActionButton
import com.localbank.finance.ui.components.StatusChip
import com.localbank.finance.ui.viewmodel.SavingsGoalViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SavingsGoalScreen(viewModel: SavingsGoalViewModel) {
    val goals by viewModel.goals.collectAsState()
    val appColors = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    var showAddDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf<SavingsGoal?>(null) }
    var showEditDialog by remember { mutableStateOf<SavingsGoal?>(null) }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = appColors.brandPrimaryDark,
                contentColor = appColors.textPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova meta")
            }
        }
    ) { padding ->
        if (goals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Savings, null, Modifier.size(64.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhuma meta criada", color = OnDarkTextSecondary,
                        fontWeight = FontWeight.Medium)
                    Text("Toque no + para criar sua primeira meta",
                        fontSize = 13.sp, color = OnDarkTextSecondary.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Metas de economia", fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = OnDarkText)
                }
                items(goals) { goal ->
                    GoalCard(
                        goal = goal,
                        currency = currency,
                        onDeposit = { showDepositDialog = goal },
                        onEdit = { showEditDialog = goal },
                        onDelete = { viewModel.deleteGoal(goal) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        GoalFormDialog(
            title = "Nova meta",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, target, color, deadline ->
                viewModel.addGoal(name, target, color, deadline)
                showAddDialog = false
            }
        )
    }

    showDepositDialog?.let { goal ->
        DepositDialog(
            goal = goal,
            currency = currency,
            onDismiss = { showDepositDialog = null },
            onConfirm = { amount ->
                viewModel.deposit(goal, amount)
                showDepositDialog = null
            }
        )
    }

    showEditDialog?.let { goal ->
        GoalFormDialog(
            title = "Editar meta",
            initialName = goal.name,
            initialTarget = (goal.targetAmount * 100).toLong().toString(),
            initialColor = goal.colorHex,
            initialDeadline = goal.deadline,
            onDismiss = { showEditDialog = null },
            onConfirm = { name, target, color, deadline ->
                viewModel.updateGoal(goal.copy(name = name, targetAmount = target,
                    colorHex = color, deadline = deadline))
                showEditDialog = null
            }
        )
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoal,
    currency: NumberFormat,
    onDeposit: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val appColors = LocalAppColors.current
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val progress = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat() else 0f
    val isComplete = progress >= 1f
    val goalColor = try { Color(android.graphics.Color.parseColor(goal.colorHex)) }
                    catch (_: Exception) { appColors.primary }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardTokens.radius),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(CardTokens.elevation),
        border = CardTokens.border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(goalColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isComplete) Icons.Default.CheckCircle else Icons.Default.Savings,
                        null,
                        tint = if (isComplete) IncomeGreen else goalColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OnDarkText)
                    if (goal.deadline != null) {
                        Text("Meta: ${sdf.format(Date(goal.deadline))}",
                            fontSize = 12.sp, color = OnDarkTextSecondary)
                    }
                }
                if (isComplete) {
                    StatusChip("Concluída", ChipVariant.SUCCESS)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(16.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.6f))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(16.dp),
                        tint = OnDarkTextSecondary.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(currency.format(goal.savedAmount),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = goalColor)
                Text("de ${currency.format(goal.targetAmount)}",
                    fontSize = 13.sp, color = OnDarkTextSecondary)
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isComplete) IncomeGreen else goalColor,
                trackColor = DarkSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${(progress * 100).toInt()}% atingido",
                    fontSize = 12.sp, color = OnDarkTextSecondary)
                if (!isComplete) {
                    ItemActionButton(
                        label = "Depositar",
                        icon = Icons.Default.Add,
                        onClick = onDeposit
                    )
                }
            }
        }
    }
}

@Composable
private fun DepositDialog(
    goal: SavingsGoal,
    currency: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val appColors = LocalAppColors.current
    var amountRaw by remember { mutableStateOf("") }
    val remaining = goal.targetAmount - goal.savedAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Depositar em \"${goal.name}\"", color = OnDarkText,
            fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Faltam ${currency.format(remaining)} para a meta.",
                    color = OnDarkTextSecondary, fontSize = 13.sp)
                CurrencyField(
                    rawValue = amountRaw,
                    onRawValueChange = { amountRaw = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0
                if (amount > 0) onConfirm(amount)
            }) {
                Text("Confirmar", color = appColors.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) }
        }
    )
}

@Composable
private fun GoalFormDialog(
    title: String,
    initialName: String = "",
    initialTarget: String = "",
    initialColor: String = "#4CAF50",
    initialDeadline: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, target: Double, colorHex: String, deadline: Long?) -> Unit
) {
    val appColors = LocalAppColors.current
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    var name by remember { mutableStateOf(initialName) }
    var amountRaw by remember { mutableStateOf(initialTarget) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var deadlineText by remember {
        mutableStateOf(initialDeadline?.let { sdf.format(Date(it)) } ?: "")
    }
    var showError by remember { mutableStateOf(false) }

    val colors = CategoryColorPalette

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text(title, color = OnDarkText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; showError = false },
                    label = { Text("Nome da meta") }, singleLine = true,
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
                    label = { Text("Valor alvo") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it },
                    label = { Text("Prazo (dd/MM/yyyy) — opcional") },
                    singleLine = true,
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

                if (showError) {
                    Text("Preencha nome e valor", color = ExpenseRed,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0
                if (name.isBlank() || amount <= 0) { showError = true; return@TextButton }
                val deadline = if (deadlineText.isNotBlank())
                    try { sdf.parse(deadlineText)?.time } catch (_: Exception) { null }
                else null
                onConfirm(name.trim(), amount, selectedColor, deadline)
            }) {
                Text("Salvar", color = appColors.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = OnDarkTextSecondary) }
        }
    )
}
