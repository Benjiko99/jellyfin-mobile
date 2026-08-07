package org.jellyfin.mobile.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.domain.CreditList
import org.jellyfin.mobile.domain.Filmography
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the person screen and its filmography lists.
 *
 * The two shapes a credit takes are both here: films and shows as browsable artwork, episodes as a
 * flat list that only makes sense with the show name attached. They are the same data drawn two
 * ways, which is easy to get subtly out of step.
 */

private const val PreviewWidth = 390
private const val PreviewHeight = 844
private const val TallPreviewHeight = 1200

@Preview(name = "Person · content", widthDp = PreviewWidth, heightDp = TallPreviewHeight)
@Composable
private fun PersonContentPreview() {
    PreviewSurface {
        PersonPreviewScreen(
            PersonUiState.Content(
                person = PreviewData.personDetail,
                filmography = PreviewData.filmography,
                filmographyLoading = false,
            ),
        )
    }
}

/** The person arrives before their filmography does, so the page renders half-built first. */
@Preview(name = "Person · filmography loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun PersonFilmographyLoadingPreview() {
    PreviewSurface {
        PersonPreviewScreen(PersonUiState.Content(person = PreviewData.personDetail))
    }
}

/** A credited person whose work is not in this library — common on a small server. */
@Preview(name = "Person · nothing in library", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun PersonEmptyFilmographyPreview() {
    PreviewSurface {
        PersonPreviewScreen(
            PersonUiState.Content(
                person = PreviewData.personDetail.copy(
                    biography = null,
                    imageUrl = null,
                    birthPlace = null,
                    isFavorite = false,
                    links = emptyList(),
                ),
                filmography = Filmography(),
                filmographyLoading = false,
            ),
        )
    }
}

/** Only episode credits, which is what a guest actor's page looks like. */
@Preview(name = "Person · episodes only", widthDp = PreviewWidth, heightDp = TallPreviewHeight)
@Composable
private fun PersonEpisodeCreditsPreview() {
    PreviewSurface {
        PersonPreviewScreen(
            PersonUiState.Content(
                person = PreviewData.personDetail,
                filmography = Filmography(
                    episodes = CreditList(PreviewData.episodeCredits, hasMore = true),
                ),
                filmographyLoading = false,
            ),
        )
    }
}

@Preview(name = "Person · loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun PersonLoadingPreview() {
    PreviewSurface {
        PersonPreviewScreen(PersonUiState.Loading)
    }
}

@Preview(name = "Person · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun PersonErrorPreview() {
    PreviewSurface {
        PersonPreviewScreen(PersonUiState.Error("Could not load this person"))
    }
}

@Composable
private fun PersonPreviewScreen(state: PersonUiState) {
    PersonScreen(
        state = state,
        onBack = {},
        onRetry = {},
        onToggleFavorite = {},
        onDismissActionError = {},
        onCreditClick = {},
        onShowAll = {},
    )
}

// ---- The full credit lists behind "More" -----------------------------------------------------

@Preview(name = "Credits · grid", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun CreditGridPreview() {
    PreviewSurface {
        PersonCreditsScreen(
            personName = "Elena Marsh",
            kind = CreditKind.Movies,
            state = PersonCreditsUiState(
                credits = PreviewData.movieCredits,
                totalCount = 31,
                loadingFirstPage = false,
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onCreditClick = {},
        )
    }
}

@Preview(name = "Credits · episode list", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun CreditEpisodeListPreview() {
    PreviewSurface {
        PersonCreditsScreen(
            personName = "Elena Marsh",
            kind = CreditKind.Episodes,
            state = PersonCreditsUiState(
                credits = PreviewData.episodeCredits,
                totalCount = 62,
                loadingFirstPage = false,
                loadingMore = true,
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onCreditClick = {},
        )
    }
}

@Preview(name = "Credits · loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun CreditsLoadingPreview() {
    PreviewSurface {
        PersonCreditsScreen(
            personName = "Elena Marsh",
            kind = CreditKind.Shows,
            state = PersonCreditsUiState(),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onCreditClick = {},
        )
    }
}

@Preview(name = "Credits · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun CreditsErrorPreview() {
    PreviewSurface {
        PersonCreditsScreen(
            personName = "Elena Marsh",
            kind = CreditKind.Movies,
            state = PersonCreditsUiState(
                loadingFirstPage = false,
                error = "Could not reach the server",
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onCreditClick = {},
        )
    }
}

// ---- Components ------------------------------------------------------------------------------

@Preview(name = "Section header", widthDp = PreviewWidth)
@Composable
private fun SectionHeaderPreview() {
    PreviewSurface {
        Column {
            SectionHeader(title = "Movies", onMore = {})
            SectionHeader(title = "Shows")
        }
    }
}

@Preview(name = "Credit card", widthDp = PreviewWidth)
@Composable
private fun CreditCardPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewData.movieCredits.take(3).forEach { credit ->
                CreditCard(credit, onClick = {})
            }
        }
    }
}

@Preview(name = "Episode credit rows", widthDp = PreviewWidth)
@Composable
private fun EpisodeCreditRowPreview() {
    PreviewSurface {
        Column(Modifier.padding(vertical = 8.dp)) {
            PreviewData.episodeCredits.take(4).forEach { credit ->
                EpisodeCreditRow(credit, onClick = {})
            }
        }
    }
}
