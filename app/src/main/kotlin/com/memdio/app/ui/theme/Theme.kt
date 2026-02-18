package com.memdio.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val MemdioDarkColorScheme = darkColorScheme(
    primary = PrimaryDefault,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
)

@Composable
fun MemdioTheme(
    // Memdio is dark-first; ignore system setting
    darkTheme: Boolean = true,
    // Dynamic color: use Material You on Android 12+, fall back to brand palette
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> MemdioDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MemdioTypography,
        content = content,
    )
}
