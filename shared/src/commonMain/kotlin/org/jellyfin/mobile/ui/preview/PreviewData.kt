package org.jellyfin.mobile.ui.preview

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.Credit
import org.jellyfin.mobile.domain.CreditList
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ExternalLink
import org.jellyfin.mobile.domain.Filmography
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.LibraryFilterOptions
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.domain.LibraryRow
import org.jellyfin.mobile.domain.LibraryRowTarget
import org.jellyfin.mobile.domain.LibraryView
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.MenuLink
import org.jellyfin.mobile.domain.ParentLink
import org.jellyfin.mobile.domain.PersonDetail
import org.jellyfin.mobile.domain.Ratings
import org.jellyfin.mobile.domain.Season
import org.jellyfin.mobile.domain.SectionKind

/**
 * Sample domain objects for the previews.
 *
 * Invented titles rather than real ones, so nothing here reads as a claim about a real work. The
 * values are chosen to exercise the cases that actually break layouts — a title long enough to
 * ellipsize, a three-digit badge, a missing image, an item with neither subtitle nor overview —
 * because a preview built from tidy data only ever proves the tidy case.
 *
 * URLs point at the reserved `.invalid` TLD: they are never fetched (see [PreviewSurface]), and
 * this way they cannot be fetched by accident either.
 */
internal object PreviewData {

    /**
     * Titles for the generated lists. Declared first because an `object` initialises its properties
     * in source order, and the lists below read from it.
     */
    private val GridTitles = listOf(
        "The Cartographer",
        "Harbour Lights",
        "Nine Winters",
        "A Quiet Signal",
        "The Long Ferry",
        "Every Salt Road",
    )

    // ---- Cards -------------------------------------------------------------------------------

    val movie = MediaItem(
        id = "movie-1",
        title = "The Cartographer",
        subtitle = "2019",
        imageUrl = art("movie-1"),
        progress = null,
        watched = false,
        kind = ItemKind.Movie,
    )

    /** A title long enough to be cut off, which is the common case on a poster this narrow. */
    val longTitleMovie = MediaItem(
        id = "movie-2",
        title = "Harbour Lights and the Very Long Winter",
        subtitle = "2021",
        imageUrl = art("movie-2"),
        progress = null,
        watched = false,
        kind = ItemKind.Movie,
    )

    val watchedMovie = MediaItem(
        id = "movie-3",
        title = "Nine Winters",
        subtitle = "2016",
        imageUrl = art("movie-3"),
        progress = null,
        watched = true,
        kind = ItemKind.Movie,
    )

    /** Sparsely-scraped libraries are full of these; the card falls back to the title. */
    val artlessMovie = MediaItem(
        id = "movie-4",
        title = "A Quiet Signal",
        subtitle = null,
        imageUrl = null,
        progress = null,
        watched = false,
        kind = ItemKind.Movie,
    )

    /** Container with episodes left, which is what puts a count in the corner. */
    val series = MediaItem(
        id = "series-1",
        title = "Northern Line",
        subtitle = "3 seasons",
        imageUrl = art("series-1"),
        progress = null,
        watched = false,
        unwatchedCount = 7,
        kind = ItemKind.Series,
    )

    /** Three digits, so the badge has to stretch from a circle into a pill. */
    val hugeCollection = MediaItem(
        id = "boxset-1",
        title = "The Complete Archive",
        subtitle = "312 films",
        imageUrl = art("boxset-1"),
        progress = null,
        watched = false,
        unwatchedCount = 312,
        kind = ItemKind.BoxSet,
    )

    val finishedSeries = MediaItem(
        id = "series-2",
        title = "The Glasshouse",
        subtitle = "2 seasons",
        imageUrl = art("series-2"),
        progress = null,
        watched = true,
        unwatchedCount = 0,
        kind = ItemKind.Series,
    )

    /** Half-watched episode: the landscape card with a resume bar across the bottom. */
    val episodeInProgress = MediaItem(
        id = "episode-1",
        title = "The Undertow",
        subtitle = "Northern Line · S2:E4",
        imageUrl = art("episode-1"),
        progress = 0.42f,
        watched = false,
        kind = ItemKind.Episode,
    )

    val episodeJustStarted = MediaItem(
        id = "episode-2",
        title = "Low Tide",
        subtitle = "Signal Hill · S1:E1",
        imageUrl = art("episode-2"),
        progress = 0.04f,
        watched = false,
        kind = ItemKind.Episode,
    )

    val person = MediaItem(
        id = "person-1",
        title = "Elena Marsh",
        subtitle = null,
        imageUrl = art("person-1"),
        progress = null,
        watched = false,
        kind = ItemKind.Person,
    )

