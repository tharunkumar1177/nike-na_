/*
 * Unit tests for the plan's mixer and ducking: track volumes scale their clips, and clips that duck get windows under
 * audible voiceovers, with close voiceovers merged.
 */
package com.framecraft.editor.engine.plan

import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One second in microseconds. */
private const val SECOND = 1_000_000L

/**
 * Verifies the mix of a base video A [0, 6 s) that ducks by 60 on a track at 50 %, music M [0, 6 s) at 80 % that
 * ducks by 50, and voiceovers at [1 s, 2 s), [2.3 s, 3 s), and [5 s, 5.5 s).
 */
class AudioMixTest {
    private val video = asset("v", MediaKind.VIDEO, 10 * SECOND)

    private val song = asset("song", MediaKind.AUDIO, 10 * SECOND)

    private val take = asset("take", MediaKind.AUDIO, 10 * SECOND)

    /** Returns an asset [id] of [kind] lasting [durationMicros], with sound. */
    private fun asset(
        id: String,
        kind: MediaKind,
        durationMicros: Long,
    ): MediaAsset {
        val picture = kind == MediaKind.VIDEO
        return MediaAsset(
            id = id,
            uri = "content://media/$id",
            kind = kind,
            durationMicros = durationMicros,
            width = if (picture) 1920 else 1,
            height = if (picture) 1080 else 1,
            rotationDegrees = 0,
            hasAudio = true,
            fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
        )
    }

    /** Builds the project with voiceover takes at [takeVolume] and the base track at [baseVolume]. */
    private fun project(
        takeVolume: Int = 100,
        baseVolume: Int = 50,
    ): Project {
        val spans = listOf(1 * SECOND to SECOND, 2_300_000L to 700_000L, 5 * SECOND to 500_000L)
        val voiceovers =
            spans.mapIndexed { index, (at, length) ->
                Clip("vo$index", "take", at, 0L, length, volumePercent = takeVolume, voiceover = true)
            }
        val base = Clip("a", "v", 0L, 0L, 6 * SECOND, autoDuckPercent = 60)
        return Project(
            id = "p",
            name = "P",
            frameRate = FrameRate.FPS_30,
            assets = listOf(video, song, take),
            tracks =
                listOf(
                    Track("base", TrackKind.VIDEO, true, listOf(base), baseVolume),
                    Track(
                        "music",
                        TrackKind.AUDIO,
                        false,
                        listOf(Clip("m", "song", 0L, 0L, 6 * SECOND, volumePercent = 80, autoDuckPercent = 50)),
                    ),
                    Track("vo", TrackKind.AUDIO, false, voiceovers),
                ),
        )
    }

    /** Returns the media segments of [sequence], in order. */
    private fun mediaOf(sequence: SequencePlan) = sequence.segments.filterIsInstance<PlanSegment.Media>()

    /** A track's volume scales its clips' own volumes. */
    @Test
    fun trackVolumeScalesClips() {
        val (base, music) = PlaybackPlanner.plan(project()).sequences
        assertEquals(50, mediaOf(base).single().volumePercent)
        assertEquals(80, mediaOf(music).single().volumePercent)
        val muted = PlaybackPlanner.plan(project(baseVolume = 0)).sequences.first()
        assertEquals(0, mediaOf(muted).single().volumePercent)
    }

    /** Clips that duck get one window per merged voiceover span at their floor, in their own time. */
    @Test
    fun duckingClipsGetMergedWindows() {
        val (base, music, voice) = PlaybackPlanner.plan(project()).sequences
        val baseWindows = listOf(DuckWindow(SECOND, 3 * SECOND, 0.4f), DuckWindow(5 * SECOND, 5_500_000L, 0.4f))
        assertEquals(baseWindows, mediaOf(base).single().duckWindows)
        assertEquals(baseWindows.map { it.copy(floorGain = 0.5f) }, mediaOf(music).single().duckWindows)
        assertTrue(mediaOf(voice).all { it.duckWindows.isEmpty() })
    }

    /** Silent voiceovers duck nothing. */
    @Test
    fun silentVoiceoversDuckNothing() {
        val sequences = PlaybackPlanner.plan(project(takeVolume = 0)).sequences
        assertTrue(sequences.flatMap(::mediaOf).all { it.duckWindows.isEmpty() })
    }

    /** A window reaches a clip that starts within the ramp after a voiceover, in that clip's own time. */
    @Test
    fun windowsUseClipTime() {
        val take = PlanSegment.Media("take", MediaKind.AUDIO, SECOND, 0L, SECOND, voiceover = true)
        val voice = SequencePlan(listOf(take), kind = TrackKind.AUDIO)
        val later = PlanSegment.Media("song", MediaKind.AUDIO, 10 * SECOND, 0L, SECOND, autoDuckPercent = 100)
        val music = SequencePlan(listOf(PlanSegment.Gap(1_100_000L), later), kind = TrackKind.AUDIO)
        val ducked = AudioMix.ducked(listOf(voice, music))
        assertEquals(listOf(DuckWindow(-1_100_000L, -100_000L, 0f)), mediaOf(ducked[1]).single().duckWindows)
        val far = SequencePlan(listOf(PlanSegment.Gap(2 * SECOND), later), kind = TrackKind.AUDIO)
        val undisturbed = mediaOf(AudioMix.ducked(listOf(voice, far))[1]).single()
        assertTrue(undisturbed.duckWindows.isEmpty())
    }
}
