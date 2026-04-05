package com.localbank.finance.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

/**
 * Campo de texto com máscara de moeda brasileira (R$).
 * - [rawValue]: string de dígitos puros, ex: "1500" = R$ 15,00
 * - [onRawValueChange]: recebe somente dígitos
 *
 * Para converter na hora de salvar:
 *   val amount = (rawValue.toLongOrNull() ?: 0) / 100.0
 */
@Composable
fun CurrencyField(
    rawValue: String,
    onRawValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = { Text("Valor (R$)") }
) {
    OutlinedTextField(
        value = rawValue,
        onValueChange = { new ->
            onRawValueChange(new.filter { it.isDigit() }.take(10))
        },
        label = label,
        visualTransformation = CurrencyVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier
    )
}

class CurrencyVisualTransformation : VisualTransformation {
    private val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val cents = raw.toLongOrNull() ?: 0
        val formatted = if (cents == 0L && raw.isEmpty()) ""
                        else formatter.format(cents / 100.0)

        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = formatted.length
                override fun transformedToOriginal(offset: Int): Int = raw.length
            }
        )
    }
}
