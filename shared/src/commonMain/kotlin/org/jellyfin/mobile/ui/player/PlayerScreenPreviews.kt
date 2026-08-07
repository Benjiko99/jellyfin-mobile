package org.jellyfin.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.player.PlayerEngine
import org.jellyfin.mobile.player.PlayerState
import org.jellyfin.mobile.player.ScreenOrientation
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the player.
 *
 * There is no picture behind the controls: Android's `VideoSurface` draws nothing for an engine it
 * does not recognise, so a preview shows the controls over black. That is the right thing to look
 * at — the controls are the shared part, and the picture is whatever the user happens to be
 * watching.
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
        PlayerPreviewScreen(
            state = playingState(),
            positionMs = 1_284_000,
        )
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
        PlayerPreviewScreen(
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
        PlayerPreviewScreen(
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
        PlayerPreviewScreen(
            state = playingState().copy(controlsVisible = false),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · subtitle picker", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerSubtitleMenuPreview() {
    PreviewSurface {
        PlayerPreviewScreen(
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
        PlayerPreviewScreen(
            state = playingState().copy(openMenu = TrackMenu.Audio),
            positionMs = 1_284_000,
        )
    }
}

@Preview(name = "Player · loading", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerLoadingPreview() {
    PreviewSurface {
        PlayerPreviewScreen(
            state = PlayerUiState(title = "The Cartographer"),
            positionMs = 0,
        )
    }
}

@Preview(name = "Player · error", widthDp = LandscapeWidth, heightDp = LandscapeHeight)
@Composable
private fun PlayerErrorPreview() {
    PreviewSurface {
        PlayerPreviewScreen(
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
private fun PlayerPreviewScreen(state: PlayerUiState, positionMs: Long) {
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
 * `VideoSurface` checks the engine type and returns early on one it does not know, so this leaves
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
