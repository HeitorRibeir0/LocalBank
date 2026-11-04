package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.localbank.finance.auth.AuthManager
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.sync.HouseholdManager
import com.localbank.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var confirmJoin by remember { mutableStateOf(false) }
    var inviteError by remember { mutableStateOf<String?>(null) }

    // Entrar num lar: a activity é recriada para o sync reinicializar com o lar novo
    fun performJoin() {
        isJoining = true
        joinError = null
        scope.launch {
            try {
                val success = HouseholdManager.joinHousehold(context, AuthManager.displayName, joinCode)
                if (success) {
                    joinSuccess = true
                    context.findActivity()?.recreate()
                } else {
                    joinError = "Código inválido ou expirado"
                }
            } catch (e: Exception) {
                joinError = "Erro: ${e.localizedMessage}"
            } finally {
                isJoining = false
            }
        }
    }

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
        containerColor = LocalAppColors.current.background,
        topBar = {
            TopAppBar(
                title = { Text("Minha conta", color = LocalAppColors.current.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = LocalAppColors.current.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Info do usuário ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card)
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
                            color = LocalAppColors.current.textPrimary
                        )
                        Text(
                            text = AuthManager.currentUser?.email ?: "",
                            fontSize = 13.sp,
                            color = LocalAppColors.current.textSecondary
                        )
                    }
                }
            }

            // ── Tema do app ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card)
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
                        Text("Tema do app", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tema Esmeralda (verde)
                        ThemeOption(
                            name = "Esmeralda",
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
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card)
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
                        Text("Lar financeiro", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                    }

                    if (householdId != null) {
                        Spacer(Modifier.height(16.dp))

                        Text("Membros:", fontSize = 13.sp, color = LocalAppColors.current.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        memberNames.values.forEach { name ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, Modifier.size(16.dp),
                                    tint = LocalAppColors.current.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontSize = 14.sp, color = LocalAppColors.current.textPrimary)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = LocalAppColors.current.surfaceVariant)
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Convide seu parceiro(a) para o lar:",
                            fontSize = 13.sp,
                            color = LocalAppColors.current.textSecondary
                        )
                        Spacer(Modifier.height(8.dp))

                        if (newInviteCode != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceVariant),
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
                                        color = LocalAppColors.current.textSecondary)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isGenerating = true
                                    inviteError = null
                                    scope.launch {
                                        try {
                                            // Convite para o lar ATUAL (não cria outro lar)
                                            newInviteCode = HouseholdManager.createInvite(householdId)
                                        } catch (e: Exception) {
                                            inviteError = "Não foi possível gerar o convite: ${e.localizedMessage}"
                                        }
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
                            inviteError?.let {
                                Text(it, color = LocalAppColors.current.error, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Você ainda não está em nenhum lar.",
                            fontSize = 13.sp,
                            color = LocalAppColors.current.textSecondary
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.warning)
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.error)
            ) {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limpeza total")
            }

            Spacer(Modifier.height(24.dp))

            // ── Sair ──
            OutlinedButton(
                onClick = { onLogout() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LocalAppColors.current.error
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
            containerColor = LocalAppColors.current.card,
            icon = {
                Icon(Icons.Default.Warning, null, tint = LocalAppColors.current.warning, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Limpar histórico", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Isso vai apagar permanentemente:",
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text("• Todas as transações", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Text("• Todas as despesas agendadas", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Text("• Todos os orçamentos", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠️ Suas contas e categorias serão mantidas.\nEssa ação não pode ser desfeita.",
                        color = LocalAppColors.current.warning,
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
                    Text("Limpar tudo", color = LocalAppColors.current.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    // Dialog confirmar limpeza total
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = LocalAppColors.current.card,
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = LocalAppColors.current.error, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Limpeza total", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Isso vai apagar permanentemente:",
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text("• Todas as transações", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Text("• Todas as despesas agendadas", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Text("• Todos os orçamentos", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Text("• Todas as contas", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Text("• Todas as categorias", color = LocalAppColors.current.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tudo será apagado para todos os membros do lar. Essa ação não pode ser desfeita.",
                        color = LocalAppColors.current.error,
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
                    Text("Apagar tudo", color = LocalAppColors.current.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancelar", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    // Dialog para entrar em outro lar
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; joinError = null; joinCode = "" },
            containerColor = LocalAppColors.current.card,
            title = { Text("Entrar num lar", color = LocalAppColors.current.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digite o código de 6 caracteres que seu parceiro(a) compartilhou:",
                        color = LocalAppColors.current.textSecondary)
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
                        Text(it, color = LocalAppColors.current.error, fontSize = 13.sp)
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
                        scope.launch {
                            // Se já há lançamentos neste celular, pede confirmação antes de substituí-los
                            val hasLocal = try {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getInstance(context).transactionDao().getAllOnce().isNotEmpty()
                                }
                            } catch (_: Exception) { false }
                            if (hasLocal) confirmJoin = true else performJoin()
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
                }) { Text(if (joinSuccess) "Fechar" else "Cancelar", color = LocalAppColors.current.textSecondary) }
            }
        )
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
                    Text("Entrar e substituir", color = appColors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmJoin = false }) {
                    Text("Cancelar", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/** O LocalContext do Compose pode ser um ContextWrapper; procura a Activity por trás dele. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceVariant)
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
                color = if (isSelected) color1 else LocalAppColors.current.textSecondary
            )
        }
    }
}
