package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * **F**, which is what every video player on a desktop uses.
 *
 * Taken from the keyboard manager rather than from a focused composable, because the request is
 * about the window rather than about anything on screen: a `Modifier.onKeyEvent` only fires while
 * the node holding it has focus, so it would need the player to take focus and keep it across every
 * control the user touches. A dispatcher hears the key wherever it lands, and only exists while the
 * player is composed — leave the player and F is an ordinary letter again.
 *
 * Plain F alone: anything with a modifier held belongs to whatever else uses it, and consuming
 * Ctrl-F would take a shortcut away from the platform.
 *
 * The event is consumed when it matches, so the keypress does not also reach the control underneath.
 */
@Composable
actual fun FullscreenShortcut(onToggle: () -> Unit) {
    // The player hands us a new lambda on most recompositions; the dispatcher is installed once, so
    // it has to read the current one rather than the one that was current when it was installed.
    val current by rememberUpdatedState(onToggle)

    DisposableEffect(Unit) {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher = KeyEventDispatcher { event ->
            val pressedF = event.id == KeyEvent.KEY_PRESSED &&
                event.keyCode == KeyEvent.VK_F &&
                event.modifiersEx == 0
            if (pressedF) current()
            pressedF
        }

        manager.addKeyEventDispatcher(dispatcher)
        onDispose { manager.removeKeyEventDispatcher(dispatcher) }
    }
}
