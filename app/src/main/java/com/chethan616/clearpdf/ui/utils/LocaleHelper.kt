package com.chethan616.clearpdf.ui.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.chethan616.clearpdf.data.repository.OnboardingManager
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "clearpdf_settings"
    private const val KEY_PENDING_FADE = "pending_locale_fade"

    /**
     * A locale change has to restart the Activity — 15 call sites read strings outside Compose, so a
     * Compose-only swap would leave half the app in the old language. The restart is therefore kept
     * and *choreographed* instead: the outgoing instance fades out, and this one-shot flag tells the
     * incoming one to fade in rather than snap. Written on the way out, consumed on the way in.
     */
    fun markLocaleFadePending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PENDING_FADE, true).apply()
    }

    /** Reads the flag and clears it, so a later recreate (rotation, theme) doesn't re-fade. */
    fun consumeLocaleFadePending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_PENDING_FADE, false)
        if (pending) prefs.edit().remove(KEY_PENDING_FADE).apply()
        return pending
    }

    /** Kills the OS's own restart cross-fade so it can't stack on ours. */
    fun suppressActivityTransition(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    private fun normalizeLanguageTag(languageTag: String): String {
        val locale = Locale.forLanguageTag(languageTag.replace('_', '-'))
        return when {
            locale.language.equals("pt", ignoreCase = true) -> "pt-BR"
            locale.language.equals("es", ignoreCase = true) -> "es"
            else -> "en"
        }
    }

    fun normalizeForUi(languageTag: String): String = normalizeLanguageTag(languageTag)

    fun getLocalizedContext(context: Context, languageTag: String): Context {
        val locale = parseLocale(normalizeLanguageTag(languageTag))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun parseLocale(languageTag: String): Locale {
        return Locale.forLanguageTag(languageTag.replace('_', '-'))
    }

    fun applyLocale(
        context: Context,
        languageTag: String,
        recreate: Boolean = false,
        updateAppCompat: Boolean = true
    ) {
        val normalizedTag = normalizeLanguageTag(languageTag)
        OnboardingManager.setSelectedLocale(context, normalizedTag)

        val locale = parseLocale(normalizedTag)
        Locale.setDefault(locale)

        // AppCompat owns the activity configuration on modern Android. The
        // legacy resource update is kept only for API 23, where per-app
        // language APIs are not available.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }

        if (updateAppCompat) {
            val requestedLocales = LocaleListCompat.forLanguageTags(normalizedTag)
            if (AppCompatDelegate.getApplicationLocales() != requestedLocales) {
                AppCompatDelegate.setApplicationLocales(requestedLocales)
            }
        }

        if (recreate) {
            (context as? android.app.Activity)?.recreate()
        }
    }

    fun getLanguageDisplayName(languageTag: String): String {
        return when (normalizeLanguageTag(languageTag)) {
            "pt-BR", "pt" -> "Português (Brasil)"
            "es" -> "Español"
            "en" -> "English"
            else -> "English"
        }
    }
}
