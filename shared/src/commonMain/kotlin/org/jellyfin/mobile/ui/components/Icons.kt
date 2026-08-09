package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * The app's icons, named for what they mean here rather than for what they depict.
 *
 * All Material Icons. These were hand-drawn `ImageVector` paths until this file was rewritten,
 * because `material-icons-*` is deprecated upstream and is no longer a transitive dependency of
 * Material 3 — see AGENTS.md for what depending on it costs and what to watch for.
 *
 * The indirection earns its place twice over: it keeps a Jellyfin-shaped vocabulary at the call
 * sites — a chevron is [ChevronIcon] because it opens a list, not because of its shape — and it is
 * the one file to change if the dependency has to go again.
 *
 * Several are `AutoMirrored`, the variant that flips in right-to-left locales. Anything meaning
 * "onwards", "away" or "back" needs it; a magnifier and a television look the same everywhere.
 */
internal val SearchIcon: ImageVector = Icons.Default.Search

/** The "everything watched" badge. */
internal val CheckIcon: ImageVector = Icons.Default.Check

/** Opens the rest of a list, from the end of a [SectionHeader] title. */
internal val ChevronIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight

/** Stands in for the profile picture of a user who has not set one. */
internal val PersonIcon: ImageVector = Icons.Default.Person

/** Opens the navigation drawer. */
internal val MenuIcon: ImageVector = Icons.Default.Menu

/** The library screen's sort-and-filter button. */
internal val FilterIcon: ImageVector = Icons.Default.FilterList

/** Empties the search field. */
internal val ClearIcon: ImageVector = Icons.Default.Clear

/**
 * Dismisses the fullscreen artwork viewer. The same cross as [ClearIcon] and a separate name on
 * purpose: one empties a field, the other closes something drawn over the page, and only one of
 * them would follow if the search control ever became a different glyph.
 */
internal val CloseIcon: ImageVector = Icons.Default.Close

/** Goes back. Here rather than inside [BackButton], so the whole set stays in one file. */
internal val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

/** A movie library. */
internal val MovieIcon: ImageVector = Icons.Default.Movie

/** A TV library. */
internal val TvIcon: ImageVector = Icons.Default.Tv

/** A playlist library. `PlaylistPlay` rather than `List`: these are things that play, in order. */
internal val PlaylistIcon: ImageVector = Icons.AutoMirrored.Filled.PlaylistPlay

/**
 * A library of collections — box sets.
 *
 * `VideoLibrary` rather than `Collections`, which is a stack of photographs and belongs to a photo
 * library. A box set groups films.
 */
internal val CollectionIcon: ImageVector = Icons.Default.VideoLibrary

/** A library whose type we have no picture for: music, books, photos. */
internal val FolderIcon: ImageVector = Icons.Default.Folder

/**
 * A link that leaves the app, for the server's custom menu links.
 *
 * The arrow is the point: these go to separate services in a browser, and it says so before the tap
 * rather than after.
 */
internal val OpenInNewIcon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew

/*
 * The player's controls.
 *
 * Not `AutoMirrored`, any of them, including the two seek icons. A transport control means the
 * direction the *tape* runs, which is the same everywhere — Material leaves these unmirrored for
 * that reason, and flipping them would put rewind on the right in Arabic while the scrubber below
 * still filled from the left.
 */

internal val PlayIcon: ImageVector = Icons.Default.PlayArrow

internal val PauseIcon: ImageVector = Icons.Default.Pause

/** Seek back. The icon has the "10" in it, so the amount must stay 10s if this stays this icon. */
internal val SeekBackIcon: ImageVector = Icons.Default.Replay10

/** Seek forward, likewise pinned to 30s by the numeral it draws. */
internal val SeekForwardIcon: ImageVector = Icons.Default.Forward30

/**
 * The next and previous episode of a show.
 *
 * Not `AutoMirrored`, despite meaning "onwards": transport controls follow the direction of the
 * timeline they move along, which runs left to right in every locale, and Material does not mirror
 * them either. The scrubber beside them does not flip, so these must not.
 */
internal val SkipNextIcon: ImageVector = Icons.Default.SkipNext

internal val SkipPreviousIcon: ImageVector = Icons.Default.SkipPrevious

/**
 * Locks the screen to landscape. Fullscreen is what a user calls it; landscape is what it does —
 * the picture already fills the screen either way.
 */