    // ---- Rows and grids ----------------------------------------------------------------------

    val homeSections = listOf(
        HomeSection(
            id = "resume",
            title = "Continue Watching",
            items = listOf(episodeInProgress, episodeJustStarted),
            kind = SectionKind.Resume,
        ),
        HomeSection(
            id = "latest-movies",
            title = "Recently Added in Movies",
            items = listOf(movie, longTitleMovie, watchedMovie, artlessMovie),
            kind = SectionKind.LatestInLibrary,
            parentId = "library-movies",
            libraryItemKind = ItemKind.Movie,
            hasMore = true,
        ),
        HomeSection(
            id = "latest-shows",
            title = "Recently Added in Shows",
            items = listOf(series, finishedSeries, hugeCollection),
            kind = SectionKind.LatestInLibrary,
            parentId = "library-shows",
            libraryItemKind = ItemKind.Series,
            hasMore = true,
        ),
    )

    val favoriteSections = listOf(
        HomeSection(
            id = "favorite-movies",
            title = "Movies",
            items = listOf(watchedMovie, movie),
            kind = SectionKind.FavoriteMovies,
        ),
        HomeSection(
            id = "favorite-people",
            title = "People",
            items = listOf(person),
            kind = SectionKind.FavoritePeople,
        ),
    )

    val searchSections = listOf(
        HomeSection(
            id = "search-movies",
            title = "Movies",
            items = listOf(movie, longTitleMovie, artlessMovie),
            kind = SectionKind.SearchMovies,
            searchTerm = "north",
            hasMore = true,
        ),
        HomeSection(
            id = "search-series",
            title = "Shows",
            items = listOf(series),
            kind = SectionKind.SearchSeries,
            searchTerm = "north",
        ),
        HomeSection(
            id = "search-people",
            title = "People",
            items = listOf(person),
            kind = SectionKind.SearchPeople,
            searchTerm = "north",
        ),
    )

    val suggestions = listOf(movie, series, watchedMovie, longTitleMovie, artlessMovie, hugeCollection)

    /** Enough to fill a grid and prove the adaptive column count. */
    val posterGrid = List(12) { index ->
        movie.copy(
            id = "grid-$index",
            title = GridTitles[index % GridTitles.size],
            subtitle = (2010 + index).toString(),
            imageUrl = art("grid-$index"),
        )
    }

    val thumbGrid = List(8) { index ->
        episodeInProgress.copy(
            id = "grid-episode-$index",
            title = GridTitles[index % GridTitles.size],
            subtitle = "Northern Line · S1:E${index + 1}",
            imageUrl = art("grid-episode-$index"),
            progress = if (index == 0) 0.6f else null,
        )
    }

    // ---- Library browsing ----------------------------------------------------------------------

    /**
     * What `/Items/Filters` comes back with. Genres long enough to wrap a chip row, and enough
     * years to prove the group scrolls rather than running off the sheet.
     */
    val filterOptions = LibraryFilterOptions(
        genres = listOf("Action & Adventure", "Comedy", "Documentary", "Drama", "Science Fiction"),
        officialRatings = listOf("G", "PG", "PG-13", "R"),
        years = (2016..2025).toList().reversed(),
    )

    /** A server with more than the four libraries everyone assumes, including one we cannot browse. */
    val libraries = listOf(
        LibraryView("lib-movies", "Movies", LibraryKind.Movies),
        LibraryView("lib-tv", "TV Shows", LibraryKind.TvShows),
        LibraryView("lib-kids", "Kids Movies", LibraryKind.Movies),
        LibraryView("lib-music", "Music", LibraryKind.Other),
        LibraryView("lib-playlists", "Playlists", LibraryKind.Playlists),
        LibraryView("lib-boxsets", "Collections", LibraryKind.Collections),
    )

    /**
     * A genres tab mid-scroll: rows that lead somewhere, and one that does not — a suggestions row
     * has no chevron, which is the difference [LibraryRow.target] draws.
     */
    val libraryRows = listOf(
        LibraryRow(
            id = "row-continue",
            title = "Continue Watching",
            items = thumbGrid.take(4),
            cardShape = CardShape.Thumb,
        ),
        LibraryRow(
            id = "row-drama",
            title = "Drama",
            items = posterGrid.take(6),
            cardShape = CardShape.Poster,
            target = LibraryRowTarget.Genre("Drama"),
        ),
        LibraryRow(
            id = "row-scifi",
            title = "Science Fiction",
            items = posterGrid.drop(6).take(5),
            cardShape = CardShape.Poster,
            target = LibraryRowTarget.Genre("Science Fiction"),
        ),
    )

