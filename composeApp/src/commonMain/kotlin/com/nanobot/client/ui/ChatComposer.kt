package com.nanobot.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.audio.VoicePhase
import com.nanobot.client.core.model.LocalAttachment
import com.nanobot.client.platform.MicPermissionState
import com.nanobot.client.platform.rememberAttachmentPicker
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.assistant_listening_pending
import com.nanobot.client.resources.attach_file
import com.nanobot.client.resources.attach_photo
import com.nanobot.client.resources.message_hint
import com.nanobot.client.resources.mic
import com.nanobot.client.resources.recording
import com.nanobot.client.resources.seconds
import com.nanobot.client.resources.send
import com.nanobot.client.resources.stop
import com.nanobot.client.resources.stop_recording
import com.nanobot.client.resources.voice_processing
import org.jetbrains.compose.resources.stringResource

private const val MAX_ATTACHMENTS = 4

@Composable
fun ChatComposer(
    controller: AppController,
    state: AppState,
    micPermission: MicPermissionState,
    modifier: Modifier = Modifier,
) {
    var text by remember(state.chatId) { mutableStateOf("") }
    var attachments by remember(state.chatId) { mutableStateOf<List<LocalAttachment>>(emptyList()) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    val picker = rememberAttachmentPicker { picked ->
        val merged = (attachments + picked).take(MAX_ATTACHMENTS)
        if (attachments.size + picked.size > MAX_ATTACHMENTS) {
            attachmentError = "Можно прикрепить не более $MAX_ATTACHMENTS файлов"
        } else {
            attachmentError = null
        }
        attachments = merged
    }

    val connected = state.connection.phase == ConnectionPhase.CONNECTED
    val voice = state.voice
    val recording = voice.phase == VoicePhase.LISTENING || voice.phase == VoicePhase.SPEECH
    val processing = voice.phase == VoicePhase.PROCESSING
    val pendingAssistant = state.assistantLaunching && voice.phase == VoicePhase.IDLE
    val expanded = recording || processing || pendingAssistant

    // The stop button reflects an actual in-flight turn, not a stale isStreaming flag.
    val turnActive = state.sending || state.awaitingFirstToken ||
        state.activeTurnId != null || state.stopping
    val canSend = connected && (text.isNotBlank() || attachments.isNotEmpty()) && !turnActive

    fun submit() {
        if (!canSend) return
        controller.send(text, attachments)
        text = ""
        attachments = emptyList()
        attachmentError = null
    }

    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 16.dp),
        ) {
            if (!expanded && attachments.isNotEmpty()) {
                AttachmentChips(attachments = attachments, onRemove = { index ->
                    attachments = attachments.toMutableList().also { it.removeAt(index) }
                })
                Spacer(Modifier.size(6.dp))
            }
            attachmentError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.96f))
                },
                label = "composer-mode",
            ) { showRecording ->
                if (showRecording) {
                    RecordingBar(
                        voice = voice,
                        recording = recording,
                        processing = processing,
                        pending = pendingAssistant,
                        onStop = {
                            if (recording) controller.stopVoice()
                        },
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = LocalWebUiColors.current.popover,
                        border = BorderStroke(1.dp, scheme.outline),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box {
                                IconButton(
                                    onClick = { attachMenuOpen = true },
                                    enabled = connected,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.AttachFile,
                                        contentDescription = stringResource(Res.string.attach_file),
                                        tint = if (connected) LocalWebUiColors.current.sidebarContent else scheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = attachMenuOpen,
                                    onDismissRequest = { attachMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.attach_photo)) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            attachMenuOpen = false
                                            picker.pickImages()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.attach_file)) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            attachMenuOpen = false
                                            picker.pickFiles()
                                        },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.message_hint),
                                        color = LocalWebUiColors.current.sidebarMuted,
                                    )
                                },
                                maxLines = 6,
                                shape = RoundedCornerShape(18.dp),
                                enabled = connected,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submit() }),
                                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                        if (canSend) {
                                            submit()
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                },
                            )
                            // Mic / record button
                            Surface(
                                onClick = {
                                    if (!micPermission.granted) {
                                        micPermission.request()
                                    } else {
                                        controller.startVoice()
                                    }
                                },
                                enabled = connected,
                                shape = CircleShape,
                                color = if (connected) scheme.primary else scheme.surfaceVariant,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (voice.phase == VoicePhase.PROCESSING) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = scheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Mic,
                                            contentDescription = stringResource(Res.string.mic),
                                            tint = if (micPermission.granted) scheme.onPrimary else scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Surface(
                                onClick = {
                                    if (turnActive) {
                                        controller.stopTurn()
                                    } else {
                                        submit()
                                    }
                                },
                                enabled = if (state.stopping) false else if (turnActive) true else canSend,
                                shape = CircleShape,
                                color = when {
                                    state.stopping -> scheme.surfaceVariant
                                    turnActive -> scheme.primary
                                    canSend -> scheme.primary
                                    else -> scheme.surfaceVariant
                                },
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (state.stopping) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    } else if (turnActive) {
                                        Icon(
                                            Icons.Rounded.Stop,
                                            contentDescription = stringResource(Res.string.stop),
                                            tint = scheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = stringResource(Res.string.send),
                                            tint = if (!canSend) scheme.onSurfaceVariant else scheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChips(attachments: List<LocalAttachment>, onRemove: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEachIndexed { index, attachment ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        attachment.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBar(
    voice: com.nanobot.client.audio.VoiceUiState,
    recording: Boolean,
    processing: Boolean,
    pending: Boolean,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.StopCircle,
                        contentDescription = stringResource(Res.string.stop_recording),
                        tint = scheme.error,
                        modifier = Modifier.size(26.dp),
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        processing -> stringResource(Res.string.voice_processing)
                        pending -> stringResource(Res.string.assistant_listening_pending)
                        else -> stringResource(Res.string.recording)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimaryContainer,
                )
                if (recording || processing) {
                    Spacer(Modifier.size(4.dp))
                    LinearProgressIndicator(
                        progress = { voice.amplitude },
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.onPrimaryContainer.copy(alpha = 0.15f),
                    )
                }
            }
            if (recording) {
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(Res.string.seconds, (voice.elapsedMs / 1000).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer,
                )
            }
        }
    }
}
