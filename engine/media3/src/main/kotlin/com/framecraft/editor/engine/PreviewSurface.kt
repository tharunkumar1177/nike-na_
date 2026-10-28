/*
 * Compose surface that renders a ProjectPreview; keeps Media3 UI types inside the engine module.
 */
package com.framecraft.editor.engine

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW

/** Renders the video output of [preview]; CompositionPlayer only supports SurfaceView output. */
@OptIn(markerClass = [UnstableApi::class])
@Composable
fun PreviewSurface(
    preview: ProjectPreview,
    modifier: Modifier = Modifier,
) {
    ContentFrame(
        player = preview.player,
        modifier = modifier,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
    )
}
