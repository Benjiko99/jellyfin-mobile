package org.jellyfin.mobile.network.dto

import kotlinx.serialization.Serializable

/**
 * `config.json`, the web client's own configuration file.
 *
 * Not an API schema — it is a static file shipped with jellyfin-web and edited in place by the
 * administrator, so it appears nowhere in the OpenAPI spec and nothing versions it alongside the
 * API. Kept in its own file for that reason, away from the generated-shape DTOs in `Dtos.kt`.
 *
 * Its keys are camelCase, unlike every API response, which is why it is decoded with
 * `WebConfigJson` rather than the naming-strategy `Json` the rest of the client uses.
 *
 * Only [menuLinks] is modelled. The file also carries themes, plugins and server lists, all of
 * which describe how the *web* client should behave and mean nothing to us.
 */
@Serializable
data class WebConfig(
    val menuLinks: List<MenuLinkDto> = emptyList(),
)

/**
 * One custom sidebar entry.
 *
 * Every field is nullable: this is hand-edited JSON, so a half-filled entry is a realistic thing to
 * find in it rather than a server bug.
 *
 * `icon` is deliberately not modelled. It names a Material Icons glyph, and the web client renders
 * it by CSS class from a font we do not ship.
 */
@Serializable
data class MenuLinkDto(
    val name: String? = null,
    val url: String? = null,
)
