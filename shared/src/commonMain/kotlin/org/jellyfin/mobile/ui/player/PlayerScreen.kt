package org.jellyfin.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.StreamInfo
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.player.PlayerEngine
import org.jellyfin.mobile.player.PlayerState
import org.jellyfin.mobile.player.ScreenOrientation
import org.jellyfin.mobile.player.VideoSurface
import org.jellyfin.mobile.player.qualityOptionsFor
import org.jellyfin.mobile.player.rememberOrientationController
import org.jellyfin.mobile.player.rememberPlaybackHardware
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.action_back
import org.jellyfin.mobile.resources.action_retry
import org.jellyfin.mobile.resources.player_audio
import org.jellyfin.mobile.resources.player_bitrate_kbps
import org.jellyfin.mobile.resources.player_bitrate_mbps
import org.jellyfin.mobile.resources.player_debug_audio_codec
import org.jellyfin.mobile.resources.player_debug_cap
import org.jellyfin.mobile.resources.player_debug_container
import org.jellyfin.mobile.resources.player_debug_hide
import org.jellyfin.mobile.resources.player_debug_method
import org.jellyfin.mobile.resources.player_debug_resolution
import org.jellyfin.mobile.resources.player_debug_resolution_label
import org.jellyfin.mobile.resources.player_debug_session
import org.jellyfin.mobile.resources.player_debug_show
import org.jellyfin.mobile.resources.player_debug_source_bitrate
import org.jellyfin.mobile.resources.player_debug_unknown
import org.jellyfin.mobile.resources.player_debug_video_codec
import org.jellyfin.mobile.resources.player_fullscreen_enter
import org.jellyfin.mobile.resources.player_fullscreen_exit
import org.jellyfin.mobile.resources.player_method_direct_play
import org.jellyfin.mobile.resources.player_method_remuxing
import org.jellyfin.mobile.resources.player_method_transcoding
import org.jellyfin.mobile.resources.player_no_audio_tracks
import org.jellyfin.mobile.resources.player_pause
import org.jellyfin.mobile.resources.player_play
import org.jellyfin.mobile.resources.player_quality
import org.jellyfin.mobile.resources.player_quality_auto
import org.jellyfin.mobile.resources.player_quality_option
import org.jellyfin.mobile.resources.player_seek_back
import org.jellyfin.mobile.resources.player_seek_forward
import org.jellyfin.mobile.resources.player_subtitles
import org.jellyfin.mobile.resources.player_subtitles_off
import org.jellyfin.mobile.resources.player_subtitles_on
import org.jellyfin.mobile.resources.player_track_none
import org.jellyfin.mobile.ui.PlayerRoute
import org.jellyfin.mobile.ui.components.AudioTrackIcon
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.components.DebugIcon
import org.jellyfin.mobile.ui.components.FullscreenExitIcon
import org.jellyfin.mobile.ui.components.FullscreenIcon
import org.jellyfin.mobile.ui.components.PauseIcon
import org.jellyfin.mobile.ui.components.PlayIcon
import org.jellyfin.mobile.ui.components.QualityIcon
import org.jellyfin.mobile.ui.components.SeekBackIcon
import org.jellyfin.mobile.ui.components.SeekForwardIcon
import org.jellyfin.mobile.ui.components.SubtitlesIcon
import org.jellyfin.mobile.ui.components.SubtitlesOffIcon
import org.jellyfin.mobile.ui.header
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.SystemBarAppearance
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How long the controls stay up after the last interaction. */
private const val ControlsTimeoutMs = 4000L

/**
 * Height of the top control row. The debug overlay is offset by it so the two never overlap —
 * the overlay outlives the controls, so it cannot simply be laid out below them.
 */
private val TopControlsHeight = 56.dp

/**
 * The seek amounts, shared by the transport buttons and the double-tap gesture.
 *
 * Drawn into [SeekBackIcon] and [SeekForwardIcon], so changing either number means changing the
 * icon with it.
 */
