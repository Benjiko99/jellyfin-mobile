package org.jellyfin.mobile.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.domain.Credit
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.domain.CreditList
import org.jellyfin.mobile.domain.PersonDetail

private val PortraitWidth = 120.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    state: PersonUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismissActionError: () -> Unit,
    onCreditClick: (Credit) -> Unit,
    onShowAll: (CreditKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? PersonUiState.Content)?.person?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹  Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PersonUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is PersonUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.message, textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text("Retry") }
                }

                is PersonUiState.Content -> {
                    LaunchedEffect(state.actionError) {
                        state.actionError?.let {
                            snackbarHostState.showSnackbar(it)
                            onDismissActionError()
                        }
                    }
                    PersonContent(state, onToggleFavorite, onCreditClick, onShowAll)
                }
            }
        }
    }
}

@Composable
private fun PersonContent(
    content: PersonUiState.Content,
    onToggleFavorite: () -> Unit,
    onCreditClick: (Credit) -> Unit,
    onShowAll: (CreditKind) -> Unit,
) {
    val filmography = content.filmography

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PersonHeader(content.person, onToggleFavorite) }

        content.person.biography?.let { biography ->
            item {
                Text(
                    text = biography,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }

        if (content.filmographyLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        creditCarousel(CreditKind.Movies, filmography.movies, onCreditClick, onShowAll)
        creditCarousel(CreditKind.Shows, filmography.shows, onCreditClick, onShowAll)

        // Episode credits are a long flat list rather than artwork to browse, so they read better
        // stacked with the show name than as another poster carousel.
        val episodes = filmography.episodes
        if (episodes.credits.isNotEmpty()) {
            item {
                SectionHeader(
                    title = CreditKind.Episodes.title,
                    onMore = if (episodes.hasMore) {
                        { onShowAll(CreditKind.Episodes) }
                    } else {
                        null
                    },
                )
            }
            items(episodes.credits, key = { it.id }) { credit ->
                EpisodeCreditRow(credit, onClick = { onCreditClick(credit) })
            }
        }

        if (!content.filmographyLoading && filmography.isEmpty) {
            item {
                Text(
                    text = "Nothing in your library features this person.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }
    }
}

/** Adds a titled carousel, or nothing at all when the person has no credits of that kind. */
private fun LazyListScope.creditCarousel(
    kind: CreditKind,
    list: CreditList,
    onCreditClick: (Credit) -> Unit,
    onShowAll: (CreditKind) -> Unit,
) {
    if (list.credits.isEmpty()) return
    item {
        Column {
            SectionHeader(
                title = kind.title,
                onMore = if (list.hasMore) {
                    { onShowAll(kind) }
                } else {
                    null
                },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list.credits, key = { it.id }) { credit ->
                    CreditCard(credit, onClick = { onCreditClick(credit) })
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(person: PersonDetail, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(PortraitWidth)
                .aspectRatio(PosterAspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (person.imageUrl != null) {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = person.name.take(1),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(person.name, style = MaterialTheme.typography.headlineSmall)

            val facts = listOfNotNull(
                person.birthYear?.let { "Born $it" },
                person.birthPlace,
            )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onToggleFavorite) {
                Text(if (person.isFavorite) "♥  Favorite" else "♡  Favorite")
            }
        }
    }
}
