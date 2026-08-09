package org.jellyfin.mobile.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/**
 * Takes the system bars away while [enabled], leaving the picture the only thing on screen.
 *
 * The player already draws under the status and navigation bars, so the clock, the battery and the
 * gesture handle sit on top of the film for its whole length. Hiding them is the difference between
 * a video filling the screen and a video behind the phone's furniture.
 *
 * Reversible, and reversed on the way out: the bars belong to the rest of the app, so a player that
 * forgot to put them back would leave the library without a clock.
 *
 * A platform hook because only Android can do it from here. See the iOS actual for why that one does
 * nothing yet.
 *
 * Pairs with `SystemBarAppearance`, which stays relevant even while this is on: the bars can still be
 * swiped back temporarily, and they have to be legible over black when they are. It pairs with
 * [barInsetsIgnoringVisibility] more tightly still — anything laid out over a player that hides its
 * bars has to be padded by that rather than by `safeDrawing`.
 */
@Composable
expect fun ImmersiveMode(enabled: Boolean)

/**
 * The room the system bars take, whether or not they are currently showing — and **only** the bars.
 *
 * Two departures from `WindowInsets.safeDrawing`, for two different reasons.
 *
 * *Ignoring visibility*, because `safeDrawing` answers "what is covering the screen right now", which
 * collapses the moment [ImmersiveMode] hides the bars. Padding the controls by it laid them out
 * against the bare edge on the frame they appeared and then slid them inward as the bars animated
 * back — a navigation bar in landscape is a 48dp strip, so that was 48dp of travel on every tap.
 * Reserving the space either way costs a strip of picture the controls sit over regardless, and buys
 * controls that are in the same place every time they are asked for. jellyfin-android's
 * `PlayerFragment` pads with `getInsetsIgnoringVisibility` for this reason.
 *
 * *Without the display cutout*, because the picture goes behind the camera and the controls over it
 * should too. Keeping clear of the cutout pushes everything in from one edge in landscape, which does
 * not read as a margin — it reads as a player whose play button is off-centre, because it is: the
 * transport row centres inside the padded box rather than on the screen. A hole-punch sits mid-edge
 * in landscape, where the row's own gap between buttons is, so crossing it costs nothing visible and
 * buys back the symmetry.
 *
 * The IME is left out too, the third thing `safeDrawing` unions in. Nothing in the player takes typing.
 */
@Composable
expect fun barInsetsIgnoringVisibility(): WindowInsets
