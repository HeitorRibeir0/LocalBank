package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.localbank.finance.auth.AuthManager
import com.localbank.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .safeDrawingPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Ícone com fundo gradiente circular
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(LocalAppColors.current.gradientStart, LocalAppColors.current.gradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }

            Text(
                text = "LocalBank",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary
            )

            Text(
                text = "Gerencie as finanças da casa\njunto com seu parceiro(a)",
                textAlign = TextAlign.Center,
                color = LocalAppColors.current.textSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            AuthManager.signInWithGoogle(context)
                            onLoginSuccess()
                        } catch (_: GetCredentialCancellationException) {
                            errorMessage = "Login cancelado"
                        } catch (_: NoCredentialException) {
                            errorMessage = "Nenhuma conta Google neste aparelho. Adicione uma nas configurações do Android."
                        } catch (e: Exception) {
                            errorMessage = "Erro ao entrar: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalAppColors.current.brandPrimary,
                    contentColor = LocalAppColors.current.onBrandPrimary,
                    disabledContainerColor = LocalAppColors.current.brandPrimary.copy(alpha = 0.4f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Entrar com Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            errorMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LocalAppColors.current.error.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = it,
                        color = LocalAppColors.current.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
