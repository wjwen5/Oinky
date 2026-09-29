@file:OptIn(ExperimentalTextApi::class)

package com.oinky.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.oinky.app.R
import com.oinky.app.ui.theme.Tokens.Colors as C

/** Bundled variable fonts (SIL Open Font License, see /licenses). */
object FontFamilies {
    private fun variable(res: Int, vararg weights: Int) = FontFamily(
        weights.map { w ->
            Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )

    val Fredoka = variable(R.font.fredoka, 400, 500, 600, 700)
    val Nunito = variable(R.font.nunito, 400, 500, 600, 700, 800)
}

// Colour schemes map DESIGN.md roles 1:1 onto Material 3 roles.
internal val LightScheme = lightColorScheme(
    primary = C.primary, onPrimary = C.onPrimary,
    primaryContainer = C.primaryContainer, onPrimaryContainer = C.onPrimaryContainer,
    secondary = C.secondary, onSecondary = C.onSecondary,
    secondaryContainer = C.secondaryContainer, onSecondaryContainer = C.onSecondaryContainer,
    tertiary = C.tertiary, onTertiary = C.onTertiary,
    tertiaryContainer = C.tertiaryContainer, onTertiaryContainer = C.onTertiaryContainer,
    error = C.error, onError = C.onError, errorContainer = C.errorContainer, onErrorContainer = C.onErrorContainer,
    background = C.background, onBackground = C.onSurface,
    surface = C.surface, onSurface = C.onSurface,
    surfaceVariant = C.surfaceContainerHigh, onSurfaceVariant = C.onSurfaceVariant,
    surfaceContainerLowest = C.surfaceContainerLowest, surfaceContainerLow = C.surfaceContainerLow,
    surfaceContainer = C.surfaceContainer, surfaceContainerHigh = C.surfaceContainerHigh,
    surfaceContainerHighest = C.surfaceContainerHighest,
    surfaceBright = C.surface, surfaceDim = C.surfaceContainerHighest,
    outline = C.outline, outlineVariant = C.outlineVariant,
)

internal val DarkScheme = darkColorScheme(
    primary = C.darkPrimary, onPrimary = C.darkOnPrimary,
    primaryContainer = C.darkPrimaryContainer, onPrimaryContainer = C.darkOnPrimaryContainer,
    secondary = C.darkSecondary, onSecondary = C.darkOnSecondary,
    secondaryContainer = C.darkSecondaryContainer, onSecondaryContainer = C.darkOnSecondaryContainer,
    tertiary = C.darkTertiary, onTertiary = C.darkOnTertiary,
    tertiaryContainer = C.darkTertiaryContainer, onTertiaryContainer = C.darkOnTertiaryContainer,
    background = C.darkBackground, onBackground = C.darkOnSurface,
    surface = C.darkSurface, onSurface = C.darkOnSurface,
    surfaceVariant = C.darkSurfaceContainerHigh, onSurfaceVariant = C.darkOnSurfaceVariant,
    surfaceContainerLowest = C.darkSurfaceContainerLowest, surfaceContainerLow = C.darkSurfaceContainerLow,
    surfaceContainer = C.darkSurfaceContainer, surfaceContainerHigh = C.darkSurfaceContainerHigh,
    surfaceContainerHighest = C.darkSurfaceContainerHighest,
    surfaceBright = C.darkSurfaceContainerHighest, surfaceDim = C.darkSurface,
    outline = C.darkOutline, outlineVariant = C.darkOutlineVariant,
)

private val OinkyTypography = Typography(
    displayLarge = Tokens.Type.display,
    displayMedium = Tokens.Type.display,
    displaySmall = Tokens.Type.headlineLg,
    headlineLarge = Tokens.Type.headlineLg,
    headlineMedium = Tokens.Type.headlineMd,
    headlineSmall = Tokens.Type.headlineMd,
    titleLarge = Tokens.Type.titleLg,
    titleMedium = Tokens.Type.titleMd,
    titleSmall = Tokens.Type.labelLg,
    bodyLarge = Tokens.Type.bodyLg,
    bodyMedium = Tokens.Type.bodyMd,
    bodySmall = Tokens.Type.bodySm,
    labelLarge = Tokens.Type.labelLg,
    labelMedium = Tokens.Type.labelMd,
    labelSmall = Tokens.Type.labelSm,
)

private val OinkyShapes = Shapes(
    extraSmall = RoundedCornerShape(Tokens.Radius.sm),
    small = RoundedCornerShape(Tokens.Radius.md),
    medium = RoundedCornerShape(Tokens.Radius.md),
    large = RoundedCornerShape(Tokens.Radius.lg),
    extraLarge = RoundedCornerShape(Tokens.Radius.xl),
)

/** Roles DESIGN.md defines beyond Material's: money colours. */
@Immutable
data class MoneyColors(val income: Color, val expense: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(C.income, C.expense) }

/**
 * The Piggy Scrapbook theme from DESIGN.md. [wallpaperColors] opts into Android 12+ dynamic
 * colour; the brand palette is the default.
 */
@Composable
fun OinkyTheme(wallpaperColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = when {
        wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkScheme
        else -> LightScheme
    }
    val money = if (dark) MoneyColors(C.darkSecondary, C.darkOnSurface) else MoneyColors(C.income, C.expense)
    CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(colorScheme = scheme, typography = OinkyTypography, shapes = OinkyShapes, content = content)
    }
}

/** Mint for income (DESIGN.md: `amount-income`). */
@Composable
fun incomeColor(): Color = LocalMoneyColors.current.income
