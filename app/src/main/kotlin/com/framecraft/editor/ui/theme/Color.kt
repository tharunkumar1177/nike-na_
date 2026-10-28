/*
 * Framecraft Material 3 color schemes; dark is the default for video editing.
 */
package com.framecraft.editor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Dark color scheme used by default. */
val FramecraftDarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFAEC6FF),
        onPrimary = Color(0xFF002E69),
        primaryContainer = Color(0xFF11448F),
        onPrimaryContainer = Color(0xFFD7E2FF),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6D0),
    )

/** Light color scheme used when the system is in light mode. */
val FramecraftLightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF345CA8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD7E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF1A1C20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF1A1C20),
        surfaceVariant = Color(0xFFE0E2EC),
        onSurfaceVariant = Color(0xFF44474E),
    )
