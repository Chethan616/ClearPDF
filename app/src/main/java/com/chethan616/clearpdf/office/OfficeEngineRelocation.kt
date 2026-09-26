package com.chethan616.clearpdf.office

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.RandomAccessFile

/**
 * Makes a prebuilt engine that hard-codes another app's data directory usable from ClearPDF.
 *
 * LibreOffice's Android bootstrap bakes a few absolute paths into the library as string literals
 * whose lengths are compile-time constants, so they can only be replaced by a path of exactly
 * the same length. The replacement is `/proc/self/fd/<ALIAS_FD>/./././…`: a procfs link to a
 * directory descriptor that the `:office` process opens on the engine directory and pins to
 * [ALIAS_FD] before LibreOffice starts ([pinAlias]). Path walks through that link land in the
 * engine directory, so the patched literals resolve to our own files.
 *
 * Install time: [patchLibraries]. Load time (in `:office` only): [pinAlias].
 */
internal object OfficeEngineRelocation {

    /** High, fixed descriptor number; the literal is patched with it, so it cannot vary. */
    private const val ALIAS_FD = 1023

    fun aliasFor(needle: String): String {
        val base = "/proc/self/fd/$ALIAS_FD"
        val pad = needle.length - base.length
        require(pad >= 0 && pad % 2 == 0) { "Cannot build an alias of length ${needle.length}" }
        return base + "/.".repeat(pad / 2)
    }

    /** Rewrites every occurrence of [needle] in the .so files under [libDir]; returns the count. */
    fun patchLibraries(libDir: File, needle: String): Int {
        val from = needle.toByteArray(Charsets.US_ASCII)
        val to = aliasFor(needle).toByteArray(Charsets.US_ASCII)
        check(from.size == to.size)
        var total = 0
        libDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }?.forEach { lib ->
            total += patchFile(lib, from, to)
        }
        return total
    }

    private fun patchFile(file: File, from: ByteArray, to: ByteArray): Int {
        val offsets = ArrayList<Long>()
        val chunk = 1 shl 20
        val overlap = from.size - 1
        RandomAccessFile(file, "rw").use { raf ->
            val buf = ByteArray(chunk + overlap)
            var filePos = 0L
            var carried = 0
            while (true) {
                val n = raf.read(buf, carried, chunk)
                if (n <= 0) break
                val valid = carried + n
                var i = 0
                val limit = valid - from.size
                while (i <= limit) {
                    if (buf[i] == from[0] && matches(buf, i, from)) {
                        offsets += filePos - carried + i
                        i += from.size
                    } else {
                        i++
                    }
                }
                // Keep the tail so matches spanning chunk boundaries are found.
                val keep = minOf(overlap, valid)
                System.arraycopy(buf, valid - keep, buf, 0, keep)
                filePos += n
                carried = keep
            }
            // A match inside the carried tail could be found twice; offsets are de-duplicated.
            val unique = offsets.distinct()
            for (offset in unique) {
                raf.seek(offset)
                raf.write(to)
            }
            return unique.size
        }
    }

    private fun matches(buf: ByteArray, at: Int, needle: ByteArray): Boolean {
        for (k in needle.indices) if (buf[at + k] != needle[k]) return false
        return true
    }

    /**
     * Points [ALIAS_FD] at [engineDir] for the lifetime of this process. Must run in `:office`
     * before LibreOfficeKit initialises. Throws if the descriptor is taken by something else.
     */
    fun pinAlias(engineDir: File) {
        val link = File("/proc/self/fd/$ALIAS_FD")
        val existing = runCatching { Os.readlink(link.path) }.getOrNull()
        if (existing != null) {
            check(File(existing).canonicalPath == engineDir.canonicalPath) {
                "Descriptor $ALIAS_FD is already in use"
            }
            return
        }
        val fd = Os.open(engineDir.path, OsConstants.O_RDONLY, 0)
        // dup2 duplicates onto ALIAS_FD; the original descriptor stays open too, deliberately
        // never closed, as both live exactly as long as the process.
        Os.dup2(fd, ALIAS_FD)
    }
}
