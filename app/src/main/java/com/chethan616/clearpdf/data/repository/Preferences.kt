package com.chethan616.clearpdf.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecentFile(
    val name: String,
    val uriString: String,
    val timestamp: Long,
    val pageCount: Int = -1,
    val sizeBytes: Long = -1,
    /** Last page opened in the PDF reader, zero-based. */
    val currentPage: Int = 0,
    /** Pinned entries sort to the top and survive the [RecentFilesManager] trim. */
    val pinned: Boolean = false
) {
    val uri: Uri get() = Uri.parse(uriString)
}

/**
 * Manages recently opened/saved files using SharedPreferences.
 */
object RecentFilesManager {
    private const val PREFS_NAME = "clearpdf_recents"
    private const val KEY_RECENTS = "recent_files"
    private const val MAX_RECENTS = 20
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Pinned first, then newest first. Stable across every read, so the UI never has to re-sort. */
    fun getRecents(context: Context): List<RecentFile> {
        if (!AppSettingsManager.getRememberRecentFiles(context)) return emptyList()
        val raw = prefs(context).getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<RecentFile>>(raw)
                .sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.timestamp })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecent(context: Context, file: RecentFile) {
        if (!AppSettingsManager.getRememberRecentFiles(context)) return
        val current = getRecents(context).toMutableList()
        // Re-opening a pinned file must not silently unpin it — carry the flag forward.
        val existing = current.firstOrNull { it.uriString == file.uriString }
        current.removeAll { it.uriString == file.uriString }
        current.add(0, file.copy(
            pinned = file.pinned || existing?.pinned == true,
            currentPage = existing?.currentPage ?: file.currentPage
        ))
        // Trim to max, but a pin is an explicit "keep this" — pinned entries are exempt.
        val (pinned, unpinned) = current.partition { it.pinned }
        val trimmed = pinned + unpinned.take((MAX_RECENTS - pinned.size).coerceAtLeast(0))
        prefs(context).edit()
            .putString(KEY_RECENTS, json.encodeToString(trimmed))
            .apply()
    }

    /** Flips the pin on one entry. No-op if the URI isn't in the list. */
    fun togglePin(context: Context, uri: Uri) {
        val target = uri.toString()
        val updated = getRecents(context).map {
            if (it.uriString == target) it.copy(pinned = !it.pinned) else it
        }
        prefs(context).edit()
            .putString(KEY_RECENTS, json.encodeToString(updated))
            .apply()
    }

    /** Stores reading progress without changing the entry's recency or pinned state. */
    fun updateProgress(context: Context, uri: Uri, currentPage: Int, pageCount: Int) {
        if (!AppSettingsManager.getRememberRecentFiles(context)) return
        val target = uri.toString()
        val updated = getRecents(context).map { file ->
            if (file.uriString == target) {
                file.copy(
                    currentPage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                    pageCount = pageCount
                )
            } else file
        }
        prefs(context).edit()
            .putString(KEY_RECENTS, json.encodeToString(updated))
            .apply()
    }

    fun clearRecents(context: Context) {
        getRecents(context).forEach { LocalDocumentMirror.forget(context, it.uri) }
        prefs(context).edit().remove(KEY_RECENTS).apply()
    }

    /** Clears browsing history while preserving separately opted-in recovery copies. */
    fun clearHistoryOnly(context: Context) {
        prefs(context).edit().remove(KEY_RECENTS).apply()
    }

    fun removeRecent(context: Context, uri: Uri) {
        LocalDocumentMirror.forget(context, uri)
        val remaining = getRecents(context).filterNot { it.uriString == uri.toString() }
        prefs(context).edit().apply {
            if (remaining.isEmpty()) {
                remove(KEY_RECENTS)
            } else {
                putString(KEY_RECENTS, json.encodeToString(remaining))
            }
        }.apply()
    }
}

/**
 * Manages custom save location preference.
 */
object SaveLocationManager {
    private const val PREFS_NAME = "clearpdf_settings"
    private const val KEY_SAVE_URI = "custom_save_uri"
    private const val KEY_SAVE_PATH = "custom_save_path_display"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSaveUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_SAVE_URI, null) ?: return null
        return Uri.parse(raw)
    }

    fun getSavePathDisplay(context: Context): String {
        return prefs(context).getString(KEY_SAVE_PATH, "Downloads (default)") ?: "Downloads (default)"
    }

    fun setSaveLocation(context: Context, uri: Uri, displayPath: String) {
        prefs(context).edit()
            .putString(KEY_SAVE_URI, uri.toString())
            .putString(KEY_SAVE_PATH, displayPath)
            .apply()
    }

    fun clearSaveLocation(context: Context) {
        prefs(context).edit()
            .remove(KEY_SAVE_URI)
            .remove(KEY_SAVE_PATH)
            .apply()
    }
}

/**
 * Manages app-wide settings (auto-compress, keep original, notifications, default quality).
 */
