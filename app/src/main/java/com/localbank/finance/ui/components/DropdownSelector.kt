package com.localbank.finance.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Dropdown reutilizável que funciona em todas as versões do Material3.
 * Usa interactionSource para detectar toques no TextField.
 */
@Composable
fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "Selecione",
    allowNone: Boolean = false,
    noneLabel: String = "Nenhum",
    onNoneSelected: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Abre o dropdown quando o campo é tocado
    LaunchedEffect(isPressed) {
        if (isPressed) expanded = true
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedItem?.let { itemLabel(it) } ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onNoneSelected?.invoke()
                        expanded = false
                    }
                )
            }
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
