package com.localbank.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Paleta Emerald (padrão) ───

val Emerald = Color(0xFF00C9A7)
val EmeraldLight = Color(0xFF5EEAD4)
val EmeraldDark = Color(0xFF009B7D)
val EmeraldSurface = Color(0xFF003D32)

// ─── Paleta Rosa Méliuz ───

val MeliuzPink = Color(0xFFE91E8C)
val MeliuzPinkLight = Color(0xFFFF6EB4)
val MeliuzPinkDark = Color(0xFFB5006A)
val MeliuzPinkSurface = Color(0xFF3D0028)

// ─── Backgrounds & Surfaces ───

val DarkBg = Color(0xFF0D0D0D)
val DarkSurface = Color(0xFF1A1A2E)
val DarkSurfaceVariant = Color(0xFF222240)
val DarkCard = Color(0xFF1E1E32)

// ─── Accent / Secondary ───

val AccentBlue = Color(0xFF4A9DFF)
val AccentPurple = Color(0xFF9B72FF)

// ─── Semânticas ───

val IncomeGreen = Color(0xFF00E6A7)
val ExpenseRed = Color(0xFFFF6B6B)
val WarningAmber = Color(0xFFFFB347)
val InfoBlue = Color(0xFF64B5F6)

// ─── On-colors ───

val OnDarkText = Color(0xFFF1F1F1)
val OnDarkTextSecondary = Color(0xFFA0A0B8)
val OnEmerald = Color(0xFF003D32)
val OnMeliuzPink = Color(0xFFFFFFFF)

// ─── Gradient helpers ───

val GradientEmeraldStart = Color(0xFF00C9A7)
val GradientEmeraldEnd = Color(0xFF009B7D)
val GradientBlueStart = Color(0xFF4A9DFF)
val GradientBlueEnd = Color(0xFF2575FC)
val GradientPinkStart = Color(0xFFE91E8C)
val GradientPinkEnd = Color(0xFFB5006A)

// ─── Sistema de Temas ───

enum class AppThemeType { EMERALD, MELIUZ }

/**
 * Contém as cores "accent" do tema selecionado.
 * Todos os componentes devem usar estas cores via LocalAppColors
 * em vez de referenciar Emerald/MeliuzPink diretamente.
 */
data class AppColors(
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val primarySurface: Color,
    val onPrimary: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

val EmeraldColors = AppColors(
    primary = Emerald,
    primaryLight = EmeraldLight,
    primaryDark = EmeraldDark,
    primarySurface = EmeraldSurface,
    onPrimary = OnEmerald,
    gradientStart = GradientEmeraldStart,
    gradientEnd = GradientEmeraldEnd
)

val MeliuzColors = AppColors(
    primary = MeliuzPink,
    primaryLight = MeliuzPinkLight,
    primaryDark = MeliuzPinkDark,
    primarySurface = MeliuzPinkSurface,
    onPrimary = OnMeliuzPink,
    gradientStart = GradientPinkStart,
    gradientEnd = GradientPinkEnd
)

/**
 * CompositionLocal que provê as cores do tema ativo.
 * Uso: val colors = LocalAppColors.current
 */
val LocalAppColors = compositionLocalOf { EmeraldColors }