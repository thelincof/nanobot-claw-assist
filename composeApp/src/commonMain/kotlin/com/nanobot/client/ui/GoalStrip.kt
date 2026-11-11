package com.nanobot.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobot.client.core.model.GoalStatePayload
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.goal_collapse
import com.nanobot.client.resources.goal_expand
import com.nanobot.client.resources.goal_objective
import com.nanobot.client.resources.goal_recap
import com.nanobot.client.resources.goal_status_active
import com.nanobot.client.resources.goal_status_blocked
import com.nanobot.client.resources.goal_title
import org.jetbrains.compose.resources.stringResource

private val GoalBlue = Color(0xFF3B82F6)

@Composable
fun GoalStrip(
    goal: GoalStatePayload,
    runtimeStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    val blocked = goal.status == "blocked" || runtimeStatus == "blocked"
    if (!goal.active && !blocked) return
    var expanded by remember(goal.objective, goal.status) { mutableStateOf(false) }
    val statusText = if (blocked) {
        stringResource(Res.string.goal_status_blocked)
    } else {
        stringResource(Res.string.goal_status_active)
    }
    val summary = goal.uiSummary.orEmpty().trim().ifBlank { goal.objective.orEmpty().trim() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GoalBlue.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, GoalBlue.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = GoalBlue,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.goal_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GoalBlue,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (blocked) MaterialTheme.colorScheme.error else GoalBlue,
                        )
                    }
                    if (summary.isNotBlank() && !expanded) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) Res.string.goal_collapse else Res.string.goal_expand,
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = GoalBlue,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 38.dp, end = 12.dp, bottom = 10.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    goal.objective?.takeIf { it.isNotBlank() }?.let { objective ->
                        Text(
                            stringResource(Res.string.goal_objective),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoalBlue,
                        )
                        Text(
                            objective,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    goal.recap?.takeIf { it.isNotBlank() }?.let { recap ->
                        Text(
                            stringResource(Res.string.goal_recap),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoalBlue,
                        )
                        Text(
                            recap,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
