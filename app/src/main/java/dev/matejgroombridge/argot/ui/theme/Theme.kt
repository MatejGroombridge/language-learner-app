package dev.matejgroombridge.argot.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User-selectable light/dark mode. The app follows the system, like the web
 *  app's prefers-color-scheme media query. */
enum class ThemeMode { System, Light, Dark }

private fun liuliScheme(p: LiuliPalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.red,
        onPrimary = Color.White,
        primaryContainer = p.redSoft,
        onPrimaryContainer = p.ink,
        secondary = p.gold,
        onSecondary = Color.White,
        secondaryContainer = p.goldSoft,
        onSecondaryContainer = p.ink,
        tertiary = p.green,
        onTertiary = Color.White,
        tertiaryContainer = p.greenSoft,
        onTertiaryContainer = p.ink,
        background = p.bg,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.bgSoft,
        onSurfaceVariant = p.ink2,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        surfaceContainerLow = p.card,
        outline = p.line,
        outlineVariant = p.line,
        error = p.red,
        onError = Color.White,
        errorContainer = p.redSoft,
        onErrorContainer = p.ink,
    )
}

/**
 * Root theme. This app deliberately uses the Liúlì brand palette instead of
 * wallpaper-derived dynamic colour so it matches the web app's warm
 * cream/red/gold design system exactly, in both light and dark.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light  -> false
        ThemeMode.Dark   -> true
    }

    val palette = if (isDark) DarkLiuli else LightLiuli
    val colorScheme = liuliScheme(palette, isDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val barsAreLight = colorScheme.background.luminance() > 0.5f
            controller.isAppearanceLightStatusBars = barsAreLight
            controller.isAppearanceLightNavigationBars = barsAreLight
        }
    }

    CompositionLocalProvider(LocalLiuli provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
