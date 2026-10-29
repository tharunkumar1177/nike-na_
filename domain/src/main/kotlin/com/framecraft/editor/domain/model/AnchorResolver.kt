/*
 * The single place that walks the anchor forest: resolves, rewrites, and validates anchored clip starts.
 */
package com.framecraft.editor.domain.model

/** Keeps anchored clips in sync with the clips they are attached to. */
object AnchorResolver {
    /**
     * Returns [tracks] with every anchored clip moved to the start its anchor resolves to and each changed track
     * re-sorted. Throws [IllegalArgumentException] for a missing parent, a cycle, or a start before zero.
     */
    fun materialize(tracks: List<Track>): List<Track> {
        val starts = resolvedStarts(tracks)
        return tracks.map { track ->
            if (track.clips.none { it.anchor != null }) {
                track
            } else {
                val placed = track.clips.map { clip -> starts[clip.id]?.let { clip.copy(startMicros = it) } ?: clip }
                track.copy(clips = placed.sortedBy { it.startMicros })
            }
        }
    }

    /**
     * Throws [IllegalArgumentException] unless anchors in [tracks] are consistent: every parent exists, anchors form
     * no cycle, anchored clips sit on non-magnetic tracks at their resolved start, and only overlay tracks hold
     * overlay transforms.
     */
    fun validate(tracks: List<Track>) {
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                require(clip.anchor == null || !track.magnetic) {
                    "Clip ${clip.id} is anchored but sits on magnetic track ${track.id}"
                }
                require(clip.overlayTransform == null || track.kind == TrackKind.OVERLAY) {
                    "Clip ${clip.id} has an overlay transform but sits on non-overlay track ${track.id}"
                }
            }
        }
        val starts = resolvedStarts(tracks)
        tracks.flatMap { it.clips }.forEach { clip ->
            val resolved = starts[clip.id]
            require(resolved == null || resolved == clip.startMicros) {
                "Anchored clip ${clip.id} starts at ${clip.startMicros} but its anchor resolves to $resolved"
            }
        }
    }

    /** Returns the clips in [tracks] anchored directly to [parentClipId]. */
    fun childrenOf(
        tracks: List<Track>,
        parentClipId: String,
    ): List<Clip> = tracks.flatMap { it.clips }.filter { it.anchor?.parentClipId == parentClipId }

    /** Returns the resolved start of every anchored clip in [tracks], keyed by clip id. */
    private fun resolvedStarts(tracks: List<Track>): Map<String, Long> {
        val clips = tracks.flatMap { it.clips }.associateBy { it.id }
        val resolved = HashMap<String, Long>()
        clips.values
            .filter { it.anchor != null }
            .forEach { resolve(it, clips, resolved, mutableSetOf()) }
        return resolved
    }

    /** Returns where [clip] starts, resolving its anchor chain through [clips] and caching results in [resolved]. */
    private fun resolve(
        clip: Clip,
        clips: Map<String, Clip>,
        resolved: MutableMap<String, Long>,
        visiting: MutableSet<String>,
    ): Long {
        val anchor = clip.anchor
        val known = resolved[clip.id]
        return when {
            anchor == null -> {
                clip.startMicros
            }

            known != null -> {
                known
            }

            else -> {
                require(visiting.add(clip.id)) { "Anchors form a cycle through clip ${clip.id}" }
                val parent =
                    requireNotNull(clips[anchor.parentClipId]) {
                        "Clip ${clip.id} is anchored to missing clip ${anchor.parentClipId}"
                    }
                val start = resolve(parent, clips, resolved, visiting) + anchor.offsetMicros
                require(start >= 0) { "Anchored clip ${clip.id} would start before zero: $start" }
                visiting.remove(clip.id)
                resolved[clip.id] = start
                start
            }
        }
    }
}
