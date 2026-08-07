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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.ParentLink
import org.jellyfin.mobile.domain.Ratings
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.PosterAspectRatio
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jellyfin.mobile.ui.theme.WideAspectRatio
import kotlin.math.roundToInt

/**
 * Pieces shared by [MovieDetailScreen], [SeriesDetailScreen] and [EpisodeDetailScreen].
 *
 * The screens themselves differ in structure — an episode leads with a still rather than a poster,
 * only a series carries an episode list — but the artwork, ratings, action bar and credits are the
 * same wherever they appear, so they live here rather than being duplicated three times.
 */

private val PosterWidth = 116.dp
private val CastImageSize = 84.dp

/** Rotten Tomatoes' colours, which is the convention the critic score follows. */
private val FreshRed = Color(0xFFFA320A)
private val RottenGreen = Color(0xFF6BA53A)

/**
 * Full-bleed hero image with a back control.
 *
 * [imageUrl] is the item's backdrop on a movie or series, and the still frame on an episode.
 */
@Composable
internal fun Hero(
    imageUrl: String?,
    progress: Float?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(WideAspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        imageUrl?.let { url ->
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
        BackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
            tint = Color.White,
        )
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
    }
}

@Composable
internal fun Poster(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(PosterWidth)
            .aspectRatio(PosterAspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        url?.let {
            AsyncImage(
                model = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The show an episode or season belongs to, as a link up to it. */
@Composable
internal fun SeriesLink(link: ParentLink, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = link.label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable { onClick(link.id) },
    )
}

/** Each screen decides which facts belong on its metadata line; this only renders them. */
@Composable
internal fun MetadataLine(parts: List<String>, modifier: Modifier = Modifier) {
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun Tagline(tagline: String, modifier: Modifier = Modifier) {
    Text(
        text = tagline,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
internal fun Overview(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

/**
 * Jellyfin exposes a generic community score and a generic critic score, so they are labelled by
 * what the API guarantees rather than by a service name it does not.
 */
@Composable
internal fun RatingsRow(ratings: Ratings, modifier: Modifier = Modifier) {
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
internal fun ActionBar(
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
internal fun ChipRow(values: List<String>, modifier: Modifier = Modifier) {
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

/** [label] is singular; it is pluralised when there is more than one name. */
@Composable
internal fun CreditsRow(label: String, names: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
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

@Composable
internal fun CastSection(
    cast: List<CastMember>,
    onMemberClick: (CastMember) -> Unit,
    title: String = "Cast",
) {
    Column {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast, key = { it.id }) { member ->
                Column(
                    modifier = Modifier
                        .width(CastImageSize)
                        .clickable { onMemberClick(member) },
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

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = ScreenPadding),
    )
}

/** Kotlin common has no `String.format`, and ratings must not render as "8.399999618530273". */
internal fun Float.oneDecimal(): String {
    val scaled = (this * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}

/*
 * Previewed separately from the pages they appear on: a page shows one combination of these, while
 * each component has states that page cannot reach — a critic score is fresh or rotten, an action
 * bar says Play or Resume.
 */

@Preview(name = "Hero")
@Composable
private fun HeroPreview() {
    PreviewSurface {
        Hero(
            imageUrl = PreviewData.movieDetail.backdropUrl,
            progress = PreviewData.movieDetail.progress,
            onBack = {},
        )
    }
}

/** With no backdrop the scrim is all there is, so the back control still has to be legible. */
@Preview(name = "Hero · no artwork")
@Composable
private fun HeroWithoutArtworkPreview() {
    PreviewSurface {
        Hero(imageUrl = null, progress = null, onBack = {})
    }
}

@Preview(name = "Poster")
@Composable
private fun PosterPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(url = PreviewData.movieDetail.posterUrl, contentDescription = null)
            Poster(url = null, contentDescription = null)
        }
    }
}

/**
 * Both scores, at both sides of the 60% threshold that decides whether the critic figure reads as
 * fresh or rotten — the one piece of colour logic on the page.
 */
@Preview(name = "Ratings")
@Composable
private fun RatingsRowPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RatingsRow(Ratings(community = 8.4f, critic = 92, official = "PG-13"))
            RatingsRow(Ratings(community = 4.1f, critic = 23, official = "R"))
            // Only a community score, which is the usual shape on a TMDb-only server.
            RatingsRow(Ratings(community = 7.0f, critic = null, official = null))
        }
    }
}

/**
 * Every combination of the action bar's labels: Play against Resume, the trailer button's
 * presence, and the three things the played toggle can say.
 */
@Preview(name = "Action bar")
@Composable
private fun ActionBarPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Started, favourited, has a trailer.
            ActionBarPreviewRow(PreviewData.movieDetail)
            // Untouched, no trailer.
            ActionBarPreviewRow(PreviewData.sparseDetail)
            // A container, where marking watched means marking everything inside watched.
            ActionBarPreviewRow(PreviewData.seriesDetail)
            // Already played, so the toggle reads back the state rather than the action.
            ActionBarPreviewRow(PreviewData.episodeDetail)
        }
    }
}

@Composable
private fun ActionBarPreviewRow(detail: ItemDetail) {
    ActionBar(
        detail = detail,
        onPlay = {},
        onTrailer = {},
        onToggleFavorite = {},
        onTogglePlayed = {},
    )
}

@Preview(name = "Genres and credits")
@Composable
private fun ChipsAndCreditsPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipRow(PreviewData.seriesDetail.genres, Modifier.padding(horizontal = ScreenPadding))
            CreditsRow("Director", PreviewData.movieDetail.directors)
            // Pluralised by the component itself, which is the only reason it takes a singular.
            CreditsRow("Writer", PreviewData.movieDetail.writers)
        }
    }
}

@Preview(name = "Cast")
@Composable
private fun CastSectionPreview() {
    PreviewSurface {
        CastSection(PreviewData.cast, onMemberClick = {})
    }
}
