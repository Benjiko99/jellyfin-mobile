package org.jellyfin.mobile.domain

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * A piece of text that has not been turned into a `String` yet.
 *
 * Repositories, mappers and view models all produce text the user reads — a row heading, an error,
 * a runtime — and none of them are composable, so none of them can call `stringResource`. Deferring
 * the lookup is what lets them stay non-composable while the wording still comes from
 * `strings.xml`: they say *which* string, and [org.jellyfin.mobile.ui.resolve] reads it, in the
 * composition, in the locale the device is set to.
 *
 * It also keeps server text and our text in one type. A row heading is "Recently Added in Films"
 * on one screen and "Drama" on the next; the first is ours to translate and the second is the
 * administrator's to name, and only [Raw] versus [Resource] says which.
 *
 * ### Why the domain layer knows about `StringResource`
 *
 * AGENTS.md keeps Compose out of the domain, and that still holds: `StringResource` is a resource
 * *identifier* from `components-resources`, not a rendering type. It carries an id and the set of
 * files the string lives in, is readable outside a composition (`getString`), and pulls in no
 * Compose UI. An `ImageVector` is the other thing — a drawing — which is why library icons are
 * still resolved up in `NavigationDrawer` and not down here.
 */
sealed interface UiText {

    /**
     * Text that arrives already written: a genre, a library name, a media stream's own label, or
     * the message on a thrown exception. Nothing to translate — it is the server's wording, not
     * ours.
     */
    data class Raw(val value: String) : UiText

    /**
     * One string from `strings.xml`.
     *
     * [args] fill its `%1$s` placeholders in order. They may be [UiText] themselves, which is how
     * an air date composes a month name into a day-month-year pattern without either half having
     * to know the other's wording.
     */
    data class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText

    /** A string whose wording depends on [quantity], such as a season or item count. */
    data class Plural(
        val id: PluralStringResource,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * Several pieces on one line — "S2:E4 · The Undertow".
     *
     * The separator is punctuation rather than wording, so it stays here instead of becoming a
     * two-placeholder resource per combination. Empty parts are the caller's to leave out.
     */
    data class Joined(val parts: List<UiText>, val separator: String = MIDDLE_DOT) : UiText

    companion object {
        /** The separator between facts on one line, used throughout the detail and card layouts. */
        const val MIDDLE_DOT: String = " · "
    }
}

/** Shorthand for the overwhelmingly common case: one resource, no arguments. */
fun StringResource.asUiText(): UiText = UiText.Resource(this)

/**
 * An error that already knows how it should read on screen.
 *
 * Our own exceptions carry a technical message for the log — which rendition was missing, which
 * protocol the server offered — and that is the wrong thing to put in front of somebody who tapped
 * Play. Implementing this says the sentence to show instead, and leaves the message to diagnostics.
 */
interface LocalizedError {
    val uiText: UiText
}

/**
 * A failed request as text: what [LocalizedError] says, else the message the error carried, else
 * [fallback].
 *
 * Ktor and the platform HTTP stacks write their own messages, in English and in their own terms.
 * Those are worth showing — they are frequently the only clue to what went wrong — but they are not
 * ours to translate, so they arrive as [UiText.Raw] and only our own wording is localised.
 */
fun Throwable.asUiText(fallback: StringResource): UiText = when {
    this is LocalizedError -> uiText
    else -> message?.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: UiText.Resource(fallback)
}
