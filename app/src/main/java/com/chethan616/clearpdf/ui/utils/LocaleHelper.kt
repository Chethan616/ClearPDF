package com.chethan616.clearpdf.ui.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.chethan616.clearpdf.data.repository.OnboardingManager
import java.util.Locale

object LocaleHelper {

    private fun normalizeLanguageTag(languageTag: String): String {
        val locale = Locale.forLanguageTag(languageTag.replace('_', '-'))
        return if (locale.language.equals("pt", ignoreCase = true)) "pt-BR" else "en"
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
            "en" -> "English"
            else -> "English"
        }
    }
}
