/*
 * The project aggregate: settings, imported assets, and tracks, validated as one immutable snapshot.
 */
package com.framecraft.editor.domain.model

import com.framecraft.editor.domain.time.FrameRate
import kotlinx.serialization.Serializable

/** Schema version written by this build. */
const val CURRENT_SCHEMA_VERSION: Int = 2

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
        tracks.flatMap { it.clips }.forEach { clip ->
            val asset = requireNotNull(assetsById[clip.assetId]) { "Clip ${clip.id} references unknown asset" }
            asset.durationMicros?.let { sourceDuration ->
                require(clip.trimStartMicros + clip.durationMicros <= sourceDuration) {
                    "Clip ${clip.id} reads past the end of asset ${asset.id}"
                }
            }
        }
        AnchorResolver.validate(tracks)
    }

    /** Exclusive end of the latest clip across all tracks. */
    val durationMicros: Long
        get() = tracks.maxOfOrNull { it.endMicros } ?: 0L

    /** Returns the asset that [clip] reads from. */
    fun assetOf(clip: Clip): MediaAsset = assets.first { it.id == clip.assetId }
}
