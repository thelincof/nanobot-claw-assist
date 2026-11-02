package com.nanobot.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanobot.client.core.model.UiMessage
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.assistant
import com.nanobot.client.resources.copy
import com.nanobot.client.resources.send_failed
import com.nanobot.client.resources.thinking
import com.nanobot.client.resources.tts_speak
import com.nanobot.client.resources.you
import org.jetbrains.compose.resources.stringResource

@Composable
fun MessageItem(
    message: UiMessage,
    mediaBaseUrl: String = "",
    modifier: Modifier = Modifier,
    onSpeak: (String) -> Unit = {},
) {
    when (message.role) {
        "user" -> UserMessageItem(message, mediaBaseUrl, modifier)
        "system" -> SystemMessageItem(message, modifier)
        // Nanobot stores tool calls as `role="tool", kind="trace"` rows. They are
        // merged by MessageProjector into one activity card and must never be
        // rendered as a conversational bubble.
        "tool", "activity" -> ActivityMessageItem(message, modifier)
        else -> AssistantMessageItem(message, mediaBaseUrl, modifier, onSpeak)
    }
}

@Composable
private fun chatBodyStyle(base: TextStyle): TextStyle {
    val size = LocalChatFontSize.current
    return base.copy(fontSize = size.sp, lineHeight = (size * 1.45f).sp)
}

@Composable
private fun UserMessageItem(message: UiMessage, mediaBaseUrl: String, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    val ui = LocalWebUiColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Surface(
                color = if (message.failed) MaterialTheme.colorScheme.errorContainer else ui.sidebarSelected,
                contentColor = if (message.failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 620.dp),
            ) {
                Column {
                    val hasMedia = message.hasMediaContent()
                    if (message.content.isNotBlank()) {
                        Text(
                            message.content,
                            style = chatBodyStyle(MaterialTheme.typography.bodyMedium),
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = 10.dp,
                                bottom = if (hasMedia) 6.dp else 10.dp,
                            ),
                        )
                    }
                    MessageMedia(message, mediaBaseUrl)
                    if (message.failed) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.send_failed), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            ) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.content)) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(Res.string.copy),
                        modifier = Modifier.size(14.dp),
                        tint = ui.sidebarMuted,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(Res.string.you),
                    style = MaterialTheme.typography.labelSmall,
                    color = ui.sidebarMuted,
                )
            }
        }
    }
}

@Composable
private fun ActivityMessageItem(message: UiMessage, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 44.dp)) {
        AgentActivityCard(message)
    }
}

@Composable
private fun AssistantMessageItem(
    message: UiMessage,
    mediaBaseUrl: String,
    modifier: Modifier,
    onSpeak: (String) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val ui = LocalWebUiColors.current
    val hasActivity = !message.reasoning.isNullOrBlank() ||
        !message.toolEvents.isNullOrEmpty() ||
        !message.fileEdits.isNullOrEmpty() ||
        !message.traces.isNullOrEmpty()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Аватар: скруглённый квадрат с рамкой, как в webui
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, ui.border),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🐱", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (message.isStreaming && message.content.isBlank()) {
                Spacer(Modifier.height(6.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.height(6.dp))
            }
            if (hasActivity) {
                AgentActivityCard(message)
                if (message.content.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                }
            } else if (message.isStreaming && message.content.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        stringResource(Res.string.thinking),
                        style = MaterialTheme.typography.bodySmall,
                        color = ui.sidebarMuted,
                    )
                }
            }
            if (message.content.isNotBlank()) {
                if (message.isStreaming) {
                    // Incomplete markdown is parsed differently on every delta and has
                    // historically crashed the markdown renderer mid-stream.
                    Text(
                        message.content,
                        style = chatBodyStyle(MaterialTheme.typography.bodyMedium),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MarkdownText(message.content, Modifier.fillMaxWidth())
                }
            }
            MessageMedia(
                message = message,
                baseUrl = mediaBaseUrl,
                modifier = Modifier.padding(top = if (message.content.isNotBlank()) 6.dp else 0.dp),
            )
            message.latencyMs?.let { latency ->
                Spacer(Modifier.size(4.dp))
                Text(
                    formatLatency(latency),
                    style = MaterialTheme.typography.labelSmall,
                    color = ui.sidebarMuted.copy(alpha = 0.8f),
                )
            }
        }
        if (message.content.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.content)) },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(Res.string.copy),
                        modifier = Modifier.size(16.dp),
                        tint = ui.sidebarMuted,
                    )
                }
                if (!message.isStreaming) {
                    IconButton(
                        onClick = { onSpeak(message.content) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Rounded.VolumeUp,
                            contentDescription = stringResource(Res.string.tts_speak),
                            modifier = Modifier.size(17.dp),
                            tint = ui.sidebarMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemMessageItem(message: UiMessage, modifier: Modifier) {
    Text(
        message.content,
        style = chatBodyStyle(MaterialTheme.typography.bodySmall),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

private fun formatLatency(ms: Long): String {
    if (ms < 1000) return "${ms} ms"
    val whole = ms / 1000
    val tenth = (ms % 1000) / 100
    return if (whole < 10) "$whole.$tenth s" else "$whole s"
}
