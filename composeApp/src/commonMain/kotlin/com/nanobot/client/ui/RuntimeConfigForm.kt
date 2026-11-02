package com.nanobot.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.resources.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class ConfigKind { SWITCH, INT, TEXT, MULTILINE, CHOICE }

private enum class ConfigGroup { AGENT, TOOLS, EXEC, WEB, CLI, GATEWAY, API, IMAGE }

private data class ConfigField(
    val key: String,
    val group: ConfigGroup,
    val label: StringResource,
    val kind: ConfigKind = ConfigKind.TEXT,
    val min: Int? = null,
    val max: Int? = null,
    val choices: List<String> = emptyList(),
    val readOnly: Boolean = false,
)

private val CONFIG_FIELDS = listOf(
    ConfigField("agents.defaults.timezone_mode", ConfigGroup.AGENT, Res.string.cfg_timezone_mode, ConfigKind.CHOICE, choices = listOf("auto", "manual")),
    ConfigField("agents.defaults.timezone", ConfigGroup.AGENT, Res.string.cfg_timezone),
    ConfigField("agents.defaults.dream.enabled", ConfigGroup.AGENT, Res.string.cfg_dream, ConfigKind.SWITCH),
    ConfigField("agents.defaults.max_tool_iterations", ConfigGroup.AGENT, Res.string.cfg_max_tool_iterations, ConfigKind.INT, min = 1),
    ConfigField("agents.defaults.max_concurrent_subagents", ConfigGroup.AGENT, Res.string.cfg_max_subagents, ConfigKind.INT, min = 1),
    ConfigField("agents.defaults.max_tool_result_chars", ConfigGroup.AGENT, Res.string.cfg_max_tool_result_chars, ConfigKind.INT, min = 1),
    ConfigField("agents.defaults.provider_retry_mode", ConfigGroup.AGENT, Res.string.cfg_retry_mode, ConfigKind.CHOICE, choices = listOf("standard", "persistent")),
    ConfigField("agents.defaults.tool_hint_max_length", ConfigGroup.AGENT, Res.string.cfg_tool_hint_length, ConfigKind.INT, min = 20, max = 500),
    ConfigField("agents.defaults.unified_session", ConfigGroup.AGENT, Res.string.cfg_unified_session, ConfigKind.SWITCH),

    ConfigField("tools.exec.enable", ConfigGroup.TOOLS, Res.string.cfg_exec_enable, ConfigKind.SWITCH),
    ConfigField("tools.file.enable", ConfigGroup.TOOLS, Res.string.cfg_file_enable, ConfigKind.SWITCH),
    ConfigField("tools.web.enable", ConfigGroup.TOOLS, Res.string.cfg_web_enable, ConfigKind.SWITCH),
    ConfigField("tools.cli_apps.enable", ConfigGroup.TOOLS, Res.string.cfg_cli_enable, ConfigKind.SWITCH),
    ConfigField("tools.my.enable", ConfigGroup.TOOLS, Res.string.cfg_my_enable, ConfigKind.SWITCH),
    ConfigField("tools.my.allow_set", ConfigGroup.TOOLS, Res.string.cfg_my_allow_set, ConfigKind.SWITCH),
    ConfigField("tools.restrict_to_workspace", ConfigGroup.TOOLS, Res.string.cfg_restrict_workspace, ConfigKind.SWITCH),
    ConfigField("tools.max_session_messages_per_minute", ConfigGroup.TOOLS, Res.string.cfg_session_messages_per_minute, ConfigKind.INT, min = 1),

    ConfigField("tools.exec.timeout", ConfigGroup.EXEC, Res.string.cfg_exec_timeout, ConfigKind.INT, min = 0),
    ConfigField("tools.exec.path_prepend", ConfigGroup.EXEC, Res.string.cfg_path_prepend),
    ConfigField("tools.exec.path_append", ConfigGroup.EXEC, Res.string.cfg_path_append),
    ConfigField("tools.exec.sandbox", ConfigGroup.EXEC, Res.string.cfg_exec_sandbox),
    ConfigField("tools.exec.sandbox_ro_binds", ConfigGroup.EXEC, Res.string.cfg_sandbox_ro, ConfigKind.MULTILINE),
    ConfigField("tools.exec.sandbox_rw_binds", ConfigGroup.EXEC, Res.string.cfg_sandbox_rw, ConfigKind.MULTILINE),
    ConfigField("tools.exec.allowed_env_keys", ConfigGroup.EXEC, Res.string.cfg_allowed_env, ConfigKind.MULTILINE),
    ConfigField("tools.exec.allow_patterns", ConfigGroup.EXEC, Res.string.cfg_allow_patterns, ConfigKind.MULTILINE),
    ConfigField("tools.exec.deny_patterns", ConfigGroup.EXEC, Res.string.cfg_deny_patterns, ConfigKind.MULTILINE),

    ConfigField("tools.web.proxy", ConfigGroup.WEB, Res.string.cfg_web_proxy),
    ConfigField("tools.web.user_agent", ConfigGroup.WEB, Res.string.cfg_user_agent),
    ConfigField("tools.ssrf_whitelist", ConfigGroup.WEB, Res.string.cfg_ssrf_whitelist, ConfigKind.MULTILINE),
    ConfigField("tools.webui_allow_remote_package_install", ConfigGroup.WEB, Res.string.cfg_remote_install, ConfigKind.SWITCH, readOnly = true),

    ConfigField("tools.cli_apps.install_timeout", ConfigGroup.CLI, Res.string.cfg_cli_install_timeout, ConfigKind.INT, min = 1),
    ConfigField("tools.cli_apps.run_timeout", ConfigGroup.CLI, Res.string.cfg_cli_run_timeout, ConfigKind.INT, min = 1),
    ConfigField("tools.cli_apps.catalog_ttl_seconds", ConfigGroup.CLI, Res.string.cfg_cli_catalog_ttl, ConfigKind.INT, min = 1),

    ConfigField("gateway.host", ConfigGroup.GATEWAY, Res.string.cfg_gateway_host),
    ConfigField("gateway.port", ConfigGroup.GATEWAY, Res.string.cfg_gateway_port, ConfigKind.INT, min = 1, max = 65535),
    ConfigField("gateway.restart_mode", ConfigGroup.GATEWAY, Res.string.cfg_restart_mode, ConfigKind.CHOICE, choices = listOf("auto", "exec", "spawn", "exit")),

    ConfigField("api.host", ConfigGroup.API, Res.string.cfg_api_host),
    ConfigField("api.timeout", ConfigGroup.API, Res.string.cfg_api_timeout, ConfigKind.INT, min = 1, max = 3600),

    ConfigField("tools.image_generation.save_dir", ConfigGroup.IMAGE, Res.string.cfg_image_dir),
)

