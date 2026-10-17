package com.localbank.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private fun buildColorScheme(c: AppColors) = darkColorScheme(
    primary             = c.brandPrimary,
    onPrimary           = c.onBrandPrimary,
    primaryContainer    = c.brandPrimarySurface,
    onPrimaryContainer  = c.brandPrimaryLight,

    secondary           = c.accent,
    onSecondary         = c.background,
    secondaryContainer  = c.brandPrimarySurface,
    onSecondaryContainer = c.brandPrimaryLight,

    tertiary            = c.info,
    onTertiary          = c.background,

    background          = c.background,
    onBackground        = c.textPrimary,

    surface             = c.surface,
    onSurface           = c.textPrimary,
    surfaceVariant      = c.surfaceVariant,
    onSurfaceVariant    = c.textSecondary,

    // Diálogos, folhas e menus usam os containers — tudo no tom do tema
    surfaceContainerLowest  = c.background,
    surfaceContainerLow     = c.surface,
    surfaceContainer        = c.surface,
    surfaceContainerHigh    = c.card,
    surfaceContainerHighest = c.surfaceVariant,
    surfaceBright           = c.surfaceVariant,
    surfaceDim              = c.background,

    error               = c.error,
    onError             = c.background,
    errorContainer      = c.error.copy(alpha = 0.18f),
    onErrorContainer    = c.error,

    outline             = c.textMuted,
    outlineVariant      = c.surfaceVariant,
    scrim               = Color.Black,

    inverseSurface      = c.textPrimary,
    inverseOnSurface    = c.background,
    inversePrimary      = c.brandPrimaryDark,
    surfaceTint         = c.brandPrimary
)

private val Shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ── Gerenciador de tema ──

object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY = "selected_theme"

    fun getTheme(context: Context): AppThemeType {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY, AppThemeType.EMERALD.name) ?: AppThemeType.EMERALD.name
        return try { AppThemeType.valueOf(name) } catch (_: Exception) { AppThemeType.EMERALD }
    }

    fun setTheme(context: Context, theme: AppThemeType) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, theme.name)
            .apply()
    }
}

@Composable
fun LocalBankTheme(
    themeType: AppThemeType = AppThemeType.EMERALD,
    content: @Composable () -> Unit
) {
    val appColors = when (themeType) {
        AppThemeType.EMERALD -> EmeraldColors
        AppThemeType.MELIUZ -> MeliuzColors
    }

    val colorScheme = remember(themeType) { buildColorScheme(appColors) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = appColors.background.toArgb()
            window.navigationBarColor = appColors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}