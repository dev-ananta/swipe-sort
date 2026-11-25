package com.swipesort.domain

/**
 * Text format for the Recently-deleted index: one entry per line, tab-separated fields,
 * with \t, \n, \r and \ escaped so any file name round-trips safely.
 */
object TrashIndexCodec {
    private const val VERSION = "swipesort-trash-v1"

    fun encode(entries: List<TrashEntry>): String = buildString {
        append(VERSION).append('\n')
        for (e in entries) {
            append(listOf(e.trashedPath, e.originalPath, e.name, e.size.toString(), e.mime ?: "", e.deletedAt.toString())
                .joinToString("\t") { esc(it) })
            append('\n')
        }
    }

    fun decode(text: String): List<TrashEntry> {
        val lines = text.split('\n')
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { line ->
            val f = line.split('\t').map { unesc(it) }
            if (f.size < 6) return@mapNotNull null
            TrashEntry(
                trashedPath = f[0], originalPath = f[1], name = f[2],
                size = f[3].toLongOrNull() ?: 0L, mime = f[4].ifEmpty { null },
                deletedAt = f[5].toLongOrNull() ?: 0L,
            )
        }
    }

    private fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\"); '\t' -> append("\\t"); '\n' -> append("\\n"); '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun unesc(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 't' -> append('\t'); 'n' -> append('\n'); 'r' -> append('\r'); else -> append(s[i + 1]) }
                i += 2
            } else { append(c); i++ }
        }
    }
}
