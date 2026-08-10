package com.chethan616.clearpdf.ui.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.chethan616.clearpdf.data.repository.OnboardingManager
import java.util.Locale

object LocaleHelper {

    fun getLocalizedContext(context: Context, languageTag: String): Context {
        val locale = parseLocale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun parseLocale(languageTag: String): Locale {
        return if (languageTag.contains("-")) {
            val parts = languageTag.split("-")
            Locale(parts[0], parts[1])
        } else if (languageTag.contains("_")) {
            val parts = languageTag.split("_")
            Locale(parts[0], parts[1])
        } else {
            Locale(languageTag)
        }
    }

    fun applyLocale(context: Context, languageTag: String, recreate: Boolean = false) {
        OnboardingManager.setSelectedLocale(context, languageTag)

        val locale = parseLocale(languageTag)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        val appRes = context.applicationContext.resources
        val appConfig = Configuration(appRes.configuration)
        appConfig.setLocale(locale)
        @Suppress("DEPRECATION")
        appRes.updateConfiguration(appConfig, appRes.displayMetrics)

        // Android 13+ / AppCompat per-app language
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(languageTag)
        )

        if (recreate) {
            (context as? android.app.Activity)?.recreate()
        }
    }

    fun getLanguageDisplayName(languageTag: String): String {
        return when (languageTag) {
            "pt-BR", "pt" -> "Português (Brasil)"
            "en" -> "English"
            else -> "English"
        }
    }
}
