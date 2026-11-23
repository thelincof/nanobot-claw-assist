package com.nanobot.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.core.model.UiMessage
import com.nanobot.client.platform.MicPermissionState
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.chat_untitled
import com.nanobot.client.resources.choose_chat
import com.nanobot.client.resources.empty_state_title
import com.nanobot.client.resources.choose_chat_hint
import com.nanobot.client.resources.connection_failed
import com.nanobot.client.resources.connection_lost
import com.nanobot.client.resources.reconnecting
import com.nanobot.client.resources.reconnecting_hint
import com.nanobot.client.resources.empty_chat_hint
import com.nanobot.client.resources.empty_chat_title
import com.nanobot.client.resources.load_earlier
import com.nanobot.client.resources.loading
import com.nanobot.client.resources.thinking_placeholder
import com.nanobot.client.resources.link_restoring
import com.nanobot.client.resources.menu
import com.nanobot.client.resources.retry
import com.nanobot.client.resources.settings
import com.nanobot.client.resources.tts_off
import com.nanobot.client.resources.tts_on
import com.nanobot.client.resources.tts_speaking
import com.nanobot.client.resources.tts_stop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatPane(
    controller: AppController,
    state: AppState,
    micPermission: MicPermissionState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onMenu: () -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        ChatTopBar(
            state = state,
            controller = controller,
            compact = compact,
            onMenu = onMenu,
            onOpenSettings = onOpenSettings,
        )
        // Reconnection is already visible in the top bar; only critical failures
        // deserve a banner so the chat doesn't look broken/flashy.
        if (state.connection.phase == ConnectionPhase.FAILED) {
            ConnectionBanner(
                text = "${stringResource(Res.string.connection_failed)}: ${state.connection.error.orEmpty()}",
                isError = true,
                onRetry = { controller.connect(state.settings.baseUrl, state.settings.secret) },
                retryLabel = stringResource(Res.string.retry),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.selectedSession == null -> EmptyChatState()
                state.messagesLoading && state.messages.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.messages.isEmpty() && state.messagesError == null -> EmptyChatState()
                else -> MessageList(controller, state)
            }
            if (state.messagesError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.messagesError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            state.selectedSession?.let(controller::openSession)
                        }) { Text(stringResource(Res.string.retry)) }
                    }
                }
            }
        }

        if (state.selectedSession != null && (state.goal.active || state.goal.status == "blocked")) {
            GoalStrip(
                goal = state.goal,
                runtimeStatus = state.goalStatus,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        ChatComposer(controller, state, micPermission)
    }
}

