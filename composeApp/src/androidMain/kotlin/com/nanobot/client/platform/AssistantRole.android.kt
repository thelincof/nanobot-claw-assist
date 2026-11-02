package com.nanobot.client.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nanobot.client.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class AssistantRoleStatus { UNKNOWN, HELD, AVAILABLE, UNSUPPORTED }

@Composable
actual fun AssistantPlatformSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(AssistantRoleStatus.UNKNOWN) }

    fun refresh() {
        status = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> AssistantRoleStatus.UNSUPPORTED
            else -> {
                val manager = context.getSystemService(RoleManager::class.java)
                when {
                    manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) ->
                        AssistantRoleStatus.UNSUPPORTED
                    manager.isRoleHeld(RoleManager.ROLE_ASSISTANT) -> AssistantRoleStatus.HELD
                    else -> AssistantRoleStatus.AVAILABLE
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (status) {
                AssistantRoleStatus.HELD -> stringResource(Res.string.assistant_role_held)
                AssistantRoleStatus.AVAILABLE -> stringResource(Res.string.assistant_role_available)
                AssistantRoleStatus.UNSUPPORTED -> stringResource(Res.string.assistant_role_unsupported)
                AssistantRoleStatus.UNKNOWN -> stringResource(Res.string.assistant_role_checking)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (status) {
                AssistantRoleStatus.HELD -> MaterialTheme.colorScheme.primary
                AssistantRoleStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(
                onClick = {
                    val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.getSystemService(RoleManager::class.java)
                    } else {
                        null
                    }
                    if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                        requestRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                    } else {
                        openDefaultAppsSettings(context)
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (status == AssistantRoleStatus.HELD) Res.string.assistant_open_settings
                        else Res.string.assistant_assign_role,
                    ),
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            stringResource(Res.string.assistant_samsung_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.assistant_fallback_shortcut),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openDefaultAppsSettings(context: Context) {
    val candidates = listOf(
        Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            runCatching { context.startActivity(intent) }
                .onSuccess { return }
        }
    }
}
