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
import kotlinx.coroutines.launch

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
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
                            colors = listOf(GradientEmeraldStart, GradientEmeraldEnd)
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
                color = OnDarkText
            )

            Text(
                text = "Crie um lar para gerenciar finanças\nou entre no lar do seu parceiro(a)",
                textAlign = TextAlign.Center,
                color = OnDarkTextSecondary,
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
                                val code = HouseholdManager.createHousehold(
                                    context,
                                    AuthManager.userId!!,
                                    AuthManager.displayName
                                )
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
                        containerColor = Emerald,
                        contentColor = OnEmerald,
                        disabledContainerColor = Emerald.copy(alpha = 0.4f)
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
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Lar criado! Compartilhe o código:", fontSize = 14.sp, color = OnDarkTextSecondary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = code,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp,
                                color = Emerald
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(code))
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp), tint = Emerald)
                                Spacer(Modifier.width(4.dp))
                                Text("Copiar código", color = Emerald)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Válido por 24 horas",
                                fontSize = 12.sp,
                                color = OnDarkTextSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { onHouseholdReady() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald,
                                    contentColor = OnEmerald
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
                    HorizontalDivider(Modifier.weight(1f), color = DarkSurfaceVariant)
                    Text("  ou  ", color = OnDarkTextSecondary, fontSize = 13.sp)
                    HorizontalDivider(Modifier.weight(1f), color = DarkSurfaceVariant)
                }

                // ── ENTRAR NO LAR ──
                if (!showCreate || inviteCode == null) {
                    OutlinedButton(
                        onClick = { showJoin = true; showCreate = false },
                        enabled = !showJoin,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Emerald
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald.copy(alpha = 0.5f))
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
                            focusedBorderColor = Emerald,
                            focusedLabelColor = Emerald,
                            cursorColor = Emerald
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
                                try {
                                    val success = HouseholdManager.joinHousehold(
                                        context,
                                        AuthManager.userId!!,
                                        AuthManager.displayName,
                                        codeInput
                                    )
                                    if (success) {
                                        onHouseholdReady()
                                    } else {
                                        joinError = "Código inválido ou expirado"
                                    }
                                } catch (e: Exception) {
                                    joinError = "Erro: ${e.localizedMessage}"
                                } finally {
                                    isJoining = false
                                }
                            }
                        },
                        enabled = !isJoining && codeInput.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald,
                            contentColor = OnEmerald,
                            disabledContainerColor = Emerald.copy(alpha = 0.4f)
                        )
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Entrar", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(onClick = { showJoin = false; codeInput = ""; joinError = null }) {
                        Text("Voltar", color = OnDarkTextSecondary)
                    }
                }
            }

            joinError?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(it, color = ExpenseRed, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