private const val SeekBackMs = -10_000L
private const val SeekForwardMs = 30_000L

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
    onOpenMenu: (PlayerMenu) -> Unit,
    onCloseMenu: () -> Unit,
    onSelectAudio: (MediaTrack) -> Unit,
    onSelectSubtitle: (MediaTrack?) -> Unit,
    onSelectQuality: (Int?) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDebugInfo: () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    /** Applied once as the player opens, when the user asked for their brightness to be remembered. */
    initialBrightness: Float? = null,
    onBrightnessSettled: (Float) -> Unit = {},
) {
    val orientationController = rememberOrientationController()
    LaunchedEffect(state.orientation) { orientationController.request(state.orientation) }

    // Dark regardless of the app's scheme: the player is black video and white controls whichever
    // theme the user picked, so the clock and the battery over it have to be light. Restored on the
    // way out, back to whatever the app itself is drawing in.
    SystemBarAppearance(darkTheme = true)

    val hardware = rememberPlaybackHardware()
    // Keyed on the value, not on Unit: it arrives from disk a moment after the player opens, so an
    // effect that ran once on entry would run before there was anything to apply.
    LaunchedEffect(initialBrightness) {
        initialBrightness?.let(hardware::setBrightness)
    }

    // The auto-hide effect calls this on the far side of a delay, and must not be keyed on it — see
    // the comment on that effect. Tracking the latest lambda keeps the timer running across a
    // recomposition that hands us a new one.
    val currentOnControlsVisibleChange by rememberUpdatedState(onControlsVisibleChange)

    Box(modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(engine, Modifier.fillMaxSize())

        // Tapping anywhere toggles the controls; dragging either half adjusts brightness or volume.
        // One layer, because whichever node takes the pointer takes all of it.
        PlayerGestureLayer(
            hardware = hardware,
            gesturesEnabled = gesturesEnabled,
            onTap = { onControlsVisibleChange(!state.controlsVisible) },
            // The same amounts the transport buttons use, from the same constants: a double tap and
            // the button beside it are the two ways to ask for one thing, and they would be a bug
            // waiting to happen if they could drift apart.
            onSeekBackward = { onSeekBy(SeekBackMs) },
            onSeekForward = { onSeekBy(SeekForwardMs) },
            onBrightnessSettled = onBrightnessSettled,
            onHideControls = { onControlsVisibleChange(false) },
        )

        when {
            // Getting ready, or stalled with the controls down. Once playback has started and the
            // controls are up, the transport row carries the spinner instead, in the slot the play
            // button vacates — but while preparing there is no transport row to put it in.
            state.preparing || (state.isBuffering && !state.controlsVisible) -> CircularProgressIndicator(
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
                    currentOnControlsVisibleChange(false)
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
                onToggleFullscreen = onToggleFullscreen,
                onToggleDebugInfo = onToggleDebugInfo,
            )
        }

        // Outside the controls block on purpose: the point of the overlay is to be readable while
        // playback runs, which is exactly when the controls have timed out. It emits no pointer
        // input, so taps fall through to the toggle layer beneath it.
        if (state.debugVisible) {
            DebugOverlay(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(start = 12.dp, top = TopControlsHeight, end = 12.dp),
            )
        }

        state.openMenu?.let { menu ->
            PickerSheet(
                menu = menu,
                state = state,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
                onSelectQuality = onSelectQuality,
                onDismiss = onCloseMenu,
            )
        }
    }
}

/**
 * Audio, subtitle and quality picker.
 *
 * Not a `ModalBottomSheet`: the player runs edge-to-edge over a black background in either
 * orientation, and a sheet anchored to the bottom is unusable in landscape, which is exactly when
 * someone is most likely to be changing subtitles.
 */
