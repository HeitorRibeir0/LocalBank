package com.localbank.finance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.*
import com.localbank.finance.ui.components.CurrencyVisualTransformation
import com.localbank.finance.ui.util.label
import com.localbank.finance.ui.util.rememberDateFormat
import com.localbank.ui.theme.AppColors
import com.localbank.ui.theme.LocalAppColors
import com.localbank.ui.theme.Manrope
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Folha de lançamento: entrada ou saída, agora (com data opcional) ou agendada.
 * Substitui o antigo diálogo — o valor vem primeiro e em destaque.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEntrySheet(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAddTransaction: (accountId: String, categoryId: String?, desc: String, amount: Double, type: TransactionType, date: Long) -> Unit,
    onAddScheduled: (accountId: String, categoryId: String?, desc: String, amount: Double, dueDate: Long, isRecurring: Boolean, recurrenceRule: RecurrenceRule?) -> Unit,
    onAddCategory: (name: String, colorHex: String, type: TransactionType) -> Unit
) {
    val c = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isIncome by remember { mutableStateOf(false) }
    var isScheduled by remember { mutableStateOf(false) }
    var amountRaw by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var date by remember { mutableStateOf<Long?>(null) }   // null = agora
    var isRecurring by remember { mutableStateOf(false) }
    var recurrenceRule by remember { mutableStateOf(RecurrenceRule.MONTHLY) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
    val tone = if (isIncome) c.success else c.error
    val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }
    val dateFormat = rememberDateFormat("dd/MM/yyyy")
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(accounts) {
        if (selectedAccount == null && accounts.isNotEmpty()) selectedAccount = accounts.first()
    }
    LaunchedEffect(isIncome) { selectedCategory = null }
    LaunchedEffect(isScheduled) { date = null }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    fun save() {
        val account = selectedAccount
        val amount = (amountRaw.toLongOrNull() ?: 0) / 100.0
        error = when {
            account == null -> "Escolha uma conta. Se não houver nenhuma, crie em Perfil › Contas."
            amount <= 0 -> "Informe o valor."
            isScheduled && date == null -> "Escolha a data de vencimento."
            else -> null
        }
        if (error != null || account == null) return
        if (isScheduled) {
            onAddScheduled(account.id, selectedCategory?.id, description.trim(), amount, date!!,
                isRecurring, if (isRecurring) recurrenceRule else null)
        } else {
            onAddTransaction(account.id, selectedCategory?.id, description.trim(), amount, type,
                date ?: System.currentTimeMillis())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Text("Novo lançamento", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

            Segmented(
                options = listOf("Saiu", "Entrou"),
                selected = if (isIncome) 1 else 0,
                selectedColor = tone,
                onSelect = { isIncome = it == 1; if (isIncome) isScheduled = false },
                c = c
            )

            // Valor em destaque
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Valor", fontSize = 14.sp, color = c.textSecondary)
                BasicTextField(
                    value = amountRaw,
                    onValueChange = { amountRaw = it.filter(Char::isDigit).take(10); error = null },
                    visualTransformation = CurrencyVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    cursorBrush = SolidColor(c.brandPrimary),
                    textStyle = TextStyle(fontFamily = Manrope, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp, color = tone),
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus)
                        .semantics { contentDescription = "Valor" },
                    decorationBox = { inner ->
                        Column {
                            Box(Modifier.heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                                if (amountRaw.isEmpty()) {
                                    Text("R$ 0,00", fontFamily = Manrope, fontSize = 44.sp,
                                        fontWeight = FontWeight.ExtraBold, color = c.textMuted.copy(alpha = 0.5f))
                                }
                                inner()
                            }
                            Box(Modifier.fillMaxWidth().height(2.dp).background(c.brandPrimary))
                        }
                    }
                )
            }

            // Categoria
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Categoria", fontSize = 14.sp, color = c.textSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.selectableGroup()
                ) {
                    filteredCategories.forEach { cat ->
                        ChoiceChip(
                            text = cat.displayName,
                            selected = selectedCategory?.id == cat.id,
                            dot = parseHex(cat.colorHex),
                            onClick = { selectedCategory = if (selectedCategory?.id == cat.id) null else cat },
                            c = c
                        )
                    }
                    AssistChip(
                        onClick = { showAddCategory = true },
                        label = { Text("Nova", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(40.dp),
                        colors = AssistChipDefaults.assistChipColors(labelColor = c.brandPrimary,
                            leadingIconContentColor = c.brandPrimary),
                        border = BorderStroke(1.dp, c.surfaceVariant)
                    )
                }
            }

            // Conta
            if (accounts.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Conta", fontSize = 14.sp, color = c.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                        accounts.forEach { acc ->
                            ChoiceChip(acc.name, selectedAccount?.id == acc.id, null, { selectedAccount = acc }, c)
                        }
                    }
                }
            }

            // Quando
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quando", fontSize = 14.sp, color = c.textSecondary)
                if (!isIncome) {
                    Segmented(listOf("Já aconteceu", "Agendar"), if (isScheduled) 1 else 0, c.brandPrimary,
                        { isScheduled = it == 1 }, c)
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, c.surfaceVariant),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = c.background, contentColor = c.textPrimary)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = c.textSecondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            date != null -> dateFormat.format(Date(date!!))
                            isScheduled -> "Escolher vencimento"
                            else -> "Hoje"
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isScheduled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isRecurring, onCheckedChange = { isRecurring = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.brandPrimary,
                                checkedThumbColor = c.onBrandPrimary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Repetir", fontSize = 15.sp, color = c.textPrimary)
                    }
                    if (isRecurring) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                            RecurrenceRule.entries.forEach { rule ->
                                ChoiceChip(rule.label, recurrenceRule == rule, null, { recurrenceRule = rule }, c)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Descrição (opcional)") }, placeholder = { Text("Ex.: padaria") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.brandPrimary, focusedLabelColor = c.brandPrimary,
                    cursorColor = c.brandPrimary, unfocusedBorderColor = c.surfaceVariant,
                    focusedContainerColor = c.background, unfocusedContainerColor = c.background
                )
            )

            error?.let { Text(it, color = c.error, fontSize = 14.sp) }

            Button(
                onClick = ::save,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.brandPrimary, contentColor = c.onBrandPrimary)
            ) {
                Text(
                    when {
                        isScheduled -> "Agendar despesa"
                        isIncome -> "Salvar entrada"
                        else -> "Salvar despesa"
                    },
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date?.let(::localToUtcDay)
            ?: localToUtcDay(System.currentTimeMillis() + if (isScheduled) 24L * 60 * 60 * 1000 else 0))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { utc ->
                        // Agendada vence à meia-noite local; lançamento passado fica ao meio-dia
                        date = utcDayToLocal(utc, hour = if (isScheduled) 0 else 12)
                        error = null
                    }
                    showDatePicker = false
                }) { Text("Confirmar", color = c.brandPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = c.textSecondary) } },
            colors = DatePickerDefaults.colors(containerColor = c.card)
        ) {
            DatePicker(
                state = picker,
                colors = DatePickerDefaults.colors(
                    containerColor = c.card,
                    selectedDayContainerColor = c.brandPrimary,
                    selectedDayContentColor = c.onBrandPrimary,
                    todayDateBorderColor = c.brandPrimary,
                    todayContentColor = c.brandPrimary
                )
            )
        }
    }

    if (showAddCategory) {
        AddCategoryDialog(
            type = type,
            onDismiss = { showAddCategory = false },
            onConfirm = { name, colorHex ->
                onAddCategory(name, colorHex, type)
                showAddCategory = false
            }
        )
    }
}

@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    selectedColor: Color,
    onSelect: (Int) -> Unit,
    c: AppColors
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.background).padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.surfaceVariant else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center
            ) {
                Text(text, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (on) selectedColor else c.textSecondary)
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, dot: Color?, onClick: () -> Unit, c: AppColors) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) c.surfaceVariant else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.5.dp, c.textPrimary, RoundedCornerShape(20.dp))
                else Modifier.border(1.dp, c.surfaceVariant, RoundedCornerShape(20.dp))
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
        Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) c.textPrimary else c.textSecondary)
    }
}

private fun parseHex(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }

// O DatePicker trabalha com meia-noite UTC; o app guarda horário local
private fun localToUtcDay(localMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMs }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcDayToLocal(utcMs: Long, hour: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, 0, 0)
    }.timeInMillis
}
