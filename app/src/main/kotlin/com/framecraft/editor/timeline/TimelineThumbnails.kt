/*
 * Maps visible clips to thumbnail tiles and cache keys; shared by drawing and prefetching so both agree.
 */
package com.framecraft.editor.timeline

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.speedMap
import com.framecraft.editor.engine.ThumbnailKey

/** The visible window of the timeline in pixels. */
data class TimelineViewport(
    val centerMicros: Long,
    val pxPerSecond: Float,
    val widthPx: Float,
    val rowHeightPx: Float,
)

/** A tile of a clip paired with the thumbnail it shows. */
data class ClipThumbnail(
    val tile: ThumbnailTile,
    val key: ThumbnailKey,
    val fullTileWidthPx: Float,
)

/** Returns the visible thumbnail tiles of [clip]; tiles are as wide as the asset's aspect ratio at row height. */
fun clipThumbnails(
    project: Project,
    clip: Clip,
    viewport: TimelineViewport,
): List<ClipThumbnail> {
    val asset = project.assetOf(clip)
    val left = TimelineGeometry.timeToX(clip.startMicros, viewport.centerMicros, viewport.pxPerSecond, viewport.widthPx)
    val right = TimelineGeometry.timeToX(clip.endMicros, viewport.centerMicros, viewport.pxPerSecond, viewport.widthPx)
    val tileWidth = viewport.rowHeightPx * asset.displayAspectRatio
    val span = ClipSpan(left, right - left, clip.trimStartMicros, clip.speedMap(project.frameRate))
    return TimelineGeometry.thumbnailTiles(span, tileWidth, viewport.pxPerSecond, viewport.widthPx).map { tile ->
        val sourceMicros = if (asset.kind == MediaKind.IMAGE) 0L else tile.sourceMicros
        ClipThumbnail(tile, ThumbnailKey(asset.uri, asset.kind, sourceMicros), tileWidth)
    }
}

/**
 * Returns the distinct thumbnail keys visible across all picture tracks; audio lanes show waveforms and text lanes
 * their words instead.
 */
fun visibleThumbnailKeys(
    project: Project,
    viewport: TimelineViewport,
): List<ThumbnailKey> =
    project.tracks
        .filter { it.kind == TrackKind.VIDEO || it.kind == TrackKind.OVERLAY }
        .flatMap { it.clips }
        .flatMap { clip -> clipThumbnails(project, clip, viewport).map { it.key } }
        .distinct()
