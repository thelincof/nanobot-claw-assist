package com.nanobot.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.core.model.McpFieldItem
import com.nanobot.client.core.model.McpPresetItem
import com.nanobot.client.resources.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

@Composable
fun McpTab(controller: AppController, state: AppState) {
    val connected = state.connection.phase == ConnectionPhase.CONNECTED
    var enableTarget by remember { mutableStateOf<McpPresetItem?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var toolsTarget by remember { mutableStateOf<McpPresetItem?>(null) }

    LaunchedEffect(connected) {
        if (connected && state.mcpPresets.isEmpty()) controller.refreshMcpPresets()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!connected) {
            NoticeCard(stringResource(Res.string.gateway_not_connected_hint))
            return@Column
        }

        Row(Modifier.fillMaxWidth().widthIn(max = 760.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = controller::refreshMcpPresets, enabled = !state.mcpLoading) {
                if (state.mcpLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.refresh))
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { showImport = true }) {
                Text(stringResource(Res.string.mcp_import))
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = { showCustom = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(Res.string.mcp_add_custom))
            }
        }

        state.mcpError?.let { NoticeCard("${stringResource(Res.string.error)}: $it", isError = true) }
        if (state.mcpRestartRequired) {
            NoticeCard(stringResource(Res.string.mcp_restart_hint))
        }

        val installed = state.mcpPresets.filter { it.installed }
        val available = state.mcpPresets.filter { !it.installed }

        if (installed.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.mcp_installed, installed.size))
            installed.forEach { preset ->
                McpRow(
                    preset = preset,
                    busy = state.mcpBusyName == preset.name,
                    controller = controller,
                    onEnable = { enableTarget = preset },
                    onTools = { toolsTarget = preset },
                )
            }
        }
        if (available.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.mcp_available))
            available.forEach { preset ->
                McpRow(
                    preset = preset,
                    busy = state.mcpBusyName == preset.name,
                    controller = controller,
                    onEnable = { enableTarget = preset },
                    onTools = { toolsTarget = preset },
                )
            }
        }
        if (installed.isEmpty() && available.isEmpty() && !state.mcpLoading) {
            NoticeCard(stringResource(Res.string.mcp_empty))
        }
        Spacer(Modifier.height(8.dp))
    }

    enableTarget?.let { preset ->
        EnableMcpDialog(
            preset = preset,
            onDismiss = { enableTarget = null },
            onConfirm = { fields ->
                controller.enableMcpPreset(preset.name, fields)
                enableTarget = null
            },
        )
    }
    if (showCustom) {
        CustomMcpDialog(
            onDismiss = { showCustom = false },
            onConfirm = { values ->
                controller.saveCustomMcp(values)
                showCustom = false
            },
        )
    }
    if (showImport) {
        ImportMcpDialog(
            onDismiss = { showImport = false },
            onConfirm = { json ->
                controller.importMcp(json)
                showImport = false
            },
        )
    }
    toolsTarget?.let { preset ->
        ToolsDialog(
            preset = preset,
            onDismiss = { toolsTarget = null },
            onSave = { tools ->
                controller.updateMcpTools(preset.name, tools)
                toolsTarget = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(top = 4.dp),
    )
}

@Composable
private fun McpRow(
    preset: McpPresetItem,
    busy: Boolean,
    controller: AppController,
    onEnable: () -> Unit,
    onTools: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val isPlugin = preset.source == "plugin" || preset.name.startsWith("plugin-")
    val enabled = if (isPlugin) preset.enabled == true else preset.installed
    val oauth = preset.auth == "oauth"
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        preset.displayName.ifBlank { preset.name },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        listOfNotNull(
                            preset.category.takeIf { it.isNotBlank() },
                            preset.transport?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(preset)
                if (busy) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (isPlugin) {
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { controller.setMcpEnabled(preset.name, it) },
                        enabled = !busy,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (preset.installed && !isPlugin) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.mcp_test)) },
                                onClick = { menu = false; controller.testMcpPreset(preset.name) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.mcp_reconnect)) },
                                onClick = { menu = false; controller.reconnectMcpPreset(preset.name) },
                            )
                            if (preset.requiredFields.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.mcp_configure)) },
                                    onClick = { menu = false; onEnable() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.mcp_tools)) },
                                onClick = { menu = false; onTools() },
                            )
                        }
                        if (!isPlugin) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.mcp_remove)) },
                                onClick = { menu = false; controller.removeMcpPreset(preset.name) },
                            )
                        }
                    }
                }
            }
            if (preset.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            preset.connectionSummary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (oauth && !preset.installed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.mcp_oauth_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!preset.installed && !oauth) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onEnable) {
                    Text(stringResource(Res.string.mcp_connect))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(preset: McpPresetItem) {
    val isPlugin = preset.source == "plugin" || preset.name.startsWith("plugin-")
    val (label, color) = when {
        isPlugin && preset.enabled == false -> stringResource(Res.string.mcp_status_disabled) to MaterialTheme.colorScheme.onSurfaceVariant
        isPlugin && preset.enabled == true -> stringResource(Res.string.mcp_status_enabled) to MaterialTheme.colorScheme.primary
        preset.installed -> stringResource(Res.string.mcp_status_installed) to MaterialTheme.colorScheme.primary
        else -> stringResource(Res.string.mcp_status_available) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EnableMcpDialog(
    preset: McpPresetItem,
    onDismiss: () -> Unit,
    onConfirm: (JsonObject) -> Unit,
) {
    val values = remember(preset.name) {
        mutableStateMapOf<String, String>().apply {
            preset.requiredFields.forEach { put(it.name, "") }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.displayName.ifBlank { preset.name }) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (preset.description.isNotBlank()) {
                    Text(preset.description, style = MaterialTheme.typography.bodySmall)
                }
                preset.requiredFields.forEach { field ->
                    McpFieldInput(field, values[field.name].orEmpty()) { values[field.name] = it }
                }
                if (preset.requiredFields.isEmpty()) {
                    Text(
                        stringResource(Res.string.mcp_no_required_fields),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val payload = buildJsonObject {
                    preset.requiredFields.forEach { field ->
                        val value = values[field.name].orEmpty()
                        if (value.isNotBlank()) put(field.name, value)
                    }
                }
                onConfirm(payload)
            }) { Text(stringResource(Res.string.mcp_connect)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun McpFieldInput(field: McpFieldItem, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(field.label.ifBlank { field.name }) },
        placeholder = field.placeholder?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (field.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        supportingText = {
            val suffix = buildString {
                if (field.required) append(stringResource(Res.string.mcp_required))
                if (!field.envVar.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(field.envVar)
                }
                if (field.configured) {
                    if (isNotEmpty()) append(" · ")
                    append(stringResource(Res.string.mcp_already_configured))
                }
            }
            if (suffix.isNotBlank()) Text(suffix)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CustomMcpDialog(
    onDismiss: () -> Unit,
    onConfirm: (JsonObject) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("stdio") }
    var command by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var env by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("60") }
    var tools by remember { mutableStateOf("*") }
    val remote = transport != "stdio"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.mcp_custom_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.mcp_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("stdio", "sse", "streamableHttp").forEach { option ->
                        androidx.compose.material3.FilterChip(
                            selected = transport == option,
                            onClick = { transport = option },
                            label = { Text(option) },
                        )
                    }
                }
                if (!remote) {
                    OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text(stringResource(Res.string.mcp_command)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = args, onValueChange = { args = it }, label = { Text(stringResource(Res.string.mcp_args)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cwd, onValueChange = { cwd = it }, label = { Text(stringResource(Res.string.mcp_cwd)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(Res.string.mcp_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = headers, onValueChange = { headers = it }, label = { Text(stringResource(Res.string.mcp_headers)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = env, onValueChange = { env = it }, label = { Text(stringResource(Res.string.mcp_env)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { timeout = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(Res.string.mcp_tool_timeout)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tools,
                        onValueChange = { tools = it },
                        label = { Text(stringResource(Res.string.mcp_enabled_tools)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val payload = buildJsonObject {
                        put("name", name.trim())
                        put("transport", transport)
                        put("command", if (remote) "" else command.trim())
                        put("args", splitLines(args))
                        put("cwd", if (remote) "" else cwd.trim())
                        put("url", if (remote) url.trim() else "")
                        put("headers", parsePairs(headers))
                        put("env", parsePairs(env))
                        put("tool_timeout", timeout.toIntOrNull() ?: 60)
                        put(
                            "enabled_tools",
                            JsonArray(
                                tools.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) },
                            ),
                        )
                    }
                    onConfirm(payload)
                },
                enabled = name.isNotBlank() && (remote && url.isNotBlank() || !remote && command.isNotBlank()),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ToolsDialog(
    preset: McpPresetItem,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var text by remember(preset.name) {
        mutableStateOf(preset.enabledTools.joinToString(", ").ifBlank { "*" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.mcp_tools)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.mcp_tools_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tools = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(tools.ifEmpty { listOf("*") })
                },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ImportMcpDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var json by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.mcp_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.mcp_import_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it },
                    minLines = 8,
                    maxLines = 16,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(json) }, enabled = json.isNotBlank()) {
                Text(stringResource(Res.string.mcp_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

private fun splitLines(text: String): JsonArray =
    JsonArray(text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })

private fun parsePairs(text: String): JsonObject {
    val entries = text.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val index = line.indexOfFirst { it == '=' || it == ':' }
            if (index <= 0) return@mapNotNull null
            val key = line.substring(0, index).trim()
            val value = line.substring(index + 1).trim()
            if (key.isEmpty()) null else key to JsonPrimitive(value)
        }
    return JsonObject(entries.toMap())
}

@Composable
private fun NoticeCard(text: String, isError: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
