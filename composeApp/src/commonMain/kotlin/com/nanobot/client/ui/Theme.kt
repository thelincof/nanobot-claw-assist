package com.nanobot.client.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Цветовая система повторяет webui nanobot (CSS-переменные из web/dist).
 * Light: :root, Dark: .dark.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFF97316),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF57534E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3F1ED),
    onSecondaryContainer = Color(0xFF292524),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1D1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1C19),
    surfaceVariant = Color(0xFFF3F1ED),
    onSurfaceVariant = Color(0xFF6F6B64),
    outline = Color(0xFFE6E1D9),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFF991B1B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBF9),
    surfaceContainer = Color(0xFFF7F4EF),
    surfaceContainerHigh = Color(0xFFF2EEE8),
    surfaceContainerHighest = Color(0xFFEDE8E0),
    inverseSurface = Color(0xFF1D1C19),
    inverseOnSurface = Color(0xFFF7F4EF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFB923C),
    onPrimary = Color(0xFF2A1204),
    primaryContainer = Color(0xFF3B2414),
    onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = Color(0xFFD6D3D1),
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = Color(0xFF2A2723),
    onSecondaryContainer = Color(0xFFF5F1EA),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF151412),
    onBackground = Color(0xFFF5F1EA),
    surface = Color(0xFF1D1B18),
    onSurface = Color(0xFFF5F1EA),
    surfaceVariant = Color(0xFF2A2723),
    onSurfaceVariant = Color(0xFFA8A199),
    outline = Color(0xFF3A352E),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    surfaceContainerLowest = Color(0xFF11100E),
    surfaceContainerLow = Color(0xFF191714),
    surfaceContainer = Color(0xFF211E1A),
    surfaceContainerHigh = Color(0xFF2A2621),
    surfaceContainerHighest = Color(0xFF332E28),
    inverseSurface = Color(0xFFF5F1EA),
    inverseOnSurface = Color(0xFF1D1B18),
)

/** Дополнительные цвета webui, не помещающиеся в Material3-схему. */
@Immutable
data class WebUiColors(
    val sidebar: Color,              // --sidebar
    val sidebarForeground: Color,    // --sidebar-foreground
    val sidebarContent: Color,       // --sidebar-content
    val sidebarMuted: Color,         // --sidebar-muted-foreground
    val sidebarSelected: Color,      // --sidebar-selected
    val sidebarAccent: Color,        // --sidebar-accent
    val border: Color,               // --border
    val popover: Color,              // --popover (= card)
)

private val LightWebUi = WebUiColors(
    sidebar = Color(0xFFF5F2EE),
    sidebarForeground = Color(0xFF1D1C19),
    sidebarContent = Color(0xFF4A4640),
    sidebarMuted = Color(0xFF7A746B),
    sidebarSelected = Color(0xFFFBE3D2),
    sidebarAccent = Color(0xFFFAF0E8),
    border = Color(0xFFE6E1D9),
    popover = Color(0xFFFFFFFF),
)

private val DarkWebUi = WebUiColors(
    sidebar = Color(0xFF1B1917),
    sidebarForeground = Color(0xFFF5F1EA),
    sidebarContent = Color(0xFFD6D0C7),
    sidebarMuted = Color(0xFFA8A199),
    sidebarSelected = Color(0xFF3A2A1E),
    sidebarAccent = Color(0xFF241F1A),
    border = Color(0xFF332E28),
    popover = Color(0xFF211E1A),
)

val LocalWebUiColors = staticCompositionLocalOf { LightWebUi }

@Composable
fun NanobotTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkColors else LightColors
    val webUi = if (dark) DarkWebUi else LightWebUi
    CompositionLocalProvider(LocalWebUiColors provides webUi) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
