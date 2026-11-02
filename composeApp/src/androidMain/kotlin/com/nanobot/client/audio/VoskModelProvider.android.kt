package com.nanobot.client.audio

import android.util.Log
import com.nanobot.client.platform.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

actual object VoskModelProvider {
    private const val TAG = "VoskModelProvider"
    private const val MODEL_ZIP = "vosk-model-small-ru-0.22.zip"
    private const val MODEL_DIR = "vosk-model-small-ru-0.22"

    private val mutex = Mutex()
    private var cachedDir: File? = null

    actual suspend fun getModelDir(): File? = mutex.withLock {
        cachedDir?.let { if (it.exists()) return it }

        withContext(Dispatchers.IO) {
            val context = AppContext.context
            val targetDir = File(context.filesDir, MODEL_DIR)

            // If already extracted, return it
            if (File(targetDir, "conf").isDirectory || File(targetDir, "am").isDirectory) {
                cachedDir = targetDir
                return@withContext targetDir
            }

            // Check if extracted with subfolder
            targetDir.walkTopDown().maxDepth(2)
                .firstOrNull { it.isDirectory && (File(it, "conf").isDirectory || File(it, "am").isDirectory) }
                ?.let {
                    cachedDir = it
                    return@withContext it
                }

            Log.i(TAG, "Unpacking Vosk model from assets...")
            targetDir.mkdirs()

            try {
                context.assets.open(MODEL_ZIP).use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val cleanName = entry.name.removePrefix("$MODEL_DIR/")
                            val outFile = File(targetDir, cleanName)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                Log.i(TAG, "Vosk model unpacked successfully to ${targetDir.absolutePath}")
                cachedDir = targetDir
                targetDir
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unpack Vosk model", e)
                null
            }
        }
    }
}
