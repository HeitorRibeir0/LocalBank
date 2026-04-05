package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onManageAccounts: () -> Unit = {},
    onThemeChanged: (AppThemeType) -> Unit = {},
    onClearData: () -> Unit = {},
    onClearAllData: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val appColors = LocalAppColors.current

    val householdId = HouseholdManager.getHouseholdId(context)
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var newInviteCode by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var joinSuccess by remember { mutableStateOf(false) }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(ThemeManager.getTheme(context)) }

    LaunchedEffect(householdId) {
        if (householdId != null) {
            try {
                memberNames = HouseholdManager.getMemberNames(householdId)
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Minha conta", color = OnDarkText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = OnDarkTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Info do usuário ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(appColors.gradientStart, appColors.gradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = AuthManager.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = OnDarkText
                        )
                        Text(
                            text = AuthManager.currentUser?.email ?: "",
                            fontSize = 13.sp,
                            color = OnDarkTextSecondary
                        )
                    }
                }
            }

            // ── Tema do app ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(appColors.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, null, Modifier.size(20.dp), tint = appColors.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Tema do app", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OnDarkText)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Emerald theme option
                        ThemeOption(
                            name = "Emerald",
                            color1 = GradientEmeraldStart,
                            color2 = GradientEmeraldEnd,
                            isSelected = selectedTheme == AppThemeType.EMERALD,
                            onClick = {
                                selectedTheme = AppThemeType.EMERALD
                                onThemeChanged(AppThemeType.EMERALD)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        // Méliuz theme option
                        ThemeOption(
                            name = "Méliuz",
                            color1 = GradientPinkStart,
                            color2 = GradientPinkEnd,
                            isSelected = selectedTheme == AppThemeType.MELIUZ,
                            onClick = {
                                selectedTheme = AppThemeType.MELIUZ
                                onThemeChanged(AppThemeType.MELIUZ)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Lar financeiro ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(appColors.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Home, null, Modifier.size(20.dp), tint = appColors.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Lar financeiro", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OnDarkText)
                    }

                    if (householdId != null) {
                        Spacer(Modifier.height(16.dp))

                        Text("Membros:", fontSize = 13.sp, color = OnDarkTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        memberNames.values.forEach { name ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, Modifier.size(16.dp),
                                    tint = OnDarkTextSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontSize = 14.sp, color = OnDarkText)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = DarkSurfaceVariant)
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Convide seu parceiro(a) para o lar:",
                            fontSize = 13.sp,
                            color = OnDarkTextSecondary
                        )
                        Spacer(Modifier.height(8.dp))

                        if (newInviteCode != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = newInviteCode!!,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp,
                                        color = appColors.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    TextButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(newInviteCode!!))
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = appColors.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copiar", fontSize = 13.sp, color = appColors.primary)
                                    }
                                    Text("Válido por 1 hora", fontSize = 11.sp,
                                        color = OnDarkTextSecondary)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isGenerating = true
                                    scope.launch {
                                        try {
                                            val code = HouseholdManager.createHousehold(
                                                context,
                                                AuthManager.userId!!,
                                                AuthManager.displayName
                                            )
                                            newInviteCode = code
                                        } catch (_: Exception) {}
                                        isGenerating = false
                                    }
                                },
                                enabled = !isGenerating,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primary)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = appColors.primary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gerar código de convite")
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Você ainda não está em nenhum lar.",
                            fontSize = 13.sp,
                            color = OnDarkTextSecondary
                        )
                    }
                }
            }

            // ── Gerenciar contas ──
            OutlinedButton(
                onClick = onManageAccounts,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primary)
            ) {
                Icon(Icons.Default.AccountBalance, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Gerenciar contas")
            }

            // ── Entrar em outro lar ──
            OutlinedButton(
                onClick = { showJoinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primary)
            ) {
                Icon(Icons.Default.People, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Entrar no lar de outra pessoa")
            }

            // ── Limpar histórico ──
            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber)
            ) {
                Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limpar histórico")
            }

            // ── Limpeza total ──
            OutlinedButton(
                onClick = { showClearAllConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed)
            ) {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limpeza total")
            }

            Spacer(Modifier.weight(1f))

            // ── Sair ──
            OutlinedButton(
                onClick = {
                    AuthManager.signOut(context)
                    HouseholdManager.clearHouseholdId(context)
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ExpenseRed
                )
            ) {
                Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sair da conta")
            }
        }
    }

    // Dialog confirmar limpeza
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = DarkCard,
            icon = {
                Icon(Icons.Default.Warning, null, tint = WarningAmber, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Limpar histórico", color = OnDarkText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Isso vai apagar permanentemente:",
                        color = OnDarkText,
                        fontWeight = FontWeight.Medium
                    )
                    Text("• Todas as transações", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Text("• Todas as despesas agendadas", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Text("• Todos os orçamentos", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠️ Suas contas e categorias serão mantidas.\nEssa ação não pode ser desfeita.",
                        color = WarningAmber,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearData()
                        showClearConfirm = false
                    }
                ) {
                    Text("Limpar tudo", color = ExpenseRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar", color = OnDarkTextSecondary)
                }
            }
        )
    }

    // Dialog confirmar limpeza total
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = DarkCard,
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = ExpenseRed, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Limpeza total", color = OnDarkText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Isso vai apagar permanentemente:",
                        color = OnDarkText,
                        fontWeight = FontWeight.Medium
                    )
                    Text("• Todas as transações", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Text("• Todas as despesas agendadas", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Text("• Todos os orçamentos", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Text("• Todas as contas", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Text("• Todas as categorias", color = OnDarkTextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tudo será apagado para todos os membros do lar. Essa ação não pode ser desfeita.",
                        color = ExpenseRed,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllData()
                        showClearAllConfirm = false
                    }
                ) {
                    Text("Apagar tudo", color = ExpenseRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancelar", color = OnDarkTextSecondary)
                }
            }
        )
    }

    // Dialog para entrar em outro lar
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; joinError = null; joinCode = "" },
            containerColor = DarkCard,
            title = { Text("Entrar num lar", color = OnDarkText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digite o código de 6 caracteres que seu parceiro(a) compartilhou:",
                        color = OnDarkTextSecondary)
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(6); joinError = null },
                        label = { Text("Código") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.primary,
                            focusedLabelColor = appColors.primary,
                            cursorColor = appColors.primary
                        )
                    )
                    if (joinSuccess) {
                        Text("✅ Conectado com sucesso!", color = appColors.primary,
                            fontWeight = FontWeight.Medium)
                    }
                    joinError?.let {
                        Text(it, color = ExpenseRed, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (joinCode.length != 6) {
                            joinError = "O código tem 6 caracteres"
                            return@TextButton
                        }
                        isJoining = true
                        joinError = null
                        scope.launch {
                            try {
                                val success = HouseholdManager.joinHousehold(
                                    context, AuthManager.userId!!,
                                    AuthManager.displayName, joinCode
                                )
                                if (success) {
                                    joinSuccess = true
                                } else {
                                    joinError = "Código inválido ou expirado"
                                }
                            } catch (e: Exception) {
                                joinError = "Erro: ${e.localizedMessage}"
                            }
                            isJoining = false
                        }
                    },
                    enabled = !isJoining && joinCode.length == 6 && !joinSuccess
                ) {
                    if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), color = appColors.primary, strokeWidth = 2.dp)
                    else Text("Entrar", color = appColors.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinDialog = false; joinError = null; joinCode = ""; joinSuccess = false
                }) { Text(if (joinSuccess) "Fechar" else "Cancelar", color = OnDarkTextSecondary) }
            }
        )
    }
}

@Composable
private fun ThemeOption(
    name: String,
    color1: Color,
    color2: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, color1, RoundedCornerShape(14.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(color1, color2))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, Modifier.size(24.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) color1 else OnDarkTextSecondary
            )
        }
    }
}
