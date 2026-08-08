package org.jellyfin.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jellyfin.mobile.player.PlaybackHardware
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_brightness
import org.jellyfin.mobile.resources.player_volume
import org.jellyfin.mobile.ui.components.BrightnessIcon
import org.jellyfin.mobile.ui.components.VolumeIcon
import org.jellyfin.mobile.ui.components.VolumeMutedIcon
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** What a vertical drag is adjusting, which is decided by which half of the screen it started on. */
internal enum class PlayerAdjustment {
    Brightness,
    Volume,
}

/** How long the overlay stays up after the last change. Long enough to read, short enough to not linger. */
private const val OverlayTimeoutMs = 700L

/** Width of the overlay's bar. Fixed rather than proportional, so it reads the same in either orientation. */
private val OverlayBarWidth = 140.dp

private val OverlayIconSize = 32.dp

/**
 * The layer that takes touches on the player: a tap anywhere, and a vertical drag on either half.
 *
 * This *replaces* the plain tap target rather than sitting over it. Compose delivers a pointer to
 * the topmost node that handles input, so a separate gesture layer above the tap layer would take
 * the taps too and the controls would stop toggling. Both live here for that reason.
 *
 * The split is the convention every mobile player uses — brightness left, volume right — and it is
 * not labelled anywhere, so it has to match what a thumb already expects.
 *
 * @param onBrightnessSettled the value a brightness drag finished on, for whoever wants to remember it.
 */
@Composable
internal fun PlayerGestureLayer(
    hardware: PlaybackHardware,
    gesturesEnabled: Boolean,
    onTap: () -> Unit,
    onBrightnessSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adjustment by remember { mutableStateOf<PlayerAdjustment?>(null) }
    var shown by remember { mutableFloatStateOf(0f) }

    // Keyed on the value as well as the kind, so each change restarts the countdown and the overlay
    // stays up for as long as the finger keeps moving.
    LaunchedEffect(adjustment, shown) {
        if (adjustment != null) {
            delay(OverlayTimeoutMs)
            adjustment = null
        }
    }

    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            GestureHalf(
                kind = PlayerAdjustment.Brightness,
                enabled = gesturesEnabled,
                read = hardware::brightness,
                write = hardware::setBrightness,
                onTap = onTap,
                onChange = { kind, value ->
                    adjustment = kind
                    shown = value
                },
                onSettled = onBrightnessSettled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            GestureHalf(
                kind = PlayerAdjustment.Volume,
                // iOS has no public way to set the system volume, so the right half is a plain tap
                // target there rather than a drag that moves a bar and changes nothing.
                enabled = gesturesEnabled && hardware.canSetVolume,
                read = hardware::volume,
                write = hardware::setVolume,
                onTap = onTap,
                onChange = { kind, value ->
                    adjustment = kind
                    shown = value
                },
                onSettled = {},
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        adjustment?.let { kind ->
            AdjustmentOverlay(kind, shown, Modifier.align(Alignment.Center))
        }
    }
}

/**
 * One half of the screen.
 *
 * [read] is called when a drag starts rather than held in state: the volume keys and the system
 * brightness control are still there while the player is open, so a cached value would be stale.
 */
@Composable
@Suppress("LongParameterList")
private fun GestureHalf(
    kind: PlayerAdjustment,
    enabled: Boolean,
    read: () -> Float,
    write: (Float) -> Unit,
    onTap: () -> Unit,
    onChange: (PlayerAdjustment, Float) -> Unit,
    onSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The drag runs inside a suspend block that outlives a recomposition, so it must not close over
    // a stale lambda — the same reason the controls' auto-hide tracks its callback.
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnSettled by rememberUpdatedState(onSettled)

    Box(
        modifier
            // No ripple: this is the picture, not a button.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            // Keyed on `enabled` so switching the setting off takes effect without leaving the
            // player, and on `kind` because the two halves must not share a detector.
            .pointerInput(enabled, kind) {
                if (!enabled) return@pointerInput
                // A drag from bottom to top covers the full range whatever the screen size or
                // orientation, which is why this is scaled by height rather than by a fixed number
                // of pixels per step.
                val height = size.height.toFloat()
                if (height <= 0f) return@pointerInput

                var value = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        value = read()
                        currentOnChange(kind, value)
                    },
                    onDragEnd = { currentOnSettled(value) },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Up increases, hence the subtraction: the y axis grows downwards.
                        value = (value - dragAmount / height).coerceIn(0f, 1f)
                        write(value)
                        // Read back rather than trusting the request: Android quantises volume to
                        // the device's own steps, so the bar shows what was actually set.
                        currentOnChange(kind, read())
                    },
                )
            },
    )
}

/**
 * The value, while it is being changed.
 *
 * Centred over the picture rather than beside the finger: it has to be readable with a thumb on
 * either edge, and following the touch would put it under the hand half the time.
 */
@Composable
private fun AdjustmentOverlay(
    kind: PlayerAdjustment,
    value: Float,
    modifier: Modifier = Modifier,
) {
    val percent = (value * 100).roundToInt()
    val label = stringResource(
        if (kind == PlayerAdjustment.Brightness) Res.string.player_brightness else Res.string.player_volume,
        percent.toString(),
    )
    val icon = when {
        kind == PlayerAdjustment.Brightness -> BrightnessIcon
        value <= 0f -> VolumeMutedIcon
        else -> VolumeIcon
    }

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            // One announcement for the pair: the icon and the bar are two halves of one reading,
            // and a screen reader listing them separately would say neither usefully.
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(OverlayIconSize),
        )
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.width(OverlayBarWidth),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            // The default inserts a gap before the track and draws a stop dot at the end, which on
            // a bar this short reads as a value rather than as decoration.
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

@Preview(name = "Player · brightness overlay")
@Composable
private fun BrightnessOverlayPreview() {
    PreviewSurface {
        Box(Modifier.size(320.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            AdjustmentOverlay(PlayerAdjustment.Brightness, value = 0.7f)
        }
    }
}

@Preview(name = "Player · volume overlay")
@Composable
private fun VolumeOverlayPreview() {
    PreviewSurface {
        Box(Modifier.size(320.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            AdjustmentOverlay(PlayerAdjustment.Volume, value = 0.35f)
        }
    }
}

/** Silence gets its own icon, which is the only state the bar alone cannot tell you at a glance. */
@Preview(name = "Player · muted overlay")
@Composable
private fun MutedOverlayPreview() {
    PreviewSurface {
        Box(Modifier.size(320.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            AdjustmentOverlay(PlayerAdjustment.Volume, value = 0f)
        }
    }
}
