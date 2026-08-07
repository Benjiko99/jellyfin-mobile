package org.jellyfin.mobile.ui

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.domain.UiText
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Reads a [UiText] in the composition, which is the only place the device's locale is known.
 *
 * Nested [UiText] arguments resolve first, so a pattern can be built from other patterns — see
 * [UiText.Resource].
 */
@Composable
internal fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> when {
        args.isEmpty() -> stringResource(id)
        else -> stringResource(id, *resolveArgs(args))
    }

    is UiText.Plural -> when {
        args.isEmpty() -> pluralStringResource(id, quantity)
        else -> pluralStringResource(id, quantity, *resolveArgs(args))
    }

    // `map` is inline, so the composable call inside it is legal; `joinToString` is not and takes
    // the already-resolved strings.
    is UiText.Joined -> parts.map { it.resolve() }.joinToString(separator)
}

@Composable
private fun resolveArgs(args: List<Any>): Array<Any> =
    args.map { if (it is UiText) it.resolve() else it }.toTypedArray()
