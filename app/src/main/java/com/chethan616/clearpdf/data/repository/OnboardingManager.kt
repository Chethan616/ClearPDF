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

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETED, false)

    fun setOnboardingComplete(context: Context) =
        prefs(context).edit().putBoolean(KEY_COMPLETED, true).apply()

    fun resetOnboarding(context: Context) =
        prefs(context).edit().putBoolean(KEY_COMPLETED, false).apply()

    fun getSelectedLocale(context: Context): String =
        prefs(context).getString(KEY_LOCALE, "en") ?: "en"

    fun setSelectedLocale(context: Context, languageTag: String) =
        prefs(context).edit().putString(KEY_LOCALE, languageTag).apply()
}
