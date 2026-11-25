package com.swipesort.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.swipesort.domain.FileItem
import com.swipesort.domain.FileKind
import com.swipesort.domain.FileQuery
import com.swipesort.domain.MediaQueryBuilder
import com.swipesort.domain.Names
import com.swipesort.domain.SortFolder
import com.swipesort.domain.TrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The single source of truth for files, SwipeSort folders and Recently deleted.
 *
 * - The main list comes from MediaStore.Files (fast, indexed, pageable, includes non-media files
 *   when "All files access" is granted).
 * - SwipeSort folders are real folders in  <storage>/SwipeSort/<name>/  so sorted files are
 *   ordinary files any app can open or share. Files there are left out of the main list, which
 *   therefore works like an inbox of not-yet-sorted things.
 * - Deleting moves to  <storage>/SwipeSort/.trash  (see [TrashStore]) so it can be undone.
 */
class FileRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    val root: File = File(Environment.getExternalStorageDirectory(), "SwipeSort")
    private val trash = TrashStore(File(root, ".trash"))
    private val resolver: ContentResolver get() = context.contentResolver

    private val _folders = MutableStateFlow<List<SortFolder>>(emptyList())
    val folders: StateFlow<List<SortFolder>> = _folders.asStateFlow()

    private val _trash = MutableStateFlow<List<TrashEntry>>(emptyList())
    val trashEntries: StateFlow<List<TrashEntry>> = _trash.asStateFlow()

    /** Bumped after anything that can change the main list (so it re-queries). */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private fun changed() = _version.update { it + 1 }

    // ---- Main list (MediaStore) ------------------------------------------------------------

    private val filesUri = MediaStore.Files.getContentUri("external")
    private val projection = arrayOf(
        MediaQueryBuilder.COL_ID, MediaQueryBuilder.COL_DATA, MediaQueryBuilder.COL_NAME,
        MediaQueryBuilder.COL_SIZE, MediaQueryBuilder.COL_MODIFIED, MediaQueryBuilder.COL_MIME,
    )

    /** One page of the main list. Called by [FilesPagingSource] on a background thread. */
    fun queryPage(q: FileQuery, offset: Int, limit: Int): List<FileItem> {
        val b = MediaQueryBuilder.build(q, root.absolutePath)
        val cursor: Cursor? = if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+: LIMIT/OFFSET must go through query arguments.
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, b.selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, b.args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, b.orderBy)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(filesUri, projection, args, null)
        } else {
            // Android 8–10: the classic trick of appending LIMIT/OFFSET to the sort order.
            resolver.query(filesUri, projection, b.selection, b.args, "${b.orderBy} LIMIT $limit OFFSET $offset")
        }
        val out = ArrayList<FileItem>(limit)
        cursor?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaQueryBuilder.COL_ID)
            val iData = c.getColumnIndexOrThrow(MediaQueryBuilder.COL_DATA)
            val iName = c.getColumnIndexOrThrow(MediaQueryBuilder.COL_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaQueryBuilder.COL_SIZE)
            val iMod = c.getColumnIndexOrThrow(MediaQueryBuilder.COL_MODIFIED)
            val iMime = c.getColumnIndexOrThrow(MediaQueryBuilder.COL_MIME)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                val name = c.getString(iName) ?: File(path).name
                val mime = c.getString(iMime)
                out += FileItem(
                    id = c.getLong(iId), path = path, name = name, mime = mime,
                    size = c.getLong(iSize), modified = c.getLong(iMod) * 1000L,
                    kind = FileKind.from(mime, name),
                )
            }
        }
        return out
    }

    // ---- Folders ---------------------------------------------------------------------------

    suspend fun refreshFolders() = withContext(Dispatchers.IO) {
        root.mkdirs()
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
        _folders.value = dirs.map { d ->
            val files = d.listFiles { f -> f.isFile && !f.name.startsWith(".") }.orEmpty()
            val cover = files.filter { FileKind.from(mimeOf(it), it.name).isVisual }.maxByOrNull { it.lastModified() }
            SortFolder(d.name, d.absolutePath, files.size, files.sumOf { it.length() }, cover?.absolutePath)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun createFolder(name: String): SortFolder = withContext(Dispatchers.IO) {
        val error = Names.validateFolderName(name, _folders.value.map { it.name })
        if (error != null) throw IllegalArgumentException(error)
        val dir = File(root, name.trim())
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Couldn't create the folder")
        refreshFolders()
        _folders.value.first { it.path == dir.absolutePath }
    }

    suspend fun renameFolder(folder: SortFolder, newName: String) = withContext(Dispatchers.IO) {
        val others = _folders.value.filter { it.path != folder.path }.map { it.name }
        Names.validateFolderName(newName, others)?.let { throw IllegalArgumentException(it) }
        val from = File(folder.path)
        val to = File(root, newName.trim())
        val oldPaths = from.listFiles().orEmpty().map { it.absolutePath }
        if (!from.renameTo(to)) throw IOException("Couldn't rename the folder")
        oldPaths.forEach { forget(it) }
        scan(to.listFiles().orEmpty().map { it.absolutePath })
        refreshFolders()
    }

    /** Deletes a folder; its files go to Recently deleted. Returns how many files were moved there. */
    suspend fun deleteFolder(folder: SortFolder): Int = withContext(Dispatchers.IO) {
        val dir = File(folder.path)
        val files = dir.listFiles { f -> f.isFile }.orEmpty()
        files.forEach { trash.trash(it, mimeOf(it)); forget(it.absolutePath) }
        dir.deleteRecursively()
        refreshFolders(); refreshTrash()
        files.size
    }

    /** Files inside one SwipeSort folder, newest first. */
    suspend fun listFolder(folder: SortFolder): List<FileItem> = withContext(Dispatchers.IO) {
        File(folder.path).listFiles { f -> f.isFile && !f.name.startsWith(".") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { it.toItem() }
    }

    // ---- Move / copy -----------------------------------------------------------------------

    /**
     * Moves (or copies) [path] into [folder]. Returns the new file. Reports 0..1 progress — instant
     * on the same storage, byte-by-byte when copying or crossing to another volume.
     */
    suspend fun moveToFolder(path: String, folder: SortFolder, copy: Boolean, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val src = File(path)
            if (!src.exists()) throw IOException("${src.name} no longer exists")
            val dir = File(folder.path).apply { mkdirs() }
            val dest = File(dir, Names.uniqueName(src.name) { File(dir, it).exists() })
            if (copy) FileOps.copy(src, dest, onProgress) else FileOps.move(src, dest, onProgress)
            if (!copy) forget(src.absolutePath)
            scan(listOf(dest.absolutePath))
            refreshFolders()
            changed()
            dest
        }

    /** Undo for a move: puts the file back where it came from. */
    suspend fun moveBack(current: File, originalPath: String): File = withContext(Dispatchers.IO) {
        val original = File(originalPath)
        val parent = original.parentFile ?: throw IOException("Unknown original location")
        val dest = File(parent, Names.uniqueName(original.name) { File(parent, it).exists() })
        FileOps.move(current, dest) {}
        forget(current.absolutePath)
        scan(listOf(dest.absolutePath))
        refreshFolders()
        changed()
        dest
    }

    /** Undo for a copy: removes the copy that was just made. */
    suspend fun discardCopy(copyPath: String) = withContext(Dispatchers.IO) {
        File(copyPath).delete()
        forget(copyPath)
        refreshFolders()
    }

    // ---- Recently deleted -----------------------------------------------------------------

    suspend fun refreshTrash() = withContext(Dispatchers.IO) { _trash.value = trash.list() }

    suspend fun trashFile(path: String, mime: String?): TrashEntry = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.exists()) throw IOException("${f.name} no longer exists")
        val entry = trash.trash(f, mime ?: mimeOf(f))
        forget(path)
        refreshTrash()
        if (path.startsWith(root.absolutePath)) refreshFolders()
        changed()
        entry
    }

    suspend fun restore(entry: TrashEntry): File = withContext(Dispatchers.IO) {
        val f = trash.restore(entry)
        scan(listOf(f.absolutePath))
        refreshTrash()
        if (f.absolutePath.startsWith(root.absolutePath)) refreshFolders()
        changed()
        f
    }

    suspend fun deleteForever(entry: TrashEntry) = withContext(Dispatchers.IO) {
        trash.deleteForever(entry); refreshTrash()
    }

    suspend fun emptyTrash(): Int = withContext(Dispatchers.IO) {
        val n = trash.empty(); refreshTrash(); n
    }

    suspend fun startup() = withContext(Dispatchers.IO) {
        runCatching { trash.purgeOld() }
        refreshFolders()
        refreshTrash()
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /**
     * Keeps MediaStore in sync. On Android 11+ the system notices file moves by itself; on older
     * versions we drop the stale row and ask the media scanner to index the new location.
     */
    private fun forget(oldPath: String) {
        if (Build.VERSION.SDK_INT < 30) {
            runCatching { resolver.delete(filesUri, "${MediaQueryBuilder.COL_DATA} = ?", arrayOf(oldPath)) }
        }
    }

    private fun scan(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    fun mimeOf(f: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())

    private fun File.toItem(): FileItem {
        val mime = mimeOf(this)
        return FileItem(
            id = absolutePath.hashCode().toLong(), path = absolutePath, name = name, mime = mime,
            size = length(), modified = lastModified(), kind = FileKind.from(mime, name),
        )
    }
}
