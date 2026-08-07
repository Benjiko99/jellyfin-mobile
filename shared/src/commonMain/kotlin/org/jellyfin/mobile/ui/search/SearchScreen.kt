package org.jellyfin.mobile.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.error_generic
import org.jellyfin.mobile.resources.search_clear
import org.jellyfin.mobile.resources.search_field_hint
import org.jellyfin.mobile.resources.search_no_results
import org.jellyfin.mobile.resources.search_prompt
import org.jellyfin.mobile.resources.search_suggestions
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.components.ClearIcon
import org.jellyfin.mobile.ui.components.ErrorState
import org.jellyfin.mobile.ui.components.MediaCard
import org.jellyfin.mobile.ui.components.PosterWidth
import org.jellyfin.mobile.ui.home.SectionRows
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jetbrains.compose.resources.stringResource

/**
 * Search.
 *
 * Results are rendered as the home screen's rows — one per category — because that is what the
 * result of searching across several kinds of thing is: several short lists, each of which may have
 * more behind it. A single ranked list would have to choose between burying the one person who
 * matched under forty films and showing four of everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onShowAll: (HomeSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { SearchField(state.query, onQueryChange) },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = ClearIcon,
                                contentDescription = stringResource(Res.string.search_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val content = state.content) {
                SearchContent.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is SearchContent.Error -> ErrorState(
                    message = content.message,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchContent.Suggestions -> if (content.items.isEmpty()) {
                    // /Items/Suggestions is built from viewing history, so it is legitimately empty
                    // for someone who has not watched anything yet.
                    Hint(stringResource(Res.string.search_prompt))
                } else {
                    SuggestionGrid(content.items, onItemClick)
                }

                is SearchContent.Results -> if (content.sections.isEmpty()) {
                    Hint(stringResource(Res.string.search_no_results, content.term))
                } else {
                    // Keyed by term so a new query builds fresh rows rather than reusing the
                    // previous ones, which would leave each row scrolled where the last search
                    // left it.
                    key(content.term) {
                        SectionRows(content.sections, onItemClick, onShowAll)
                    }
                }
            }
        }
    }
}

/**
 * The query field, in the app bar where the title would otherwise be.
 *
 * [BasicTextField] rather than Material's `TextField`: this needs to sit on the app bar rather than
 * carry a container and label of its own.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The screen exists to be typed into, so it opens ready for that.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box {
        if (query.isEmpty()) {
            Text(
                text = stringResource(Res.string.search_field_hint),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Results already arrive as the user types; the action only dismisses the keyboard so
            // they can see them.
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

@Composable
private fun SuggestionGrid(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = PosterWidth),
        contentPadding = PaddingValues(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(Res.string.search_suggestions),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(items, key = { it.id }) { item ->
            MediaCard(
                item = item,
                shape = CardShape.Poster,
                onClick = { onItemClick(item) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
    )
}

/** The resting state: nothing typed, recommendations from the user's viewing history. */
@Preview(name = "Search · suggestions")
@Composable
private fun SearchSuggestionsPreview() {
    PreviewSurface {
        SearchScreenPreview(SearchUiState(content = SearchContent.Suggestions(PreviewData.suggestions)))
    }
}

/** A fresh account has no viewing history, so `/Items/Suggestions` legitimately returns nothing. */
@Preview(name = "Search · no suggestions")
@Composable
private fun SearchNoSuggestionsPreview() {
    PreviewSurface {
        SearchScreenPreview(SearchUiState(content = SearchContent.Suggestions(emptyList())))
    }
}

@Preview(name = "Search · results")
@Composable
private fun SearchResultsPreview() {
    PreviewSurface {
        SearchScreenPreview(
            SearchUiState(
                query = "north",
                content = SearchContent.Results("north", PreviewData.searchSections),
            ),
        )
    }
}

@Preview(name = "Search · no results")
@Composable
private fun SearchNoResultsPreview() {
    PreviewSurface {
        SearchScreenPreview(
            SearchUiState(
                query = "qwertyuiop",
                content = SearchContent.Results("qwertyuiop", emptyList()),
            ),
        )
    }
}

@Preview(name = "Search · error")
@Composable
private fun SearchErrorPreview() {
    PreviewSurface {
        SearchScreenPreview(
            SearchUiState(
                query = "north",
                content = SearchContent.Error(Res.string.error_generic.asUiText()),
            ),
        )
    }
}

@Composable
private fun SearchScreenPreview(state: SearchUiState) {
    SearchScreen(
        state = state,
        onQueryChange = {},
        onBack = {},
        onRetry = {},
        onItemClick = {},
        onShowAll = {},
    )
}
