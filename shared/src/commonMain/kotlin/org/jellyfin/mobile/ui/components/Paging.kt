package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.preview.PreviewSurface

/** How close to the end the user gets before the next page is requested. */
private const val PrefetchDistance = 8

/**
 * The footer under a paged list: a spinner while the next page loads, a retry when one failed, and
 * a little breathing room otherwise.
 */
@Composable
internal fun PageFooter(loadingMore: Boolean, loadFailed: Boolean, onRetry: () -> Unit) {
    when {
        loadingMore -> Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        loadFailed -> Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedButton(onClick = onRetry) { Text("Load more") }
        }

        else -> Box(Modifier.fillMaxWidth().height(8.dp))
    }
}

/**
 * Requests the next page once the user scrolls within [PrefetchDistance] of the end.
 *
 * [lastVisibleIndex] is a lambda rather than a state object so this serves both `LazyListState` and
 * `LazyGridState`, which share no supertype.
 *
 * The `derivedStateOf` is keyed on exactly what the predicate reads. Keying it on a whole UI state
 * object rebuilds it on every emission — several per page load — which defeats the memoisation this
 * exists for, since the point is to absorb the per-frame churn of reading layout info.
 */
@Composable
internal fun LoadMoreWhenNearEnd(
    itemCount: Int,
    endReached: Boolean,
    loadFailed: Boolean,
    onLoadMore: () -> Unit,
    lastVisibleIndex: () -> Int?,
) {
    // Keyed on shouldLoad alone, so a caller passing a fresh lambda each recomposition must not
    // restart the effect — that would fire a second load for the same page.
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    val shouldLoad by remember(itemCount, endReached, loadFailed) {
        derivedStateOf {
            val lastVisible = lastVisibleIndex() ?: return@derivedStateOf false
            !endReached && !loadFailed && lastVisible >= itemCount - PrefetchDistance
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) currentOnLoadMore() }
}

/** All three footer states stacked, since the point of the component is that they swap in place. */
@Preview(name = "Page footer", widthDp = 360)
@Composable
private fun PageFooterPreview() {
    PreviewSurface {
        Column(Modifier.padding(vertical = 12.dp)) {
            PageFooter(loadingMore = true, loadFailed = false, onRetry = {})
            PageFooter(loadingMore = false, loadFailed = true, onRetry = {})
            PageFooter(loadingMore = false, loadFailed = false, onRetry = {})
        }
    }
}
