package org.jellyfin.mobile.network

/** Image types we request. Full list in the spec's `ImageType` enum. */
object ImageType {
    const val PRIMARY = "Primary"
    const val BACKDROP = "Backdrop"
    const val THUMB = "Thumb"
    const val LOGO = "Logo"
}

/**
 * Builds an image URL:
 * `{server}/Items/{itemId}/Images/{type}?tag={tag}&maxHeight={h}&quality={q}`
 *
 * Passing the `tag` matters: it is the image's content hash, so the URL changes when the artwork
 * changes and both the server and our image cache can treat the response as immutable.
 */
fun buildImageUrl(
    serverUrl: String,
    itemId: String,
    imageType: String,
    tag: String? = null,
    maxHeight: Int? = null,
    maxWidth: Int? = null,
    quality: Int = 90,
): String = buildString {
    append(serverUrl.trimEnd('/'))
    append("/Items/")
    append(itemId)
    append("/Images/")
    append(imageType)

    val query = buildList {
        if (tag != null) add("tag=$tag")
        if (maxHeight != null) add("maxHeight=$maxHeight")
        if (maxWidth != null) add("maxWidth=$maxWidth")
        add("quality=$quality")
    }
    append('?')
    query.joinTo(this, separator = "&")
}
