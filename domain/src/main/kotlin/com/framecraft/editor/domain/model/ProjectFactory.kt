/*
 * Builds a new project from imported assets, placing them back to back on one magnetic video track.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate

/** Creates new projects. */
object ProjectFactory {
    /** How long an imported image shows by default. */
    const val DEFAULT_IMAGE_DURATION_MICROS: Long = 3_000_000L

    /** Creates a project named [name] whose single magnetic video track plays [assets] in order. */
    fun create(
        name: String,
        assets: List<MediaAsset>,
        idGenerator: IdGenerator,
        frameRate: FrameRate = FrameRate.FPS_30,
    ): Project {
        require(assets.isNotEmpty()) { "A project needs at least one asset" }
        var cursor = 0L
        val clips =
            assets.map { asset ->
                val duration = asset.durationMicros ?: DEFAULT_IMAGE_DURATION_MICROS
                Clip(
                    id = idGenerator.next(),
                    assetId = asset.id,
                    startMicros = cursor,
                    trimStartMicros = 0L,
                    durationMicros = duration,
                ).also { cursor += duration }
            }
        val track = Track(id = idGenerator.next(), kind = TrackKind.VIDEO, magnetic = true, clips = clips)
        return Project(
            id = idGenerator.next(),
            name = name,
            frameRate = frameRate,
            assets = assets,
            tracks = listOf(track),
        )
    }
}
