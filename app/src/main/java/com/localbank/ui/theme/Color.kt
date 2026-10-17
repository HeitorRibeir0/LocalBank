package com.localbank.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// SISTEMA DE DESIGN — fonte de verdade para todas as telas
//
// Dois temas com a mesma linguagem: fundo escuro tingido pela cor da marca,
// uma cor principal e duas de apoio escolhidas para harmonizar com ela.
//
//  brandPrimary       → ações principais, botão Lançar, seleção ativa, destaque
//  brandPrimaryLight  → estado pressionado / hover
//  brandPrimaryDark   → contêiner de ação secundária
//  brandPrimarySurface→ fundo de chip selecionado, indicador
//  onBrandPrimary     → texto/ícone sobre brandPrimary
//  accent             → cor de apoio (categorias, meta concluída)
//  gradientStart/End  → amostra do tema no seletor e logo
//
//  success   → entradas, valores positivos, pago
//  warning   → atenção (orçamento em 80%)
//  error     → saídas, vencimento próximo/atrasado, estouro, erros de formulário
//  info      → informação neutra
//
//  background     → tela inteira
//  surface        → barras de navegação
//  card           → grupos de conteúdo
//  surfaceVariant → divisores, trilhas de progresso, bordas
//
//  textPrimary / textSecondary / textMuted → hierarquia de texto
// ─────────────────────────────────────────────────────────────────────────────

enum class AppThemeType { EMERALD, MELIUZ }

data class AppColors(
    // Marca
    val brandPrimary: Color,
    val brandPrimaryLight: Color,
    val brandPrimaryDark: Color,
    val brandPrimarySurface: Color,
    val onBrandPrimary: Color,
    val accent: Color,
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

// ── Mata (tema verde) ────────────────────────────────────────────────────────
// Jade como cor principal; oliva (vizinho no círculo) e pêssego (complementar
// quente) como apoio. Fundo verde-escuro, não preto.
val EmeraldColors = AppColors(
    brandPrimary        = Color(0xFF5FD4A0),   // jade
    brandPrimaryLight   = Color(0xFF8BE5BC),
    brandPrimaryDark    = Color(0xFF2FA676),
    brandPrimarySurface = Color(0xFF173D30),
    onBrandPrimary      = Color(0xFF04281A),
    accent              = Color(0xFFC9D96F),   // oliva
    gradientStart       = Color(0xFF5FD4A0),
    gradientEnd         = Color(0xFF2FA676),

    success         = Color(0xFF5FD4A0),
    warning         = Color(0xFFE2C07A),       // areia dourada
    error           = Color(0xFFF2A07B),       // pêssego
    info            = Color(0xFF5CC3C9),       // azul-petróleo

    background      = Color(0xFF0F1F1A),
    surface         = Color(0xFF132822),
    card            = Color(0xFF17302A),
    surfaceVariant  = Color(0xFF24443B),

    textPrimary     = Color(0xFFEAF4EF),
    textSecondary   = Color(0xFFA7C2B7),
    textMuted       = Color(0xFF86A498)
)

// ── Méliuz (tema rosa) ───────────────────────────────────────────────────────
// Rosa como cor principal; lilás (vizinho) e menta (complementar) como apoio.
val MeliuzColors = AppColors(
    brandPrimary        = Color(0xFFF57AB0),   // rosa
    brandPrimaryLight   = Color(0xFFFFA3CB),
    brandPrimaryDark    = Color(0xFFC94A86),
    brandPrimarySurface = Color(0xFF4A1D35),
    onBrandPrimary      = Color(0xFF2E0718),
    accent              = Color(0xFFC9A2F0),   // lilás
    gradientStart       = Color(0xFFF57AB0),
    gradientEnd         = Color(0xFFC94A86),

    success         = Color(0xFF7FD6A8),       // menta
    warning         = Color(0xFFE8C07A),
    error           = Color(0xFFF5A86B),       // damasco — distinto do rosa da marca
    info            = Color(0xFFA7B5F2),

    background      = Color(0xFF1C1018),
    surface         = Color(0xFF23141E),
    card            = Color(0xFF2B1925),
    surfaceVariant  = Color(0xFF43283A),

    textPrimary     = Color(0xFFF6ECF1),
    textSecondary   = Color(0xFFC9AFBE),
    textMuted       = Color(0xFFA08797)
)

val LocalAppColors = compositionLocalOf { EmeraldColors }

// ─────────────────────────────────────────────────────────────────────────────
// ALIASES LEGADOS — leem o tema ativo, então telas antigas seguem o tema
// escolhido. Novas telas devem usar LocalAppColors.current diretamente.
// ─────────────────────────────────────────────────────────────────────────────

private val current: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

val DarkBg: Color              @Composable @ReadOnlyComposable get() = current.background
val DarkSurface: Color         @Composable @ReadOnlyComposable get() = current.surface
val DarkSurfaceVariant: Color  @Composable @ReadOnlyComposable get() = current.surfaceVariant
val DarkCard: Color            @Composable @ReadOnlyComposable get() = current.card
val IncomeGreen: Color         @Composable @ReadOnlyComposable get() = current.success
val ExpenseRed: Color          @Composable @ReadOnlyComposable get() = current.error
val WarningAmber: Color        @Composable @ReadOnlyComposable get() = current.warning
val InfoBlue: Color            @Composable @ReadOnlyComposable get() = current.info
val OnDarkText: Color          @Composable @ReadOnlyComposable get() = current.textPrimary
val OnDarkTextSecondary: Color @Composable @ReadOnlyComposable get() = current.textSecondary
val Emerald: Color             @Composable @ReadOnlyComposable get() = current.brandPrimary
val OnEmerald: Color           @Composable @ReadOnlyComposable get() = current.onBrandPrimary

// Amostras fixas de cada tema (seletor de tema no perfil)
val GradientEmeraldStart = EmeraldColors.gradientStart
val GradientEmeraldEnd   = EmeraldColors.gradientEnd
val GradientPinkStart    = MeliuzColors.gradientStart
val GradientPinkEnd      = MeliuzColors.gradientEnd