internal val FullscreenIcon: ImageVector = Icons.Default.Fullscreen

/** Hands rotation back to the device. */
internal val FullscreenExitIcon: ImageVector = Icons.Default.FullscreenExit

/** Opens the subtitle picker while a subtitle track is on. */
internal val SubtitlesIcon: ImageVector = Icons.Default.ClosedCaption

/**
 * The same control with subtitles off — the struck-through variant, which is the only sign anywhere
 * that they are off. `ClosedCaptionDisabled`, not `ClosedCaptionOff`: Material's "Off" suffix names
 * an outline *style*, not a state, and draws no strike at all.
 */
internal val SubtitlesOffIcon: ImageVector = Icons.Default.ClosedCaptionDisabled

/** Opens the audio track picker. Badge-shaped, to sit alongside [SubtitlesIcon] and [QualityIcon]. */
internal val AudioTrackIcon: ImageVector = Icons.Default.SurroundSound

/** Opens the streaming quality picker. */
internal val QualityIcon: ImageVector = Icons.Default.HighQuality

/** Toggles the player's debug overlay. */
internal val DebugIcon: ImageVector = Icons.Default.BugReport

/**
 * Heads the overlay a brightness drag puts up. A sun, which is what everyone else uses for this —
 * `LightMode` rather than `BrightnessHigh`, whose half-filled disc reads as a contrast control.
 */
internal val BrightnessIcon: ImageVector = Icons.Default.LightMode

/** The same, for a volume drag. */
internal val VolumeIcon: ImageVector = Icons.Default.VolumeUp

/** Volume dragged all the way down, so silence is legible at a glance rather than an empty bar. */
internal val VolumeMutedIcon: ImageVector = Icons.Default.VolumeOff

/**
 * The set side by side, which is the only way to see that it *is* a set — one icon at a time says
 * nothing about whether it matches its neighbours.
 */
@Preview(name = "Icons")
@Composable
private fun IconsPreview() {
    PreviewSurface {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = MenuIcon, contentDescription = "Menu")
            Icon(imageVector = BackIcon, contentDescription = "Back")
            Icon(imageVector = FilterIcon, contentDescription = "Filter")
            Icon(imageVector = SearchIcon, contentDescription = "Search")
            Icon(imageVector = ClearIcon, contentDescription = "Clear")
            Icon(imageVector = CloseIcon, contentDescription = "Close")
            Icon(imageVector = CheckIcon, contentDescription = "Watched")
            Icon(imageVector = ChevronIcon, contentDescription = "Show all")
            Icon(imageVector = PersonIcon, contentDescription = "Account")
            Icon(imageVector = MovieIcon, contentDescription = "Movies")
            Icon(imageVector = TvIcon, contentDescription = "TV shows")
            Icon(imageVector = PlaylistIcon, contentDescription = "Playlists")
            Icon(imageVector = CollectionIcon, contentDescription = "Collections")
            Icon(imageVector = FolderIcon, contentDescription = "Library")
            Icon(imageVector = OpenInNewIcon, contentDescription = "Opens outside the app")
            Icon(imageVector = PlayIcon, contentDescription = "Play")
            Icon(imageVector = PauseIcon, contentDescription = "Pause")
            Icon(imageVector = SeekBackIcon, contentDescription = "Seek back")
            Icon(imageVector = SeekForwardIcon, contentDescription = "Seek forward")
            Icon(imageVector = SkipPreviousIcon, contentDescription = "Previous episode")
            Icon(imageVector = SkipNextIcon, contentDescription = "Next episode")
            Icon(imageVector = FullscreenIcon, contentDescription = "Fullscreen")
            Icon(imageVector = FullscreenExitIcon, contentDescription = "Exit fullscreen")
            Icon(imageVector = SubtitlesIcon, contentDescription = "Subtitles on")
            Icon(imageVector = SubtitlesOffIcon, contentDescription = "Subtitles off")
            Icon(imageVector = AudioTrackIcon, contentDescription = "Audio track")
            Icon(imageVector = QualityIcon, contentDescription = "Quality")
            Icon(imageVector = DebugIcon, contentDescription = "Debug info")
            Icon(imageVector = BrightnessIcon, contentDescription = "Brightness")
            Icon(imageVector = VolumeIcon, contentDescription = "Volume")
            Icon(imageVector = VolumeMutedIcon, contentDescription = "Muted")
        }
    }
}
