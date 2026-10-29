/*
 * The project aggregate: settings, imported assets, and tracks, validated as one immutable snapshot.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Schema version written by this build. */
const val CURRENT_SCHEMA_VERSION: Int = 6

/** An immutable project snapshot. Construction fails if any cross-object invariant is broken. */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val frameRate: FrameRate,
    val assets: List<MediaAsset>,
    val tracks: List<Track>,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val canvas: CanvasSettings = CanvasSettings(),
) {
    init {
        require(id.isNotBlank()) { "Project id must not be blank" }
        val clipIds = tracks.flatMap { track -> track.clips.map { it.id } }
        val allIds = listOf(id) + assets.map { it.id } + tracks.map { it.id } + clipIds
        val duplicates = allIds.groupBy { it }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate ids in project $id: $duplicates" }
        val assetsById = assets.associateBy { it.id }
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                val asset = requireNotNull(assetsById[clip.assetId]) { "Clip ${clip.id} references unknown asset" }
                requireFits(track, clip, asset)
            }
        }
        AnchorResolver.validate(tracks)
    }

    /**
     * Throws unless [clip] on [track] reads inside [asset], suits the track kind, carries text exactly when it is a
     * text clip, and keeps images and text silent.
     */
    private fun requireFits(
        track: Track,
        clip: Clip,
        asset: MediaAsset,
    ) {
        asset.durationMicros?.let { sourceDuration ->
            require(clip.trimStartMicros + clip.sourceSpanMicros <= sourceDuration) {
                "Clip ${clip.id} reads past the end of asset ${asset.id}"
            }
        }
        clip.speed?.let { speed ->
            require(asset.isTimed) { "Clip ${clip.id} has a speed but no timed source" }
            val expected = SpeedMap.durationFor(speed, clip.sourceSpanMicros, frameRate)
            require(abs(expected - clip.durationMicros) < frameRate.startOfFrame(1)) {
                "Clip ${clip.id} lasts ${clip.durationMicros} but its speed plays $expected"
            }
        }
        val suitsTrack =
            when (track.kind) {
                TrackKind.AUDIO -> asset.hasAudio && asset.kind != MediaKind.IMAGE && asset.kind != MediaKind.TEXT
                TrackKind.VIDEO, TrackKind.OVERLAY -> asset.kind == MediaKind.VIDEO || asset.kind == MediaKind.IMAGE
                TrackKind.TEXT -> asset.kind == MediaKind.TEXT
            }
        require(suitsTrack) { "Clip ${clip.id} puts a ${asset.kind} asset on ${track.kind} track ${track.id}" }
        require((clip.text != null) == (track.kind == TrackKind.TEXT)) { "Clip ${clip.id} has text off a text track" }
        require(asset.isTimed || clip.hasDefaultAudio) { "Clip ${clip.id} has no sound to change" }
    }

    /** Exclusive end of the latest clip across all tracks. */
    val durationMicros: Long
        get() = tracks.maxOfOrNull { it.endMicros } ?: 0L

    /** Returns the asset that [clip] reads from. */
    fun assetOf(clip: Clip): MediaAsset = assets.first { it.id == clip.assetId }
}
