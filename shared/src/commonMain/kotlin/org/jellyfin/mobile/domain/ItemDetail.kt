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

data class ItemDetail(
    val id: String,
    val title: String,
    /** Shown only when it differs from [title] — i.e. a non-English original. */
    val originalTitle: String?,
    val tagline: String?,
    val overview: String?,
    val year: Int?,
    val runtime: String?,
    val ratings: Ratings,
    val genres: List<String>,
    val studios: List<String>,
    val directors: List<String>,
    val writers: List<String>,
    val cast: List<CastMember>,
    val posterUrl: String?,
    val backdropUrl: String?,
    val trailerUrl: String?,
    val imdbUrl: String?,
    val isFavorite: Boolean,
    val isPlayed: Boolean,
    val progress: Float?,
    val kind: ItemKind,
    /** Set on episodes and seasons; the series they belong to. */
    val seriesId: String?,
    val childCount: Int?,
) {
    /** For containers "mark watched" means "mark everything inside watched". */
    val isContainer: Boolean get() = kind.isContainer

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
