package org.jellyfin.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.player.PlayerEngine
import org.jellyfin.mobile.player.PlayerState
import org.jellyfin.mobile.player.ScreenOrientation
import org.jellyfin.mobile.player.VideoSurface
import org.jellyfin.mobile.player.rememberOrientationController
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/** How long the controls stay up after the last interaction. */
private const val ControlsTimeoutMs = 4000L

/**
 * The player.
 *
 * Everything except [VideoSurface] is shared: the surface is the only part that has to be a
 * platform view, so Android and iOS get identical controls from this file.
 */
@Composable
@Suppress("LongParameterList")
fun PlayerScreen(
    state: PlayerUiState,
    positionMs: Long,
    engine: PlayerEngine,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onRetry: () -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit,
    onOpenMenu: (TrackMenu) -> Unit,
    onCloseMenu: () -> Unit,
    onSelectAudio: (MediaTrack) -> Unit,
    onSelectSubtitle: (MediaTrack?) -> Unit,
    onCycleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orientationController = rememberOrientationController()
    LaunchedEffect(state.orientation) { orientationController.request(state.orientation) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(engine, Modifier.fillMaxSize())

        // Tapping anywhere toggles the controls. No ripple — this is the video, not a button.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onControlsVisibleChange(!state.controlsVisible) },
        )

        when {
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )

            state.error != null -> PlaybackError(state.error, onRetry, onBack, Modifier.align(Alignment.Center))
        }

        if (state.controlsVisible && state.error == null) {
            // Keyed on visibility rather than position: position changes every tick, which would
            // restart the timer continuously and mean the controls never hid at all. Showing them
            // again flips controlsVisible, which restarts the countdown — which is what we want.
            LaunchedEffect(state.controlsVisible, state.isPlaying) {
                if (state.isPlaying) {
                    delay(ControlsTimeoutMs)
                    onControlsVisibleChange(false)
                }
            }

            Controls(
                state = state,
                positionMs = positionMs,
                onBack = onBack,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSeekBy = onSeekBy,
                onOpenMenu = onOpenMenu,
                onCycleOrientation = onCycleOrientation,
            )
        }

        state.openMenu?.let { menu ->
            TrackSheet(
                menu = menu,
                state = state,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
                onDismiss = onCloseMenu,
            )
        }
    }
}

/**
 * Track picker.
 *
 * Not a `ModalBottomSheet`: the player runs edge-to-edge over a black background in either
 * orientation, and a sheet anchored to the bottom is unusable in landscape, which is exactly when
 * someone is most likely to be changing subtitles.
 */
