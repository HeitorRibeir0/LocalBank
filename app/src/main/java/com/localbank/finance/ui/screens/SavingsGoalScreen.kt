package com.localbank.finance.ui.screens

import com.localbank.finance.ui.util.rememberCurrencyFormat
import com.localbank.finance.ui.util.rememberDateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    val c = LocalAppColors.current
    val currency = rememberCurrencyFormat()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf<SavingsGoal?>(null) }
    var showEditDialog by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmDelete by remember { mutableStateOf<SavingsGoal?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Metas", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary,
                    modifier = Modifier.semantics { heading() })
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    border = BorderStroke(1.5.dp, c.surfaceVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
                ) { Text("Nova meta", fontWeight = FontWeight.SemiBold) }
            }
        }

        if (goals.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Savings, null, Modifier.size(56.dp), tint = c.textMuted.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhuma meta ainda", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Crie uma meta para guardar dinheiro junto com o lar.",
                        fontSize = 14.sp, color = c.textSecondary)
                }
            }
        } else {
            item(key = "total") {
                val saved = goals.sumOf { it.savedAmount }
                val target = goals.sumOf { it.targetAmount }
                Column(Modifier.padding(top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("O lar já guardou", fontSize = 15.sp, color = c.textSecondary)
                    Text(currency.format(saved), style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp,
                        lineHeight = 50.sp), color = c.textPrimary, maxLines = 1)
                    Text(
                        if (goals.size == 1) "de ${currency.format(target)} na meta"
                        else "de ${currency.format(target)} nas ${goals.size} metas",
                        fontSize = 14.sp, color = c.textSecondary
                    )
                }
            }
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    currency = currency,
                    onDeposit = { showDepositDialog = goal },
                    onEdit = { showEditDialog = goal },
                    onDelete = { confirmDelete = goal }
                )
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

    confirmDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = c.card,
            title = { Text("Excluir \"${goal.name}\"?", color = c.textPrimary) },
            text = { Text("A meta some para todos do lar. O saldo das contas não muda.",
                color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(goal); confirmDelete = null }) {
                    Text("Excluir meta", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar", color = c.textSecondary) }
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
    val c = LocalAppColors.current
    val monthYear = rememberDateFormat("MMMM 'de' yyyy")
    val progress = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat() else 0f
    val isComplete = progress >= 1f
    val remaining = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)
    val goalColor = try { Color(android.graphics.Color.parseColor(goal.colorHex)) }
                    catch (_: Exception) { c.brandPrimary }
    var menuOpen by remember { mutableStateOf(false) }

    val deadlineText = when {
        goal.deadline == null -> "Sem prazo"
        else -> "Até ${monthYear.format(Date(goal.deadline))}"
    }
    val plan = when {
        isComplete -> "Meta concluída."
        goal.deadline == null -> "Faltam ${currency.format(remaining)}."
        else -> {
            val months = monthsUntil(goal.deadline)
            if (months <= 0) "O prazo passou. Faltam ${currency.format(remaining)}."
            else "Guardar ${currency.format(remaining / months)} por mês para chegar no prazo."
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(goal.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.textPrimary)
                Text(deadlineText, fontSize = 13.sp, color = c.textSecondary)
            }
            Text(
                if (isComplete) "Concluída" else "${(progress * 100).toInt()}%",
                fontSize = if (isComplete) 14.sp else 22.sp, fontWeight = FontWeight.ExtraBold, color = goalColor
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "Opções da meta", tint = c.textSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                    containerColor = c.surfaceVariant) {
                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Excluir", color = c.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.background)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(7.dp)).background(goalColor))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom) {
                Text(currency.format(goal.savedAmount), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = c.textPrimary)
                Text("de ${currency.format(goal.targetAmount)}", fontSize = 13.sp, color = c.textSecondary)
            }
        }

        HorizontalDivider(color = c.surfaceVariant)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plan, fontSize = 14.sp, lineHeight = 20.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
            if (!isComplete) {
                Button(
                    onClick = onDeposit,
                    colors = ButtonDefaults.buttonColors(containerColor = c.brandPrimary, contentColor = c.onBrandPrimary),
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Meses inteiros entre hoje e o prazo, contando o mês do prazo. */
private fun monthsUntil(deadline: Long): Int {
    val now = Calendar.getInstance()
    val end = Calendar.getInstance().apply { timeInMillis = deadline }
    if (end.before(now)) return 0
    val diff = (end.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12 + end.get(Calendar.MONTH) - now.get(Calendar.MONTH)
    return diff.coerceAtLeast(0) + 1
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val sdf = rememberDateFormat("dd/MM/yyyy")

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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
