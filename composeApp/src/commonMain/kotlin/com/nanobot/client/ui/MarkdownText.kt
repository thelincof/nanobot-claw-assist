package com.nanobot.client.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/** Preferred chat message font size (sp), configured in settings. */
val LocalChatFontSize = staticCompositionLocalOf { 15f }

fun withChatFont(base: TextStyle, size: Float): TextStyle =
    base.copy(fontSize = size.sp, lineHeight = (size * 1.45f).sp)

@Composable
fun MarkdownText(content: String, modifier: Modifier = Modifier) {
    val size = LocalChatFontSize.current
    val body = withChatFont(MaterialTheme.typography.bodyLarge, size)
    val h1 = withChatFont(MaterialTheme.typography.titleMedium, size * 1.25f).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    val h2 = withChatFont(MaterialTheme.typography.titleSmall, size * 1.15f).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    val h3 = withChatFont(MaterialTheme.typography.bodyLarge, size * 1.05f).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    val h4 = withChatFont(MaterialTheme.typography.bodyMedium, size).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    val h5 = withChatFont(MaterialTheme.typography.bodySmall, size * 0.95f).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    val h6 = withChatFont(MaterialTheme.typography.labelSmall, size * 0.9f).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    Markdown(
        content = content,
        modifier = modifier.fillMaxWidth(),
        typography = markdownTypography(
            h1 = h1,
            h2 = h2,
            h3 = h3,
            h4 = h4,
            h5 = h5,
            h6 = h6,
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            inlineCode = body.copy(fontFamily = FontFamily.Monospace),
            code = body.copy(fontFamily = FontFamily.Monospace),
        ),
    )
}
