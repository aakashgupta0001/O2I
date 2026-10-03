package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan400,
    onPrimary = SlateNavy950,
    primaryContainer = CyberCyan900,
    onPrimaryContainer = CyberCyan300,
    secondary = EmeraldGreen400,
    onSecondary = SlateNavy950,
    secondaryContainer = EmeraldGreen900,
    onSecondaryContainer = EmeraldGreen400,
    tertiary = AmberWarning400,
    onTertiary = SlateNavy950,
    tertiaryContainer = AmberWarning900,
    onTertiaryContainer = AmberWarning400,
    error = RoseAlert400,
    onError = SlateNavy950,
    errorContainer = RoseAlert900,
    onErrorContainer = RoseAlert400,
    background = SlateNavy950,
    onBackground = Color(0xFFF8FAFC),
    surface = SlateNavy900,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SlateNavy800,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = SlateNavy700
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF164E63),
    secondary = LightSecondaryEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Use intentional enterprise dark/light palette rather than generic wallpaper colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