@Composable
private fun PickerSheet(
    menu: PlayerMenu,
    state: PlayerUiState,
    onSelectAudio: (MediaTrack) -> Unit,
    onSelectSubtitle: (MediaTrack?) -> Unit,
    onSelectQuality: (Int?) -> Unit,
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
        // A Surface, not a Column over Modifier.background. Two things come with it that the card
        // needs and a painted background does not: it publishes LocalContentColor alongside the
        // colour it paints, so text inside inherits onSurface instead of Compose's default black —
        // invisible on this card in dark mode — and it takes pointer input, which is what stops a
        // tap inside the card reaching the scrim behind and dismissing the sheet.
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = stringResource(menu.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                when (menu) {
                    PlayerMenu.Audio, PlayerMenu.Subtitles -> TrackList(menu, state, onSelectAudio, onSelectSubtitle)
                    PlayerMenu.Quality -> QualityList(state, onSelectQuality)
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    menu: PlayerMenu,
    state: PlayerUiState,
    onSelectAudio: (MediaTrack) -> Unit,
    onSelectSubtitle: (MediaTrack?) -> Unit,
) {
    LazyColumn {
        if (menu == PlayerMenu.Subtitles) {
            item {
                PickerRow(
                    label = stringResource(Res.string.player_track_none),
                    selected = state.selectedSubtitleIndex == null,
                    onClick = { onSelectSubtitle(null) },
                )
            }
        }

        val tracks = if (menu == PlayerMenu.Audio) state.audioTracks else state.subtitleTracks
        items(tracks, key = { it.index }) { track ->
            val selected = track.index == when (menu) {
                PlayerMenu.Audio -> state.selectedAudioIndex
                else -> state.selectedSubtitleIndex
            }
            PickerRow(
                label = track.label.resolve(),
                selected = selected,
                onClick = {
                    if (menu == PlayerMenu.Audio) onSelectAudio(track) else onSelectSubtitle(track)
                },
            )
        }

        if (tracks.isEmpty() && menu == PlayerMenu.Audio) {
            item {
                Text(
                    text = stringResource(Res.string.player_no_audio_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * The quality ladder, with Auto at the top.
 *
 * Auto leads for the same reason "None" leads the subtitle list: it is the way back out of a choice
 * the user made, and it is where most people should stay.
 */
@Composable
private fun QualityList(state: PlayerUiState, onSelectQuality: (Int?) -> Unit) {
    LazyColumn {
        item {
            PickerRow(
                label = stringResource(Res.string.player_quality_auto),
                selected = state.maxStreamingBitrate == null,
                onClick = { onSelectQuality(null) },
            )
        }
        items(state.qualityOptions, key = { it.bitrate }) { option ->
            PickerRow(
                label = stringResource(
                    Res.string.player_quality_option,
                    option.maxHeight.toString(),
                    formatBitrate(option.bitrate),
                ),
                selected = state.maxStreamingBitrate == option.bitrate,
                onClick = { onSelectQuality(option.bitrate) },
            )
        }
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
@Suppress("LongParameterList")
private fun Controls(
    state: PlayerUiState,
    positionMs: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onOpenMenu: (PlayerMenu) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDebugInfo: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).safeDrawingPadding()) {
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = Color.White)
            Text(
                text = state.title.resolve(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Everything past the title acts on a stream that does not exist yet while preparing.
            // The back button and the title stay: leaving is the one thing still worth offering,
            // and the title is what says the wait belongs to the thing you asked for.
            if (!state.preparing) {
                // Audio is hidden when there is nothing to choose between; subtitles always show,
                // because "off" is itself a choice a user looks for.
                if (state.audioTracks.size > 1) {
                    PlayerIconButton(
                        icon = AudioTrackIcon,
                        contentDescription = stringResource(Res.string.player_audio),
                        onClick = { onOpenMenu(PlayerMenu.Audio) },
                    )
                }
                val subtitlesOn = state.selectedSubtitleIndex != null
                PlayerIconButton(
                    icon = if (subtitlesOn) SubtitlesIcon else SubtitlesOffIcon,
                    contentDescription = stringResource(
                        if (subtitlesOn) Res.string.player_subtitles_on else Res.string.player_subtitles_off,
                    ),
                    onClick = { onOpenMenu(PlayerMenu.Subtitles) },
                )
                // Empty until the first negotiation lands, since the ladder is filtered by the source.
                if (state.qualityOptions.isNotEmpty()) {
                    PlayerIconButton(
                        icon = QualityIcon,
                        contentDescription = stringResource(Res.string.player_quality),
                        onClick = { onOpenMenu(PlayerMenu.Quality) },
                    )
                }
                PlayerIconButton(
                    icon = DebugIcon,
                    contentDescription = stringResource(
                        if (state.debugVisible) Res.string.player_debug_hide else Res.string.player_debug_show,
                    ),
                    onClick = onToggleDebugInfo,
                    tint = if (state.debugVisible) Color.White else Color.White.copy(alpha = 0.6f),
                )
            }
        }

        // The transport and the scrubber are both hidden while preparing: the root spinner
        // has the middle of the screen to itself, and there is no duration to scrub through.
        if (!state.preparing) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = SeekBackIcon,
                    contentDescription = stringResource(Res.string.player_seek_back),
                    onClick = { onSeekBy(SeekBackMs) },
                    size = SeekButtonSize,
                    iconSize = SeekIconSize,
                )
                // A stall takes the play button's place rather than sitting beside it: tapping it
                // would only pause, and the one thing worth saying here is that the wait is the
                // network's doing and not the user's. Sized to the button so nothing shifts.
                if (state.isBuffering) {
                    Box(Modifier.size(PlayButtonSize), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(PlayIconSize),
                            color = Color.White,
                        )
                    }
                } else {
                    PlayerIconButton(
                        icon = if (state.isPlaying) PauseIcon else PlayIcon,
                        contentDescription = stringResource(
                            if (state.isPlaying) Res.string.player_pause else Res.string.player_play,
                        ),
                        onClick = onPlayPause,
                        size = PlayButtonSize,
                        iconSize = PlayIconSize,
                    )
                }
                PlayerIconButton(
                    icon = SeekForwardIcon,
                    contentDescription = stringResource(Res.string.player_seek_forward),
                    onClick = { onSeekBy(SeekForwardMs) },
                    size = SeekButtonSize,
                    iconSize = SeekIconSize,
                )
            }

            Scrubber(
                state = state,
                positionMs = positionMs,
                onSeek = onSeek,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Scrubber(
    state: PlayerUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging, the thumb follows the finger rather than the engine — otherwise the position
    // poll fights the gesture and the thumb jumps back.
    var dragging by remember { mutableStateOf(false) }
    var draggedMs by remember { mutableFloatStateOf(0f) }

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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(formatTime(position.toLong()))
            Spacer(Modifier.weight(1f))
            TimeLabel(formatTime(state.durationMs))
            // Bottom-right, where every player puts it, and out of the crowded top row.
            PlayerIconButton(
                icon = if (state.isFullscreen) FullscreenExitIcon else FullscreenIcon,
                contentDescription = stringResource(
                    if (state.isFullscreen) Res.string.player_fullscreen_exit else Res.string.player_fullscreen_enter,
                ),
                onClick = onToggleFullscreen,
            )
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White)
}

/**
 * Diagnostics, for a user working out why their server is busy or their picture is soft.
 *
 * Terse and unpunctuated on purpose: it is read next to a server log, not as prose. Position is
 * absent because it changes twice a second and is already on the scrubber — putting it here would
 * recompose the overlay on every tick for something the user can see anyway.
 */
@Composable
private fun DebugOverlay(state: PlayerUiState, modifier: Modifier = Modifier) {
    val unknown = stringResource(Res.string.player_debug_unknown)
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .background(Color.Black.copy(alpha = 0.65f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DebugRow(
            label = stringResource(Res.string.player_debug_method),
            value = state.playMethod?.let { stringResource(it.label) } ?: unknown,
        )
        DebugRow(
            label = stringResource(Res.string.player_debug_resolution_label),
            value = if (state.stream.width != null && state.stream.height != null) {
                stringResource(
                    Res.string.player_debug_resolution,
                    state.stream.width.toString(),
                    state.stream.height.toString(),
                )
            } else {
                unknown
            },
        )
        DebugRow(stringResource(Res.string.player_debug_video_codec), state.stream.videoCodec ?: unknown)
        DebugRow(stringResource(Res.string.player_debug_audio_codec), state.selectedAudio?.codec ?: unknown)
        DebugRow(stringResource(Res.string.player_debug_container), state.stream.container ?: unknown)
        DebugRow(
            label = stringResource(Res.string.player_debug_source_bitrate),
            value = state.stream.bitrate?.let { formatBitrate(it) } ?: unknown,
        )
        DebugRow(
            label = stringResource(Res.string.player_debug_cap),
            value = state.maxStreamingBitrate?.let { formatBitrate(it) }
                ?: stringResource(Res.string.player_quality_auto),
        )
        DebugRow(stringResource(Res.string.player_debug_session), state.playSessionId ?: unknown)
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(min = 84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/*
 * Control sizes. The transport row is deliberately bigger than the row of pickers above it: those
 * are settings, reached deliberately, while play/pause is hit in the dark with a thumb.
 */
private val ControlButtonSize = 48.dp
private val ControlIconSize = 24.dp
private val SeekButtonSize = 56.dp
private val SeekIconSize = 34.dp
private val PlayButtonSize = 72.dp
private val PlayIconSize = 48.dp

/**
 * A control on the player.
 *
 * White rather than themed, and stated rather than inherited: these sit over the picture, not over
 * a surface, so the theme's `onSurface` would be invisible on a bright frame in light mode.
 *
 * [size] and [iconSize] move independently because Material's `IconButton` pins its icon to 24dp
 * — growing only the button would grow the touch target and leave the icon alone.
 */
@Composable
@Suppress("LongParameterList")
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ControlButtonSize,
    iconSize: Dp = ControlIconSize,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, modifier = modifier.size(size)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun PlaybackError(
    message: UiText,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message.resolve(), color = Color.White, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
        TextButton(onClick = onBack) {
            Text(stringResource(Res.string.action_back), color = Color.White)
        }
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

private const val BitsPerMegabit = 1_000_000
private const val BitsPerKilobit = 1_000

/** "20 Mbps", "1.5 Mbps", "720 kbps". */
@Composable
private fun formatBitrate(bitsPerSecond: Int): String = when {
    bitsPerSecond >= BitsPerMegabit ->
        stringResource(Res.string.player_bitrate_mbps, scaled(bitsPerSecond, BitsPerMegabit))

    else -> stringResource(Res.string.player_bitrate_kbps, scaled(bitsPerSecond, BitsPerKilobit))
}

/**
 * [value] divided by [unit] to one decimal place, dropping a trailing `.0`.
 *
 * Hand-rolled: `String.format` is JVM-only, and this file compiles for iOS too.
 */
internal fun scaled(value: Int, unit: Int): String {
    val tenths = (value.toLong() * 10 + unit / 2) / unit
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

private val PlayMethod.label: StringResource
    get() = when (this) {
        PlayMethod.DirectPlay -> Res.string.player_method_direct_play
        PlayMethod.DirectStream -> Res.string.player_method_remuxing
        PlayMethod.Transcode -> Res.string.player_method_transcoding
    }

private val PlayerMenu.title: StringResource
    get() = when (this) {
        PlayerMenu.Audio -> Res.string.player_audio
        PlayerMenu.Subtitles -> Res.string.player_subtitles
        PlayerMenu.Quality -> Res.string.player_quality
    }

/*
 * Previews. There is no picture behind the controls: Android's VideoSurface draws nothing for an
 * engine it does not recognise, so these show the controls over black. That is the right thing to
 * look at — the controls are the shared part, and the picture is whatever is being watched.
 *
 * They render at whatever size the editor picks rather than a pinned landscape canvas, so the
 * fullscreen control differs by the state it is *in*, not by the shape of the preview itself.
 */

@Preview(name = "Player · playing")
@Composable
private fun PlayerPlayingPreview() {
    PreviewSurface {
        PlayerScreenPreview(state = playingState(), positionMs = 1_284_000)
    }
}

/**
 * Paused, transcoding, subtitles on and locked to landscape — so the subtitle and fullscreen
 * controls both show their other icon.
 */
@Preview(name = "Player · paused with subtitles")
@Composable
private fun PlayerPausedPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                isPlaying = false,
                playMethod = PlayMethod.Transcode,
                selectedSubtitleIndex = 4,
                maxStreamingBitrate = 4_000_000,
                orientation = ScreenOrientation.Landscape,
            ),
            positionMs = 42_000,
        )
    }
}

/** The overlay, up while the controls are down — the state it is actually read in. */
@Preview(name = "Player · debug overlay")
@Composable
private fun PlayerDebugOverlayPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                controlsVisible = false,
                debugVisible = true,
                playMethod = PlayMethod.Transcode,
                maxStreamingBitrate = 6_000_000,
            ),
            positionMs = 1_284_000,
        )
    }
}

/**
 * Stalled. The spinner takes the play button's slot and the seek controls stay put — the point of
 * the preview is that this does *not* look like the paused state above it.
 */
@Preview(name = "Player · buffering")
@Composable
private fun PlayerBufferingPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(isBuffering = true),
            positionMs = 1_284_000,
        )
    }
}

/** Controls hidden, which is what a user watching rather than fiddling actually sees. */
@Preview(name = "Player · controls hidden")
@Composable
private fun PlayerControlsHiddenPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(controlsVisible = false),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · subtitle picker")
@Composable
private fun PlayerSubtitleMenuPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                openMenu = PlayerMenu.Subtitles,
                selectedSubtitleIndex = 5,
            ),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · audio picker")
@Composable
private fun PlayerAudioMenuPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(openMenu = PlayerMenu.Audio),
            positionMs = 1_284_000,
        )
    }
}

