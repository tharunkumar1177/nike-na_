/*
 * Launcher activity hosting the Compose UI.
 */
package com.framecraft.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.framecraft.editor.ui.theme.FramecraftTheme

/** Single activity that renders the app's Compose content edge to edge. */
class MainActivity : ComponentActivity() {
    /** Enables edge-to-edge drawing and sets the themed app root as content. */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FramecraftApplication).container
        setContent {
            FramecraftTheme {
                FramecraftApp(container)
            }
        }
    }
}
