/*
 * Framecraft Compose theme entry point.
 */
package com.framecraft.editor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shape scale; extra large is used for primary actions. */
private val FramecraftShapes = Shapes(extraLarge = RoundedCornerShape(28.dp))

/** Applies Framecraft colors, typography, and shapes to [content], following the system dark mode. */
@Composable
fun FramecraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FramecraftDarkColors else FramecraftLightColors,
        typography = FramecraftTypography,
        shapes = FramecraftShapes,
        content = content,
    )
}
