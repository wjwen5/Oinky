// GENERATED from DESIGN.md by tools/gen_design_tokens.py. Do not edit by hand.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.oinky.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Design tokens for "Oinky — Piggy Scrapbook". */
object Tokens {
    object Colors {
        val primary = Color(0xFFC2356A)
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFFFD9E3)
        val onPrimaryContainer = Color(0xFF5C1030)
        val secondary = Color(0xFF2B7A57)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFCDEFDD)
        val onSecondaryContainer = Color(0xFF0F3D2A)
        val tertiary = Color(0xFF855A00)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFFFDC7A)
        val onTertiaryContainer = Color(0xFF3D2800)
        val error = Color(0xFFBA1A1A)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFFFDAD6)
        val onErrorContainer = Color(0xFF410002)
        val neutral = Color(0xFFFFF8F1)
        val background = Color(0xFFFFF8F1)
        val surface = Color(0xFFFFF8F1)
        val onSurface = Color(0xFF3A2A2E)
        val onSurfaceVariant = Color(0xFF6E565C)
        val surfaceContainerLowest = Color(0xFFFFFFFF)
        val surfaceContainerLow = Color(0xFFFFF1E8)
        val surfaceContainer = Color(0xFFFBEAE0)
        val surfaceContainerHigh = Color(0xFFF6E4DA)
        val surfaceContainerHighest = Color(0xFFF0DAD0)
        val outline = Color(0xFF9C8189)
        val outlineVariant = Color(0xFFE8D2D6)
        val income = Color(0xFF2B7A57)
        val expense = Color(0xFF3A2A2E)
        val darkBackground = Color(0xFF211A1C)
        val darkSurface = Color(0xFF211A1C)
        val darkOnSurface = Color(0xFFF2DEE2)
        val darkOnSurfaceVariant = Color(0xFFD8C0C6)
        val darkSurfaceContainerLowest = Color(0xFF1B1416)
        val darkSurfaceContainerLow = Color(0xFF2B2225)
        val darkSurfaceContainer = Color(0xFF302629)
        val darkSurfaceContainerHigh = Color(0xFF3A2F32)
        val darkSurfaceContainerHighest = Color(0xFF453A3D)
        val darkPrimary = Color(0xFFFFB1C8)
        val darkOnPrimary = Color(0xFF5E1133)
        val darkPrimaryContainer = Color(0xFF7E2448)
        val darkOnPrimaryContainer = Color(0xFFFFD9E3)
        val darkSecondary = Color(0xFF8FD6B0)
        val darkOnSecondary = Color(0xFF003824)
        val darkSecondaryContainer = Color(0xFF145238)
        val darkOnSecondaryContainer = Color(0xFFCDEFDD)
        val darkTertiary = Color(0xFFF2C15B)
        val darkOnTertiary = Color(0xFF402D00)
        val darkTertiaryContainer = Color(0xFF5C4000)
        val darkOnTertiaryContainer = Color(0xFFFFDF9A)
        val darkOutline = Color(0xFFA38A91)
        val darkOutlineVariant = Color(0xFF524347)
    }

    object Type {
        val display = TextStyle(fontFamily = FontFamilies.Fredoka, fontSize = 36.sp, fontWeight = FontWeight(600), lineHeight = 44.sp, letterSpacing = (-0.01).em)
        val headlineLg = TextStyle(fontFamily = FontFamilies.Fredoka, fontSize = 28.sp, fontWeight = FontWeight(600), lineHeight = 36.sp)
        val headlineMd = TextStyle(fontFamily = FontFamilies.Fredoka, fontSize = 22.sp, fontWeight = FontWeight(600), lineHeight = 28.sp)
        val titleLg = TextStyle(fontFamily = FontFamilies.Fredoka, fontSize = 18.sp, fontWeight = FontWeight(500), lineHeight = 24.sp)
        val titleMd = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 16.sp, fontWeight = FontWeight(700), lineHeight = 22.sp)
        val bodyLg = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 17.sp, fontWeight = FontWeight(400), lineHeight = 26.sp)
        val bodyMd = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 15.sp, fontWeight = FontWeight(400), lineHeight = 22.sp)
        val bodySm = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 13.sp, fontWeight = FontWeight(400), lineHeight = 18.sp)
        val labelLg = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 14.sp, fontWeight = FontWeight(700), lineHeight = 20.sp)
        val labelMd = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 12.sp, fontWeight = FontWeight(700), lineHeight = 16.sp, letterSpacing = (0.02).em)
        val labelSm = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 11.sp, fontWeight = FontWeight(600), lineHeight = 14.sp, letterSpacing = (0.02).em)
        val amountLg = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 24.sp, fontWeight = FontWeight(800), lineHeight = 30.sp, fontFeatureSettings = "tnum")
        val amountMd = TextStyle(fontFamily = FontFamilies.Nunito, fontSize = 15.sp, fontWeight = FontWeight(700), lineHeight = 20.sp, fontFeatureSettings = "tnum")
    }

    object Radius {
        val sm = 8.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
        val full = 9999.dp
    }

    object Space {
        val xs = 4.dp
        val sm = 8.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
        val gutter = 16.dp
        val cardPadding = 16.dp
        val touchTarget = 48.dp
    }
}
