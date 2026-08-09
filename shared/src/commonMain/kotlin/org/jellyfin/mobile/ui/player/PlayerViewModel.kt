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
import org.jellyfin.mobile.data.UserDataStore
import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.StreamInfo
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.domain.msToTicks
import org.jellyfin.mobile.network.SessionExpiredException
import org.jellyfin.mobile.player.PlayerEngine
import org.jellyfin.mobile.player.PlayerStatus
import org.jellyfin.mobile.player.QualityOption
import org.jellyfin.mobile.player.ScreenOrientation
import org.jellyfin.mobile.player.qualityOptionsFor
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_error_failed

data class PlayerUiState(
    val title: String,
    /** A negotiation is in flight. The server has not yet said what, or whether, we can play. */
    val loading: Boolean = true,
    /**
     * Nothing is playable yet: either [loading], or the server has answered and the first frame has
     * not arrived. Distinct from [isBuffering], which is a stall in playback that has already begun.
     *
     * The controls have nothing to act on while this holds — there is no duration to scrub through
     * and no track list to choose from — so the player shows a spinner and a way back out, and
     * nothing else. It clears the first time the engine reports itself ready, and is set again by a
     * re-negotiation, which puts the player back in the same position.
     */
    val preparing: Boolean = true,
    val error: UiText? = null,
    /**
     * Whether playback is meant to be running, which is what the transport control reflects — not
     * whether frames are arriving. Offering Play to someone whose video is merely rebuffering is
     * offering to do the thing that is already happening. [isBuffering] is that other question.
     */
    val isPlaying: Boolean = false,
    /** Meant to be running, but stalled. The spinner's cue. */
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val controlsVisible: Boolean = true,
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val selectedAudioIndex: Int? = null,
    val selectedSubtitleIndex: Int? = null,
    /** The rungs worth offering for this source. Empty until a negotiation has succeeded. */
    val qualityOptions: List<QualityOption> = emptyList(),
    /** The cap in force, in bits per second. Null is Auto — no cap beyond the device profile's. */
    val maxStreamingBitrate: Int? = null,
    val openMenu: PlayerMenu? = null,
    val orientation: ScreenOrientation = ScreenOrientation.Auto,
    /* ---- Diagnostics. Everything below here is read only by the debug overlay. ---- */
    val debugVisible: Boolean = false,
    val playMethod: PlayMethod? = null,
    /** The server's handle on this playback attempt, which is what its log is indexed by. */
    val playSessionId: String? = null,
    val stream: StreamInfo = StreamInfo(),
) {
    /**
     * Fullscreen means locked to landscape. The picture fills the screen in either orientation, so
     * the control is really a rotation lock — but nobody calls it that.
     */
    val isFullscreen: Boolean get() = orientation == ScreenOrientation.Landscape

    val selectedAudio: MediaTrack? get() = audioTracks.firstOrNull { it.index == selectedAudioIndex }
}

