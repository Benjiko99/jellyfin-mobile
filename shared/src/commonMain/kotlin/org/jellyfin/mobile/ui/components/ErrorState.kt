package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.action_retry
import org.jellyfin.mobile.resources.error_generic
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jetbrains.compose.resources.stringResource

/** A failed load, with the way out of it. Every screen that can fail to load shows this one. */
@Composable
internal fun ErrorState(
    message: UiText,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message.resolve(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

@Preview(name = "Error state")
@Composable
private fun ErrorStatePreview() {
    PreviewSurface {
        ErrorState(message = UiText.Resource(Res.string.error_generic), onRetry = {})
    }
}