object AppSettingsManager {
    private const val PREFS_NAME = "clearpdf_settings"
    private const val KEY_AUTO_COMPRESS = "auto_compress"
    private const val KEY_KEEP_ORIGINAL = "keep_original"
    private const val KEY_DEFAULT_QUALITY = "default_quality"
    private const val KEY_THEME_MODE = "theme_mode" // 0: System, 1: Light, 2: Dark
    private const val KEY_SHOW_WALLPAPER = "show_wallpaper"
    private const val KEY_REMEMBER_RECENT_FILES = "remember_recent_files"
    private const val KEY_KEEP_LOCAL_COPIES = "keep_local_document_copies"
    private const val KEY_REDUCE_GLASS_MOTION = "reduce_glass_motion"
    private const val KEY_AUTO_DARK_PAGES = "auto_dark_pages"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShowWallpaper(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_WALLPAPER, false)

    fun setShowWallpaper(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_WALLPAPER, value).apply()

    // Opt-in (default off: full quality). Freezes the gravity-tracked specular highlight that
    // every liquidGlassPanel/capsule reads instead of letting it keep publishing tilt updates —
    // see UISensor's own KDoc for why that is the dominant redraw cost on devices sensitive to it.
    /** Documents open with dark pages whenever the app is in dark mode (#74). */
    fun getAutoDarkPages(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_DARK_PAGES, false)

    /**
     * Swaps the viewer's share/open button: tap shares the document, press-and-hold then swipe up
     * opens another file (the default is the other way round).
     */
    fun getInvertShareMorph(context: Context): Boolean = prefs(context).getBoolean("invert_share_morph", false)
    fun setInvertShareMorph(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("invert_share_morph", enabled).apply()
    fun setAutoDarkPages(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_DARK_PAGES, enabled).apply()

    /** The viewer trims the empty border around pages, so the text fills the screen (#85). */
    fun getTrimMargins(context: Context): Boolean = prefs(context).getBoolean("trim_page_margins", false)
    fun setTrimMargins(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("trim_page_margins", enabled).apply()

    /** Book mode: one page per screen, a sideways swipe turns the page (#84). */
    fun getBookMode(context: Context): Boolean = prefs(context).getBoolean("book_mode", false)
    fun setBookMode(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("book_mode", enabled).apply()

    fun getReduceGlassMotion(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REDUCE_GLASS_MOTION, false)

    fun setReduceGlassMotion(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_REDUCE_GLASS_MOTION, value).apply()

    // Optional user-picked background image (content URI). Null = use the built-in wallpaper.
    fun getCustomWallpaper(context: Context): String? =
        prefs(context).getString("custom_wallpaper_uri", null)

    fun setCustomWallpaper(context: Context, uri: String) =
        prefs(context).edit().putString("custom_wallpaper_uri", uri).apply()

    fun clearCustomWallpaper(context: Context) =
        prefs(context).edit().remove("custom_wallpaper_uri").apply()

    fun getAutoCompress(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_COMPRESS, true)

    fun setAutoCompress(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_COMPRESS, value).apply()

    fun getRememberRecentFiles(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REMEMBER_RECENT_FILES, true)

    fun setRememberRecentFiles(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMEMBER_RECENT_FILES, value).apply()
        if (!value) RecentFilesManager.clearHistoryOnly(context)
    }

    fun getKeepLocalCopies(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_LOCAL_COPIES, true)

    fun setKeepLocalCopies(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_LOCAL_COPIES, value).apply()
        if (!value) LocalDocumentMirror.clearAll(context)
    }

    fun getKeepOriginal(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_ORIGINAL, true)

    fun setKeepOriginal(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_ORIGINAL, value).apply()

    fun getDefaultQuality(context: Context): Float =
        prefs(context).getFloat(KEY_DEFAULT_QUALITY, 0.7f)

    fun setDefaultQuality(context: Context, value: Float) =
        prefs(context).edit().putFloat(KEY_DEFAULT_QUALITY, value).apply()

    fun getThemeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, 1)

    fun setThemeMode(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_THEME_MODE, value).apply()

    fun getScrollOrientation(context: Context): Int =
        prefs(context).getInt("scroll_orientation", 0)

    fun setScrollOrientation(context: Context, value: Int) =
        prefs(context).edit().putInt("scroll_orientation", value).apply()
}

/**
 * Decides when to ask for a GitHub star from how the person really uses the app, not a fixed count.
 * Everything stays on the device.
 *
 * - **Earned.** A task that produced a file (merge, compress, scan, export...) scores
 *   [SUCCESS_POINTS]; opening a document scores [OPEN_POINTS]. Nothing is asked below [SCORE_NEEDED].
 * - **A returning user.** Used on [ACTIVE_DAYS_NEEDED] different days and installed at least
 *   [MIN_INSTALL_DAYS] days ago, so the first sessions are never interrupted.
 * - **A good moment.** Only after something went right in this launch (a file made, a document
 *   read), and only once the person is back on Home or Tools (the app checks that). Never at launch,
 *   never mid-task.
 * - **Not after a failure.** A task that failed in the last [FAILURE_QUIET_MS] holds it back.
 * - **Backs off.** "Not now" snoozes for [SNOOZE_DAYS] in turn and the score starts over; once
 *   those run out, the next "Not now" is taken as a no. A star or "Don't show again" ends it. At most
 *   one ask per launch.
 */
