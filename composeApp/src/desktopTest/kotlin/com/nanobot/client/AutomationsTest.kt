package com.nanobot.client

import com.nanobot.client.app.AutomationCalendar
import com.nanobot.client.core.model.AutomationJob
import com.nanobot.client.core.model.AutomationRun
import com.nanobot.client.core.model.AutomationSchedule
import com.nanobot.client.core.model.AutomationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class AutomationCalendarTest {
    private fun job(
        id: String,
        enabled: Boolean,
        next: Long?,
        last: Long?,
        history: List<AutomationRun> = emptyList(),
        status: String? = "ok",
    ) = AutomationJob(
        id = id,
        name = id,
        enabled = enabled,
        schedule = AutomationSchedule(kind = "every", everyMs = 60_000),
        state = AutomationState(
            nextRunAtMs = next,
            lastRunAtMs = last,
            lastStatus = status,
            runHistory = history,
        ),
    )

    @Test
    fun buildsPlannedAndRecordedEntries() {
        val jobs = listOf(
            job("a", true, 1_000, 500, listOf(AutomationRun(500, "ok"))),
            job("b", false, null, 700),
            job("c", true, 1_200, 800),
        )
        val entries = AutomationCalendar.entries(jobs)
        assertEquals(1, entries.count { it.job.id == "a" && !it.planned })
        assertEquals(1, entries.count { it.job.id == "a" && it.planned })
        // Disabled job without history falls back to the last run.
        assertEquals(1, entries.count { it.job.id == "b" && !it.planned })
        // Enabled job with a planned run but no history records no synthetic "last".
        assertEquals(1, entries.count { it.job.id == "c" && it.planned })
        assertEquals(4, entries.size)
    }

    @Test
    fun monthGridStartsOnMonday() {
        val grid = AutomationCalendar.monthGrid(LocalDate(2026, 11, 1))
        assertEquals(42, grid.size)
        assertEquals(LocalDate(2026, 10, 26), grid.first())
        assertTrue(grid.contains(LocalDate(2026, 11, 30)))
    }
}

class LivePayloadParsingTest {
    @Test
    fun parsesLiveAutomationsAndMcp() {
        val automationsPath = System.getenv("NANOBOT_AUTOMATIONS_JSON") ?: return
        val jobs = com.nanobot.client.core.model.GatewayJson.decodeFromString(
            com.nanobot.client.core.model.AutomationsPayload.serializer(),
            java.io.File(automationsPath).readText(),
        ).jobs
        assertTrue(jobs.isNotEmpty())
        assertTrue(jobs.all { it.id.isNotBlank() })
        assertTrue(jobs.any { it.schedule.kind == "every" })
        val entries = AutomationCalendar.entries(jobs)
        assertTrue(entries.any { it.planned })
        assertTrue(entries.any { !it.planned })

        val mcpPath = System.getenv("NANOBOT_MCP_JSON") ?: return
        val presets = com.nanobot.client.core.model.GatewayJson.decodeFromString(
            com.nanobot.client.core.model.McpPresetsPayload.serializer(),
            java.io.File(mcpPath).readText(),
        ).presets
        assertTrue(presets.isNotEmpty())
        assertTrue(presets.any { it.requiredFields.isNotEmpty() })
        assertTrue(presets.any { it.auth == "oauth" })
    }
}

class LiveReadApiTest {
    @Test
    fun readsSettingsMcpAndAutomations() = kotlinx.coroutines.runBlocking {
        if (System.getenv("NANOBOT_LIVE") != "1") return@runBlocking
        val base = System.getenv("NANOBOT_BASE") ?: error("NANOBOT_BASE is required")
        val secret = System.getenv("NANOBOT_SECRET") ?: error("NANOBOT_SECRET is required")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val boot = com.nanobot.client.core.gateway.BootstrapClient(client).fetch(base, secret)
        val token = boot.apiToken ?: boot.token
        val api = com.nanobot.client.core.gateway.GatewayApi(
            client = client,
            baseUrlProvider = { base },
            tokenProvider = { token },
            refreshToken = { token },
        )
        val settings = api.settings()
        assertTrue((settings.runtimeConfig?.size ?: 0) >= 30)
        val mcp = api.mcpPresets()
        assertTrue(mcp.presets.isNotEmpty())
        val automations = api.automations()
        assertTrue(automations.jobs.isNotEmpty())
        val job = automations.jobs.firstOrNull { !it.protected && it.state.runHistory.isNotEmpty() }
        if (job != null) {
            val run = job.state.runHistory.last()
            val kind = if (job.schedule.kind == "local") "local_trigger" else "cron"
            api.automationResult(job.id, run.runAtMs, kind)
        }
    }
}
