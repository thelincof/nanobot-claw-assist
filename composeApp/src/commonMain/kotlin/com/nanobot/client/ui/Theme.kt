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
    primary = Color(0xFF262628),          // --primary: 240 4% 16%
    onPrimary = Color(0xFFFAFAFA),        // --primary-foreground: 0 0% 98%
    primaryContainer = Color(0xFFF5F5F5), // --secondary/muted: 0 0% 96.1%
    onPrimaryContainer = Color(0xFF1D1D20),
    secondary = Color(0xFF262628),
    onSecondary = Color(0xFFFAFAFA),
    secondaryContainer = Color(0xFFF5F5F5),
    onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF262628),
    background = Color(0xFFFFFFFF),       // --background: white
    onBackground = Color(0xFF1E1E21),      // --foreground: 240 3% 12%
    surface = Color(0xFFFFFFFF),           // --card
    onSurface = Color(0xFF1E1E21),
    surfaceVariant = Color(0xFFF5F5F5),    // --muted
    onSurfaceVariant = Color(0xFF737373),  // --muted-foreground: 45.1%
    outline = Color(0xFFE9E6E1),           // --border: 40 8% 90.5%
    error = Color(0xFFEF4444),             // --destructive: 0 84.2% 60.2%
    onError = Color.White,
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFF991B1B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFCFC),
    surfaceContainer = Color(0xFFF7F6F4),
    surfaceContainerHigh = Color(0xFFF5F5F5),
    surfaceContainerHighest = Color(0xFFF0F0F0),
    inverseSurface = Color(0xFF1E1E21),
    inverseOnSurface = Color(0xFFF5F5F5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF7F7F8),           // 240 5% 98%
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF474747),
    onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = Color(0xFFF7F7F8),
    onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF474747),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = Color(0xFFF7F7F8),
    background = Color(0xFF303030),        // 0 0% 19%
    onBackground = Color(0xFFF4F4F5),      // 240 4% 96%
    surface = Color(0xFF383838),           // --card: 0 0% 22%
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF383838),
    onSurfaceVariant = Color(0xFFA6A6A6),  // muted-foreground 65%
    outline = Color(0xFF474747),           // --border: 0 0% 28%
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),    // destructive dark: 0 62.8% 30.6%
    onErrorContainer = Color(0xFFFECACA),
    surfaceContainerLowest = Color(0xFF2B2B2B),
    surfaceContainerLow = Color(0xFF333333),
    surfaceContainer = Color(0xFF383838),
    surfaceContainerHigh = Color(0xFF3D3D3D),
    surfaceContainerHighest = Color(0xFF444444),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Color(0xFF303030),
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
    sidebar = Color(0xFFF7F6F3),           // 40 8% 96.8%
    sidebarForeground = Color(0xFF171717),
    sidebarContent = Color(0xFF43434B),    // 240 3% 30%
    sidebarMuted = Color(0xFF707078),      // 240 2% 44%
    sidebarSelected = Color(0xFFE4E3E0),   // 40 1% 89.4%
    sidebarAccent = Color(0xFFF4F4F3),
    border = Color(0xFFE9E6E1),
    popover = Color(0xFFFFFFFF),
)

private val DarkWebUi = WebUiColors(
    sidebar = Color(0xFF383838),
    sidebarForeground = Color(0xFFFAFAFA),
    sidebarContent = Color(0xFFD6D6DA),    // 240 3% 84%
    sidebarMuted = Color(0xFFB3B3B8),      // 240 2% 70%
    sidebarSelected = Color(0xFF4C4C4C),   // 0 0% 29.8%
    sidebarAccent = Color(0xFF303030),
    border = Color(0xFF474747),
    popover = Color(0xFF383838),
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