/** A 1080p source, so the ladder stops there rather than offering 4K the file cannot fill. */
@Preview(name = "Player · quality picker")
@Composable
private fun PlayerQualityMenuPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(openMenu = PlayerMenu.Quality, maxStreamingBitrate = 10_000_000),
            positionMs = 1_284_000,
        )
    }
}

/**
 * Opening. Only the spinner and the way back out — no transport over a picture that does not exist,
 * and no track or quality pickers for a stream nobody has described yet.
 */
@Preview(name = "Player · preparing")
@Composable
private fun PlayerPreparingPreview() {
    PreviewSurface {
        PlayerScreenPreview(state = PlayerUiState(title = movieTitle), positionMs = 0)
    }
}

/**
 * The half of preparing that is easy to forget: the server has answered, so `loading` is false, but
 * the first frame has not arrived. It looks the same, which is the point.
 */
@Preview(name = "Player · buffering the first frame")
@Composable
private fun PlayerFirstFramePreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(preparing = true, isBuffering = true),
            positionMs = 0,
        )
    }
}

@Preview(name = "Player · error")
@Composable
private fun PlayerErrorPreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = PlayerUiState(
                title = movieTitle,
                loading = false,
                preparing = false,
                error = UiText.Raw("This item has no playable media source"),
            ),
            positionMs = 0,
        )
    }
}

