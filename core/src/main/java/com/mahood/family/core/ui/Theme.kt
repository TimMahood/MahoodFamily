package com.mahood.family.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2F6A4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EBD6),
    onPrimaryContainer = Color(0xFF0B2E1E),
    secondaryContainer = Color(0xFFE3EDE6),
    background = Color(0xFFF6FAF6),
    surface = Color(0xFFF6FAF6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF4EF),
    surfaceContainer = Color(0xFFE7EFE9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9AD5B4),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF155238),
    onPrimaryContainer = Color(0xFFC8EBD6),
)

/** Material You: uses the phone's wallpaper colours on Android 12+, the family green otherwise. */
@Composable
fun FamilyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
