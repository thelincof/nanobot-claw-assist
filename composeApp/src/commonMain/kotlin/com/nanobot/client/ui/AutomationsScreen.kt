package com.nanobot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.AutomationCalendar
import com.nanobot.client.app.AppState
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.core.model.AutomationJob
import com.nanobot.client.core.model.AutomationRun
import com.nanobot.client.resources.*
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

private enum class AutomationView { TASKS, CALENDAR }
private enum class AutomationFilter { ALL, ACTIVE, PAUSED, FAILED, SYSTEM }
private enum class AutomationSort { NEXT, LAST, UPDATED, NAME }

private val TZ = TimeZone.currentSystemDefault()

@Composable
fun AutomationsScreen(
    controller: AppController,
    state: AppState,
    onBack: () -> Unit,
) {
    var view by remember { mutableStateOf(AutomationView.TASKS) }
    var filter by remember { mutableStateOf(AutomationFilter.ALL) }
    var sort by remember { mutableStateOf(AutomationSort.NEXT) }
    var selected by remember { mutableStateOf<AutomationJob?>(null) }
    var editing by remember { mutableStateOf<AutomationJob?>(null) }
    var dayDialog by remember { mutableStateOf<Pair<LocalDate, List<AutomationCalendar.Entry>>?>(null) }
    var deleteTarget by remember { mutableStateOf<AutomationJob?>(null) }
    val connected = state.connection.phase == ConnectionPhase.CONNECTED

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        controller.loadAutomations()
        while (true) {
            delay(30_000)
            controller.loadAutomations()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                }
                Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                    Text(
                        stringResource(Res.string.automations_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (state.automationsLoading) {
                        Text(
                            stringResource(Res.string.automations_loading),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        view = if (view == AutomationView.TASKS) AutomationView.CALENDAR else AutomationView.TASKS
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(
                        if (view == AutomationView.TASKS) Icons.Rounded.Checklist else Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (view == AutomationView.TASKS) Res.string.automations_tasks
                            else Res.string.automations_calendar,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = { controller.loadAutomations() }, enabled = connected) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(Res.string.refresh))
                }
                AutomationOptionsMenu(
                    filter = filter,
                    sort = sort,
                    onFilter = { filter = it },
                    onSort = { sort = it },
                )
            }
        }

        if (!connected) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.gateway_not_connected_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        state.automationsError?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        val filtered = remember(state.automations, filter) {
            state.automations.filter { matchesFilter(it, filter) }
        }
        val sorted = remember(filtered, sort) {
            when (sort) {
                AutomationSort.NEXT -> filtered.sortedBy { it.state.nextRunAtMs ?: Long.MAX_VALUE }
                AutomationSort.LAST -> filtered.sortedByDescending { it.state.lastRunAtMs ?: 0 }
                AutomationSort.UPDATED -> filtered.sortedByDescending { it.updatedAtMs ?: 0 }
                AutomationSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            }
        }

        if (view == AutomationView.TASKS) {
            TasksList(
                jobs = sorted,
                busyId = state.automationBusyId,
                onOpen = { selected = it },
            )
        } else {
            CalendarView(
                jobs = filtered,
                darkTheme = when (state.settings.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                },
                onOpenDay = { day, entries -> dayDialog = day to entries },
            )
        }
    }

    selected?.let { job ->
        AutomationDetailsDialog(
            job = job,
            busy = state.automationBusyId == job.id,
            onDismiss = { selected = null },
            onRun = { controller.runAutomation(job.id) },
            onToggle = { controller.setAutomationEnabled(job.id, !job.enabled) },
            onDelete = { deleteTarget = job },
            onEdit = { editing = job; selected = null },
            onOpenRun = { runAtMs -> controller.loadAutomationResult(job, runAtMs) },
        )
    }

    editing?.let { job ->
        EditAutomationDialog(
            job = job,
            onDismiss = { editing = null },
            onSave = { values ->
                controller.updateAutomation(job.id, values) { editing = null }
            },
        )
    }

    if (state.automationResultLoading || state.automationResultText != null || state.automationResultError != null) {
        AutomationResultDialog(
            state = state,
            onDismiss = { controller.clearAutomationResult() },
        )
    }

    deleteTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(Res.string.automations_delete)) },
            text = {
                Text(stringResource(Res.string.automations_delete_confirm, job.name.ifBlank { job.id }))
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.deleteAutomation(job.id)
                    deleteTarget = null
                    selected = null
                }) {
                    Text(stringResource(Res.string.automations_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    dayDialog?.let { (day, entries) ->
        AlertDialog(
            onDismissRequest = { dayDialog = null },
            title = { Text(formatDate(day)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    entries.forEach { entry ->
                        CalendarEntryRow(entry) {
                            dayDialog = null
                            if (entry.planned) selected = entry.job
                            else controller.loadAutomationResult(entry.job, entry.runAtMs)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dayDialog = null }) { Text(stringResource(Res.string.close)) }
            },
        )
    }
}

@Composable
private fun AutomationOptionsMenu(
    filter: AutomationFilter,
    sort: AutomationSort,
    onFilter: (AutomationFilter) -> Unit,
    onSort: (AutomationSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.automations_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.automations_filter_label), style = MaterialTheme.typography.labelSmall) },
                enabled = false,
                onClick = {},
            )
            AutomationFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(filterLabel(option)) },
                    leadingIcon = if (option == filter) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = { onFilter(option); open = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.automations_sort_label), style = MaterialTheme.typography.labelSmall) },
                enabled = false,
                onClick = {},
            )
            AutomationSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    leadingIcon = if (option == sort) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = { onSort(option); open = false },
                )
            }
        }
    }
}

