package com.nanobot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.defaultWorkspacePathOf
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.app.filterSessionsByWorkspace
import com.nanobot.client.core.model.SessionRow
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.app_name
import com.nanobot.client.resources.automations_title
import com.nanobot.client.resources.chat_untitled
import com.nanobot.client.resources.chats
import com.nanobot.client.resources.cancel
import com.nanobot.client.resources.clear_search
import com.nanobot.client.resources.close
import com.nanobot.client.resources.connected
import com.nanobot.client.resources.connecting
import com.nanobot.client.resources.connection_failed
import com.nanobot.client.resources.connection_lost
import com.nanobot.client.resources.default_model
import com.nanobot.client.resources.delete
import com.nanobot.client.resources.delete_chat
import com.nanobot.client.resources.delete_chat_text
import com.nanobot.client.resources.delete_chat_title
import com.nanobot.client.resources.models
import com.nanobot.client.resources.model_status_available
import com.nanobot.client.resources.model_status_unconfigured
import com.nanobot.client.resources.model_status_unreachable
import com.nanobot.client.resources.new_chat
import com.nanobot.client.resources.no_models_available
import com.nanobot.client.resources.not_connected
import com.nanobot.client.resources.no_chats
import com.nanobot.client.resources.no_chats_hint
import com.nanobot.client.resources.no_results
import com.nanobot.client.resources.reconnecting
import com.nanobot.client.resources.rename_chat
import com.nanobot.client.resources.restart_gateway
import com.nanobot.client.resources.restart_gateway_confirm
import com.nanobot.client.resources.save
import com.nanobot.client.resources.*
import com.nanobot.client.resources.settings
import com.nanobot.client.resources.settings_workspaces
import com.nanobot.client.resources.status_connected
import com.nanobot.client.resources.status_connecting
import com.nanobot.client.resources.status_offline
import com.nanobot.client.resources.workspace_add
import com.nanobot.client.resources.workspace_add_title
import com.nanobot.client.resources.workspace_default
import com.nanobot.client.resources.workspace_name
import com.nanobot.client.resources.workspace_path
import com.nanobot.client.resources.workspace_pick_folder
import com.nanobot.client.resources.workspace_show_all
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(
    controller: AppController,
    state: AppState,
    modifier: Modifier = Modifier,
    showClose: Boolean = false,
    onClose: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenAutomations: () -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<SessionRow?>(null) }
    var pendingRename by remember { mutableStateOf<SessionRow?>(null) }
    var showAddWorkspace by remember { mutableStateOf(false) }
    val query = state.search.trim()
    val workspaceFiltered = remember(state.sessions, state.showAllChats, state.workspacesInfo, state.settings.activeWorkspacePath) {
        filterSessionsByWorkspace(state)
    }
    val sessions = remember(workspaceFiltered, query) {
        if (query.isEmpty()) workspaceFiltered else workspaceFiltered.filter { row ->
            row.title.orEmpty().contains(query, ignoreCase = true) ||
                row.preview.orEmpty().contains(query, ignoreCase = true) ||
                row.key.contains(query, ignoreCase = true) ||
                row.handle?.name?.contains(query, ignoreCase = true) == true
        }
    }
    val darkTheme = when (state.settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val archivedCount = sessions.count { it.key in state.archivedKeys }
    val visibleSessions = sessions.filter { state.showArchived || it.key !in state.archivedKeys }
    val pinnedSessions = visibleSessions.filter { it.key in state.pinnedKeys }
    val regularSessions = visibleSessions.filter { it.key !in state.pinnedKeys }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(LocalWebUiColors.current.sidebar)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🐱",
                fontSize = 24.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalWebUiColors.current.sidebarForeground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenAutomations) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = stringResource(Res.string.automations_title),
                    tint = LocalWebUiColors.current.sidebarMuted,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(Res.string.settings),
                    tint = LocalWebUiColors.current.sidebarMuted,
                )
            }
            if (showClose) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                value = state.search,
                onValueChange = controller::setSearch,
                onClear = { controller.setSearch("") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            // Круглая кнопка «новый чат», как в webui
            Surface(
                onClick = {
                    controller.newChat()
                    onClose()
                },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(Res.string.new_chat),
                        tint = LocalWebUiColors.current.sidebarContent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.chats),
                style = MaterialTheme.typography.labelMedium,
                color = LocalWebUiColors.current.sidebarMuted,
                modifier = Modifier.weight(1f),
            )
            if (state.sessionsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(4.dp))

        if (state.sessionsError != null) {
            Text(
                state.sessionsError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (visibleSessions.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(if (query.isEmpty()) Res.string.no_chats else Res.string.no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (query.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(Res.string.no_chats_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                if (pinnedSessions.isNotEmpty()) {
                    item(key = "pinned-header") { SessionSectionLabel(stringResource(Res.string.pinned_chats)) }
                    items(pinnedSessions, key = { it.key }) { session ->
                        SessionRowItem(
                            session = session,
                            selected = state.selectedSession?.key == session.key,
                            running = session.running && state.chatId != session.chatId,
                            pinned = true,
                            archived = session.key in state.archivedKeys,
                            darkTheme = darkTheme,
                            onClick = {
                                controller.openSession(session)
                                onClose()
                            },
                            onTogglePin = { controller.togglePin(session) },
                            onRename = { pendingRename = session },
                            onToggleArchive = { controller.toggleArchive(session) },
                            onDelete = { pendingDelete = session },
                        )
                    }
                }
                if (regularSessions.isNotEmpty()) {
                    items(regularSessions, key = { it.key }) { session ->
                        SessionRowItem(
                            session = session,
                            selected = state.selectedSession?.key == session.key,
                            running = session.running && state.chatId != session.chatId,
                            pinned = false,
                            archived = session.key in state.archivedKeys,
                            darkTheme = darkTheme,
                            onClick = {
                                controller.openSession(session)
                                onClose()
                            },
                            onTogglePin = { controller.togglePin(session) },
                            onRename = { pendingRename = session },
                            onToggleArchive = { controller.toggleArchive(session) },
                            onDelete = { pendingDelete = session },
                        )
                    }
                }
            }
            if (archivedCount > 0) {
                item(key = "archived-toggle") {
                    TextButton(
                        onClick = { controller.setShowArchived(!state.showArchived) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.showArchived) Res.string.hide_archive else Res.string.show_archive,
                                archivedCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        WorkspaceSelectorBar(
            controller = controller,
            state = state,
            onAddWorkspace = { showAddWorkspace = true },
        )
        Spacer(Modifier.height(2.dp))
        ModelSelectorBar(
            controller = controller,
            state = state,
        )
        Spacer(Modifier.height(4.dp))
        StatusRow(state)
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.delete_chat_title)) },
            text = { Text(stringResource(Res.string.delete_chat_text)) },
            confirmButton = {
                TextButton(onClick = {
                    controller.deleteSession(session)
                    pendingDelete = null
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    pendingRename?.let { session ->
        RenameDialog(
            session = session,
            onDismiss = { pendingRename = null },
            onConfirm = { title ->
                controller.renameSession(session, title)
                pendingRename = null
            },
        )
    }

    if (showAddWorkspace) {
        AddWorkspaceDialog(
            controller = controller,
            state = state,
            onDismiss = { showAddWorkspace = false },
        )
    }
}

@Composable
private fun WorkspaceSelectorBar(
    controller: AppController,
    state: AppState,
    onAddWorkspace: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val defaultPath = defaultWorkspacePathOf(state)
    val active = state.settings.activeWorkspacePath
    val currentLabel = when {
        active.isBlank() -> stringResource(Res.string.workspace_default)
        active == defaultPath -> active.substringAfterLast('/').ifBlank { active }
        else -> state.settings.workspaces.firstOrNull { it.path == active }?.name ?: active.substringAfterLast('/').ifBlank { active }
    }
    Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LocalWebUiColors.current.sidebarMuted,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_workspaces),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalWebUiColors.current.sidebarMuted,
                )
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalWebUiColors.current.sidebarContent,
                )
            }
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = LocalWebUiColors.current.sidebarMuted,
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.workspace_default)) },
            leadingIcon = if (active.isBlank()) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            onClick = {
                controller.setActiveWorkspace("")
                expanded = false
            },
        )
        if (defaultPath.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("${defaultPath.substringAfterLast('/')} · $defaultPath") },
                leadingIcon = if (active == defaultPath) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                onClick = {
                    controller.setActiveWorkspace(defaultPath)
                    expanded = false
                },
            )
        }
        state.settings.workspaces.forEach { entry ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(entry.name)
                        Text(
                            entry.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = if (active == entry.path) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                onClick = {
                    controller.setActiveWorkspace(entry.path)
                    expanded = false
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.workspace_add)) },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = {
                expanded = false
                onAddWorkspace()
            },
        )
    }
}

