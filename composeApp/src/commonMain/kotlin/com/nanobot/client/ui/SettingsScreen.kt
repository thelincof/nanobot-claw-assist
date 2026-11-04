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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.hotword.hotwordSupported
import com.nanobot.client.platform.AssistantPlatformSection
import com.nanobot.client.platform.rememberMicPermission
import com.nanobot.client.settings.AppSettings
import com.nanobot.client.tts.availableTtsEngines
import com.nanobot.client.tts.systemDefaultTtsEngine
import com.nanobot.client.resources.*
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.api_key_stored
import com.nanobot.client.resources.app_version
import com.nanobot.client.resources.back
import com.nanobot.client.resources.bootstrap_secret
import com.nanobot.client.resources.chat_font_size
import com.nanobot.client.resources.connect
import com.nanobot.client.resources.connected
import com.nanobot.client.resources.connecting
import com.nanobot.client.resources.connection_failed
import com.nanobot.client.resources.disconnect
import com.nanobot.client.resources.error
import com.nanobot.client.resources.gateway_url
import com.nanobot.client.resources.milliseconds
import com.nanobot.client.resources.not_connected
import com.nanobot.client.resources.reconnecting
import com.nanobot.client.resources.save
import com.nanobot.client.resources.saved
import com.nanobot.client.resources.seconds
import com.nanobot.client.resources.settings_chat
import com.nanobot.client.resources.settings_connection
import com.nanobot.client.resources.settings_stt
import com.nanobot.client.resources.settings_title
import com.nanobot.client.resources.settings_tts
import com.nanobot.client.resources.speed_value
import com.nanobot.client.resources.stt_hint
import com.nanobot.client.resources.stt_max
import com.nanobot.client.resources.stt_mode
import com.nanobot.client.resources.stt_mode_gateway
import com.nanobot.client.resources.stt_mode_local
import com.nanobot.client.resources.stt_silence
import com.nanobot.client.resources.active_model
import com.nanobot.client.resources.cancel
import com.nanobot.client.resources.available_models_title
import com.nanobot.client.resources.current_default_model
import com.nanobot.client.resources.gateway_config_json_hint
import com.nanobot.client.resources.gateway_config_json_invalid
import com.nanobot.client.resources.gateway_config_json_title
import com.nanobot.client.resources.gateway_config_save_success
import com.nanobot.client.resources.gateway_not_connected_hint
import com.nanobot.client.resources.no_models_available
import com.nanobot.client.resources.model_status_unconfigured
import com.nanobot.client.resources.model_status_available
import com.nanobot.client.resources.provider_api_base
import com.nanobot.client.resources.provider_api_key
import com.nanobot.client.resources.provider_edit
import com.nanobot.client.resources.provider_section_title
import com.nanobot.client.resources.refresh
import com.nanobot.client.resources.set_as_default
import com.nanobot.client.resources.tab_app_settings
import com.nanobot.client.resources.tab_gateway_config
import com.nanobot.client.resources.tab_mcp
import com.nanobot.client.resources.tab_models_providers
import com.nanobot.client.resources.theme
import com.nanobot.client.resources.theme_dark
import com.nanobot.client.resources.theme_light
import com.nanobot.client.resources.theme_system
import com.nanobot.client.resources.tts_api_key
import com.nanobot.client.resources.tts_api_key_hint
import com.nanobot.client.resources.tts_api_url
import com.nanobot.client.resources.tts_api_url_hint
import com.nanobot.client.resources.tts_enabled
import com.nanobot.client.resources.tts_enabled_hint
import com.nanobot.client.resources.tts_error
import com.nanobot.client.resources.tts_mode
import com.nanobot.client.resources.tts_mode_http
import com.nanobot.client.resources.tts_mode_local
import com.nanobot.client.resources.tts_model
import com.nanobot.client.resources.tts_not_configured
import com.nanobot.client.resources.tts_speed
import com.nanobot.client.resources.tts_test
import com.nanobot.client.resources.tts_test_text
import com.nanobot.client.resources.tts_voice
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Tab indices
private const val TAB_APP = 0
private const val TAB_GATEWAY_CONFIG = 1
private const val TAB_MODELS = 2
private const val TAB_MCP = 3