private fun groupLabel(group: ConfigGroup): StringResource = when (group) {
    ConfigGroup.AGENT -> Res.string.cfg_group_agent
    ConfigGroup.TOOLS -> Res.string.cfg_group_tools
    ConfigGroup.EXEC -> Res.string.cfg_group_exec
    ConfigGroup.WEB -> Res.string.cfg_group_web
    ConfigGroup.CLI -> Res.string.cfg_group_cli
    ConfigGroup.GATEWAY -> Res.string.cfg_group_gateway
    ConfigGroup.API -> Res.string.cfg_group_api
    ConfigGroup.IMAGE -> Res.string.cfg_group_image
}

@Composable
fun RuntimeConfigForm(controller: AppController, state: AppState) {
    val connected = state.connection.phase == ConnectionPhase.CONNECTED
    var draft by remember(state.runtimeConfig) {
        mutableStateOf(state.runtimeConfig ?: JsonObject(emptyMap()))
    }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saved by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (connected && state.runtimeConfig == null) controller.loadGatewayConfig()
    }
    LaunchedEffect(saved) {
        if (saved) {
            delay(1500)
            saved = false
        }
    }

    if (!connected) {
        ConfigNoticeCard(stringResource(Res.string.gateway_not_connected_hint))
        return
    }

    val original = state.runtimeConfig ?: JsonObject(emptyMap())
    val changed = CONFIG_FIELDS.filter { field ->
        draft[field.key] != original[field.key]
    }
    val dirty = changed.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { controller.loadGatewayConfig() },
                enabled = !state.gatewayConfigLoading && !state.gatewayConfigSaving,
            ) {
                if (state.gatewayConfigLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.refresh))
            }
            Spacer(Modifier.weight(1f))
            if (saved) {
                Text(
                    stringResource(Res.string.gateway_config_save_success),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = {
                    if (!dirty) return@TextButton
                    val issues = validateFields(draft, changed)
                    errors = issues
                    if (issues.isEmpty()) {
                        val values = buildJsonObject {
                            changed.forEach { field ->
                                val value = draft[field.key]
                                if (field.kind == ConfigKind.INT) {
                                    val number = (value as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()
                                    put(field.key, number ?: 0)
                                } else {
                                    put(field.key, value ?: JsonPrimitive(""))
                                }
                            }
                        }
                        controller.saveRuntimeConfig(values) { saved = true }
                    }
                },
                enabled = dirty && !state.gatewayConfigSaving,
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.save))
            }
        }

        if (state.runtimeConfigRestartRequired) {
            ConfigNoticeCard(stringResource(Res.string.cfg_restart_hint))
        }
        state.gatewayConfigError?.let { error ->
            ConfigNoticeCard("${stringResource(Res.string.error)}: $error", isError = true)
        }
        if (state.gatewayConfigLoading && state.runtimeConfig == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        ConfigGroup.entries.forEach { group ->
            val fields = CONFIG_FIELDS.filter { it.group == group }
            if (fields.isEmpty()) return@forEach
            ConfigCard(title = stringResource(groupLabel(group)), modifier = Modifier.widthIn(max = 760.dp)) {
                fields.forEachIndexed { index, field ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    ConfigFieldRow(field, draft[field.key], errors[field.key]) { value ->
                        draft = JsonObject(draft + (field.key to value))
                        errors = errors - field.key
                    }
                }
            }
        }

        ConfigCard(title = stringResource(Res.string.cfg_raw_json), modifier = Modifier.widthIn(max = 760.dp)) {
            Text(
                if (showRaw) stringResource(Res.string.cfg_raw_hint) else stringResource(Res.string.cfg_raw_collapsed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showRaw = !showRaw }) {
                Text(stringResource(if (showRaw) Res.string.hide_details else Res.string.show_details))
            }
            if (showRaw) {
                Spacer(Modifier.height(10.dp))
                var rawDraft by remember(state.gatewayConfigRaw, showRaw) {
                    mutableStateOf(state.gatewayConfigRaw.orEmpty())
                }
                OutlinedTextField(
                    value = rawDraft,
                    onValueChange = { rawDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    maxLines = 30,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { controller.saveGatewayConfig(rawDraft) { saved = true; showRaw = false } },
                    enabled = rawDraft.isNotBlank() && rawDraft != state.gatewayConfigRaw && !state.gatewayConfigSaving,
                ) { Text(stringResource(Res.string.save)) }
            }
        }
    }
}