    // ---- Navigation drawer ---------------------------------------------------------------------

    /**
     * A typical set of custom links: the request service most installs put there, and a second
     * entry named at enough length to test what the row does when it runs out of drawer.
     */
    val menuLinks = listOf(
        MenuLink(name = "Request new content", url = "https://requests.preview.invalid/"),
        MenuLink(name = "Household media server documentation", url = "https://wiki.preview.invalid/"),
    )

    // ---- Detail ------------------------------------------------------------------------------

    val links = listOf(
        ExternalLink(name = "IMDb", url = "https://preview.invalid/imdb"),
        ExternalLink(name = "TheMovieDb", url = "https://preview.invalid/tmdb"),
        ExternalLink(name = "Trakt", url = "https://preview.invalid/trakt"),
    )

    val cast = listOf(
        CastMember("person-1", "Elena Marsh", "Ruth Vance", art("person-1")),
        CastMember("person-2", "Tomas Reyes", "Inspector Bell", art("person-2")),
        // No portrait, so the card falls back to an initial.
        CastMember("person-3", "Priya Raghunathan", "Dr. Amelia Fenwick-Barrow", null),
        CastMember("person-4", "Sam Okoye", null, art("person-4")),
    )

    val movieDetail = ItemDetail(
        id = "movie-1",
        title = "The Cartographer",
        originalTitle = "Le Cartographe",
        tagline = "Every map is an argument about what matters.",
        overview = "A surveyor sent to redraw a disputed border finds the villages on either side " +
            "have been quietly trading across it for a century, and that his new map will end that.",
        year = 2019,
        runtime = "2h 04m",
        ratings = Ratings(community = 7.8f, critic = 84, official = "PG-13"),
        genres = listOf("Drama", "History"),
        studios = listOf("Meridian Pictures"),
        directors = listOf("Anneke Vos"),
        writers = listOf("Anneke Vos", "Peter Lindqvist"),
        cast = cast,
        posterUrl = art("movie-1"),
        backdropUrl = art("movie-1-backdrop"),
        trailerUrl = "https://preview.invalid/trailer",
        links = links,
        isFavorite = true,
        isPlayed = false,
        progress = 0.35f,
        playbackPositionTicks = 26_100_000_000,
        kind = ItemKind.Movie,
        seriesId = null,
        seriesLink = null,
        episodeNumbering = null,
        childCount = null,
    )

    val seriesDetail = movieDetail.copy(
        id = "series-1",
        title = "Northern Line",
        originalTitle = null,
        tagline = null,
        overview = "Two signal engineers keep a failing branch line running through the winter the " +
            "timetable was meant to end it.",
        year = 2017,
        runtime = null,
        ratings = Ratings(community = 8.4f, critic = null, official = "TV-14"),
        genres = listOf("Drama", "Mystery", "Thriller"),
        writers = emptyList(),
        posterUrl = art("series-1"),
        backdropUrl = art("series-1-backdrop"),
        trailerUrl = null,
        isFavorite = false,
        progress = null,
        playbackPositionTicks = 0,
        kind = ItemKind.Series,
        childCount = 3,
    )

    val seasonDetail = seriesDetail.copy(
        id = "season-2",
        title = "Season 2",
        kind = ItemKind.Season,
        seriesId = "series-1",
        seriesLink = ParentLink(id = "series-1", label = "Northern Line"),
        childCount = null,
    )

    val episodeDetail = movieDetail.copy(
        id = "episode-1",
        title = "The Undertow",
        originalTitle = null,
        tagline = null,
        overview = "A cancelled service strands the night crew at Hallow Bridge, and the log book " +
            "for the shift before theirs has been torn out.",
        year = 2018,
        runtime = "58m",
        ratings = Ratings(community = 9.1f, critic = null, official = null),
        genres = emptyList(),
        studios = emptyList(),
        directors = listOf("Ines Delacroix"),
        writers = listOf("Peter Lindqvist"),
        posterUrl = art("episode-1"),
        backdropUrl = art("series-1-backdrop"),
        trailerUrl = null,
        isFavorite = false,
        isPlayed = true,
        progress = 0.82f,
        kind = ItemKind.Episode,
        seriesId = "series-1",
        seriesLink = ParentLink(id = "series-1", label = "Northern Line"),
        episodeNumbering = "S2:E4",
        childCount = null,
    )