@Composable
fun SettingsScreen(
    controller: AppController,
    state: AppState,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(TAB_APP) }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                    Text(
                        stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Tab row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                ) {
                    Tab(
                        selected = selectedTab == TAB_APP,
                        onClick = { selectedTab = TAB_APP },
                        text = { Text(stringResource(Res.string.tab_app_settings)) },
                    )
                    Tab(
                        selected = selectedTab == TAB_GATEWAY_CONFIG,
                        onClick = {
                            selectedTab = TAB_GATEWAY_CONFIG
                            if (state.connection.phase == ConnectionPhase.CONNECTED && state.gatewayConfigRaw == null) {
                                controller.loadGatewayConfig()
                            }
                        },
                        text = { Text(stringResource(Res.string.tab_gateway_config)) },
                    )
                    Tab(
                        selected = selectedTab == TAB_MODELS,
                        onClick = {
                            selectedTab = TAB_MODELS
                            if (state.connection.phase == ConnectionPhase.CONNECTED) {
                                controller.refreshGatewaySettings()
                            }
                        },
                        text = { Text(stringResource(Res.string.tab_models_providers)) },
                    )
                    Tab(
                        selected = selectedTab == TAB_MCP,
                        onClick = {
                            selectedTab = TAB_MCP
                            if (state.connection.phase == ConnectionPhase.CONNECTED) {
                                controller.refreshMcpPresets()
                            }
                        },
                        text = { Text(stringResource(Res.string.tab_mcp)) },
                    )
                }
            }
        }

        // Tab content
        when (selectedTab) {
            TAB_APP -> AppSettingsTab(controller = controller, state = state)
            TAB_GATEWAY_CONFIG -> GatewayConfigTab(controller = controller, state = state)
            TAB_MODELS -> ModelsTab(controller = controller, state = state)
            TAB_MCP -> McpTab(controller = controller, state = state)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// TAB 1: App Settings (все старые настройки)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun AppSettingsTab(controller: AppController, state: AppState) {
    var draft by remember(state.settings) { mutableStateOf(state.settings) }
    var secretVisible by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    val micPermission = rememberMicPermission()
    val connected = state.connection.phase == ConnectionPhase.CONNECTED
    val testText = stringResource(Res.string.tts_test_text)

    LaunchedEffect(showSaved) {
        if (showSaved) {
            delay(1500)
            showSaved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Save button row
        Row(
            Modifier.fillMaxWidth().widthIn(max = 760.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSaved) {
                Text(
                    stringResource(Res.string.saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Button(
                onClick = { controller.saveSettings(draft); showSaved = true },
                enabled = draft != state.settings,
            ) { Text(stringResource(Res.string.save)) }
        }

        SettingsCard(title = stringResource(Res.string.settings_connection), modifier = Modifier.widthIn(max = 760.dp)) {
            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { draft = draft.copy(baseUrl = it) },
                label = { Text(stringResource(Res.string.gateway_url)) },
                placeholder = { Text("http://192.168.0.250:8765") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.secret,
                onValueChange = { draft = draft.copy(secret = it) },
                label = { Text(stringResource(Res.string.bootstrap_secret)) },
                singleLine = true,
                visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { secretVisible = !secretVisible }) {
                        Icon(if (secretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (state.connection.phase) {
                        ConnectionPhase.CONNECTED -> stringResource(Res.string.connected)
                        ConnectionPhase.CONNECTING -> stringResource(Res.string.connecting)
                        ConnectionPhase.RECONNECTING -> stringResource(Res.string.reconnecting)
                        ConnectionPhase.FAILED -> stringResource(Res.string.connection_failed)
                        ConnectionPhase.IDLE -> stringResource(Res.string.not_connected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (connected) {
                    OutlinedButton(onClick = controller::disconnect) { Text(stringResource(Res.string.disconnect)) }
                } else {
                    Button(
                        onClick = { controller.saveSettings(draft); controller.connect(draft.baseUrl, draft.secret) },
                        enabled = draft.baseUrl.isNotBlank() && draft.secret.isNotBlank(),
                    ) { Text(stringResource(Res.string.connect)) }
                }
            }
        }

        SettingsCard(title = stringResource(Res.string.assistant_section), modifier = Modifier.widthIn(max = 760.dp)) {
            AssistantPlatformSection()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.assistant_auto_listen), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = draft.assistantAutoListen,
                    onCheckedChange = { draft = draft.copy(assistantAutoListen = it) },
                )
            }
            if (hotwordSupported()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.hotword_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(Res.string.hotword_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.hotwordEnabled,
                        onCheckedChange = { enabled ->
                            draft = draft.copy(hotwordEnabled = enabled)
                            if (enabled && !micPermission.granted) micPermission.request()
                        },
                    )
                }
                if (draft.hotwordEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.hotwordPhrase,
                        onValueChange = { draft = draft.copy(hotwordPhrase = it) },
                        label = { Text(stringResource(Res.string.hotword_phrase)) },
                        supportingText = { Text(stringResource(Res.string.hotword_phrase_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!micPermission.granted) {
                        Text(
                            stringResource(Res.string.hotword_mic_needed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = micPermission.request) {
                            Text(stringResource(Res.string.hotword_grant_mic))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.assistant_chat_mode), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.assistantChatMode != "specific",
                    onClick = { draft = draft.copy(assistantChatMode = "new") },
                    label = { Text(stringResource(Res.string.assistant_mode_new)) },
                )
                FilterChip(
                    selected = draft.assistantChatMode == "specific",
                    onClick = { draft = draft.copy(assistantChatMode = "specific") },
                    label = { Text(stringResource(Res.string.assistant_mode_specific)) },
                )
            }
            if (draft.assistantChatMode == "specific") {
                Spacer(Modifier.height(10.dp))
                AssistantSessionPicker(
                    controller = controller,
                    state = state,
                    draft = draft,
                    onSelect = { draft = draft.copy(assistantSessionKey = it) },
                )
            }
        }

        SettingsCard(title = stringResource(Res.string.settings_stt), modifier = Modifier.widthIn(max = 760.dp)) {
            Text(stringResource(Res.string.stt_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.stt_mode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = draft.sttMode == "local", onClick = { draft = draft.copy(sttMode = "local") }, label = { Text(stringResource(Res.string.stt_mode_local)) })
                FilterChip(selected = draft.sttMode == "gateway", onClick = { draft = draft.copy(sttMode = "gateway") }, label = { Text(stringResource(Res.string.stt_mode_gateway)) })
            }
            Spacer(Modifier.height(10.dp))
            SliderRow(stringResource(Res.string.stt_silence), stringResource(Res.string.milliseconds, draft.sttSilenceMs), draft.sttSilenceMs.toFloat(), 300f..3000f, 26) { draft = draft.copy(sttSilenceMs = it.roundToInt()) }
            Spacer(Modifier.height(8.dp))
            SliderRow(stringResource(Res.string.stt_max), stringResource(Res.string.seconds, draft.sttMaxSeconds), draft.sttMaxSeconds.toFloat(), 5f..180f, 34) { draft = draft.copy(sttMaxSeconds = it.roundToInt()) }
        }

        SettingsCard(title = stringResource(Res.string.settings_tts), modifier = Modifier.widthIn(max = 760.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.tts_enabled), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.tts_enabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = draft.ttsEnabled, onCheckedChange = { draft = draft.copy(ttsEnabled = it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.tts_mode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = draft.ttsMode == "local", onClick = { draft = draft.copy(ttsMode = "local") }, label = { Text(stringResource(Res.string.tts_mode_local)) })
                FilterChip(selected = draft.ttsMode == "http", onClick = { draft = draft.copy(ttsMode = "http") }, label = { Text(stringResource(Res.string.tts_mode_http)) })
            }
            if (draft.ttsMode == "local") {
                Spacer(Modifier.height(12.dp))
                TtsEnginePicker(
                    selected = draft.ttsEngine,
                    engines = remember { availableTtsEngines() },
                    systemDefault = remember { systemDefaultTtsEngine() },
                    onSelect = { draft = draft.copy(ttsEngine = it) },
                )
            }
            if (draft.ttsMode == "http") {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = draft.ttsBaseUrl, onValueChange = { draft = draft.copy(ttsBaseUrl = it) }, label = { Text(stringResource(Res.string.tts_api_url)) }, placeholder = { Text(stringResource(Res.string.tts_api_url_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.ttsApiKey,
                    onValueChange = { draft = draft.copy(ttsApiKey = it) },
                    label = { Text(stringResource(Res.string.tts_api_key)) },
                    placeholder = { Text(stringResource(Res.string.tts_api_key_hint)) },
                    supportingText = { Text(stringResource(Res.string.api_key_stored)) },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = draft.ttsModel, onValueChange = { draft = draft.copy(ttsModel = it) }, label = { Text(stringResource(Res.string.tts_model)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = draft.ttsVoice, onValueChange = { draft = draft.copy(ttsVoice = it) }, label = { Text(stringResource(Res.string.tts_voice)) }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            SliderRow(stringResource(Res.string.tts_speed), stringResource(Res.string.speed_value, formatSpeed(draft.ttsSpeed)), draft.ttsSpeed, 0.5f..2.0f, 14) { draft = draft.copy(ttsSpeed = it) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { controller.saveSettings(draft); controller.testTts(testText) },
                    enabled = draft.ttsMode == "local" || draft.ttsBaseUrl.isNotBlank(),
                ) { Text(stringResource(Res.string.tts_test)) }
                if (draft.ttsMode == "http" && draft.ttsBaseUrl.isBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.tts_not_configured), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.tts.error != null) {
                Spacer(Modifier.height(8.dp))
                Text("${stringResource(Res.string.tts_error)}: ${state.tts.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        SettingsCard(title = stringResource(Res.string.settings_chat), modifier = Modifier.widthIn(max = 760.dp)) {
            SliderRow(stringResource(Res.string.chat_font_size), draft.chatFontSize.roundToInt().toString(), draft.chatFontSize, 12f..22f, 9) { draft = draft.copy(chatFontSize = it) }
        }

        SettingsCard(title = stringResource(Res.string.theme), modifier = Modifier.widthIn(max = 760.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to Res.string.theme_system, "light" to Res.string.theme_light, "dark" to Res.string.theme_dark).forEach { (mode, labelRes) ->
                    FilterChip(
                        selected = draft.themeMode == mode,
                        onClick = { draft = draft.copy(themeMode = mode) },
                        label = { Text(stringResource(labelRes)) },
                        leadingIcon = if (draft.themeMode == mode) { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                    )
                }
            }
        }

        Text(stringResource(Res.string.app_version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────
// TAB 2: Gateway Config — parameter form (+ raw JSON fallback)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun GatewayConfigTab(controller: AppController, state: AppState) {
    RuntimeConfigForm(controller = controller, state = state)
}

// ─────────────────────────────────────────────────────────────────
// TAB 3: Models & Providers (Nanobot)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun ModelsTab(controller: AppController, state: AppState) {
    val connected = state.connection.phase == ConnectionPhase.CONNECTED
    var editingProvider by remember { mutableStateOf<String?>(null) }
    val activeName = state.activeModelPreset ?: state.agentModel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!connected) {
            Card(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(stringResource(Res.string.gateway_not_connected_hint), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth().widthIn(max = 760.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { controller.refreshGatewaySettings() }, enabled = !state.modelsLoading) {
                if (state.modelsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.refresh))
            }
        }

        if (activeName != null) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.current_default_model, activeName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        SettingsCard(title = stringResource(Res.string.available_models_title), modifier = Modifier.widthIn(max = 760.dp)) {
            if (state.modelsLoading && state.modelPresets.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.modelPresets.isEmpty()) {
                Text(stringResource(Res.string.no_models_available), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            } else {
                state.modelPresets.forEachIndexed { index, preset ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val selected = preset.name == activeName || (preset.active && preset.model == activeName)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                preset.label.ifBlank { preset.name },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                listOfNotNull(
                                    preset.provider.ifBlank { null },
                                    preset.model.ifBlank { null },
                                    preset.contextWindowTokens?.let { "$it ctx" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (selected) stringResource(Res.string.active_model) else preset.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!selected) {
                            TextButton(onClick = { controller.selectModelPreset(preset.name) }) {
                                Text(stringResource(Res.string.set_as_default), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        SettingsCard(title = stringResource(Res.string.provider_section_title), modifier = Modifier.widthIn(max = 760.dp)) {
            if (state.providers.isEmpty()) {
                Text(stringResource(Res.string.no_models_available), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            } else {
                state.providers.forEachIndexed { index, provider ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.label.ifBlank { provider.name }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    provider.apiBase?.takeIf { it.isNotBlank() },
                                    provider.apiKeyHint?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ").ifBlank { provider.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (provider.configured) stringResource(Res.string.model_status_available)
                                else stringResource(Res.string.model_status_unconfigured),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (provider.configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { editingProvider = provider.name }) {
                            Text(stringResource(Res.string.provider_edit), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    editingProvider?.let { name ->
        val provider = state.providers.firstOrNull { it.name == name }
        ProviderDialog(
            name = name,
            provider = provider,
            onDismiss = { editingProvider = null },
            onSave = { apiBase, apiKey ->
                controller.saveProvider(name, apiBase, apiKey)
                editingProvider = null
            },
        )
    }
}

@Composable
private fun ProviderDialog(
    name: String,
    provider: com.nanobot.client.core.model.ProviderItem?,
    onDismiss: () -> Unit,
    onSave: (String?, String?) -> Unit,
) {
    var apiBase by remember(name) { mutableStateOf(provider?.apiBase.orEmpty()) }
    var apiKey by remember(name) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = apiBase,
                    onValueChange = { apiBase = it },
                    label = { Text(stringResource(Res.string.provider_api_base)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(Res.string.provider_api_key)) },
                    supportingText = { Text(provider?.apiKeyHint.orEmpty()) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiBase.takeIf { it.isNotBlank() }, apiKey.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun TtsEnginePicker(
    selected: String,
    engines: List<com.nanobot.client.tts.TtsEngineInfo>,
    systemDefault: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = when {
        selected.isBlank() -> stringResource(Res.string.tts_engine_system)
        else -> engines.firstOrNull { it.id == selected }?.label ?: selected
    }
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.tts_engine), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(currentLabel, style = MaterialTheme.typography.bodyMedium)
                    if (selected.isBlank() && systemDefault != null) {
                        Text(
                            systemDefault,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.tts_engine_system)) },
                leadingIcon = if (selected.isBlank()) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                onClick = { onSelect(""); expanded = false },
            )
            engines.forEach { engine ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(engine.label)
                            Text(
                                engine.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (selected == engine.id) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = { onSelect(engine.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun AssistantSessionPicker(
    controller: AppController,
    state: AppState,
    draft: AppSettings,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.sessions.firstOrNull {
        it.key == draft.assistantSessionKey || it.chatId == draft.assistantSessionKey
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            selected?.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.assistant_pick_chat),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        selected?.handle?.name?.takeIf { it.isNotBlank() }?.let { handle ->
                            Text(
                                "@$handle",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { controller.refreshSessions() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(Res.string.refresh))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (state.sessions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.assistant_no_chats)) },
                    onClick = { expanded = false },
                )
            } else {
                state.sessions.forEach { session ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(session.title?.takeIf { it.isNotBlank() } ?: session.key)
                                session.handle?.name?.takeIf { it.isNotBlank() }?.let { handle ->
                                    Text(
                                        "@$handle",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(session.key)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
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
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        androidx.compose.material3.Slider(value = value, onValueChange = onChange, valueRange = valueRange, steps = steps)
    }
}

private fun formatSpeed(value: Float): String {
    val rounded = (value * 10).roundToInt()
    return "${rounded / 10}.${rounded % 10}"
}