@Composable
private fun filterLabel(filter: AutomationFilter): String = stringResource(
    when (filter) {
        AutomationFilter.ALL -> Res.string.automations_filter_all
        AutomationFilter.ACTIVE -> Res.string.automations_filter_active
        AutomationFilter.PAUSED -> Res.string.automations_filter_paused
        AutomationFilter.FAILED -> Res.string.automations_filter_failed
        AutomationFilter.SYSTEM -> Res.string.automations_filter_system
    },
)

@Composable
private fun sortLabel(sort: AutomationSort): String = stringResource(
    when (sort) {
        AutomationSort.NEXT -> Res.string.automations_sort_next
        AutomationSort.LAST -> Res.string.automations_sort_last
        AutomationSort.UPDATED -> Res.string.automations_sort_updated
        AutomationSort.NAME -> Res.string.automations_sort_name
    },
)

private fun matchesFilter(job: AutomationJob, filter: AutomationFilter): Boolean = when (filter) {
    AutomationFilter.ALL -> true
    AutomationFilter.ACTIVE -> job.enabled && job.state.lastStatus != "error"
    AutomationFilter.PAUSED -> !job.enabled
    AutomationFilter.FAILED -> job.state.lastStatus == "error" || job.state.lastError != null
    AutomationFilter.SYSTEM -> job.protected
}

@Composable
private fun TasksList(jobs: List<AutomationJob>, busyId: String?, onOpen: (AutomationJob) -> Unit) {
    if (jobs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.automations_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 792.dp).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(jobs, key = { it.id }) { job ->
            AutomationRow(job = job, busy = busyId == job.id, onClick = { onOpen(job) })
        }
    }
}

