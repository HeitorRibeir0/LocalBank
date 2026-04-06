package com.localbank.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// PALETA BRUTA — valores absolutos, não use diretamente nas telas
// ─────────────────────────────────────────────────────────────────────────────

// Marca
private val _Emerald       = Color(0xFF00C9A7)
private val _EmeraldLight  = Color(0xFF5EEAD4)
private val _EmeraldDark   = Color(0xFF009B7D)
private val _EmeraldSurface = Color(0xFF003D32)

private val _Pink          = Color(0xFFE91E8C)
private val _PinkLight     = Color(0xFFFF6EB4)
private val _PinkDark      = Color(0xFFB5006A)
private val _PinkSurface   = Color(0xFF3D0028)

// Superfícies — escuras neutras, mínimo de tinte colorido
private val _Bg            = Color(0xFF0F0F12)   // fundo geral: quase preto, leve frio
private val _Surface       = Color(0xFF161620)   // barras de navegação e app bar
private val _SurfaceVar    = Color(0xFF1E1E2A)   // divisores, trilha de progress bar
private val _Card          = Color(0xFF1A1A24)   // containers de card

// Semânticas
private val _Green         = Color(0xFF3CCF9A)   // sucesso / entrada: verde seguro, não neon
private val _Red           = Color(0xFFE05C5C)   // erro / atraso real: vermelho apagado
private val _Amber         = Color(0xFFD4963A)   // alerta / atenção: âmbar queimado
private val _Blue          = Color(0xFF5BA4D4)   // informação: azul acinzentado

// Texto
private val _TextPrimary   = Color(0xFFF1F1F1)
private val _TextSecondary = Color(0xFFA0A0B8)
private val _TextMuted     = Color(0xFF606078)

// ─────────────────────────────────────────────────────────────────────────────
// ALIASES LEGADOS — mantidos apenas para compatibilidade durante migração
// Novas telas não devem importar estes. Use LocalAppColors.current.
// ─────────────────────────────────────────────────────────────────────────────

val DarkBg             = _Bg
val DarkSurface        = _Surface
val DarkSurfaceVariant = _SurfaceVar
val DarkCard           = _Card
val IncomeGreen        = _Green
val ExpenseRed         = _Red
val WarningAmber       = _Amber
val InfoBlue           = _Blue
val OnDarkText         = _TextPrimary
val OnDarkTextSecondary = _TextSecondary
val AccentBlue         = Color(0xFF4A9DFF)
val AccentPurple       = Color(0xFF9B72FF)
val Emerald            = _Emerald
val EmeraldLight       = _EmeraldLight
val EmeraldDark        = _EmeraldDark
val EmeraldSurface     = _EmeraldSurface
val MeliuzPink         = _Pink
val MeliuzPinkLight    = _PinkLight
val MeliuzPinkDark     = _PinkDark
val MeliuzPinkSurface  = _PinkSurface
val OnEmerald          = _EmeraldSurface
val OnMeliuzPink       = Color.White
val GradientEmeraldStart = _Emerald
val GradientEmeraldEnd   = _EmeraldDark
val GradientPinkStart    = _Pink
val GradientPinkEnd      = _PinkDark

// ─────────────────────────────────────────────────────────────────────────────
// SISTEMA DE DESIGN — fonte de verdade para todas as telas
//
// Papéis e regras de uso:
//
//  brandPrimary       → ações principais, FAB, seleção ativa, destaque da marca
//  brandPrimaryLight  → hover / estado pressionado
//  brandPrimarySurface→ fundo de chip selecionado, indicador de aba
//  onBrandPrimary     → texto/ícone sobre brandPrimary
//  gradientStart/End  → hero card do dashboard
//
//  success   → valores positivos (entradas, metas concluídas, PAGO)
//  warning   → alertas (orçamento em 80%, vencimento próximo)
//  error     → erros reais, vencimento ultrapassado, estouro de limite
//  info      → informações neutras (opcional)
//
//  background     → tela inteira
//  surface        → bottom bar, top bar
//  card           → cartões de conteúdo
//  surfaceVariant → divisores, trilha de progress bar, fundos secundários
//
//  textPrimary    → títulos, valores, rótulos principais
//  textSecondary  → subtítulos, datas, labels de apoio
//  textMuted      → placeholders, informações de baixa ênfase
// ─────────────────────────────────────────────────────────────────────────────

enum class AppThemeType { EMERALD, MELIUZ }

data class AppColors(
    // Marca
    val brandPrimary: Color,
    val brandPrimaryLight: Color,
    val brandPrimaryDark: Color,
    val brandPrimarySurface: Color,
    val onBrandPrimary: Color,
    val gradientStart: Color,
    val gradientEnd: Color,

    // Semânticas
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,

    // Superfícies
    val background: Color,
    val surface: Color,
    val card: Color,
    val surfaceVariant: Color,

    // Texto
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
) {
    // Aliases de compatibilidade — mapeiam nomes antigos para os papéis semânticos
    val primary: Color        get() = brandPrimary
    val primaryLight: Color   get() = brandPrimaryLight
    val primaryDark: Color    get() = brandPrimaryDark
    val primarySurface: Color get() = brandPrimarySurface
    val onPrimary: Color      get() = onBrandPrimary
}

val EmeraldColors = AppColors(
    brandPrimary        = _Emerald,
    brandPrimaryLight   = _EmeraldLight,
    brandPrimaryDark    = _EmeraldDark,
    brandPrimarySurface = _EmeraldSurface,
    onBrandPrimary      = _EmeraldSurface,
    gradientStart       = Color(0xFF0D2822),   // escuro esmeralda — hero premium, não neon
    gradientEnd         = Color(0xFF091D18),   // quase preto com toque verde

    success         = _Green,
    warning         = _Amber,
    error           = _Red,
    info            = _Blue,

    background      = _Bg,
    surface         = _Surface,
    card            = _Card,
    surfaceVariant  = _SurfaceVar,

    textPrimary     = _TextPrimary,
    textSecondary   = _TextSecondary,
    textMuted       = _TextMuted
)

val MeliuzColors = AppColors(
    brandPrimary        = _Pink,
    brandPrimaryLight   = _PinkLight,
    brandPrimaryDark    = _PinkDark,
    brandPrimarySurface = _PinkSurface,
    onBrandPrimary      = Color.White,
    gradientStart       = Color(0xFF280D1E),   // escuro rosa — hero premium
    gradientEnd         = Color(0xFF1A0814),   // quase preto com toque rosa

    success         = _Green,
    warning         = _Amber,
    error           = _Red,
    info            = _Blue,

    background      = _Bg,
    surface         = _Surface,
    card            = _Card,
    surfaceVariant  = _SurfaceVar,

    textPrimary     = _TextPrimary,
    textSecondary   = _TextSecondary,
    textMuted       = _TextMuted
)

val LocalAppColors = compositionLocalOf { EmeraldColors }
