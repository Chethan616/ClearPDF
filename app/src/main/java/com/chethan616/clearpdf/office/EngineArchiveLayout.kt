package com.chethan616.clearpdf.office

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Where each entry of an engine bundle lands inside an engine directory. Both installers use it:
 *
 *   `<abi>/lib*.so`       -> `lib/`        (LO_LIB_DIR = $APP_DATA_DIR/lib)
 *   `program/…`, `share/…` -> same path    (bootstrap rc files, registry, UI configs)
 *   `unpack/…`            -> engine root   (etc/fonts, user/fonts, program/sofficerc, types)
 *
 * Any other ABI directory or unknown entry is ignored. Every destination is checked to stay
 * inside the engine directory (zip-slip).
 */
internal object EngineArchiveLayout {

    fun destinationFor(root: File, abi: String, entryName: String): File? {
        val rel = entryName.replace('\\', '/').trimStart('.', '/')
        if (rel.isEmpty() || rel.endsWith("/")) return null
        val mapped = when {
            rel.startsWith("$abi/") -> "lib/" + rel.removePrefix("$abi/")
            rel.startsWith("program/") || rel.startsWith("share/") -> rel
            rel.startsWith("unpack/") -> rel.removePrefix("unpack/")
            else -> return null
        }
        val target = File(root, mapped)
        val rootPath = root.canonicalPath + File.separator
        if (!target.canonicalPath.startsWith(rootPath)) throw IOException("Unsafe archive entry: $entryName")
        return target
    }

    /**
     * Streams a (decompressed) ustar/GNU tar archive into [root]. Supports regular files and GNU
     * long names, which is all the engine bundles use. Returns the number of files written.
     */
    fun extractTar(input: InputStream, root: File, abi: String, isCancelled: () -> Boolean = { false }): Int {
        val header = ByteArray(512)
        var longName: String? = null
        var files = 0
        while (readFully(input, header) && header.any { it.toInt() != 0 }) {
            if (isCancelled()) throw java.util.concurrent.CancellationException()
            val name = longName ?: cString(header, 0, 100)
            longName = null
            val size = cString(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
            val type = header[156].toInt().toChar()
            val padded = (size + 511) / 512 * 512
            if (type == 'L') {
                val bytes = ByteArray(size.toInt())
                if (!readFully(input, bytes)) throw IOException("Truncated archive")
                skip(input, padded - size)
                longName = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                continue
            }
            val target = if (type == '0' || type == '\u0000') destinationFor(root, abi, name) else null
            if (target == null) {
                skip(input, padded)
                continue
            }
            target.parentFile?.mkdirs()
            target.outputStream().use { copyExactly(input, it, size) }
            skip(input, padded - size)
            files++
        }
        return files
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end].toInt() != 0) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun copyExactly(input: InputStream, out: OutputStream, size: Long) {
        val buf = ByteArray(1 shl 16)
        var left = size
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("Truncated archive")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun skip(input: InputStream, count: Long) {
        var left = count
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("Truncated archive")
            left -= n
        }
    }
}
