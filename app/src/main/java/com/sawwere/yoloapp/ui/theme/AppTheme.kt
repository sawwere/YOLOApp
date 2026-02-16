package com.sawwere.yoloapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    // Primary - зеленый
    primary = Primary500,
    onPrimary = NeutralWhite,
    primaryContainer = Primary100,
    onPrimaryContainer = Primary900,

    // Secondary - розовый
    secondary = Secondary500,
    onSecondary = NeutralWhite,
    secondaryContainer = Secondary100,
    onSecondaryContainer = Secondary900,

    // Tertiary - светло-зеленый для акцентов
    tertiary = Primary300,
    onTertiary = Neutral900,
    tertiaryContainer = Primary50,
    onTertiaryContainer = Primary800,

    // Background
    background = Neutral50,
    onBackground = Neutral900,
    surface = NeutralWhite,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral700,

    // Error
    error = Secondary500,
    onError = NeutralWhite,
    errorContainer = Secondary100,
    onErrorContainer = Secondary900,

    // Outline
    outline = Neutral400,
    outlineVariant = Neutral300,

    // Inverse
    inverseSurface = Neutral900,
    inverseOnSurface = Neutral50,
    inversePrimary = Primary300,

    // Surface Tint
    surfaceTint = Primary500,

    // Surface variants
    surfaceBright = NeutralWhite,
    surfaceDim = Neutral200
)

private val DarkColorScheme = darkColorScheme(
    // Primary - зеленый (адаптированный для темной темы)
    primary = Primary300,
    onPrimary = Neutral900,
    primaryContainer = Primary800,
    onPrimaryContainer = Primary100,

    // Secondary - розовый
    secondary = Secondary300,
    onSecondary = Neutral900,
    secondaryContainer = Secondary800,
    onSecondaryContainer = Secondary100,

    // Tertiary
    tertiary = Primary200,
    onTertiary = Neutral900,
    tertiaryContainer = Primary900,
    onTertiaryContainer = Primary50,

    // Background
    background = Neutral900,
    onBackground = Neutral100,
    surface = Neutral800,
    onSurface = Neutral100,
    surfaceVariant = Neutral700,
    onSurfaceVariant = Neutral300,

    // Error
    error = Secondary300,
    onError = Neutral900,
    errorContainer = Secondary800,
    onErrorContainer = Secondary100,

    // Outline
    outline = Neutral600,
    outlineVariant = Neutral700,

    // Inverse
    inverseSurface = Neutral100,
    inverseOnSurface = Neutral900,
    inversePrimary = Primary500,

    // Surface Tint
    surfaceTint = Primary300,

    // Surface variants
    surfaceBright = Neutral700,
    surfaceDim = Neutral900
)

@Composable
fun YOLOAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}