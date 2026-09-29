package com.oinky.app.ui.theme

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

// Soft piggy-pink palette used when dynamic color is unavailable.
private val Light = lightColorScheme(
    primary = Color(0xFFB8436A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFF52634F),
    secondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFF38656A),
    background = Color(0xFFFFF8F9),
    surface = Color(0xFFFFF8F9),
    surfaceVariant = Color(0xFFF6E4EA),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    primaryContainer = Color(0xFF8E2F52),
    secondary = Color(0xFFB9CCB4),
    secondaryContainer = Color(0xFF3B4B38),
    tertiary = Color(0xFFA0CFD4),
    background = Color(0xFF1C1113),
    surface = Color(0xFF1C1113),
    surfaceVariant = Color(0xFF524347),
)

val IncomeGreen = Color(0xFF2E7D32)
val IncomeGreenDark = Color(0xFF81C784)

@Composable
fun OinkyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) IncomeGreenDark else IncomeGreen
