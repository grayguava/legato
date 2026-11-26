package com.grayguava.legato.base

import java.io.File

/**
 * A file kept whole: written to a temporary file and renamed over, so a save
 * cut short never leaves half a file, one save at a time. A file that's there
 * but won't read (damaged, or from a version this one can't read) is set aside
 * rather than written over with an empty one at the next save.
 */
public class SavedFile(public val file: File, private val tag: String) {
    private val lock = Any()

    /** Whether something changed that isn't on disk yet; see [flushIfDirty]. */
    @Volatile public var dirty: Boolean = false

    public fun <T> read(empty: T, decode: (String) -> T): T {
        if (!file.exists()) return empty
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            val aside = File(file.parentFile, "${file.name}.broken-${System.currentTimeMillis()}")
            val kept = runCatching { file.renameTo(aside) }.getOrDefault(false)
            DebugLog.w(tag, "Couldn't read ${file.name} (${e.javaClass.simpleName}); ${if (kept) "kept it as ${aside.name}" else "left it"} and started empty")
            empty
        }
    }

    public fun write(text: String): Unit = synchronized(lock) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        dirty = false
    }

    /** Writes [text] now if a change is still waiting for its save. */
    public fun flushIfDirty(text: () -> String) {
        if (!dirty) return
        runCatching { write(text()) }.onFailure { DebugLog.w(tag, "Couldn't save ${file.name}: ${it.message}") }
    }
}