@Composable
private fun TrackSheet(
    menu: TrackMenu,
    state: PlayerUiState,
    onSelectAudio: (MediaTrack) -> Unit,
    onSelectSubtitle: (MediaTrack?) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 420.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                // Swallows taps so clicking inside the card does not dismiss it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = if (menu == TrackMenu.Audio) "Audio" else "Subtitles",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            LazyColumn {
                if (menu == TrackMenu.Subtitles) {
                    item {
                        TrackRow(
                            label = "None",
                            selected = state.selectedSubtitleIndex == null,
                            onClick = { onSelectSubtitle(null) },
                        )
                    }
                }

                val tracks = if (menu == TrackMenu.Audio) state.audioTracks else state.subtitleTracks
                items(tracks, key = { it.index }) { track ->
                    val selected = track.index == when (menu) {
                        TrackMenu.Audio -> state.selectedAudioIndex
                        TrackMenu.Subtitles -> state.selectedSubtitleIndex
                    }
                    TrackRow(
                        label = track.label,
                        selected = selected,
                        onClick = {
                            if (menu == TrackMenu.Audio) onSelectAudio(track) else onSelectSubtitle(track)
                        },
                    )
                }

                if (tracks.isEmpty() && menu == TrackMenu.Audio) {
                    item {
                        Text(
                            text = "This item has no selectable audio tracks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (selected) "✓" else " ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Controls(
    state: PlayerUiState,
    positionMs: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onOpenMenu: (TrackMenu) -> Unit,
    onCycleOrientation: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).safeDrawingPadding()) {
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = Color.White)
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Audio is hidden when there is nothing to choose between; subtitles always show,
            // because "off" is itself a choice a user looks for.
            if (state.audioTracks.size > 1) {
                TextButton(onClick = { onOpenMenu(TrackMenu.Audio) }) {
                    Text("Audio", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = { onOpenMenu(TrackMenu.Subtitles) }) {
                Text(
                    text = if (state.selectedSubtitleIndex != null) "CC •" else "CC",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = onCycleOrientation) {
                Text(
                    text = state.orientation.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onSeekBy(-10_000) }) {
                Text("−10s", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onPlayPause) {
                Text(
                    text = if (state.isPlaying) "Pause" else "Play",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            TextButton(onClick = { onSeekBy(30_000) }) {
                Text("+30s", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        Scrubber(
            state = state,
            positionMs = positionMs,
            onSeek = onSeek,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Scrubber(
    state: PlayerUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging, the thumb follows the finger rather than the engine — otherwise the position
    // poll fights the gesture and the thumb jumps back.
    var dragging by remember { mutableStateOf(false) }
    var draggedMs by remember { mutableStateOf(0f) }

    val duration = state.durationMs.coerceAtLeast(1)
    val position = if (dragging) draggedMs else positionMs.toFloat()

    Column(modifier.fillMaxWidth()) {
        Slider(
            value = position.coerceIn(0f, duration.toFloat()),
            valueRange = 0f..duration.toFloat(),
            onValueChange = {
                dragging = true
                draggedMs = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(draggedMs.toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTime(position.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            state.playMethod?.let {
                Text(
                    text = it.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Text(
                text = formatTime(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PlaybackError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = Color.White, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onBack) { Text("Back", color = Color.White) }
    }
}

/** `h:mm:ss` past an hour, `m:ss` below it — the convention every player uses. */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
    }
}

private val PlayMethod.label: String
    get() = when (this) {
        PlayMethod.DirectPlay -> "Direct play"
        PlayMethod.DirectStream -> "Remuxing"
        PlayMethod.Transcode -> "Transcoding"
    }

/** Names the state the control is *in*, not the one tapping it moves to. */
private val ScreenOrientation.label: String
    get() = when (this) {
        ScreenOrientation.Auto -> "Auto"
        ScreenOrientation.Landscape -> "Landscape"
        ScreenOrientation.Portrait -> "Portrait"
    }

/*
 * Previews. There is no picture behind the controls: Android's VideoSurface draws nothing for an
 * engine it does not recognise, so these show the controls over black. That is the right thing to
 * look at — the controls are the shared part, and the picture is whatever is being watched.
 *
 * Landscape by default, since that is how a video is watched; the portrait preview is here because
 * the controls have to survive the narrower bar as well.
 */

private const val LandscapeWidth = 844
private const val LandscapeHeight = 390
private const val PortraitWidth = 390
private const val PortraitHeight = 844

@Preview(name = "Player · playing", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerPlayingPreview() {
    PreviewSurface {
        PlayerScreenPreview(state = playingState(), positionMs = 1_284_000)
    }
}

/**
 * Paused, transcoding, and with a subtitle track on — the CC control gains a dot to say so, which
 * is the only indication anywhere that subtitles are active.
 */
@Preview(name = "Player · paused with subtitles", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerPausedPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                isPlaying = false,
                playMethod = PlayMethod.Transcode,
                selectedSubtitleIndex = 4,
                orientation = ScreenOrientation.Landscape,
            ),
            positionMs = 42_000,
        )
    }
}

@Preview(name = "Player · portrait", widthDp = PortraitWidth, heightDp = PortraitHeight)
@Composable
private fun PlayerPortraitPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                title = "Northern Line · S2:E4 · The Undertow",
                orientation = ScreenOrientation.Portrait,
            ),
            positionMs = 600_000,
        )
    }
}

/** Controls hidden, which is what a user watching rather than fiddling actually sees. */
@Preview(name = "Player · controls hidden", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerControlsHiddenPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(controlsVisible = false),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · subtitle picker", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerSubtitleMenuPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                openMenu = TrackMenu.Subtitles,
                selectedSubtitleIndex = 5,
            ),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · audio picker", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerAudioMenuPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(openMenu = TrackMenu.Audio),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · loading", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerLoadingPreview() {
    PreviewSurface {
        PlayerScreenPreview(state = PlayerUiState(title = "The Cartographer"), positionMs = 0)
    }
}

@Preview(name = "Player · error", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerErrorPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = PlayerUiState(
                title = "The Cartographer",
                loading = false,
                error = "This item has no playable media source",
            ),
            positionMs = 0,
        )
    }
}

/** Mid-film, playing, with tracks to choose between. The base every preview above varies from. */
private fun playingState() = PlayerUiState(
    title = "The Cartographer",
    loading = false,
    isPlaying = true,
    durationMs = 7_440_000,
    playMethod = PlayMethod.DirectPlay,
    audioTracks = PreviewData.audioTracks,
    subtitleTracks = PreviewData.subtitleTracks,
    selectedAudioIndex = 1,
)

@Composable
private fun PlayerScreenPreview(state: PlayerUiState, positionMs: Long) {
    PlayerScreen(
        state = state,
        positionMs = positionMs,
        engine = PreviewPlayerEngine,
        onBack = {},
        onPlayPause = {},
        onSeek = {},
        onSeekBy = {},
        onRetry = {},
        onControlsVisibleChange = {},
        onOpenMenu = {},
        onCloseMenu = {},
        onSelectAudio = {},
        onSelectSubtitle = {},
        onCycleOrientation = {},
    )
}

/**
 * An engine that does nothing.
 *
 * The real ones own decoders and a platform surface, neither of which a preview can have. Android's
 * [VideoSurface] checks the engine type and returns early on one it does not know, so this leaves
 * the picture black rather than failing to render.
 */
private object PreviewPlayerEngine : PlayerEngine {
    override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
    override fun load(source: PlaybackSource) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun positionMs(): Long = 0
    override fun release() = Unit
}
