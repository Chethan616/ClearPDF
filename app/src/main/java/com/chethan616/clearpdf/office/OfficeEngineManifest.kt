package com.chethan616.clearpdf.office

import android.os.Build
import android.os.Process

/**
 * Pinned description of the optional Office engine (powered by LibreOffice).
 *
 * The engine is a prebuilt LibreOfficeKit bundle for Android (liblo-native-code.so + the
 * LibreOffice runtime tree). Nothing about it is trusted at runtime except what is pinned here:
 * the foss download is accepted only if its size and SHA-256 match, and the Play module is built
 * from the same pinned archives (office_engine/build.gradle.kts — keep both in sync).
 *
 * Source: https://github.com/vasuki-re/LibreOffice-Lite release v2.0, a LibreOffice core build
 * (MPL-2.0; see THIRD_PARTY_NOTICES.md). Values verified against the GitHub release API digests
 * and by hashing the downloaded arm64 archive.
 */
object OfficeEngineManifest {

    /** Bumping this makes installed engines report "update available". */
    const val VERSION = "lo-lite-2.0"
    const val DISPLAY_VERSION = "2.0"

    /** Rough install footprint, for the "Not installed" row. */
    const val APPROX_DOWNLOAD_BYTES = 47_521_152L

    data class Bundle(val abi: String, val url: String, val sizeBytes: Long, val sha256: String)

    val bundles: List<Bundle> = listOf(
        Bundle(
            abi = "arm64-v8a",
            url = "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm64.tar.xz",
            sizeBytes = 47_521_152L,
            sha256 = "6665ad47db41d116248c40e6fc608a3425b9eb4a304f5ad5e5d5c80dbea61e30"
        ),
        Bundle(
            abi = "armeabi-v7a",
            url = "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm.tar.xz",
            sizeBytes = 46_666_612L,
            sha256 = "7546e244f66c8c1b5a5a4ede7cb26c8f7e767879bbf3b39d405a5d8fa148b045"
        )
    )

    /**
     * This particular build hard-codes its original host app's data directory in a few bootstrap
     * paths (e.g. `file:///data/data/vasuki.istanpdf/files/program/unorc`), which ClearPDF cannot
     * access. Those bytes are rewritten, at install time, to an equal-length alias that the
     * `:office` process points at the engine directory — see [OfficeEngineRelocation].
     * Set to null for a bundle built without hard-coded paths.
     */
    const val RELOCATE_FROM: String = "/data/data/vasuki.istanpdf/files"

    /**
     * The ABI the engine must match: the ABI this *process* runs as. An x86 device that also lists
     * ARM ABIs (via translation) cannot load ARM libraries into an x86 process, so only the
     * process's own primary ABI counts.
     */
    fun processAbi(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Process.is64Bit()) {
        Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
    } else {
        Build.SUPPORTED_32_BIT_ABIS.firstOrNull()
    }

    /** The bundle for this device, or null when the engine is unsupported here (e.g. x86). */
    fun bundleForDevice(): Bundle? = processAbi()?.let { abi -> bundles.firstOrNull { it.abi == abi } }
}
