package com.ahu.ahutong.next.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.ahu.ahutong.next.ui.theme.NextPalette
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Final (non-streaming) markdown rendering. Uses the JetBrains markdown parser through
 * multiplatform-markdown-renderer, so code blocks, tables, quotes and task lists are covered.
 */
@Composable
fun AppMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = markdown,
        colors = nextMarkdownColors(),
        typography = nextMarkdownTypography(),
        modifier = modifier.fillMaxWidth(),
        loading = {},
        error = { errorModifier ->
            Text(
                text = markdown,
                modifier = errorModifier,
                style = MaterialTheme.typography.bodyLarge,
                color = NextPalette.Ink,
            )
        },
    )
}

/**
 * Streaming buffer rendering. 0.41 does not expose the append-only streaming state,
 * so we re-render the accumulated text through the same renderer while it grows.
 */
@Composable
fun StreamingAppMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    AppMarkdown(markdown = markdown, modifier = modifier)
}

@Composable
private fun nextMarkdownColors() = markdownColor(
    text = NextPalette.Ink,
    codeBackground = NextPalette.SurfaceSoft,
    inlineCodeBackground = NextPalette.AccentSoft,
    dividerColor = NextPalette.Line,
    tableBackground = NextPalette.SurfaceSoft.copy(alpha = 0.55f),
)

@Composable
private fun nextMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.titleLarge,
    h2 = MaterialTheme.typography.titleMedium,
    h3 = MaterialTheme.typography.titleSmall,
    h4 = MaterialTheme.typography.titleSmall,
    h5 = MaterialTheme.typography.titleSmall,
    h6 = MaterialTheme.typography.titleSmall,
    text = MaterialTheme.typography.bodyLarge,
    code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    inlineCode = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    quote = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
    paragraph = MaterialTheme.typography.bodyLarge,
    ordered = MaterialTheme.typography.bodyLarge,
    bullet = MaterialTheme.typography.bodyLarge,
    list = MaterialTheme.typography.bodyLarge,
    table = MaterialTheme.typography.bodySmall,
    textLink = TextLinkStyles(
        style = SpanStyle(
            color = NextPalette.Accent,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    ),
)