/** The pickers the controls can open. Only one is up at a time. */
enum class PlayerMenu {
    Audio,
    Subtitles,
    Quality,
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
    private val userDataStore: UserDataStore,
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
        val current = _state.value
        _state.value = current.copy(loading = true, preparing = true, error = null)
        // Retry keeps the quality the user chose; only the tracks go back to the server's defaults,
        // because a failed negotiation never told us which ones it would have picked.
        start(
            positionTicks = startPositionTicks,
            audioIndex = null,
            subtitleIndex = null,
            maxStreamingBitrate = current.maxStreamingBitrate,
        )
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
        renegotiate(audioIndex = track.index, subtitleIndex = current.selectedSubtitleIndex)
    }

    /** [track] of null turns subtitles off. */
    fun selectSubtitle(track: MediaTrack?) {
        val current = _state.value
        if (current.selectedSubtitleIndex == track?.index) return closeMenu()
        renegotiate(audioIndex = current.selectedAudioIndex, subtitleIndex = track?.index)
    }

    /**
     * Caps the stream, or lifts the cap when [bitrate] is null.
     *
     * Same round trip as a track change, and for the same reason: the bitrate is an input to the
     * server's direct-play-or-transcode decision, so only the server can answer what it means.
     * Capping below what the file needs is precisely how a user asks it to transcode.
     */
    fun selectQuality(bitrate: Int?) {
        val current = _state.value
        if (current.maxStreamingBitrate == bitrate) return closeMenu()
        renegotiate(
            audioIndex = current.selectedAudioIndex,
            subtitleIndex = current.selectedSubtitleIndex,
            maxStreamingBitrate = bitrate,
        )
    }

    private fun renegotiate(
        audioIndex: Int?,
        subtitleIndex: Int?,
        maxStreamingBitrate: Int? = _state.value.maxStreamingBitrate,
    ) {
        val resumeAt = positionMs().msToTicks()
        source?.let { current -> report { repository.reportStopped(current, positionMs()) } }
        _state.value = _state.value.copy(loading = true, preparing = true, openMenu = null)
        start(resumeAt, audioIndex, subtitleIndex, maxStreamingBitrate)
    }

    private fun start(
        positionTicks: Long,
        audioIndex: Int?,
        subtitleIndex: Int?,
        maxStreamingBitrate: Int?,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.resolve(
                    itemId = itemId,
                    startPositionTicks = positionTicks,
                    audioStreamIndex = audioIndex,
                    subtitleStreamIndex = subtitleIndex,
                    maxStreamingBitrate = maxStreamingBitrate,
                )
            }
                .onSuccess { resolved ->
                    source = resolved
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        playMethod = resolved.playMethod,
                        playSessionId = resolved.playSessionId,
                        stream = resolved.stream,
                        audioTracks = resolved.audioTracks,
                        subtitleTracks = resolved.subtitleTracks,
                        selectedAudioIndex = resolved.selectedAudioIndex,
                        selectedSubtitleIndex = resolved.selectedSubtitleIndex,
                        qualityOptions = qualityOptionsFor(resolved.stream.width, resolved.stream.height),
                        maxStreamingBitrate = resolved.maxStreamingBitrate,
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
                        error = error.asUiText(Res.string.player_error_failed),
                    )
                }
        }
    }

    fun openMenu(menu: PlayerMenu) {
        _state.value = _state.value.copy(openMenu = menu, controlsVisible = true)
    }

    fun closeMenu() {
        _state.value = _state.value.copy(openMenu = null)
    }

    fun toggleFullscreen() {
        val current = _state.value
        _state.value = current.copy(
            orientation = if (current.isFullscreen) ScreenOrientation.Auto else ScreenOrientation.Landscape,
        )
    }

    /**
     * Shows or hides the diagnostics overlay.
     *
     * Deliberately not tied to [setControlsVisible]: the overlay is there to be watched while
     * playback runs, which is exactly when the controls have timed out.
     */
    fun toggleDebugInfo() {
        _state.value = _state.value.copy(debugVisible = !_state.value.debugVisible)
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
     * The user is leaving.
     *
     * Called before the navigation pops rather than from the composition's disposal, which does not
     * run until the exit transition has finished — long enough that playback carried on over the
     * top of the animation, audible and visible after the screen had been left.
     *
     * [stop] alone would not have done it: that tells the *server* to stop, and the engine would
     * happily keep decoding into a surface on its way off screen.
     */
    fun leave() {
        engine.pause()
        stopTicker()
        stop()
    }

    /**
     * Tells the server playback ended. Called explicitly on the way out rather than from
     * [onCleared], because it must run on a scope that outlives this view model.
     *
     * Idempotent: [source] is cleared as it goes, so the disposal backstop after [leave] is a
     * no-op rather than a second report.
     */
    fun stop() {
        val current = source ?: return
        val position = positionMs()
        source = null
        report {
            repository.reportStopped(current, position)
            // Watching is the other way watched state changes, and the server decides both the new
            // resume point and whether this counts as finished — so it is asked rather than
            // assumed, after the stop report it derives them from. Broadcast, because the page the
            // user is about to land back on is showing the state from before they pressed play.
            userDataStore.refresh(listOf(itemId))
        }
    }

    private fun observeEngine() {
        viewModelScope.launch {
            engine.state.collect { engineState ->
                _state.value = _state.value.copy(
                    isPlaying = engineState.playWhenReady,
                    isBuffering = engineState.playWhenReady &&
                        engineState.status == PlayerStatus.Buffering,
                    // Ready is the first moment there is anything for the controls to act on: a
                    // duration to scrub through, and a decoded frame behind them. Latched off
                    // rather than tracking the status, so a later rebuffer does not take the
                    // controls away mid-film.
                    preparing = _state.value.preparing && engineState.status != PlayerStatus.Ready,
                    durationMs = engineState.durationMs,
                    error = engineState.error ?: _state.value.error,
                )
                // Keyed on intent, so the poll survives a rebuffer. It costs one call every half
                // second against a position that is not moving, and buys a clock that resumes with
                // the picture instead of waiting for the next state change to restart it.
                if (engineState.playWhenReady) startTicker() else stopTicker()
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
