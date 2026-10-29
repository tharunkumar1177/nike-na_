/*
 * The project aggregate: settings, imported assets, and tracks, validated as one immutable snapshot.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.Serializable

/** Schema version written by this build. */
const val CURRENT_SCHEMA_VERSION: Int = 3

/** An immutable project snapshot. Construction fails if any cross-object invariant is broken. */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val frameRate: FrameRate,
    val assets: List<MediaAsset>,
    val tracks: List<Track>,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
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

    /** Throws unless [clip] on [track] reads inside [asset], suits the track kind, and keeps images silent. */
    private fun requireFits(
        track: Track,
        clip: Clip,
        asset: MediaAsset,
    ) {
        asset.durationMicros?.let { sourceDuration ->
            require(clip.trimStartMicros + clip.durationMicros <= sourceDuration) {
                "Clip ${clip.id} reads past the end of asset ${asset.id}"
            }
        }
        val suitsTrack =
            when (track.kind) {
                TrackKind.AUDIO -> asset.hasAudio && asset.kind != MediaKind.IMAGE
                TrackKind.VIDEO, TrackKind.OVERLAY -> asset.kind != MediaKind.AUDIO
            }
        require(suitsTrack) { "Clip ${clip.id} puts a ${asset.kind} asset on ${track.kind} track ${track.id}" }
        require(asset.kind != MediaKind.IMAGE || clip.hasDefaultAudio) {
            "Image clip ${clip.id} cannot carry volume or fades"
        }
    }

    /** Exclusive end of the latest clip across all tracks. */
    val durationMicros: Long
        get() = tracks.maxOfOrNull { it.endMicros } ?: 0L

    /** Returns the asset that [clip] reads from. */
    fun assetOf(clip: Clip): MediaAsset = assets.first { it.id == clip.assetId }
}
