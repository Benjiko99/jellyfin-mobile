package org.jellyfin.mobile.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.PlaybackRepository
import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.msToTicks
import org.jellyfin.mobile.network.SessionExpiredException
import org.jellyfin.mobile.player.PlayerEngine
import org.jellyfin.mobile.player.PlayerStatus
import org.jellyfin.mobile.player.ScreenOrientation

data class PlayerUiState(
    val title: String,
    val loading: Boolean = true,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    /** Surfaced so a user can see when their server is transcoding rather than just streaming. */
    val playMethod: PlayMethod? = null,
    val controlsVisible: Boolean = true,
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val selectedAudioIndex: Int? = null,
    val selectedSubtitleIndex: Int? = null,
    val openMenu: TrackMenu? = null,
    val orientation: ScreenOrientation = ScreenOrientation.Auto,
)

enum class TrackMenu {
    Audio,
    Subtitles,
}

/**
 * Drives one playback session: negotiate, hand the result to the engine, then keep the scrubber and
 * the server's resume point in step with it.
 *
 * The engine is created by the composition (it holds decoders tied to the screen) and injected here,
 * so this class never owns its lifetime.
 */
class PlayerViewModel(
    private val itemId: String,
    title: String,
    private val startPositionTicks: Long,
    private val repository: PlaybackRepository,
    private val engine: PlayerEngine,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState(title = title))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * Playback position, kept out of [state] on purpose.
     *
     * It changes twice a second; folding it into the screen-wide state would emit — and recompose
     * everything observing playback, including the video surface — on every tick, for a value only
     * the scrubber reads and only while the controls are up.
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var source: PlaybackSource? = null
    private var ticker: Job? = null

    init {
        observeEngine()
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        start(startPositionTicks, audioIndex = null, subtitleIndex = null)
    }

    /**
     * Switches audio track, which means asking the server for a new stream.
     *
     * A track change cannot be done purely client-side: when the server is transcoding, only the
     * chosen audio track is in the stream at all, so a different one requires re-negotiating. We
     * resume at the current position so the switch costs a rebuffer rather than the user's place.
     */
    fun selectAudio(track: MediaTrack) {
        val current = _state.value
        if (current.selectedAudioIndex == track.index) return closeMenu()
        switchTracks(audioIndex = track.index, subtitleIndex = current.selectedSubtitleIndex)
    }

    /** [track] of null turns subtitles off. */
    fun selectSubtitle(track: MediaTrack?) {
        val current = _state.value
        if (current.selectedSubtitleIndex == track?.index) return closeMenu()
        switchTracks(audioIndex = current.selectedAudioIndex, subtitleIndex = track?.index)
    }

    private fun switchTracks(audioIndex: Int?, subtitleIndex: Int?) {
        val resumeAt = positionMs().msToTicks()
        source?.let { current -> report { repository.reportStopped(current, positionMs()) } }
        _state.value = _state.value.copy(loading = true, openMenu = null)
        start(resumeAt, audioIndex, subtitleIndex)
    }

    private fun start(positionTicks: Long, audioIndex: Int?, subtitleIndex: Int?) {
        viewModelScope.launch {
            runCatching {
                repository.resolve(
                    itemId = itemId,
                    startPositionTicks = positionTicks,
                    audioStreamIndex = audioIndex,
                    subtitleStreamIndex = subtitleIndex,
                )
            }
                .onSuccess { resolved ->
                    source = resolved
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        playMethod = resolved.playMethod,
                        audioTracks = resolved.audioTracks,
                        subtitleTracks = resolved.subtitleTracks,
                        selectedAudioIndex = resolved.selectedAudioIndex,
                        selectedSubtitleIndex = resolved.selectedSubtitleIndex,
                    )
                    engine.load(resolved)
                    engine.play()
                    // Reported before the first frame so the server registers the session even if
                    // the user backs out during buffering — otherwise a transcode is left running.
                    report { repository.reportStart(resolved, positionMs()) }
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.message ?: "Playback failed",
                    )
                }
        }
    }

    fun openMenu(menu: TrackMenu) {
        _state.value = _state.value.copy(openMenu = menu, controlsVisible = true)
    }

    fun closeMenu() {
        _state.value = _state.value.copy(openMenu = null)
    }

    fun cycleOrientation() {
        _state.value = _state.value.copy(orientation = _state.value.orientation.next())
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) engine.pause() else engine.play()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun seekBy(deltaMs: Long) {
        val target = (engine.positionMs() + deltaMs).coerceIn(0, _state.value.durationMs)
        seekTo(target)
    }

    fun setControlsVisible(visible: Boolean) {
        _state.value = _state.value.copy(controlsVisible = visible)
    }

    /**
     * Tells the server playback ended. Called explicitly on the way out rather than from
     * [onCleared], because it must run on a scope that outlives this view model.
     */
    fun stop() {
        val current = source ?: return
        val position = positionMs()
        source = null
        report { repository.reportStopped(current, position) }
    }

    private fun observeEngine() {
        viewModelScope.launch {
            engine.state.collect { engineState ->
                _state.value = _state.value.copy(
                    isPlaying = engineState.isPlaying,
                    durationMs = engineState.durationMs,
                    error = engineState.error ?: _state.value.error,
                )
                if (engineState.isPlaying) startTicker() else stopTicker()
                if (engineState.status == PlayerStatus.Ended) stop()
            }
        }
    }

    /**
     * Polls the engine for position, since no engine pushes it.
     *
     * Runs only while playing, and reports to the server on a much slower cadence than it updates
     * the scrubber — the UI needs smoothness, the server needs a resume point.
     */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            var sinceReport = 0L
            while (isActive) {
                _positionMs.value = engine.positionMs()
                sinceReport += TICK_MS
                if (sinceReport >= REPORT_INTERVAL_MS) {
                    sinceReport = 0
                    source?.let { current ->
                        report { repository.reportProgress(current, positionMs(), isPaused = false) }
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun positionMs(): Long = engine.positionMs()

    /**
     * Reporting is best-effort: a dropped progress ping costs an out-of-date resume point, which is
     * not worth interrupting playback for.
     */
    private fun report(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    private companion object {
        const val TICK_MS = 500L
        const val REPORT_INTERVAL_MS = 10_000L
    }
}
