package com.optisuite.optiplay.ui.theme

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
import com.optisuite.optiplay.data.ThemeMode

private val BrandBlue = Color(0xFF22A7F0)
private val BrandGreen = Color(0xFF3DDC84)

private val FallbackDark = darkColorScheme(
    primary = BrandBlue,
    secondary = BrandGreen,
    background = Color(0xFF0B0E14),
    surface = Color(0xFF121620)
)

private val FallbackLight = lightColorScheme(
    primary = BrandBlue,
    secondary = BrandGreen
)

@Composable
fun OptiPlayTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current

    var scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> FallbackDark
        else -> FallbackLight
    }

    // Modo AMOLED: negro puro para ahorro de batería en paneles OLED.
    if (themeMode == ThemeMode.AMOLED) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0A0A0A)
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
