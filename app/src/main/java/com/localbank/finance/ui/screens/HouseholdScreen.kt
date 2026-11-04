package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.auth.AuthManager
import com.localbank.finance.sync.HouseholdManager
import com.localbank.ui.theme.*
import com.localbank.finance.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HouseholdScreen(onHouseholdReady: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }

    var inviteCode by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    var codeInput by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var confirmJoin by remember { mutableStateOf(false) }

    fun performJoin() {
        isJoining = true
        joinError = null
        scope.launch {
            try {
                val success = HouseholdManager.joinHousehold(context, AuthManager.displayName, codeInput)
                if (success) onHouseholdReady() else joinError = "Código inválido ou expirado"
            } catch (e: Exception) {
                joinError = "Erro: ${e.localizedMessage}"
            } finally {
                isJoining = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(LocalAppColors.current.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(LocalAppColors.current.gradientStart, LocalAppColors.current.gradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }

            Text(
                text = "Olá, ${AuthManager.displayName}!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary
            )

            Text(
                text = "Crie um lar para gerenciar finanças\nou entre no lar do seu parceiro(a)",
                textAlign = TextAlign.Center,
                color = LocalAppColors.current.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(8.dp))

            // ── CRIAR LAR ──
            if (!showJoin) {
                Button(
                    onClick = {
                        if (inviteCode != null) {
                            onHouseholdReady()
                            return@Button
                        }
                        showCreate = true
                        isCreating = true
                        scope.launch {
                            try {
                                val code = HouseholdManager.createHousehold(context, AuthManager.displayName)
                                inviteCode = code
                            } catch (e: Exception) {
                                joinError = "Erro ao criar: ${e.localizedMessage}"
                            } finally {
                                isCreating = false
                            }
                        }
                    },
                    enabled = !isCreating && !isJoining,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.brandPrimary,
                        contentColor = LocalAppColors.current.onBrandPrimary,
                        disabledContainerColor = LocalAppColors.current.brandPrimary.copy(alpha = 0.4f)
                    )
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Home, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Criar meu lar", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Mostrar código gerado
                inviteCode?.let { code ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Lar criado! Compartilhe o código:", fontSize = 14.sp, color = LocalAppColors.current.textSecondary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = code,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp,
                                color = LocalAppColors.current.brandPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(code))
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp), tint = LocalAppColors.current.brandPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Copiar código", color = LocalAppColors.current.brandPrimary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Válido por 1 hora",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { onHouseholdReady() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalAppColors.current.brandPrimary,
                                    contentColor = LocalAppColors.current.onBrandPrimary
                                )
                            ) {
                                Text("Continuar para o app", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── DIVISOR ──
            if (inviteCode == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = LocalAppColors.current.surfaceVariant)
                    Text("  ou  ", color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
                    HorizontalDivider(Modifier.weight(1f), color = LocalAppColors.current.surfaceVariant)
                }

                // ── ENTRAR NO LAR ──
                if (!showCreate || inviteCode == null) {
                    OutlinedButton(
                        onClick = { showJoin = true; showCreate = false },
                        enabled = !showJoin,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalAppColors.current.brandPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.brandPrimary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.People, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Entrar num lar existente", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (showJoin) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase().take(6); joinError = null },
                        label = { Text("Código de convite") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalAppColors.current.brandPrimary,
                            focusedLabelColor = LocalAppColors.current.brandPrimary,
                            cursorColor = LocalAppColors.current.brandPrimary
                        )
                    )

                    Button(
                        onClick = {
                            if (codeInput.length != 6) {
                                joinError = "O código tem 6 caracteres"
                                return@Button
                            }
                            isJoining = true
                            joinError = null
                            scope.launch {
                                // Se já há lançamentos neste celular, pede confirmação antes de substituí-los
                                val hasLocal = try {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getInstance(context).transactionDao().getAllOnce().isNotEmpty()
                                    }
                                } catch (_: Exception) { false }
                                if (hasLocal) {
                                    isJoining = false
                                    confirmJoin = true
                                } else {
                                    performJoin()
                                }
                            }
                        },
                        enabled = !isJoining && codeInput.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalAppColors.current.brandPrimary,
                            contentColor = LocalAppColors.current.onBrandPrimary,
                            disabledContainerColor = LocalAppColors.current.brandPrimary.copy(alpha = 0.4f)
                        )
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Entrar", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(onClick = { showJoin = false; codeInput = ""; joinError = null }) {
                        Text("Voltar", color = LocalAppColors.current.textSecondary)
                    }
                }
            }

            if (confirmJoin) {
                AlertDialog(
                    onDismissRequest = { confirmJoin = false },
                    containerColor = LocalAppColors.current.card,
                    title = { Text("Substituir dados deste celular?", color = LocalAppColors.current.textPrimary) },
                    text = {
                        Text(
                            "Este celular tem lançamentos que não estão no lar. Ao entrar, eles serão apagados e substituídos pelos dados do lar.",
                            color = LocalAppColors.current.textSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { confirmJoin = false; performJoin() }) {
                            Text("Entrar e substituir", color = LocalAppColors.current.brandPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmJoin = false }) {
                            Text("Cancelar", color = LocalAppColors.current.textSecondary)
                        }
                    }
                )
            }

            joinError?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.error.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(it, color = LocalAppColors.current.error, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
