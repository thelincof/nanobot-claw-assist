package com.nanobot.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.app_version
import com.nanobot.client.resources.bootstrap_secret
import com.nanobot.client.resources.bootstrap_secret_hint
import com.nanobot.client.resources.connect_title
import com.nanobot.client.resources.error
import com.nanobot.client.resources.gateway_url
import com.nanobot.client.resources.gateway_url_hint
import com.nanobot.client.resources.settings
import com.nanobot.client.resources.where_password
import org.jetbrains.compose.resources.stringResource

private val PillShape = RoundedCornerShape(26.dp)

@Composable
fun ConnectScreen(
    controller: AppController,
    state: AppState,
    onOpenSettings: () -> Unit,
) {
    var url by remember(state.settings.baseUrl) { mutableStateOf(state.settings.baseUrl) }
    var secret by remember(state.settings.secret) { mutableStateOf(state.settings.secret) }
    var secretVisible by remember { mutableStateOf(false) }
    var hintExpanded by remember { mutableStateOf(false) }
    val connecting = state.connection.phase == ConnectionPhase.CONNECTING
    val scheme = MaterialTheme.colorScheme
    val ui = LocalWebUiColors.current

    fun submit() {
        if (!connecting) controller.connect(url, secret)
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = stringResource(Res.string.settings),
                tint = ui.sidebarMuted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "🐱",
                fontSize = 58.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(Res.string.connect_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PillField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = stringResource(Res.string.gateway_url_hint),
                    label = stringResource(Res.string.gateway_url),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    enabled = !connecting,
                )
                PillField(
                    value = secret,
                    onValueChange = { secret = it },
                    placeholder = stringResource(Res.string.bootstrap_secret_hint),
                    label = stringResource(Res.string.bootstrap_secret),
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    enabled = !connecting,
                    trailing = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                if (secretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = ui.sidebarMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // Круглая тёмная кнопка-стрелка, как в webui
                        Surface(
                            shape = CircleShape,
                            color = if (connecting || url.isBlank() || secret.isBlank()) {
                                scheme.surfaceVariant
                            } else {
                                scheme.primary
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            if (connecting) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                IconButton(onClick = ::submit) {
                                    Icon(
                                        Icons.Rounded.ArrowForward,
                                        contentDescription = stringResource(Res.string.connect_title),
                                        tint = if (url.isBlank() || secret.isBlank()) {
                                            scheme.onSurfaceVariant
                                        } else {
                                            scheme.onPrimary
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    },
                )
            }

            if (state.connection.phase == ConnectionPhase.FAILED && state.connection.error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "${stringResource(Res.string.error)}: ${state.connection.error}",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Column(Modifier.widthIn(max = 460.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { hintExpanded = !hintExpanded }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        stringResource(Res.string.where_password),
                        style = MaterialTheme.typography.bodySmall,
                        color = ui.sidebarMuted,
                    )
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = ui.sidebarMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                AnimatedVisibility(hintExpanded) {
                    Text(
                        stringResource(Res.string.bootstrap_secret_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
            Text(
                stringResource(Res.string.app_version),
                style = MaterialTheme.typography.labelSmall,
                color = ui.sidebarMuted,
            )
        }
    }
}

@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ui = LocalWebUiColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ui.sidebarMuted,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.background, PillShape)
                .border(1.dp, scheme.outline, PillShape)
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Box(
                Modifier.weight(1f).padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ui.sidebarMuted,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = scheme.onSurface,
                    ),
                    cursorBrush = SolidColor(scheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
    }
}