object GitHubStarPromptManager {
    private const val PREFS_NAME = "clearpdf_settings"
    /** The old fixed counter (3 interactions); carried into the score once. */
    private const val KEY_LEGACY_COUNT = "github_star_interaction_count"
    private const val KEY_SCORE = "github_star_score"
    private const val KEY_ACTIVE_DAYS = "github_star_active_days"
    private const val KEY_LAST_ACTIVE_DAY = "github_star_last_active_day"
    private const val KEY_LAST_FAILURE_MS = "github_star_last_failure_ms"
    private const val KEY_DISMISS_COUNT = "github_star_dismiss_count"
    private const val KEY_PROMPT_SNOOZE_UNTIL_MS = "github_star_prompt_snooze_until_ms"
    private const val KEY_NEVER_SHOW = "github_star_never_show"

    private const val SUCCESS_POINTS = 3
    private const val OPEN_POINTS = 1
    private const val SCORE_NEEDED = 9
    private const val ACTIVE_DAYS_NEEDED = 3
    private const val MIN_INSTALL_DAYS = 3
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val FAILURE_QUIET_MS = 10L * 60L * 1000L
    private val SNOOZE_DAYS = intArrayOf(21, 60)

    const val REPO_URL: String = "https://github.com/Chethan616/ClearPDF"

    /** Something went right in this launch; the app may ask at its next calm moment. */
    @Volatile private var positiveMoment = false
    @Volatile private var askedThisLaunch = false

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Once per launch: counts the distinct days the app is used. */
    fun recordActiveDay(context: Context) {
        val p = prefs(context)
        val today = System.currentTimeMillis() / DAY_MS
        if (p.getLong(KEY_LAST_ACTIVE_DAY, -1L) != today) {
            p.edit()
                .putLong(KEY_LAST_ACTIVE_DAY, today)
                .putInt(KEY_ACTIVE_DAYS, p.getInt(KEY_ACTIVE_DAYS, 0) + 1)
                .apply()
        }
    }

    /** A task produced a file. True when this would be a good time to ask. */
    fun recordSuccess(context: Context): Boolean = record(context, SUCCESS_POINTS)

    /** A document opened and rendered. True when this would be a good time to ask. */
    fun recordDocumentOpened(context: Context): Boolean = record(context, OPEN_POINTS)

    /** A task failed: nobody is asked for a star right after the app let them down. */
    fun recordFailure(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_FAILURE_MS, System.currentTimeMillis()).apply()
    }

    private fun record(context: Context, points: Int): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_NEVER_SHOW, false)) return false
        p.edit().putInt(KEY_SCORE, score(p) + points).apply()
        positiveMoment = true
        return shouldShowPrompt(context)
    }

    private fun score(p: SharedPreferences): Int =
        p.getInt(KEY_SCORE, -1).takeIf { it >= 0 } ?: (p.getInt(KEY_LEGACY_COUNT, 0) * SUCCESS_POINTS)

    private fun installedAt(context: Context): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)

    fun shouldShowPrompt(context: Context): Boolean {
        if (askedThisLaunch || !positiveMoment) return false
        val p = prefs(context)
        if (p.getBoolean(KEY_NEVER_SHOW, false)) return false
        val now = System.currentTimeMillis()
        if (now < p.getLong(KEY_PROMPT_SNOOZE_UNTIL_MS, 0L)) return false
        if (now - p.getLong(KEY_LAST_FAILURE_MS, 0L) < FAILURE_QUIET_MS) return false
        if (now - installedAt(context) < MIN_INSTALL_DAYS * DAY_MS) return false
        if (p.getInt(KEY_ACTIVE_DAYS, 0) < ACTIVE_DAYS_NEEDED) return false
        return score(p) >= SCORE_NEEDED
    }

    /** The prompt is on screen: no second ask in this launch, whatever the person picks. */
    fun onPromptShown() {
        askedThisLaunch = true
    }

    fun onPromptAccepted(context: Context) {
        setNeverShowAgain(context)
    }

    fun onPromptDismissed(context: Context) {
        val p = prefs(context)
        val dismissals = p.getInt(KEY_DISMISS_COUNT, 0) + 1
        if (dismissals > SNOOZE_DAYS.size) {
            setNeverShowAgain(context)
            return
        }
        p.edit()
            .putInt(KEY_DISMISS_COUNT, dismissals)
            .putInt(KEY_SCORE, 0)
            .putLong(KEY_PROMPT_SNOOZE_UNTIL_MS, System.currentTimeMillis() + SNOOZE_DAYS[dismissals - 1] * DAY_MS)
            .apply()
    }

    fun setNeverShowAgain(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_NEVER_SHOW, true)
            .putInt(KEY_SCORE, 0)
            .apply()
    }
}
