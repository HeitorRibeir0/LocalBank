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

    secondary           = c.info,
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFF1A3355),
    onSecondaryContainer = c.info,

    background          = c.background,
    onBackground        = c.textPrimary,

    surface             = c.surface,
    onSurface           = c.textPrimary,
    surfaceVariant      = c.surfaceVariant,
    onSurfaceVariant    = c.textSecondary,

    error               = c.error,
    onError             = Color.White,
    errorContainer      = Color(0xFF3D1515),
    onErrorContainer    = c.error,

    outline             = Color(0xFF3A3A55),
    outlineVariant      = Color(0xFF2A2A40),

    inverseSurface      = c.textPrimary,
    inverseOnSurface    = c.background,
    inversePrimary      = c.brandPrimaryDark,
    surfaceTint         = c.brandPrimary
)

private val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
            window.statusBarColor = DarkBg.toArgb()
            window.navigationBarColor = DarkSurface.toArgb()
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