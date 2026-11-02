package com.nanobot.client.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.platform.MicPermissionState
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    controller: AppController,
    state: AppState,
    micPermission: MicPermissionState,
    onOpenSettings: () -> Unit,
    onOpenAutomations: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        if (compact) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = LocalWebUiColors.current.sidebar) {
                        Sidebar(
                            controller = controller,
                            state = state,
                            showClose = true,
                            onClose = { scope.launch { drawerState.close() } },
                            onOpenSettings = onOpenSettings,
                            onOpenAutomations = onOpenAutomations,
                        )
                    }
                },
            ) {
                ChatPane(
                    controller = controller,
                    state = state,
                    micPermission = micPermission,
                    compact = true,
                    onMenu = { scope.launch { drawerState.open() } },
                    onOpenSettings = onOpenSettings,
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    controller = controller,
                    state = state,
                    modifier = Modifier.width(290.dp),
                    onOpenSettings = onOpenSettings,
                    onOpenAutomations = onOpenAutomations,
                )
                VerticalDivider(color = LocalWebUiColors.current.border, thickness = 1.dp)
                ChatPane(
                    controller = controller,
                    state = state,
                    micPermission = micPermission,
                    modifier = Modifier.weight(1f),
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}
