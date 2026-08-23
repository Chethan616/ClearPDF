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
     * Onboarding is shown on the very first launch AND again after every app update — i.e.
     * whenever the installed [currentVersionCode] is newer than the one the user last completed
     * the tour on. Completing (or replaying) it records the current version so it won't repeat
     * until the next update.
     */
    fun shouldShowOnboarding(context: Context): Boolean {
        if (!hasCompletedOnboarding(context)) return true
        return onboardedVersionCode(context) < currentVersionCode(context)
    }

    fun setOnboardingComplete(context: Context) =
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putInt(KEY_ONBOARDED_VERSION, currentVersionCode(context))
            .apply()

    fun resetOnboarding(context: Context) =
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, false)
            .remove(KEY_ONBOARDED_VERSION)
            .apply()

    fun getSelectedLocale(context: Context): String =
        prefs(context).getString(KEY_LOCALE, "en") ?: "en"

    fun setSelectedLocale(context: Context, languageTag: String) =
        prefs(context).edit().putString(KEY_LOCALE, languageTag).apply()
}