@Composable
private fun AutomationRow(job: AutomationJob, busy: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        job.name.ifBlank { job.id },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (job.protected) {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(Res.string.automations_system), MaterialTheme.colorScheme.secondary)
                    }
                    if (job.schedule.kind == "at") {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(Res.string.automations_one_shot), MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(
                    scheduleSummary(job),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${stringResource(Res.string.automations_next)}: ${formatMs(job.state.nextRunAtMs)}  ·  " +
                        "${stringResource(Res.string.automations_last)}: ${formatMs(job.state.lastRunAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                StatusBadge(job)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StatusBadge(job: AutomationJob) {
    val (text, color) = when {
        job.state.pending -> stringResource(Res.string.automations_status_running) to MaterialTheme.colorScheme.primary
        !job.enabled -> stringResource(Res.string.automations_status_paused) to MaterialTheme.colorScheme.onSurfaceVariant
        job.state.lastStatus == "error" || job.state.lastError != null ->
            stringResource(Res.string.automations_status_failed) to MaterialTheme.colorScheme.error
        else -> stringResource(Res.string.automations_status_active) to MaterialTheme.colorScheme.primary
    }
    Badge(text, color)
}

@Composable
private fun CalendarView(
    jobs: List<AutomationJob>,
    darkTheme: Boolean,
    onOpenDay: (LocalDate, List<AutomationCalendar.Entry>) -> Unit,
) {
    val today = remember { Clock.System.now().toLocalDateTime(TZ).date }
    var month by remember { mutableStateOf(LocalDate(today.year, today.monthNumber, 1)) }
    val entries = remember(jobs) { AutomationCalendar.entries(jobs) }
    val byDate = remember(entries) {
        entries.groupBy { Instant.fromEpochMilliseconds(it.runAtMs).toLocalDateTime(TZ).date }
    }
    val cells = AutomationCalendar.monthGrid(month)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${monthName(month.monthNumber)} ${month.year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { month = LocalDate(today.year, today.month, 1) }) {
                Text(stringResource(Res.string.automations_today))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { month = month.minus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(Res.string.automations_prev_month))
            }
            IconButton(onClick = { month = month.plus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(Res.string.automations_next_month))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels().forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = date.month == month.month,
                        isToday = date == today,
                        entries = byDate[date].orEmpty(),
                        darkTheme = darkTheme,
                        modifier = Modifier.weight(1f),
                        onOpenDay = onOpenDay,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    entries: List<AutomationCalendar.Entry>,
    darkTheme: Boolean,
    modifier: Modifier,
    onOpenDay: (LocalDate, List<AutomationCalendar.Entry>) -> Unit,
) {
    val groups = entries.groupBy { it.job.id }.values.toList()
    Column(
        modifier
            .heightIn(min = 62.dp)
            .padding(2.dp)
            .background(
                if (inMonth) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f),
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = entries.isNotEmpty()) { onOpenDay(date, entries) }
            .padding(4.dp),
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                inMonth -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        // One colored bar per automation (not per run), like a calendar task row.
        groups.take(4).forEach { group ->
            val job = group.first().job
            val planned = group.any { it.planned }
            Box(
                Modifier
                    .padding(vertical = 1.dp)
                    .fillMaxWidth(0.72f)
                    .height(3.dp)
                    .background(
                        sessionHandleColor(job.id, darkTheme)
                            .copy(alpha = if (planned) 1f else 0.45f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
        if (groups.size > 4) {
            Text(
                "+${groups.size - 4}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CalendarEntryRow(entry: AutomationCalendar.Entry, onClick: () -> Unit) {
    val failed = entry.run?.status == "error"
    val statusColor = when {
        entry.planned -> MaterialTheme.colorScheme.primary
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = statusColor.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            "${formatTime(entry.runAtMs)} ${entry.job.name.ifBlank { entry.job.id }}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun AutomationDetailsDialog(
    job: AutomationJob,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.name.ifBlank { job.id }, modifier = Modifier.weight(1f))
                if (job.protected) Badge(stringResource(Res.string.automations_system), MaterialTheme.colorScheme.secondary)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow(stringResource(Res.string.automations_schedule), scheduleSummary(job))
                DetailRow(stringResource(Res.string.automations_status), statusText(job))
                DetailRow(stringResource(Res.string.automations_next), formatMs(job.state.nextRunAtMs))
                DetailRow(stringResource(Res.string.automations_last), formatMs(job.state.lastRunAtMs))
                job.state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    DetailRow(stringResource(Res.string.automations_error), error)
                }
                job.origin?.let { origin ->
                    val title = origin.title?.takeIf { it.isNotBlank() }
                        ?: origin.sessionKey?.takeIf { it.isNotBlank() }
                    if (title != null) DetailRow(stringResource(Res.string.automations_origin), title)
                }
                if (job.payload.message.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(Res.string.automations_message), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            job.payload.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
                if (job.state.runHistory.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(Res.string.automations_history), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    job.state.runHistory.reversed().forEach { run ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenRun(run.runAtMs) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                formatDateTime(run.runAtMs),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                runStatus(run),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (run.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (job.enabled && !job.protected) {
                    TextButton(onClick = onRun, enabled = !busy && job.schedule.kind != "local") {
                        Text(stringResource(Res.string.automations_run_now))
                    }
                }
                if (!job.protected) {
                    TextButton(onClick = onToggle, enabled = !busy) {
                        Text(stringResource(if (job.enabled) Res.string.automations_pause else Res.string.automations_resume))
                    }
                    TextButton(onClick = onEdit, enabled = !busy) {
                        Text(stringResource(Res.string.automations_edit))
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!job.protected) {
                    TextButton(onClick = onDelete, enabled = !busy) {
                        Text(stringResource(Res.string.automations_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EditAutomationDialog(
    job: AutomationJob,
    onDismiss: () -> Unit,
    onSave: (JsonObject) -> Unit,
) {
    var name by remember(job.id) { mutableStateOf(job.name) }
    var message by remember(job.id) { mutableStateOf(job.payload.message) }
    var kind by remember(job.id) { mutableStateOf(job.schedule.kind.ifBlank { "every" }) }
    var atText by remember(job.id) {
        mutableStateOf(job.schedule.atMs?.let { formatDateTimeInput(it) } ?: "")
    }
    var everyValue by remember(job.id) {
        mutableStateOf(((job.schedule.everyMs ?: 3_600_000) / 60_000).toString())
    }
    var everyUnit by remember(job.id) { mutableStateOf("minutes") }
    var cronExpr by remember(job.id) { mutableStateOf(job.schedule.expr.orEmpty()) }
    var cronTz by remember(job.id) { mutableStateOf(job.schedule.tz.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.automations_edit_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.mcp_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (job.schedule.kind != "local") {
                    OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text(stringResource(Res.string.automations_message)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                if (job.schedule.kind != "local") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("at", "every", "cron").forEach { option ->
                            FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option) })
                        }
                    }
                }
                when (kind) {
                    "at" -> OutlinedTextField(
                        value = atText,
                        onValueChange = { atText = it },
                        label = { Text("YYYY-MM-DD HH:MM") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "every" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = everyValue,
                            onValueChange = { everyValue = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(Res.string.automations_every)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.weight(1f)) { EveryUnitPicker(everyUnit) { everyUnit = it } }
                    }
                    "cron" -> {
                        OutlinedTextField(value = cronExpr, onValueChange = { cronExpr = it }, label = { Text(stringResource(Res.string.automations_cron)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = cronTz, onValueChange = { cronTz = it }, label = { Text(stringResource(Res.string.automations_timezone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val schedule = buildJsonObject {
                        put("kind", kind)
                        when (kind) {
                            "at" -> parseDateTimeInput(atText)?.let { put("at_ms", it) }
                            "every" -> put("every_ms", (everyValue.toLongOrNull() ?: 60) * unitMs(everyUnit))
                            "cron" -> {
                                put("expr", cronExpr.trim())
                                if (cronTz.isNotBlank()) put("tz", cronTz.trim())
                            }
                        }
                    }
                    val values = buildJsonObject {
                        put("name", name.trim())
                        if (job.schedule.kind != "local" && kind != "local") put("message", message.trim())
                        if (job.schedule.kind != "local") put("schedule", schedule)
                    }
                    onSave(values)
                },
                enabled = name.isNotBlank() && when (kind) {
                    "at" -> parseDateTimeInput(atText) != null
                    "every" -> (everyValue.toLongOrNull() ?: 0) > 0
                    "cron" -> cronExpr.isNotBlank()
                    else -> true
                },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun EveryUnitPicker(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(unitLabel(value), modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        listOf("minutes", "hours", "days").forEach { unit ->
            DropdownMenuItem(text = { Text(unitLabel(unit)) }, onClick = { onChange(unit); open = false })
        }
    }
}

@Composable
private fun unitLabel(unit: String): String = stringResource(
    when (unit) {
        "hours" -> Res.string.automations_unit_hours
        "days" -> Res.string.automations_unit_days
        else -> Res.string.automations_unit_minutes
    },
)

@Composable
private fun AutomationResultDialog(state: AppState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.automations_run_result)) },
        text = {
            when {
                state.automationResultLoading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.automationResultError != null -> Text(
                    state.automationResultError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.automationResultText.isNullOrBlank() -> Text(
                    stringResource(Res.string.automations_no_output),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    state.automationResultText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    )
}

// ── helpers ────────────────────────────────────────────────────────────────

@Composable
private fun scheduleSummary(job: AutomationJob): String {
    val schedule = job.schedule
    return when (schedule.kind) {
        "at" -> "${stringResource(Res.string.automations_schedule_at)} ${formatMs(schedule.atMs)}"
        "every" -> "${stringResource(Res.string.automations_schedule_every)} ${formatDuration(schedule.everyMs ?: 0)}"
        "cron" -> buildString {
            append("Cron ${schedule.expr.orEmpty()}")
            schedule.tz?.takeIf { it.isNotBlank() }?.let { append(" (${it})") }
        }
        "local" -> stringResource(Res.string.automations_local_trigger)
        else -> schedule.kind
    }
}

@Composable
private fun statusText(job: AutomationJob): String = when {
    job.state.pending -> stringResource(Res.string.automations_status_running)
    !job.enabled -> stringResource(Res.string.automations_status_paused)
    job.state.lastStatus == "error" -> stringResource(Res.string.automations_status_failed)
    else -> stringResource(Res.string.automations_status_active)
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes <= 0 -> "${ms / 1000}s"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes}m"
    }
}

private fun formatMs(ms: Long?): String =
    if (ms == null) "—" else formatDateTime(ms)

private fun formatDateTime(ms: Long): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TZ)
    return "${pad(dt.year, 4)}-${pad(dt.monthNumber, 2)}-${pad(dt.dayOfMonth, 2)} ${pad(dt.hour, 2)}:${pad(dt.minute, 2)}"
}

private fun formatDateTimeInput(ms: Long): String = formatDateTime(ms)

private fun formatTime(ms: Long): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TZ)
    return "${pad(dt.hour, 2)}:${pad(dt.minute, 2)}"
}

private fun formatDate(date: LocalDate): String =
    "${pad(date.dayOfMonth, 2)}.${pad(date.monthNumber, 2)}.${date.year}"

private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')

private fun parseDateTimeInput(text: String): Long? {
    var normalized = text.trim().replace(' ', 'T')
    if (normalized.length == 16) normalized += ":00"
    return runCatching {
        LocalDateTime.parse(normalized).toInstant(TZ).toEpochMilliseconds()
    }.getOrNull()
}

private fun unitMs(unit: String): Long = when (unit) {
    "hours" -> 3_600_000
    "days" -> 86_400_000
    else -> 60_000
}

@Composable
private fun monthName(month: Int): String = stringResource(
    when (month) {
        1 -> Res.string.month_january
        2 -> Res.string.month_february
        3 -> Res.string.month_march
        4 -> Res.string.month_april
        5 -> Res.string.month_may
        6 -> Res.string.month_june
        7 -> Res.string.month_july
        8 -> Res.string.month_august
        9 -> Res.string.month_september
        10 -> Res.string.month_october
        11 -> Res.string.month_november
        else -> Res.string.month_december
    },
)

@Composable
private fun weekdayLabels(): List<String> = listOf(
    stringResource(Res.string.weekday_mon),
    stringResource(Res.string.weekday_tue),
    stringResource(Res.string.weekday_wed),
    stringResource(Res.string.weekday_thu),
    stringResource(Res.string.weekday_fri),
    stringResource(Res.string.weekday_sat),
    stringResource(Res.string.weekday_sun),
)

private fun runStatus(run: AutomationRun): String = when (run.status) {
    "ok" -> "OK"
    "error" -> "Error"
    "skipped" -> "Skipped"
    else -> run.status
}