private fun validateFields(draft: JsonObject, fields: List<ConfigField>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    fields.forEach { field ->
        val primitive = draft[field.key] as? JsonPrimitive
        when (field.kind) {
            ConfigKind.INT -> {
                val value = primitive?.contentOrNull?.trim()?.toIntOrNull()
                if (value == null) {
                    result[field.key] = "Введите число"
                } else {
                    if (field.min != null && value < field.min) result[field.key] = "Минимум ${field.min}"
                    if (field.max != null && value > field.max) result[field.key] = "Максимум ${field.max}"
                }
            }
            ConfigKind.TEXT -> if (primitive?.contentOrNull?.isBlank() == true && field.min != null) {
                result[field.key] = "Заполните поле"
            }
            else -> Unit
        }
    }
    return result
}

@Composable
private fun ConfigFieldRow(
    field: ConfigField,
    value: JsonElement?,
    error: String?,
    onChange: (JsonElement) -> Unit,
) {
    when (field.kind) {
        ConfigKind.SWITCH -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(field.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = (value as? JsonPrimitive)?.booleanOrNull ?: false,
                    onCheckedChange = { if (!field.readOnly) onChange(JsonPrimitive(it)) },
                    enabled = !field.readOnly,
                )
            }
            if (field.readOnly) {
                Text(
                    stringResource(Res.string.cfg_remote_install_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ConfigKind.CHOICE -> ChoiceField(field, value, onChange)
        ConfigKind.MULTILINE -> {
            OutlinedTextField(
                value = (value as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.joinToString("\n").orEmpty(),
                onValueChange = { text ->
                    onChange(JsonArray(text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
                },
                label = { Text(stringResource(field.label)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> {
            val primitive = value as? JsonPrimitive
            OutlinedTextField(
                value = primitive?.contentOrNull.orEmpty(),
                onValueChange = { text ->
                    onChange(if (field.kind == ConfigKind.INT) JsonPrimitive(text.filter { it.isDigit() || it == '-' }) else JsonPrimitive(text))
                },
                label = { Text(stringResource(field.label)) },
                singleLine = field.kind == ConfigKind.TEXT || field.kind == ConfigKind.INT,
                enabled = !field.readOnly,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = if (field.kind == ConfigKind.INT) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceField(field: ConfigField, value: JsonElement?, onChange: (JsonElement) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = (value as? JsonPrimitive)?.contentOrNull ?: field.choices.firstOrNull().orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(field.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        onChange(JsonPrimitive(choice))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ConfigNoticeCard(text: String, isError: Boolean = false) {
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
