package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.Ratings
import kotlin.math.roundToInt

private val ScreenPadding = 16.dp
private val PosterWidth = 116.dp
private const val PosterAspectRatio = 2f / 3f
private const val BackdropAspectRatio = 16f / 9f
private val CastImageSize = 84.dp

@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onDismissActionError: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is DetailUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.message, textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = onBack) { Text("Back") }
                }

                is DetailUiState.Content -> {
                    LaunchedEffect(state.actionError) {
                        state.actionError?.let {
                            snackbarHostState.showSnackbar(it)
                            onDismissActionError()
                        }
                    }
                    DetailContent(
                        content = state,
                        onBack = onBack,
                        onToggleFavorite = onToggleFavorite,
                        onTogglePlayed = onTogglePlayed,
                        onSelectSeason = onSelectSeason,
                        onEpisodeClick = onEpisodeClick,
                        onSeriesClick = onSeriesClick,
                        // The player is Phase 4. Saying so beats a button that silently does nothing.
                        onPlay = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Playback isn't implemented yet")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    content: DetailUiState.Content,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onSeriesClick: (String) -> Unit,
    onPlay: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val detail = content.detail

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Backdrop(detail, onBack) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Poster(detail)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // On an episode or season the show is the thing users want to get back to,
                    // so it sits above the title as a link rather than buried in the metadata.
                    detail.seriesLink?.let { link ->
                        Text(
                            text = link.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onSeriesClick(link.id) },
                        )
                    }
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    detail.originalTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MetadataLine(detail)
                    detail.tagline?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (detail.ratings.hasAny) {
            item { RatingsRow(detail.ratings, Modifier.padding(horizontal = ScreenPadding)) }
        }

        item {
            ActionBar(
                detail = detail,
                onPlay = onPlay,
                onTrailer = { detail.trailerUrl?.let(uriHandler::openUri) },
                onToggleFavorite = onToggleFavorite,
                onTogglePlayed = onTogglePlayed,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }

        detail.overview?.let { overview ->
            item {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }

        if (detail.genres.isNotEmpty()) {
            item { ChipRow(detail.genres, Modifier.padding(horizontal = ScreenPadding)) }
        }

        // Episodes are the point of a series page, so they sit above the credits.
        if (detail.episodeListSeriesId != null) {
            item {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            if (content.seasons.isNotEmpty()) {
                item {
                    SeasonSelector(
                        seasons = content.seasons,
                        selectedSeasonId = content.selectedSeasonId,
                        onSelectSeason = onSelectSeason,
                    )
                }
            }
            if (content.episodes.isEmpty()) {
                item {
                    EpisodesPlaceholder(
                        loading = content.episodesLoading,
                        error = content.episodesError,
                        isEmpty = true,
                    )
                }
            } else {
                items(content.episodes, key = { it.id }) { episode ->
                    EpisodeRow(episode = episode, onClick = { onEpisodeClick(episode) })
                }
            }
        }

        creditsItem("Director", detail.directors)?.let { item { it() } }
        creditsItem("Writer", detail.writers)?.let { item { it() } }
        creditsItem("Studio", detail.studios)?.let { item { it() } }

        if (detail.cast.isNotEmpty()) {
            item { CastSection(detail.cast) }
        }

        detail.imdbUrl?.let { url ->
            item {
                TextButton(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.padding(horizontal = ScreenPadding - 12.dp),
                ) {
                    Text("View on IMDb")
                }
            }
        }
    }
}

@Composable
private fun Backdrop(detail: ItemDetail, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BackdropAspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        detail.backdropUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrim so the back control stays legible over bright artwork, and the image fades into
        // the page rather than ending on a hard edge.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, MaterialTheme.colorScheme.background),
                ),
            ),
        )
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) {
            Text("‹  Back", color = Color.White)
        }
        detail.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
    }
}

@Composable
private fun Poster(detail: ItemDetail) {
    Box(
        modifier = Modifier
            .width(PosterWidth)
            .aspectRatio(PosterAspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        detail.posterUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MetadataLine(detail: ItemDetail) {
    val parts = buildList {
        detail.episodeNumbering?.let { add(it) }
        detail.year?.let { add(it.toString()) }
        detail.runtime?.let { add(it) }
        detail.ratings.official?.let { add(it) }
        if (detail.isContainer) {
            detail.childCount?.let { add(if (it == 1) "1 season" else "$it seasons") }
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Jellyfin exposes a generic community score and a generic critic score, so they are labelled by
 * what the API guarantees rather than by a service name it does not.
 */
@Composable
private fun RatingsRow(ratings: Ratings, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ratings.community?.let { community ->
            RatingPill(value = "★ ${community.oneDecimal()}", label = "Community")
        }
        ratings.critic?.let { critic ->
            RatingPill(
                value = "$critic%",
                label = if (ratings.criticIsFresh) "Critics · Fresh" else "Critics · Rotten",
                valueColor = if (ratings.criticIsFresh) FreshRed else RottenGreen,
            )
        }
    }
}

private val FreshRed = Color(0xFFFA320A)
private val RottenGreen = Color(0xFF6BA53A)

@Composable
private fun RatingPill(value: String, label: String, valueColor: Color? = null) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionBar(
    detail: ItemDetail,
    onPlay: () -> Unit,
    onTrailer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onPlay) {
            Text(if (detail.progress != null) "▶  Resume" else "▶  Play")
        }
        if (detail.trailerUrl != null) {
            OutlinedButton(onClick = onTrailer) { Text("Trailer") }
        }
        OutlinedButton(onClick = onTogglePlayed) {
            Text(
                when {
                    detail.isPlayed -> "✓  Watched"
                    detail.isContainer -> "Mark all seen"
                    else -> "Mark watched"
                },
            )
        }
        OutlinedButton(onClick = onToggleFavorite) {
            Text(if (detail.isFavorite) "♥  Favorite" else "♡  Favorite")
        }
    }
}

@Composable
private fun ChipRow(values: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Returns null when there is nothing to show, so the caller can skip the list item entirely. */
private fun creditsItem(label: String, names: List<String>): (@Composable () -> Unit)? {
    if (names.isEmpty()) return null
    return {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (names.size > 1) "${label}s" else label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp),
            )
            Text(text = names.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CastSection(cast: List<CastMember>) {
    Column {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast, key = { it.id }) { member ->
                Column(
                    modifier = Modifier.width(CastImageSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(CastImageSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (member.imageUrl != null) {
                            AsyncImage(
                                model = member.imageUrl,
                                contentDescription = member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = member.name.take(1),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    member.role?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Kotlin common has no `String.format`, and ratings must not render as "8.399999618530273". */
internal fun Float.oneDecimal(): String {
    val scaled = (this * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}
