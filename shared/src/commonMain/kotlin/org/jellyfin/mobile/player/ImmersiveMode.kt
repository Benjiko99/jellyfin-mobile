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
 * [safeDrawingIgnoringVisibility] more tightly still — anything laid out over a player that hides its
 * bars has to be padded by that rather than by `safeDrawing`.
 */
@Composable
expect fun ImmersiveMode(enabled: Boolean)

/**
 * The safe area as it would be with the system bars shown, whether or not they are.
 *
 * `WindowInsets.safeDrawing` answers "what is covering the screen right now", which collapses the
 * moment [ImmersiveMode] hides the bars. Padding the controls by it means they are laid out against
 * the bare edge on the frame they appear, then slide inward as the bars animate back — a nav bar in
 * landscape is a 48dp strip, so that is 48dp of travel on every single tap.
 *
 * Reserving the space either way costs a strip of picture the controls sit over regardless, and buys
 * controls that are in the same place every time they are asked for. It is also what jellyfin-android
 * settled on: `PlayerFragment` pads its controls with `getInsetsIgnoringVisibility` for this reason.
 *
 * The IME is left out of the union that `safeDrawing` includes — nothing in the player takes typing.
 */
@Composable
expect fun safeDrawingIgnoringVisibility(): WindowInsets
