/*
 * Export settings chosen by the user and the value types the export planner derives from them.
 */
package com.framecraft.editor.domain.export

import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.Serializable

/** Output resolution presets, named by the length of the video's short side in pixels. */
@Serializable
enum class ExportResolution(
    val shortSide: Int,
) {
    HD_720(shortSide = 720),
    FULL_HD_1080(shortSide = 1080),
    UHD_4K(shortSide = 2160),
}

/** Encoding quality presets; higher quality spends more bits per pixel. */
@Serializable
enum class ExportQuality {
    STANDARD,
    HIGH,
}

/** What the user picked for one export of a project. */
@Serializable
data class ExportSettings(
    val resolution: ExportResolution,
    val frameRate: FrameRate,
    val quality: ExportQuality,
)

/** Output frame size in pixels; both sides are even, as H.264 encoders require. */
data class ExportSize(
    val width: Int,
    val height: Int,
)

/** The choices offered for a project: [resolutions] from smallest to largest and [frameRates] from fastest. */
data class ExportOptions(
    val resolutions: List<ExportResolution>,
    val frameRates: List<FrameRate>,
)
