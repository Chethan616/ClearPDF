import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import org.tukaani.xz.XZInputStream

/*
 * :office_engine — Play Feature Delivery module for the optional Office engine (powered by
 * LibreOffice, MPL-2.0). It contains no code, only binaries fetched at build time:
 *
 *   prepareOfficeEngineBinaries downloads the pinned LibreOfficeKit bundles, verifies their
 *   size + SHA-256, and unpacks them into build/generated/officeEngine/{jniLibs,assets}, which
 *   are wired in as the `play` source set's jniLibs/assets.
 *
 * The task only runs when a Play bundle is being built (`bundlePlay*`, or `bundle*` without
 * "foss"), or when forced with -PofficeEngine.fetch=true. Every other build (compile tasks,
 * assembleFossDebug, assemblePlayDebug) needs no network and packages an empty module.
 *
 * Keep engineBundles in sync with OfficeEngineManifest.kt in :app.
 */
buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.tukaani:xz:1.10") }
}

plugins {
    alias(libs.plugins.android.dynamic.feature)
}

android {
    namespace = "com.chethan616.clearpdf.office_engine"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }
}

// Wired as static dirs (no implicit task dependency): the fetch task is attached explicitly
// below, only for Play bundle builds, so assemble/compile never touch the network.
val generatedJniLibs = layout.buildDirectory.dir("generated/officeEngine/jniLibs").get().asFile
val generatedAssets = layout.buildDirectory.dir("generated/officeEngine/assets").get().asFile
androidComponents {
    onVariants(selector().withFlavor("distribution" to "play")) { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(generatedJniLibs.absolutePath)
        variant.sources.assets?.addStaticSourceDirectory(generatedAssets.absolutePath)
    }
}

dependencies {
    implementation(project(":app"))
}

/** abi, url, size, sha256 (flattened in groups of four) — mirrors OfficeEngineManifest in :app. */
val engineBundles = listOf(
    "arm64-v8a",
    "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm64.tar.xz",
    "47521152",
    "6665ad47db41d116248c40e6fc608a3425b9eb4a304f5ad5e5d5c80dbea61e30",
    "armeabi-v7a",
    "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm.tar.xz",
    "46666612",
    "7546e244f66c8c1b5a5a4ede7cb26c8f7e767879bbf3b39d405a5d8fa148b045"
)

abstract class PrepareOfficeEngineBinaries : DefaultTask() {
    @get:Input abstract val bundles: ListProperty<String>
    @get:Internal abstract val downloadDir: DirectoryProperty
    @get:OutputDirectory abstract val jniLibsDir: DirectoryProperty
    @get:OutputDirectory abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun run() {
        val jni = jniLibsDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val assets = assetsDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val runtimeRoot = File(assets, "lo")
        bundles.get().chunked(4).forEachIndexed { index, entry ->
            val (abi, url, size, sha) = entry
            val archive = File(downloadDir.get().asFile, url.substringAfterLast('/'))
            if (!archive.exists() || archive.length() != size.toLong() || sha256(archive) != sha) {
                logger.lifecycle("Office engine: downloading $url")
                download(url, archive)
            }
            check(archive.length() == size.toLong()) { "Office engine: size mismatch for $url" }
            check(sha256(archive) == sha) { "Office engine: SHA-256 mismatch for $url" }
            // The runtime tree is ABI-independent; take it from the first bundle only.
            untar(archive, abi, File(jni, abi), if (index == 0) runtimeRoot else null)
        }
    }

    private fun download(url: String, dest: File) {
        dest.parentFile.mkdirs()
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode} for $url" }
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it, 1 shl 16) } }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Minimal ustar/GNU tar reader: regular files, GNU long names, zip-slip checked. */
    private fun untar(archive: File, abi: String, libOut: File, runtimeOut: File?) {
        XZInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16)).use { input ->
            val header = ByteArray(512)
            var longName: String? = null
            while (readFully(input, header) && header.any { it.toInt() != 0 }) {
                val name = longName ?: cString(header, 0, 100)
                longName = null
                val size = cString(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
                val type = header[156].toInt().toChar()
                val padded = (size + 511) / 512 * 512
                if (type == 'L') {
                    val bytes = ByteArray(size.toInt())
                    check(readFully(input, bytes)) { "Truncated tar" }
                    skip(input, padded - size)
                    longName = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                    continue
                }
                if (type != '0' && type != '\u0000') {
                    skip(input, padded)
                    continue
                }
                val rel = name.trimStart('.', '/')
                val (base, target) = when {
                    rel.startsWith("$abi/") -> libOut to File(libOut, rel.removePrefix("$abi/"))
                    runtimeOut != null && listOf("program/", "share/", "unpack/").any { rel.startsWith(it) } ->
                        runtimeOut to File(runtimeOut, rel)
                    else -> null to null
                }
                if (base == null || target == null) {
                    skip(input, padded)
                    continue
                }
                check(target.canonicalPath.startsWith(base.canonicalPath + File.separator)) { "Unsafe tar entry: $name" }
                target.parentFile.mkdirs()
                target.outputStream().use { out -> copyExactly(input, out, size) }
                skip(input, padded - size)
            }
        }
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
            check(n >= 0) { "Truncated tar" }
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun skip(input: InputStream, count: Long) {
        var left = count
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            check(n >= 0) { "Truncated tar" }
            left -= n
        }
    }
}

val prepareOfficeEngineBinaries = tasks.register<PrepareOfficeEngineBinaries>("prepareOfficeEngineBinaries") {
    group = "office engine"
    description = "Downloads, SHA-256 verifies and unpacks the LibreOfficeKit bundles (Play bundles only)."
    bundles.set(engineBundles)
    downloadDir.set(layout.buildDirectory.dir("officeEngine/downloads"))
    jniLibsDir.set(layout.buildDirectory.dir("generated/officeEngine/jniLibs"))
    assetsDir.set(layout.buildDirectory.dir("generated/officeEngine/assets"))
}

val buildingPlayBundle = gradle.startParameter.taskNames.any {
    val name = it.substringAfterLast(':')
    name.startsWith("bundle", ignoreCase = true) && !name.contains("foss", ignoreCase = true)
} || providers.gradleProperty("officeEngine.fetch").orNull == "true"

if (buildingPlayBundle) {
    tasks.named("preBuild") { dependsOn(prepareOfficeEngineBinaries) }
}
