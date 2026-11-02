package com.nanobot.client.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Desktop Vosk model provider.
 *
 * Resolution order (no network involved):
 * 1. An already-extracted model in well-known locations (or next to the app).
 * 2. Unpack the zip bundled with the application into ~/.cache/vosk.
 */
actual object VoskModelProvider {
    private const val MODEL_ZIP = "vosk-model-small-ru-0.22.zip"
    private const val MODEL_DIR = "vosk-model-small-ru-0.22"

    private val mutex = Mutex()
    private var cachedDir: File? = null

    private fun isModelDir(dir: File): Boolean =
        dir.isDirectory && (File(dir, "conf").isDirectory || File(dir, "am").isDirectory)

    /** Top-level dirs that may themselves contain the model dir (one level of nesting allowed). */
    private fun searchRoots(): List<File> = listOf(
        File(System.getProperty("user.home"), ".cache/vosk"),
        File(System.getProperty("user.home"), "clawd/models"),
        File(System.getProperty("user.dir"), "models"),
        File(System.getProperty("user.dir")),
        File(System.getProperty("compose.application.resources.dir"), ".."),
    ).filter { it.isDirectory }

    private fun findExisting(): File? {
        for (root in searchRoots()) {
            val direct = File(root, MODEL_DIR)
            if (isModelDir(direct)) return direct
            val nested = direct.listFiles()
                ?.firstOrNull { it.isDirectory && isModelDir(it) }
            if (nested != null) return nested
        }
        return null
    }

    actual suspend fun getModelDir(): File? = mutex.withLock {
        cachedDir?.let { if (isModelDir(it)) return it }
        cachedDir = null

        findExisting()?.let {
            cachedDir = it
            return it
        }

        withContext(Dispatchers.IO) {
            val targetDir = File(File(System.getProperty("user.home"), ".cache/vosk"), MODEL_DIR)
            try {
                val zipStream = javaClass.getResourceAsStream("/$MODEL_ZIP")
                    ?: javaClass.getResourceAsStream("/audio/$MODEL_ZIP")
                    ?: return@withContext null
                targetDir.mkdirs()
                zipStream.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            // Zip entries may be nested under the model dir name; strip it.
                            val cleanName = entry.name.removePrefix("$MODEL_DIR/").removePrefix("$MODEL_DIR\\")
                            if (cleanName.isBlank()) {
                                zis.closeEntry()
                                entry = zis.nextEntry
                                continue
                            }
                            val outFile = File(targetDir, cleanName)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                if (isModelDir(targetDir)) {
                    cachedDir = targetDir
                    targetDir
                } else {
                    targetDir.listFiles()
                        ?.firstOrNull { it.isDirectory && isModelDir(it) }
                        ?.also { cachedDir = it }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
