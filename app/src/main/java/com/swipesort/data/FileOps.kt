package com.swipesort.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/** Low-level file moves/copies with progress. Always call from a background dispatcher. */
object FileOps {

    /**
     * Moves [src] to [dest]. On the same storage volume this is an instant rename; across volumes
     * (e.g. SD card → phone) it falls back to copy-then-delete with progress.
     */
    suspend fun move(src: File, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        if (src.renameTo(dest)) { onProgress(1f); return }
        copy(src, dest, onProgress)
        if (!src.delete()) {
            dest.delete()
            throw IOException("Couldn't remove the original after copying ${src.name}")
        }
    }

    /** Copies [src] to [dest], reporting 0..1 progress, keeping the original's date. */
    suspend fun copy(src: File, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val total = src.length().coerceAtLeast(1)
        var done = 0L
        var lastReport = -1
        try {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastReport) { lastReport = pct; onProgress(done.toFloat() / total) }
                    }
                }
            }
        } catch (e: Throwable) {
            dest.delete() // never leave half-copied files behind
            throw e
        }
        dest.setLastModified(src.lastModified())
        onProgress(1f)
    }
}