@Composable
private fun ChatTopBar(
    state: AppState,
    controller: AppController,
    compact: Boolean,
    onMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ui = LocalWebUiColors.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Rounded.Menu, contentDescription = stringResource(Res.string.menu))
                }
            }
            Column(Modifier.weight(1f).padding(start = if (compact) 0.dp else 8.dp)) {
                Text(
                    state.selectedSession?.let { row ->
                        row.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.chat_untitled)
                    } ?: stringResource(Res.string.chat_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (state.connection.phase) {
                            ConnectionPhase.CONNECTED -> state.settings.baseUrl
                            ConnectionPhase.CONNECTING -> stringResource(Res.string.reconnecting)
                            ConnectionPhase.RECONNECTING -> stringResource(Res.string.connection_lost)
                            ConnectionPhase.FAILED -> stringResource(Res.string.connection_failed)
                            ConnectionPhase.IDLE -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ui.sidebarMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.tts.speaking || state.tts.loading) {
                Text(
                    stringResource(Res.string.tts_speaking),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            val speaking = state.tts.speaking || state.tts.loading
            IconButton(
                onClick = {
                    if (speaking) controller.stopSpeaking() else controller.toggleTts()
                },
            ) {
                Icon(
                    when {
                        speaking -> Icons.Rounded.StopCircle
                        state.tts.enabled -> Icons.Rounded.VolumeUp
                        else -> Icons.Rounded.VolumeOff
                    },
                    contentDescription = when {
                        speaking -> stringResource(Res.string.tts_stop)
                        state.tts.enabled -> stringResource(Res.string.tts_on)
                        else -> stringResource(Res.string.tts_off)
                    },
                    tint = when {
                        speaking -> MaterialTheme.colorScheme.primary
                        state.tts.error != null -> MaterialTheme.colorScheme.error
                        state.tts.enabled -> MaterialTheme.colorScheme.onSurface
                        else -> ui.sidebarMuted
                    },
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(Res.string.settings),
                    tint = ui.sidebarMuted,
                )
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    text: String,
    isError: Boolean,
    onRetry: () -> Unit,
    retryLabel: String,
) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isError) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

@Composable
private fun MessageList(controller: AppController, state: AppState) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var initialScrollDone by remember(state.chatId) { mutableStateOf(false) }
    val messages = state.messages
    val currentTurnVisible = state.activeTurnId?.let { turnId ->
        messages.any { message ->
            message.turnId == turnId &&
                (message.role == "activity" || message.content.isNotBlank() || !message.reasoning.isNullOrBlank())
        }
    } ?: false
    val showPending = state.sending || state.awaitingFirstToken ||
        (state.activeTurnId != null && !currentTurnVisible)
    val bottomIndex = messages.lastIndex + if (showPending) 1 else 0
    // The last item can be taller than the viewport (expanded activity). Being
    // "at the last index" is not the same as being at the bottom; otherwise a
    // stream update yanks the list back to that item's header.
    val atBottom by remember(messages.size, showPending) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            lastVisible.index >= info.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 4
        }
    }

    LaunchedEffect(state.chatId, state.messagesLoading) {
        if (!state.messagesLoading) {
            if (messages.isNotEmpty()) listState.scrollToBottom(bottomIndex)
            initialScrollDone = true
        } else {
            initialScrollDone = false
        }
    }
    LaunchedEffect(
        initialScrollDone,
        state.historyHasMore,
        state.historyLoadingOlder,
        state.historyLoadFailed,
        listState.firstVisibleItemIndex,
    ) {
        if (initialScrollDone && state.historyHasMore && !state.historyLoadingOlder &&
            !state.historyLoadFailed && listState.firstVisibleItemIndex <= 1
        ) {
            controller.loadOlderHistory()
        }
    }
    val last = messages.lastOrNull()
    LaunchedEffect(
        last?.id,
        last?.content?.length,
        last?.reasoning?.length,
        last?.toolEvents?.size,
        last?.fileEdits?.size,
        state.sending,
        state.awaitingFirstToken,
        showPending,
    ) {
        if (last != null && atBottom && bottomIndex >= 0) {
            listState.animateToBottom(bottomIndex)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val fontSize = state.settings.chatFontSize
        CompositionLocalProvider(LocalChatFontSize provides fontSize) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().widthIn(max = 792.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.historyHasMore || state.historyLoadingOlder) {
                        item(key = "history-loader") {
                            HistoryLoaderRow(
                                loading = state.historyLoadingOlder,
                                onLoad = controller::loadOlderHistory,
                            )
                        }
                    }
                    itemsIndexed(messages, key = { _, message -> messageKey(message) }) { _, message ->
                        MessageItem(
                            message = message,
                            mediaBaseUrl = state.settings.baseUrl,
                            onSpeak = { text -> controller.speakText(text) },
                        )
                    }
                    if (showPending) {
                        item(key = "pending-placeholder") { PendingBubble() }
                    }
                }
            }
        }
        if (!atBottom && messages.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateToBottom(bottomIndex) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun HistoryLoaderRow(loading: Boolean, onLoad: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TextButton(onClick = onLoad) {
                Text(stringResource(Res.string.load_earlier))
            }
        }
    }
}

private suspend fun LazyListState.scrollToBottom(index: Int) {
    if (index < 0) return
    scrollToItem(index)
    scrollRemainingToBottom(index)
}

private suspend fun LazyListState.animateToBottom(index: Int) {
    if (index < 0) return
    // While streaming the item is already visible. Scroll only by the amount it
    // grew; animating to its top first would visibly jump to the block header.
    val layout = layoutInfo
    val visibleItem = layout.visibleItemsInfo.firstOrNull { it.index == index }
    if (visibleItem != null) {
        val remaining = visibleItem.offset + visibleItem.size - layout.viewportEndOffset
        if (remaining > 0) animateScrollBy(remaining.toFloat())
        return
    }
    animateScrollToItem(index)
    scrollRemainingToBottom(index)
}

private suspend fun LazyListState.scrollRemainingToBottom(index: Int) {
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val remaining = item.offset + item.size - layout.viewportEndOffset
    if (remaining > 0) scrollBy(remaining.toFloat())
}

@Composable
private fun PendingBubble() {
    val transition = rememberInfiniteTransition(label = "pending")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pending-alpha",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(Res.string.thinking_placeholder) + "·".repeat(3),
            style = MaterialTheme.typography.bodySmall,
            color = LocalWebUiColors.current.sidebarMuted,
            modifier = Modifier.alpha(alpha),
        )
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🐱", fontSize = 32.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.empty_state_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.empty_chat_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalWebUiColors.current.sidebarMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ChooseChatState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🐱", fontSize = 32.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.empty_state_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.choose_chat_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalWebUiColors.current.sidebarMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun messageKey(message: UiMessage): String =
    message.id.ifBlank { "${message.turnId}:${message.role}:${message.createdAt}" }
