package com.chethan616.clearpdf.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages onboarding state: first-launch detection and locale preference.
 */
object OnboardingManager {
    private const val PREFS_NAME = "clearpdf_onboarding"
    private const val KEY_COMPLETED = "onboarding_completed"
    private const val KEY_LOCALE = "selected_locale"
    // The app versionCode the user last finished onboarding on. When a newer build is
    // installed this is behind [currentVersionCode], so the tour is shown again to surface
    // what changed in the update.
    private const val KEY_ONBOARDED_VERSION = "onboarded_version_code"
    private const val KEY_PDF_READER_TOUR_SEEN = "pdf_reader_tour_seen"
    private const val KEY_SPREADSHEET_READER_TOUR_SEEN = "spreadsheet_reader_tour_seen"
    // The versionCode whose "What's new" sheet the user has seen. Missing = the version they
    // onboarded on, so people who just did the full tour aren't shown it again.
    private const val KEY_WHATS_NEW_VERSION = "whats_new_seen_version"
    // First-use tutorials for features added in 2.1 (shown to new AND updating users, once).
    private const val KEY_EDIT_TEXT_TOUR_SEEN = "edit_text_tour_seen"
    private const val KEY_RECENTS_MENU_TOUR_SEEN = "recents_menu_tour_seen"
    private const val KEY_GLASS_SETTINGS_TOUR_SEEN = "glass_settings_tour_seen"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETED, false)

    /** The installed app's versionCode (0 if it can't be resolved). */
    fun currentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        0
    }

    private fun onboardedVersionCode(context: Context): Int =
        prefs(context).getInt(KEY_ONBOARDED_VERSION, -1)

    /**
     * The full welcome tour is shown on the first ordinary launch. Updates don't interrupt people
     * with a repeat tour; document-specific guides teach controls the first time they open a reader.
     */
    fun shouldShowOnboarding(context: Context): Boolean = !hasCompletedOnboarding(context)

    fun setOnboardingComplete(context: Context) =
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putInt(KEY_ONBOARDED_VERSION, currentVersionCode(context))
            // Someone who just took the full tour is already up to date.
            .putInt(KEY_WHATS_NEW_VERSION, currentVersionCode(context))
            .apply()

    /**
     * True once after an update: the user finished onboarding on an older build (or before
     * versions were recorded) and hasn't seen this build's "What's new" sheet yet.
     */
    fun shouldShowWhatsNew(context: Context): Boolean {
        if (!hasCompletedOnboarding(context)) return false
        val current = currentVersionCode(context)
        if (current <= 0) return false
        val seen = prefs(context).getInt(KEY_WHATS_NEW_VERSION, onboardedVersionCode(context))
        return seen < current
    }

    fun markWhatsNewSeen(context: Context) =
        prefs(context).edit().putInt(KEY_WHATS_NEW_VERSION, currentVersionCode(context)).apply()

    fun hasSeenEditTextTour(context: Context): Boolean = prefs(context).getBoolean(KEY_EDIT_TEXT_TOUR_SEEN, false)
    fun markEditTextTourSeen(context: Context) = prefs(context).edit().putBoolean(KEY_EDIT_TEXT_TOUR_SEEN, true).apply()

    fun hasSeenRecentsMenuTour(context: Context): Boolean = prefs(context).getBoolean(KEY_RECENTS_MENU_TOUR_SEEN, false)
    fun markRecentsMenuTourSeen(context: Context) = prefs(context).edit().putBoolean(KEY_RECENTS_MENU_TOUR_SEEN, true).apply()

    fun hasSeenGlassSettingsTour(context: Context): Boolean = prefs(context).getBoolean(KEY_GLASS_SETTINGS_TOUR_SEEN, false)
    fun markGlassSettingsTourSeen(context: Context) = prefs(context).edit().putBoolean(KEY_GLASS_SETTINGS_TOUR_SEEN, true).apply()

    fun resetOnboarding(context: Context) =
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, false)
            .remove(KEY_ONBOARDED_VERSION)
            .apply()

    fun hasSeenPdfReaderTour(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PDF_READER_TOUR_SEEN, false)

    fun markPdfReaderTourSeen(context: Context) =
        prefs(context).edit().putBoolean(KEY_PDF_READER_TOUR_SEEN, true).apply()

    fun hasSeenSpreadsheetReaderTour(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPREADSHEET_READER_TOUR_SEEN, false)

    fun markSpreadsheetReaderTourSeen(context: Context) =
        prefs(context).edit().putBoolean(KEY_SPREADSHEET_READER_TOUR_SEEN, true).apply()

    fun getSelectedLocale(context: Context): String =
        prefs(context).getString(KEY_LOCALE, "en") ?: "en"

    fun setSelectedLocale(context: Context, languageTag: String) =
        prefs(context).edit().putString(KEY_LOCALE, languageTag).apply()
}
