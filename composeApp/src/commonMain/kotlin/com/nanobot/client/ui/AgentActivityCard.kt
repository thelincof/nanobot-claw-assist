package com.nanobot.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobot.client.core.model.ToolProgressEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.nanobot.client.core.model.UiFileEdit
import com.nanobot.client.core.model.UiMessage
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.activity
import com.nanobot.client.resources.file_added
import com.nanobot.client.resources.file_deleted
import com.nanobot.client.resources.file_edit
import com.nanobot.client.resources.files
import com.nanobot.client.resources.hide_details
import com.nanobot.client.resources.reasoning
import com.nanobot.client.resources.show_details
import com.nanobot.client.resources.tool_arguments
import com.nanobot.client.resources.tool_done
import com.nanobot.client.resources.tool_empty
import com.nanobot.client.resources.tool_error
import com.nanobot.client.resources.tool_result
import com.nanobot.client.resources.tool_running
import com.nanobot.client.resources.tools
import org.jetbrains.compose.resources.stringResource

@Composable
fun AgentActivityCard(message: UiMessage, modifier: Modifier = Modifier) {
    val tools = message.toolEvents.orEmpty()
    val edits = message.fileEdits.orEmpty()
    val traces = message.traces.orEmpty()
    val hasReasoning = !message.reasoning.isNullOrBlank()
    if (tools.isEmpty() && edits.isEmpty() && traces.isEmpty() && !hasReasoning) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasReasoning) {
            ReasoningSection(
                reasoning = message.reasoning.orEmpty().trim(),
                isStreaming = message.reasoningStreaming || (message.isStreaming && message.content.isBlank()),
            )
        }
        if (tools.isNotEmpty() || (traces.isNotEmpty() && edits.isEmpty())) {
            ToolsSection(
                tools = tools,
                traces = traces,
                isStreaming = message.isStreaming,
            )
        }
        if (edits.isNotEmpty()) {
            FilesSection(edits = edits)
        }
    }
}

@Composable
private fun ReasoningSection(reasoning: String, isStreaming: Boolean) {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        Icons.Rounded.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.reasoning),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = open) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(10.dp)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSection(
    tools: List<ToolProgressEvent>,
    traces: List<String>,
    isStreaming: Boolean,
) {
    var open by remember { mutableStateOf(isStreaming) }
    val failed = tools.any { it.error != null || it.phase.isFailurePhase() }
    val running = isStreaming || tools.any { it.phase.isRunningPhase() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (failed) MaterialTheme.colorScheme.error.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.tools),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        running -> stringResource(Res.string.tool_running)
                        failed -> stringResource(Res.string.tool_error)
                        else -> stringResource(Res.string.tool_done)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        failed -> MaterialTheme.colorScheme.error
                        running -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    if (tools.isNotEmpty()) {
                        tools.forEach { tool -> ToolActivityRow(tool) }
                    } else {
                        traces.forEach { trace -> TraceRow(trace) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesSection(edits: List<UiFileEdit>) {
    var open by remember { mutableStateOf(true) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.files),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    edits.forEach { edit -> FileEditRow(edit) }
                }
            }
        }
    }
}

@Composable
private fun ToolActivityRow(tool: ToolProgressEvent) {
    var showDetails by remember(tool.callId) { mutableStateOf(false) }
    val failed = tool.error != null || tool.phase.isFailurePhase()
    val running = tool.phase.isRunningPhase()
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { showDetails = !showDetails },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 2.dp).size(15.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Build,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp).size(16.dp),
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tool.name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.tools),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    toolSummary(tool),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (showDetails) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (showDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = showDetails) {
            Column(Modifier.padding(start = 24.dp, top = 4.dp)) {
                tool.arguments?.let { args ->
                    Text(stringResource(Res.string.tool_arguments), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    JsonBlock(args.toString())
                }
                tool.result?.let { result ->
                    Text(stringResource(Res.string.tool_result), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    JsonBlock(jsonText(result))
                }
                tool.error?.let { error ->
                    Text(stringResource(Res.string.tool_error), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    JsonBlock(jsonText(error))
                }
                if (tool.arguments == null && tool.result == null && tool.error == null) {
                    Text(stringResource(Res.string.tool_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FileEditRow(edit: UiFileEdit) {
    var showDetails by remember(edit.callId) { mutableStateOf(false) }
    val failed = !edit.error.isNullOrBlank()
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { showDetails = !showDetails },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Build,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    edit.path.ifBlank { edit.tool.ifBlank { stringResource(Res.string.file_edit) } },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (edit.added > 0) append("+${edit.added} ")
                        if (edit.deleted > 0) append("-${edit.deleted} ")
                        if (edit.error != null) append(edit.error)
                        if (isEmpty()) append(edit.status)
                    }.trim().ifBlank { stringResource(Res.string.file_edit) },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (edit.diff?.text != null) {
                Icon(
                    if (showDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = showDetails && edit.diff?.text != null) {
            JsonBlock(edit.diff?.text.orEmpty())
        }
    }
}

@Composable
private fun JsonBlock(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    Surface(
        color = Color(0xFF111318),
        contentColor = Color(0xFFE5E7EE),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            trimmed.take(4000),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp).horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun TraceRow(trace: String) {
    Text(
        trace,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

private fun jsonText(element: JsonElement?): String = when (element) {
    null -> ""
    is JsonPrimitive -> element.contentOrNull ?: element.toString()
    else -> element.toString()
}

/** One-line, human-readable summary instead of the raw `{"command": ...}` JSON. */
private fun toolSummary(tool: ToolProgressEvent): String {
    val args = tool.arguments as? JsonObject

    fun arg(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (args?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    val primary = when (tool.name) {
        "exec" -> arg("command")?.let { "$ $it" }
        "web_search", "x_search" -> arg("query")?.let { "search \"$it\"" }
        "web_fetch" -> arg("url")?.let { "fetch $it" }
        "read_file", "write_file", "edit", "list_dir" ->
            arg("path", "file_path")?.let { "${tool.name} $it" }
        "find_files" -> arg("query", "glob", "path")?.let { "find $it" }
        "grep" -> arg("pattern")?.let { "grep \"$it\"" }
        "cron" -> arg("prompt", "schedule", "action")
        else -> null
    }
    primary?.let { return it.replace('\n', ' ').trim() }
    tool.result?.let { return jsonText(it).replace('\n', ' ').trim() }
    tool.error?.let { return jsonText(it).replace('\n', ' ').trim() }
    return tool.phase.orEmpty()
}

private fun String?.isRunningPhase(): Boolean =
    this?.lowercase() in setOf("start", "started", "running", "pending", "in_progress")

private fun String?.isFailurePhase(): Boolean =
    this?.lowercase() in setOf("error", "failed", "failure", "cancelled", "canceled")
