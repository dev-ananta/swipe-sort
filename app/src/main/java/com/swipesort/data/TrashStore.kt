package com.swipesort.data

import com.swipesort.domain.Names
import com.swipesort.domain.TrashEntry
import com.swipesort.domain.TrashIndexCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * "Recently deleted". A left swipe doesn't destroy a file right away — it's moved into
 * SwipeSort/.trash (same storage volume, so the move is an instant rename) and listed here.
 * That makes Undo instant and lets the user restore files later. The folder has a .nomedia
 * marker so trashed photos don't show up in gallery apps. Items older than 30 days are purged.
 */
class TrashStore(private val dir: File) {
    private val index = File(dir, ".index")
    private val lock = Mutex()

    companion object {
        const val KEEP_DAYS = 30
    }

    private fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
    }

    private fun read(): MutableList<TrashEntry> =
        if (index.exists()) TrashIndexCodec.decode(index.readText()).toMutableList() else mutableListOf()

    private fun write(list: List<TrashEntry>) {
        ensureDir()
        val tmp = File(dir, ".index.tmp")
        tmp.writeText(TrashIndexCodec.encode(list))
        if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
    }

    /** Entries whose files still exist, newest first. */
    suspend fun list(): List<TrashEntry> = lock.withLock {
        read().filter { File(it.trashedPath).exists() }.sortedByDescending { it.deletedAt }
    }

    suspend fun trash(file: File, mime: String?): TrashEntry = lock.withLock {
        ensureDir()
        val dest = File(dir, "${System.currentTimeMillis()}_${file.name}")
        val size = file.length()
        FileOps.move(file, dest) {}
        val entry = TrashEntry(dest.absolutePath, file.absolutePath, file.name, size, mime, System.currentTimeMillis())
        write(read() + entry)
        entry
    }

    /** Puts the file back where it was (or next to it, if that name is taken now). */
    suspend fun restore(entry: TrashEntry): File = lock.withLock {
        val original = File(entry.originalPath)
        val parent = original.parentFile ?: error("Unknown original location")
        parent.mkdirs()
        val target = File(parent, Names.uniqueName(original.name) { File(parent, it).exists() })
        FileOps.move(File(entry.trashedPath), target) {}
        write(read().filterNot { it.trashedPath == entry.trashedPath })
        target
    }

    suspend fun deleteForever(entry: TrashEntry) = lock.withLock {
        File(entry.trashedPath).delete()
        write(read().filterNot { it.trashedPath == entry.trashedPath })
    }

    suspend fun empty(): Int = lock.withLock {
        val all = read()
        all.forEach { File(it.trashedPath).delete() }
        write(emptyList())
        all.size
    }

    /** Permanently removes items older than [KEEP_DAYS]. */
    suspend fun purgeOld() = lock.withLock {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600 * 1000
        val (old, keep) = read().partition { it.deletedAt < cutoff || !File(it.trashedPath).exists() }
        if (old.isEmpty()) return@withLock
        old.forEach { File(it.trashedPath).delete() }
        write(keep)
    }
}
