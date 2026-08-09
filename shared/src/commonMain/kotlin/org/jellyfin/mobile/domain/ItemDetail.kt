package org.jellyfin.mobile.domain

data class CastMember(
    val id: String,
    val name: String,
    /** Character played, where the server has it. */
    val role: String?,
    val imageUrl: String?,
)

/**
 * Ratings as Jellyfin actually models them.
 *
 * There is no IMDb or Rotten Tomatoes rating field in the API. [community] is a 0-10 score and
 * [critic] a 0-100 percentage, and which service supplies them is determined by the metadata
 * providers configured on the server — with the OMDb plugin they correspond to the IMDb rating and
 * the Rotten Tomatoes critic score, but on a TMDb-only server [community] is the TMDb user score
 * and [critic] is usually absent. Labelling them by service would therefore be a guess, so the UI
 * labels them by what they are.
 */
data class Ratings(
    val community: Float?,
    val critic: Int?,
    /** Age certification such as "PG-13". Unrelated to the scores above. */
    val official: String?,
) {
    val hasAny: Boolean get() = community != null || critic != null || official != null

    /** Rotten Tomatoes' own threshold for "fresh", which is the convention this score follows. */
    val criticIsFresh: Boolean get() = (critic ?: 0) >= FRESH_THRESHOLD

    private companion object {
        const val FRESH_THRESHOLD = 60
    }
}

/** A navigable link to an item's parent, e.g. the series an episode belongs to. */
data class ParentLink(
    val id: String,
    val label: String,
)

data class ItemDetail(
    val id: String,
    val title: String,
    /** Shown only when it differs from [title] — i.e. a non-English original. */
    val originalTitle: String?,
    val tagline: String?,
    val overview: String?,
    val year: Int?,
    val runtime: UiText?,
    val ratings: Ratings,
    val genres: List<String>,
    val studios: List<String>,
    val directors: List<String>,
    val writers: List<String>,
    val cast: List<CastMember>,
    val posterUrl: String?,
    /**
     * The same artwork as [posterUrl] at the largest size worth fetching, for the fullscreen viewer.
     *
     * Two URLs rather than one because the sizes are wanted for opposite reasons: the poster on the
     * page is 116dp wide and should not cost a megabyte, and the one behind a pinch-zoom should not
     * be a thumbnail. Null exactly when [posterUrl] is — both are built from the same image tag.
     */
    val posterFullUrl: String?,
    val backdropUrl: String?,
    val trailerUrl: String?,
    /** Provider links the server generated. Which sites appear depends on the server's providers. */
    val links: List<ExternalLink>,
    val isFavorite: Boolean,
    val isPlayed: Boolean,
    val progress: Float?,
    /**
     * Where to resume, in Jellyfin ticks. Carried alongside [progress] because resuming needs an
     * exact position and [progress] is a rounded percentage — recovering ticks from it would drop
     * the user seconds away from where they stopped.
     */
    val playbackPositionTicks: Long,
    val kind: ItemKind,
    /** Set on episodes and seasons; the series they belong to. */
    val seriesId: String?,
    /**
     * The series this item belongs to, when it is not the series itself. Drives the link out of an
     * episode or season page and up to the show.
     */
    val seriesLink: ParentLink?,
    /** "S5:E14" for episodes, null otherwise. */
    val episodeNumbering: UiText?,
    val childCount: Int?,
) {
    /** For containers "mark watched" means "mark everything inside watched". */
    val isContainer: Boolean get() = kind.isContainer

    /**
     * What the detail page's tappable cover opens full screen, at full size. Null when there is
     * nothing to open, and the cover is then not tappable.
     *
     * Mirrors what each page actually puts under the tap: an episode leads with its own still and
     * falls back to the series backdrop when the scraper found none, everything else has a poster.
     */
    val coverImageUrl: String? get() = when (kind) {
        ItemKind.Episode -> posterFullUrl ?: backdropUrl
        else -> posterFullUrl
    }

    /**
     * The series whose episodes this page should list: itself for a series, its parent for a
     * season. Null for anything with no episode list.
     */
    val episodeListSeriesId: String? get() = when (kind) {
        ItemKind.Series -> id
        ItemKind.Season -> seriesId
        else -> null
    }
}