/**
 * An episode, whose header is a sentence rather than a name — the one case the title is built
 * instead of carried, so it is worth seeing beside the film.
 */
@Preview(name = "Player · episode title")
@Composable
private fun PlayerEpisodeTitlePreview() {
    PreviewSurface {
        PlayerScreenPreview(
            state = playingState().copy(
                title = PlayerRoute(
                    itemId = "episode-1",
                    title = "The Undertow",
                    startPositionTicks = 0,
                    seriesName = "Northern Line",
                    seasonNumber = 2,
                    episodeNumber = 4,
                ).header(),
            ),
            positionMs = 1_284_000,
        )
    }
}

private val movieTitle = UiText.Raw("The Cartographer")

/** Mid-film, playing, with tracks to choose between. The base every preview above varies from. */
private fun playingState(): PlayerUiState {
    val stream = StreamInfo(
        container = "mkv",
        videoCodec = "h264",
        width = 1920,
        height = 1080,
        bitrate = 8_400_000,
    )
    return PlayerUiState(
        title = movieTitle,
        loading = false,
        preparing = false,
        isPlaying = true,
        durationMs = 7_440_000,
        playMethod = PlayMethod.DirectPlay,
        playSessionId = "8f2c1d6ae4b34f0a",
        stream = stream,
        audioTracks = PreviewData.audioTracks,
        subtitleTracks = PreviewData.subtitleTracks,
        selectedAudioIndex = 1,
        qualityOptions = qualityOptionsFor(stream.width, stream.height),
    )
}

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
        onSelectQuality = {},
        onToggleFullscreen = {},
        onToggleDebugInfo = {},
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
