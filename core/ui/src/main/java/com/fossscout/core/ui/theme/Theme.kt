package com.fossscout.core.ui.theme

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

private val OnSurfaceVariantLight = Color(0xFF44514A)
private val SecondaryContainerNight = Color(0xFF4A3200)

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = SurfaceLight,
    primaryContainer = Mint,
    onPrimaryContainer = DeepForest,
    secondary = Amber,
    onSecondary = SurfaceLight,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Charcoal,
    tertiary = ForestGreen,
    onTertiary = SurfaceLight,
    background = Paper,
    onBackground = Charcoal,
    surface = SurfaceLight,
    onSurface = Charcoal,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = ForestGreenNight,
    onPrimary = MintNight,
    primaryContainer = DeepForestNight,
    onPrimaryContainer = MintBright,
    secondary = AmberNight,
    onSecondary = Charcoal,
    secondaryContainer = SecondaryContainerNight,
    onSecondaryContainer = AmberContainer,
    tertiary = ForestGreenNight,
    onTertiary = MintNight,
    background = PaperNight,
    onBackground = SurfaceVariantLight,
    surface = SurfaceNight,
    onSurface = SurfaceVariantLight,
    surfaceVariant = SurfaceVariantNight,
    onSurfaceVariant = OutlineNight,
    outline = OutlineNight,
)

@Composable
fun FossScoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but disabled by default so the
    // FossScout palette stays consistent across devices.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
