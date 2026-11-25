package com.swipesort.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand colours (used when the phone doesn't offer Material You dynamic colours).
private val Teal = Color(0xFF14B8A6)
private val TealDark = Color(0xFF0F766E)
private val Navy = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary = TealDark, onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF042F2E),
    secondaryContainer = Color(0xFFE0F2FE), onSecondaryContainer = Color(0xFF082F49),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = Teal, onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1),
    secondaryContainer = Color(0xFF1E3A5F), onSecondaryContainer = Color(0xFFE0F2FE),
    background = Navy, surface = Navy,
    surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171), onError = Color(0xFF450A0A),
)

/** Follows the system light/dark setting; uses Material You colours on Android 12+. */
@Composable
fun SwipeSortTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
