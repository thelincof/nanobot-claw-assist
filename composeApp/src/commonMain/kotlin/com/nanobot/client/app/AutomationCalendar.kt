package com.nanobot.client.app

import com.nanobot.client.core.model.AutomationJob
import com.nanobot.client.core.model.AutomationRun
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Pure calendar projection for cron jobs (planned + recorded entries). */
object AutomationCalendar {
    data class Entry(
        val job: AutomationJob,
        val runAtMs: Long,
        val planned: Boolean,
        val run: AutomationRun?,
    )

    /** Mirrors the web UI: recorded runs plus one planned entry for enabled jobs. */
    fun entries(jobs: List<AutomationJob>): List<Entry> = jobs.flatMap { job ->
        val history = job.state.runHistory
        val plannedAt = if (job.enabled) job.state.nextRunAtMs else null
        val recorded = if (history.isNotEmpty() || job.state.lastRunAtMs == null || plannedAt != null) {
            history
        } else {
            job.state.lastRunAtMs?.let {
                listOf(AutomationRun(it, job.state.lastStatus ?: "unknown", null, job.state.lastError))
            }.orEmpty()
        }
        buildList {
            recorded.forEach { run -> add(Entry(job, run.runAtMs, false, run)) }
            plannedAt?.let { add(Entry(job, it, true, null)) }
        }
    }

    /** Weeks start on Monday; returns 35 or 42 cells covering the whole month. */
    fun monthGrid(month: LocalDate): List<LocalDate> {
        val offset = month.dayOfWeek.isoDayNumber - 1
        val daysInMonth = month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        val cellCount = if (offset + daysInMonth > 35) 42 else 35
        val gridStart = month.minus(offset, DateTimeUnit.DAY)
        return (0 until cellCount).map { gridStart.plus(it, DateTimeUnit.DAY) }
    }
}
