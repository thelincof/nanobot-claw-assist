package com.nanobot.client

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Keeps a single desktop instance. A second launch (e.g. a hotkey bound to
 * `clawd --assistant`) forwards the assistant trigger to the running window.
 */
object DesktopSingleInstance {
    private val dir: File = File(System.getProperty("user.home"), ".clawd").apply { mkdirs() }
    private val lockFile = File(dir, "instance.lock")
    private val triggerFile = File(dir, "assistant.trigger")

    private var lockHandle: RandomAccessFile? = null
    private var lock: FileLock? = null

    /** @return true when this process owns the desktop instance. */
    fun acquire(): Boolean {
        return try {
            val handle = RandomAccessFile(lockFile, "rw")
            val acquired = handle.channel.tryLock()
            if (acquired == null) {
                handle.close()
                false
            } else {
                lockHandle = handle
                lock = acquired
                true
            }
        } catch (_: OverlappingFileLockException) {
            false
        } catch (_: Exception) {
            // If locking is unavailable, run normally rather than refusing to start.
            true
        }
    }

    fun forwardAssistantTrigger() {
        runCatching { triggerFile.writeText("${System.currentTimeMillis()}-${System.nanoTime()}") }
    }

    fun watchForAssistantTriggers(onTrigger: () -> Unit) {
        var last = if (triggerFile.exists()) runCatching { triggerFile.readText() }.getOrNull() else null
        Thread {
            while (true) {
                try {
                    Thread.sleep(500)
                    val current = if (triggerFile.exists()) runCatching { triggerFile.readText() }.getOrNull() else null
                    if (current != null && current != last) {
                        last = current
                        onTrigger()
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Exception) {
                    // keep watching
                }
            }
        }.apply {
            isDaemon = true
            name = "clawd-assistant-trigger"
        }.start()
    }
}
