/*
 * Where a picture clip's own frame sits on the preview, and conversions between screen pixels and points of that
 * frame, which masks and the chroma key's eyedropper work in.
 */
package com.framecraft.editor.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.framecraft.editor.domain.model.CanvasFit
import com.framecraft.editor.domain.model.CanvasPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * A clip's picture drawn [size] big around screen point [center], turned clockwise by [rotationDegrees]. Picture
 * points run from -1 to 1 across each edge, x right and y down, before the turn.
 */
data class PictureBox(
    val center: Offset,
    val size: Size,
    val rotationDegrees: Float,
) {
    /** Width over height of the picture. */
    val aspect: Float
        get() = size.width / size.height

    /** Returns the screen position of picture point [point]. */
    fun toScreen(point: Offset): Offset {
        val local = Offset(point.x * size.width / 2f, point.y * size.height / 2f)
        return center + turned(local, rotationDegrees)
    }

    /** Returns the picture point under screen position [screen]; it lies outside -1 to 1 off the picture. */
    fun toPicture(screen: Offset): Offset {
        val local = turned(screen - center, -rotationDegrees)
        return Offset(local.x * 2f / size.width, local.y * 2f / size.height)
    }

    /** Returns screen vector [screen] as a change of picture point. */
    fun toPictureDelta(screen: Offset): Offset {
        val local = turned(screen, -rotationDegrees)
        return Offset(local.x * 2f / size.width, local.y * 2f / size.height)
    }

    /** Returns true when screen position [screen] falls on the picture. */
    fun contains(screen: Offset): Boolean {
        val point = toPicture(screen)
        return point.x in -1f..1f && point.y in -1f..1f
    }

    /** Placement of pictures on the preview. */
    companion object {
        /**
         * Returns where [clip], on a track of [kind] in [project], draws on a preview of [canvas] pixels: an overlay
         * at its placement, a base clip fitted or filled at its canvas placement. Other tracks have no picture.
         */
        fun of(
            project: Project,
            clip: Clip,
            kind: TrackKind,
            canvas: Size,
        ): PictureBox? {
            val aspect = project.assetOf(clip).displayAspectRatio
            return when (kind) {
                TrackKind.OVERLAY -> {
                    val transform = clip.overlayTransform ?: OverlayTransform()
                    val size = OverlayGeometry.boxSize(canvas, aspect, transform.scale)
                    PictureBox(OverlayGeometry.center(canvas, transform), size, transform.rotationDegrees)
                }

                TrackKind.VIDEO -> {
                    val placement = clip.canvasPlacement ?: CanvasPlacement()
                    val transform = transformOf(placement)
                    val size =
                        if (placement.fit == CanvasFit.FILL) {
                            OverlayGeometry.fillBoxSize(canvas, aspect, transform.scale)
                        } else {
                            OverlayGeometry.boxSize(canvas, aspect, transform.scale)
                        }
                    PictureBox(OverlayGeometry.center(canvas, transform), size, transform.rotationDegrees)
                }

                TrackKind.AUDIO, TrackKind.TEXT -> {
                    null
                }
            }
        }
    }
}

/** Returns [vector] turned clockwise on screen by [degrees]. */
internal fun turned(
    vector: Offset,
    degrees: Float,
): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    return Offset(vector.x * cosine - vector.y * sine, vector.x * sine + vector.y * cosine)
}