    /** Nothing but a title: no artwork, overview, ratings, cast or links. */
    val sparseDetail = movieDetail.copy(
        id = "movie-4",
        title = "A Quiet Signal",
        originalTitle = null,
        tagline = null,
        overview = null,
        year = null,
        runtime = null,
        ratings = Ratings(community = null, critic = null, official = null),
        genres = emptyList(),
        studios = emptyList(),
        directors = emptyList(),
        writers = emptyList(),
        cast = emptyList(),
        posterUrl = null,
        backdropUrl = null,
        trailerUrl = null,
        links = emptyList(),
        isFavorite = false,
        progress = null,
        playbackPositionTicks = 0,
    )

    val seasons = listOf(
        // Season 0 is the specials season, which is why the list is not just "Season 1..n".
        Season(id = "season-0", name = "Specials", indexNumber = 0, imageUrl = art("season-0")),
        Season(id = "season-1", name = "Season 1", indexNumber = 1, imageUrl = art("season-1")),
        Season(id = "season-2", name = "Season 2", indexNumber = 2, imageUrl = art("season-2")),
        Season(id = "season-3", name = "Season 3", indexNumber = 3, imageUrl = art("season-3")),
    )

    val episodes = listOf(
        Episode(
            id = "episode-1",
            title = "The Undertow",
            indexNumber = 1,
            overview = "A cancelled service strands the night crew at Hallow Bridge.",
            runtime = "58m",
            imageUrl = art("episode-1"),
            isPlayed = true,
            progress = null,
        ),
        Episode(
            id = "episode-2",
            title = "Low Tide",
            indexNumber = 2,
            overview = "Ruth walks the line herself and finds the fault is not where the board says.",
            runtime = "54m",
            imageUrl = art("episode-2"),
            isPlayed = false,
            progress = 0.31f,
        ),
        Episode(
            id = "episode-3",
            title = "The Long Ferry",
            indexNumber = 3,
            // Neither still nor synopsis: the row has to hold together on the title alone.
            overview = null,
            runtime = null,
            imageUrl = null,
            isPlayed = false,
            progress = null,
        ),
    )

    // ---- People ------------------------------------------------------------------------------

    val personDetail = PersonDetail(
        id = "person-1",
        name = "Elena Marsh",
        biography = "A stage actor for a decade before her first screen role, and still returns to " +
            "the theatre between films. Born in Whitby; trained in Manchester.",
        imageUrl = art("person-1"),
        birthYear = 1979,
        birthPlace = "Whitby, England",
        isFavorite = true,
        links = links,
    )

    val movieCredits = List(7) { index ->
        Credit(
            id = "credit-movie-$index",
            title = GridTitles[index % GridTitles.size],
            subtitle = (2008 + index).toString(),
            imageUrl = if (index == 2) null else art("credit-movie-$index"),
            isPlayed = index % 3 == 0,
        )
    }

    val showCredits = List(3) { index ->
        Credit(
            id = "credit-show-$index",
            title = listOf("Northern Line", "The Glasshouse", "Signal Hill")[index],
            subtitle = (2015 + index).toString(),
            imageUrl = art("credit-show-$index"),
            isPlayed = index == 0,
        )
    }

    val episodeCredits = List(6) { index ->
        Credit(
            id = "credit-episode-$index",
            title = GridTitles[index % GridTitles.size],
            subtitle = "Northern Line · S${index / 3 + 1}:E${index % 3 + 1}",
            imageUrl = art("credit-episode-$index"),
            isPlayed = index < 2,
        )
    }

    val filmography = Filmography(
        movies = CreditList(credits = movieCredits, hasMore = true),
        shows = CreditList(credits = showCredits),
        episodes = CreditList(credits = episodeCredits, hasMore = true),
    )

    // ---- Account -----------------------------------------------------------------------------

    val userName = "Elena"
    val userImageUrl = art("user-1")

    // ---- Playback ----------------------------------------------------------------------------

    val audioTracks = listOf(
        MediaTrack(1, "English - Dolby Digital - 5.1 - Default", "eng", "ac3", null),
        MediaTrack(2, "English - AAC - Stereo - Commentary", "eng", "aac", null),
        MediaTrack(3, "Français - Dolby Digital - 5.1", "fra", "ac3", null),
    )

    val subtitleTracks = listOf(
        MediaTrack(4, "English - SRT", "eng", "subrip", "https://preview.invalid/subtitle.srt"),
        MediaTrack(5, "English (SDH) - PGS", "eng", "pgssub", null),
        MediaTrack(6, "Nederlands - SRT", "nld", "subrip", "https://preview.invalid/subtitle-nl.srt"),
    )

    private fun art(id: String) = "https://preview.invalid/image/$id"
}
