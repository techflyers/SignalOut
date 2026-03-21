package com.signalout.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// WhatsApp-inspired color palette
object WhatsAppColors {
    // Dark mode
    val DarkBackground = Color(0xFF121B22)
    val DarkSurface = Color(0xFF1F2C34)
    val DarkSurfaceVariant = Color(0xFF233138)
    val DarkPrimary = Color(0xFF00A884)
    val DarkOnSurface = Color(0xFFE9EDEF)
    val DarkOnSurfaceVariant = Color(0xFF8696A0)
    val DarkOutgoingBubble = Color(0xFF005C4B)
    val DarkIncomingBubble = Color(0xFF202C33)
    val DarkOutline = Color(0xFF2A3942)

    // Light mode
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFF0F2F5)
    val LightSurfaceVariant = Color(0xFFE9EDEF)
    val LightPrimary = Color(0xFF008069)
    val LightOnSurface = Color(0xFF111B21)
    val LightOnSurfaceVariant = Color(0xFF667781)
    val LightOutgoingBubble = Color(0xFFD9FDD3)
    val LightIncomingBubble = Color(0xFFFFFFFF)
    val LightOutline = Color(0xFFE9EDEF)

    // Shared
    val TealAccent = Color(0xFF25D366)
    val BlueTick = Color(0xFF53BDEB)
    val ErrorRed = Color(0xFFEA0038)
    val OnlineGreen = Color(0xFF25D366)
    val HeaderDark = Color(0xFF1F2C34)
    val HeaderLight = Color(0xFF008069)
    val UnreadBadge = Color(0xFF25D366)
}

// WhatsApp dark color scheme
private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppColors.DarkPrimary,
    onPrimary = Color.White,
    secondary = WhatsAppColors.TealAccent,
    onSecondary = Color.White,
    background = WhatsAppColors.DarkBackground,
    onBackground = WhatsAppColors.DarkOnSurface,
    surface = WhatsAppColors.DarkSurface,
    onSurface = WhatsAppColors.DarkOnSurface,
    surfaceVariant = WhatsAppColors.DarkSurfaceVariant,
    onSurfaceVariant = WhatsAppColors.DarkOnSurfaceVariant,
    error = WhatsAppColors.ErrorRed,
    onError = Color.White,
    tertiary = WhatsAppColors.BlueTick,
    outline = WhatsAppColors.DarkOutline
)

// WhatsApp light color scheme
private val LightColorScheme = lightColorScheme(
    primary = WhatsAppColors.LightPrimary,
    onPrimary = Color.White,
    secondary = WhatsAppColors.TealAccent,
    onSecondary = Color.White,
    background = WhatsAppColors.LightBackground,
    onBackground = WhatsAppColors.LightOnSurface,
    surface = WhatsAppColors.LightSurface,
    onSurface = WhatsAppColors.LightOnSurface,
    surfaceVariant = WhatsAppColors.LightSurfaceVariant,
    onSurfaceVariant = WhatsAppColors.LightOnSurfaceVariant,
    error = WhatsAppColors.ErrorRed,
    onError = Color.White,
    tertiary = WhatsAppColors.BlueTick,
    outline = WhatsAppColors.LightOutline
)

@Composable
fun SignaloutTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            // Status bar color matches header
            window.statusBarColor = if (shouldUseDark) {
                WhatsAppColors.HeaderDark.toArgb()
            } else {
                WhatsAppColors.HeaderLight.toArgb()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
