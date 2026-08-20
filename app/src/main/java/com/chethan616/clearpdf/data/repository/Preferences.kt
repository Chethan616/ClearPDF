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
        val raw = prefs(context).getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<RecentFile>>(raw)
                .sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.timestamp })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecent(context: Context, file: RecentFile) {
        val current = getRecents(context).toMutableList()
        // Re-opening a pinned file must not silently unpin it — carry the flag forward.
        val wasPinned = current.any { it.uriString == file.uriString && it.pinned }
        current.removeAll { it.uriString == file.uriString }
        current.add(0, if (wasPinned) file.copy(pinned = true) else file)
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

    fun clearRecents(context: Context) {
        prefs(context).edit().remove(KEY_RECENTS).apply()
    }

    fun removeRecent(context: Context, uri: Uri) {
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

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShowWallpaper(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_WALLPAPER, true)

    fun setShowWallpaper(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_WALLPAPER, value).apply()

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

    fun getKeepOriginal(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_ORIGINAL, true)

    fun setKeepOriginal(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_ORIGINAL, value).apply()

    fun getDefaultQuality(context: Context): Float =
        prefs(context).getFloat(KEY_DEFAULT_QUALITY, 0.7f)

    fun setDefaultQuality(context: Context, value: Float) =
        prefs(context).edit().putFloat(KEY_DEFAULT_QUALITY, value).apply()

    fun getThemeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, 0)

    fun setThemeMode(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_THEME_MODE, value).apply()

    fun getScrollOrientation(context: Context): Int =
        prefs(context).getInt("scroll_orientation", 0)

    fun setScrollOrientation(context: Context, value: Int) =
        prefs(context).edit().putInt("scroll_orientation", value).apply()
}

/**
 * Tracks usage for the GitHub star prompt.
 * Best practice prompt rules:
 * - Requires at least 10 successful PDF interactions
 * - Permanently disables after user stars or clicks "Don't show again"
 * - Snoozes for 30 days if user clicks "Not now"
 */
object GitHubStarPromptManager {
    private const val PREFS_NAME = "clearpdf_settings"
    private const val KEY_INTERACTION_COUNT = "github_star_interaction_count"
    private const val KEY_PROMPT_SNOOZE_UNTIL_MS = "github_star_prompt_snooze_until_ms"
    private const val KEY_NEVER_SHOW = "github_star_never_show"

    private const val INTERACTION_THRESHOLD = 3
    private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

    const val REPO_URL: String = "https://github.com/Chethan616/ClearPDF"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordPdfInteraction(context: Context): Boolean {
        val preferences = prefs(context)
        if (preferences.getBoolean(KEY_NEVER_SHOW, false)) return false

        val now = System.currentTimeMillis()
        val snoozeUntil = preferences.getLong(KEY_PROMPT_SNOOZE_UNTIL_MS, 0L)
        if (now < snoozeUntil) return false

        val nextCount = preferences.getInt(KEY_INTERACTION_COUNT, 0) + 1
        preferences.edit().putInt(KEY_INTERACTION_COUNT, nextCount).apply()
        return nextCount >= INTERACTION_THRESHOLD
    }

    fun shouldShowPrompt(context: Context): Boolean {
        val preferences = prefs(context)
        if (preferences.getBoolean(KEY_NEVER_SHOW, false)) return false

        val now = System.currentTimeMillis()
        val snoozeUntil = preferences.getLong(KEY_PROMPT_SNOOZE_UNTIL_MS, 0L)
        if (now < snoozeUntil) return false
        return preferences.getInt(KEY_INTERACTION_COUNT, 0) >= INTERACTION_THRESHOLD
    }

    fun onPromptAccepted(context: Context) {
        setNeverShowAgain(context)
    }

    fun onPromptDismissed(context: Context) {
        val snoozeUntil = System.currentTimeMillis() + THIRTY_DAYS_MS
        prefs(context).edit()
            .putInt(KEY_INTERACTION_COUNT, 0)
            .putLong(KEY_PROMPT_SNOOZE_UNTIL_MS, snoozeUntil)
            .apply()
    }

    fun setNeverShowAgain(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_NEVER_SHOW, true)
            .putInt(KEY_INTERACTION_COUNT, 0)
            .apply()
    }
}