@Composable
private fun ModelSelectorBar(
    controller: AppController,
    state: AppState,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.activeModelPreset
        ?: state.agentModel
        ?: state.modelPresets.firstOrNull { it.active }?.name
        ?: "—"

    Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LocalWebUiColors.current.sidebarMuted,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.default_model),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalWebUiColors.current.sidebarMuted,
                )
                Text(
                    current,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalWebUiColors.current.sidebarContent,
                )
            }
            if (state.modelsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            } else {
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = LocalWebUiColors.current.sidebarMuted,
                )
            }
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (state.modelPresets.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.no_models_available)) },
                onClick = { expanded = false },
            )
        } else {
            state.modelPresets.forEach { preset ->
                val selected = current == preset.name || (preset.active && current == preset.model)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(preset.label.ifBlank { preset.name }, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            Text(
                                listOfNotNull(preset.provider.ifBlank { null }, preset.model.ifBlank { null }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        controller.selectModelPreset(preset.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AddWorkspaceDialog(
    controller: AppController,
    state: AppState,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    val canPickFolder = state.workspacesInfo
        ?.get("controls")?.let { it as? kotlinx.serialization.json.JsonObject }
        ?.get("can_pick_folder")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content == "true"
    val valid = path.trim().trimEnd('/').startsWith("/") && path.trim().trimEnd('/').length > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.workspace_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.workspace_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(Res.string.workspace_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canPickFolder) {
                    TextButton(onClick = {
                        controller.pickWorkspaceFolder { picked ->
                            if (!picked.isNullOrBlank()) {
                                path = picked
                                if (name.isBlank()) name = picked.trimEnd('/').substringAfterLast('/')
                            }
                        }
                    }) {
                        Text(stringResource(Res.string.workspace_pick_folder))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    controller.addWorkspace(name, path)
                    controller.setActiveWorkspace(path.trim().trimEnd('/'))
                    onDismiss()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(
    session: SessionRow,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(session.title.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_chat)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim()) },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ui = LocalWebUiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(38.dp)
            .background(ui.sidebarAccent, RoundedCornerShape(19.dp))
            .padding(start = 12.dp, end = 4.dp),
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = ui.sidebarMuted,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    stringResource(Res.string.search_chats),
                    style = MaterialTheme.typography.bodySmall,
                    color = ui.sidebarMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = ui.sidebarForeground),
                cursorBrush = SolidColor(ui.sidebarForeground),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    tint = ui.sidebarMuted,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(state: AppState) {
    val (text, color) = when (state.connection.phase) {
        ConnectionPhase.CONNECTED -> stringResource(Res.string.status_connected) to Color(0xFF2E7D32)
        ConnectionPhase.CONNECTING -> stringResource(Res.string.status_connecting) to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionPhase.RECONNECTING -> stringResource(Res.string.connection_lost) to Color(0xFFB26A00)
        ConnectionPhase.FAILED -> stringResource(Res.string.connection_failed) to MaterialTheme.colorScheme.error
        ConnectionPhase.IDLE -> stringResource(Res.string.status_offline) to LocalWebUiColors.current.sidebarMuted
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = LocalWebUiColors.current.sidebarMuted,
            )
        }
        if (state.connection.phase == ConnectionPhase.RECONNECTING && state.connection.error != null) {
            Text(
                state.connection.error!!,
                style = MaterialTheme.typography.labelSmall,
                color = LocalWebUiColors.current.sidebarMuted.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun SessionSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalWebUiColors.current.sidebarMuted,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SessionRowItem(
    session: SessionRow,
    selected: Boolean,
    running: Boolean,
    pinned: Boolean,
    archived: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selected) LocalWebUiColors.current.sidebarSelected else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Row(
                Modifier.padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        session.handle?.takeIf { it.name.isNotBlank() }?.let { handle ->
                            HandleBadge(handle = handle, darkTheme = darkTheme)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            sessionTitle(session),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) {
                                LocalWebUiColors.current.sidebarForeground
                            } else {
                                LocalWebUiColors.current.sidebarContent
                            },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (pinned) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = stringResource(Res.string.pin),
                                tint = LocalWebUiColors.current.sidebarMuted,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        if (archived) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(Res.string.archived),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalWebUiColors.current.sidebarMuted,
                            )
                        }
                    }
                    if (!session.preview.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            session.preview.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LocalWebUiColors.current.sidebarMuted,
                        )
                    }
                    sessionTime(session.updatedAt)?.let { time ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalWebUiColors.current.sidebarMuted.copy(alpha = 0.8f),
                        )
                    }
                }
                if (running) {
                    Spacer(Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = Color(0xFF3B82F6),
                    )
                }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(Res.string.chat_menu),
                        tint = LocalWebUiColors.current.sidebarMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (pinned) Res.string.unpin else Res.string.pin)) },
                leadingIcon = {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = { menuOpen = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.rename_chat)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = { menuOpen = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (archived) Res.string.unarchive else Res.string.archive)) },
                leadingIcon = {
                    Icon(
                        if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { menuOpen = false; onToggleArchive() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun HandleBadge(handle: com.nanobot.client.core.model.SessionHandlePayload, darkTheme: Boolean) {
    val underline = sessionHandleColor(handle.id, darkTheme)
    Text(
        "@${handle.name}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = LocalWebUiColors.current.sidebarMuted,
        maxLines = 1,
        modifier = Modifier.drawBehind {
            val stroke = 2.dp.toPx()
            drawLine(
                color = underline,
                start = androidx.compose.ui.geometry.Offset(0f, size.height - stroke / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - stroke / 2),
                strokeWidth = stroke,
            )
        },
    )
}

@Composable
private fun sessionTitle(row: SessionRow): String =
    row.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.chat_untitled)

private fun sessionTime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val normalized = raw.replace('T', ' ')
        if (normalized.length >= 16) normalized.substring(5, 16) else normalized
    } catch (_: Exception) {
        null
    }
}
