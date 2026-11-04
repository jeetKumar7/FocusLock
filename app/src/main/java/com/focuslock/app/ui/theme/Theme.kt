package com.focuslock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** iOS system colours (light / dark variants from Apple's HIG). */
@Immutable
data class IosColors(
    val background: Color,        // systemGroupedBackground
    val card: Color,              // secondarySystemGroupedBackground
    val cardPressed: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,              // tertiarySystemFill (stepper / segmented bg)
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val indigo: Color,
    val purple: Color,
    val pink: Color,
    val teal: Color,
    val yellow: Color,
    val isDark: Boolean,
)

private val Light = IosColors(
    background = Color(0xFFF2F2F7), card = Color.White, cardPressed = Color(0xFFE5E5EA),
    label = Color(0xFF000000), secondaryLabel = Color(0x993C3C43), tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43), fill = Color(0x1F767680),
    blue = Color(0xFF007AFF), green = Color(0xFF34C759), red = Color(0xFFFF3B30), orange = Color(0xFFFF9500),
    indigo = Color(0xFF5856D6), purple = Color(0xFFAF52DE), pink = Color(0xFFFF2D55), teal = Color(0xFF30B0C7),
    yellow = Color(0xFFFFCC00), isDark = false,
)

private val Dark = IosColors(
    background = Color(0xFF000000), card = Color(0xFF1C1C1E), cardPressed = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF), secondaryLabel = Color(0x99EBEBF5), tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458), fill = Color(0x3D767680),
    blue = Color(0xFF0A84FF), green = Color(0xFF30D158), red = Color(0xFFFF453A), orange = Color(0xFFFF9F0A),
    indigo = Color(0xFF5E5CE6), purple = Color(0xFFBF5AF2), pink = Color(0xFFFF375F), teal = Color(0xFF40C8E0),
    yellow = Color(0xFFFFD60A), isDark = true,
)

val LocalIos = staticCompositionLocalOf { Light }

/** iOS text styles (sizes from the HIG "Large" content size), tuned for Android's system font. */
object IosType {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, lineHeight = 41.sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em, lineHeight = 28.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, lineHeight = 25.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val subhead = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val sectionHeader = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.02.em)
    val bigNumber = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
}

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    val ios = if (isSystemInDarkTheme()) Dark else Light
    val scheme = if (ios.isDark) {
        darkColorScheme(
            primary = ios.blue, onPrimary = Color.White, background = ios.background, surface = ios.card,
            onBackground = ios.label, onSurface = ios.label, error = ios.red, surfaceVariant = ios.cardPressed,
            onSurfaceVariant = ios.secondaryLabel, outline = ios.separator,
        )
    } else {
        lightColorScheme(
            primary = ios.blue, onPrimary = Color.White, background = ios.background, surface = ios.card,
            onBackground = ios.label, onSurface = ios.label, error = ios.red, surfaceVariant = ios.cardPressed,
            onSurfaceVariant = ios.secondaryLabel, outline = ios.separator,
        )
    }
    val typography = Typography(
        headlineLarge = IosType.largeTitle, headlineMedium = IosType.title2, headlineSmall = IosType.title3,
        titleLarge = IosType.title3, titleMedium = IosType.headline, titleSmall = IosType.subhead,
        bodyLarge = IosType.body, bodyMedium = IosType.subhead, bodySmall = IosType.footnote,
        labelLarge = IosType.headline, labelMedium = IosType.footnote, labelSmall = IosType.caption,
        displaySmall = IosType.largeTitle,
    )
    CompositionLocalProvider(LocalIos provides ios) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
