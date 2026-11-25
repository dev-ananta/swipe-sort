package com.swipesort.domain

import java.util.Locale

/**
 * Builds the SQL WHERE / ORDER BY for MediaStore.Files.
 * Column names are written out literally (they are MediaStore's stable column names) so this
 * stays plain Kotlin and can be tested off-device.
 */
object MediaQueryBuilder {
    const val COL_ID = "_id"
    const val COL_DATA = "_data"
    const val COL_NAME = "_display_name"
    const val COL_SIZE = "_size"
    const val COL_MODIFIED = "date_modified"
    const val COL_MIME = "mime_type"
    const val COL_MEDIA_TYPE = "media_type"

    // MediaStore.Files.FileColumns.MEDIA_TYPE_* values
    private const val TYPE_IMAGE = 1
    private const val TYPE_AUDIO = 2
    private const val TYPE_VIDEO = 3

    data class Built(val selection: String, val args: Array<String>, val orderBy: String)

    /**
     * @param excludeDir files already sorted into SwipeSort folders live here; they're hidden
     *                   from the main list so it works like an inbox.
     */
    fun build(q: FileQuery, excludeDir: String): Built {
        val where = ArrayList<String>()
        val args = ArrayList<String>()

        // Real files only (folders have no MIME type), and nothing empty.
        where += "$COL_MIME IS NOT NULL"
        where += "$COL_SIZE > 0"
        where += "$COL_DATA NOT LIKE ? ESCAPE '\\'"
        args += escapeLike(excludeDir.trimEnd('/')) + "/%"

        when (q.filter) {
            FileFilter.ALL -> {}
            FileFilter.IMAGES -> where += "$COL_MEDIA_TYPE = $TYPE_IMAGE"
            FileFilter.GIFS -> where += "$COL_MIME = 'image/gif'"
            FileFilter.VIDEOS -> where += "$COL_MEDIA_TYPE = $TYPE_VIDEO"
            FileFilter.AUDIO -> where += "$COL_MEDIA_TYPE = $TYPE_AUDIO"
            FileFilter.DOCUMENTS -> where += "$COL_MEDIA_TYPE NOT IN ($TYPE_IMAGE, $TYPE_AUDIO, $TYPE_VIDEO)"
        }

        val s = q.search.trim()
        if (s.isNotEmpty()) {
            where += "$COL_NAME LIKE ? ESCAPE '\\'"
            args += "%" + escapeLike(s) + "%"
        }

        val order = when (q.sort) {
            SortOrder.NEWEST -> "$COL_MODIFIED DESC, $COL_ID DESC"
            SortOrder.OLDEST -> "$COL_MODIFIED ASC, $COL_ID ASC"
            SortOrder.NAME -> "$COL_NAME COLLATE NOCASE ASC, $COL_ID ASC"
            SortOrder.LARGEST -> "$COL_SIZE DESC, $COL_ID DESC"
        }
        return Built(where.joinToString(" AND "), args.toTypedArray(), order)
    }

    /** Escapes % _ and \ so user text is matched literally inside LIKE. */
    fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

object Names {
    private val illegal = Regex("[/\\\\:*?\"<>|\\u0000]")

    /** Returns an error message, or null if [name] is a usable folder name. */
    fun validateFolderName(name: String, existing: Collection<String>): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Enter a name"
            n.length > 60 -> "Keep it under 60 characters"
            n.startsWith(".") -> "Names can't start with a dot"
            illegal.containsMatchIn(n) -> "Names can't contain / \\ : * ? \" < > |"
            existing.any { it.equals(n, ignoreCase = true) } -> "A folder called \"$n\" already exists"
            else -> null
        }
    }

    /** "photo.jpg" → "photo (2).jpg" → "photo (3).jpg"… until [taken] says it's free. */
    fun uniqueName(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var i = 2
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!taken(candidate)) return candidate
            i++
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes / 1024.0
        var u = 0
        while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
        return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[u]) else String.format(Locale.US, "%.1f %s", v, units[u])
    }
}
