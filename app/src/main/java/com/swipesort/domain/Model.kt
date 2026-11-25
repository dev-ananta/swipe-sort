package com.swipesort.domain

/**
 * Pure domain types and rules — no Android imports, so they're unit-testable on any JVM.
 */

/** What kind of file something is, for icons, filters and badges. */
enum class FileKind { IMAGE, GIF, VIDEO, AUDIO, PDF, TEXT, ARCHIVE, APK, DOCUMENT, OTHER;

    val isVisual: Boolean get() = this == IMAGE || this == GIF || this == VIDEO

    companion object {
        private val textExt = setOf("txt", "md", "csv", "json", "xml", "log", "html", "htm", "rtf")
        private val docExt = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "pages", "numbers", "key", "epub")
        private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

        fun from(mime: String?, name: String): FileKind {
            val m = mime?.lowercase().orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                m == "image/gif" || ext == "gif" -> GIF
                m.startsWith("image/") -> IMAGE
                m.startsWith("video/") -> VIDEO
                m.startsWith("audio/") -> AUDIO
                m == "application/pdf" || ext == "pdf" -> PDF
                m == "application/vnd.android.package-archive" || ext == "apk" -> APK
                ext in archiveExt || m.contains("zip") || m.contains("compressed") -> ARCHIVE
                m.startsWith("text/") || ext in textExt -> TEXT
                ext in docExt || m.contains("officedocument") || m.contains("msword") || m.contains("opendocument") -> DOCUMENT
                ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp") -> IMAGE
                ext in setOf("mp4", "mkv", "mov", "webm", "3gp", "avi") -> VIDEO
                ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus") -> AUDIO
                else -> OTHER
            }
        }
    }
}

/** One file shown in a list. [id] is the MediaStore row id (or a hash of the path for plain files). */
data class FileItem(
    val id: Long,
    val path: String,
    val name: String,
    val mime: String?,
    val size: Long,
    /** Last modified, epoch millis. */
    val modified: Long,
    val kind: FileKind,
)

/** A collection the user built inside SwipeSort (a real folder under SwipeSort/ on the device). */
data class SortFolder(
    val name: String,
    val path: String,
    val fileCount: Int,
    val totalBytes: Long,
    /** A visual file to use as the folder's cover, if it has one. */
    val coverPath: String?,
)

/** A file in Recently deleted, with where it came from so it can be restored. */
data class TrashEntry(
    val trashedPath: String,
    val originalPath: String,
    val name: String,
    val size: Long,
    val mime: String?,
    val deletedAt: Long,
) {
    val kind: FileKind get() = FileKind.from(mime, name)
}

enum class FileFilter(val label: String) {
    ALL("All"), IMAGES("Images"), GIFS("GIFs"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents")
}

enum class SortOrder(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name A–Z"), LARGEST("Largest first")
}

data class FileQuery(
    val filter: FileFilter = FileFilter.ALL,
    val search: String = "",
    val sort: SortOrder = SortOrder.NEWEST,
)
