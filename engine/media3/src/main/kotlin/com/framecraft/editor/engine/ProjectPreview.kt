/*
 * Real-time preview of a project backed by Media3 CompositionPlayer; exposes Media3-free playback state.
 */
package com.framecraft.editor.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.engine.plan.PlaybackPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the playhead position is sampled while playing. */
private const val PROGRESS_INTERVAL_MS = 50L

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/** Playback state of the preview, in project time. */
data class PreviewState(
    val isPlaying: Boolean = false,
    val positionMicros: Long = 0L,
    val durationMicros: Long = 0L,
    val errorCode: String? = null,
)

/**
 * Plays a project snapshot. Must be created, used, and released on the main thread.
 */
@OptIn(markerClass = [UnstableApi::class, ExperimentalApi::class])
class ProjectPreview(
    context: Context,
) {
    internal val player: CompositionPlayer = CompositionPlayer.Builder(context.applicationContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PreviewState())
    private var progressJob: Job? = null

    /** Current playback state. */
    val state: StateFlow<PreviewState> = mutableState.asStateFlow()

    private val listener =
        object : Player.Listener {
            /** Mirrors play/pause and starts or stops position sampling. */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mutableState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgress() else stopProgress()
            }

            /** Pins the playhead to the end when playback completes. */
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    mutableState.update { it.copy(isPlaying = false, positionMicros = it.durationMicros) }
                }
            }

            /** Surfaces player failures as an error code. */
            override fun onPlayerError(error: PlaybackException) {
                mutableState.update { it.copy(isPlaying = false, errorCode = error.errorCodeName) }
            }
        }

    init {
        player.addListener(listener)
    }

    /** Replaces what the preview plays with [project], keeping the playhead within the new duration. */
    fun setProject(project: Project) {
        val plan = PlaybackPlanner.plan(project)
        val position = mutableState.value.positionMicros.coerceAtMost(project.durationMicros)
        mutableState.update {
            it.copy(durationMicros = project.durationMicros, positionMicros = position, errorCode = null)
        }
        if (plan.sequences.isEmpty()) {
            player.stop()
            return
        }
        player.setComposition(CompositionFactory.create(plan), position / MICROS_PER_MILLI)
        player.prepare()
    }

    /** Pauses if playing; otherwise plays, restarting from zero when at the end. */
    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            return
        }
        if (player.playbackState == Player.STATE_ENDED) {
            seekTo(0L)
        }
        player.play()
    }

    /** Pauses and switches the player to frame-accurate scrubbing; pause first so ending the scrub cannot resume. */
    fun beginScrub() {
        player.pause()
        player.setScrubbingModeEnabled(true)
    }

    /** Leaves scrubbing mode and records the final position. */
    fun endScrub() {
        player.setScrubbingModeEnabled(false)
        syncPosition()
    }

    /** Moves the playhead to [positionMicros]. */
    fun seekTo(positionMicros: Long) {
        val clamped = positionMicros.coerceIn(0L, mutableState.value.durationMicros)
        player.seekTo(clamped / MICROS_PER_MILLI)
        mutableState.update { it.copy(positionMicros = clamped) }
    }

    /** Releases the player; the instance must not be used afterwards. */
    fun release() {
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }

    /** Samples the player position until playback stops. */
    private fun startProgress() {
        progressJob?.cancel()
        progressJob =
            scope.launch {
                while (isActive) {
                    syncPosition()
                    delay(PROGRESS_INTERVAL_MS)
                }
            }
    }

    /** Stops sampling and records the final position. */
    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
        syncPosition()
    }

    /** Copies the player position into the state. */
    private fun syncPosition() {
        val micros = (player.currentPosition * MICROS_PER_MILLI).coerceIn(0L, mutableState.value.durationMicros)
        mutableState.update { it.copy(positionMicros = micros) }
    }
}
