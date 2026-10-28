/*
 * Framecraft typography scale and spacing tokens.
 */
package com.framecraft.editor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Typography overrides on the Material 3 baseline. */
val FramecraftTypography: Typography =
    Typography().let { base ->
        base.copy(
            titleLarge = base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }

/** Spacing scale in dp used for padding and gaps. */
object Spacing {
    /** 4dp. */
    val xs = 4.dp

    /** 8dp. */
    val sm = 8.dp

    /** 16dp, the standard screen edge. */
    val md = 16.dp

    /** 24dp. */
    val lg = 24.dp

    /** 32dp. */
    val xl = 32.dp

    /** 48dp, also the minimum touch target. */
    val xxl = 48.dp

    /** 64dp. */
    val xxxl = 64.dp
}
