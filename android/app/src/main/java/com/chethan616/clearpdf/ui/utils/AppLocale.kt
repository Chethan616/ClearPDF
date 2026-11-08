package com.chethan616.clearpdf.ui.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** App language selection, with Android's per-app language setting on 13+ and a context locale earlier. */
object AppLocale {
    val supportedTags = listOf("en", "ar", "de", "es", "fr", "it", "pt", "pt-BR", "ru")

    private const val APP_PREFS = "app"
    private const val APP_KEY = "appLanguage"
    private const val LEGACY_PREFS = "clearpdf_onboarding"
    private const val LEGACY_KEY = "selected_locale"

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = selectedTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    fun selectedTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val systemChoice = context.getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales.toLanguageTags()
            if (systemChoice.isNotBlank()) return normalize(systemChoice.substringBefore(','))
        }
        val prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(APP_KEY)) return normalize(prefs.getString(APP_KEY, "").orEmpty())
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        return if (legacy.contains(LEGACY_KEY)) normalize(legacy.getString(LEGACY_KEY, "").orEmpty()) else ""
    }

    fun select(context: Context, tag: String) {
        val normalized = normalize(tag)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(normalized)
        } else {
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().putString(APP_KEY, normalized).apply()
            (context as? Activity)?.recreate()
        }
    }

    /** Preserve the user's language from ClearPDF when moving to the Android system language API. */
    fun migrateLegacyLocale(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (manager.applicationLocales.isEmpty && legacy.contains(LEGACY_KEY)) {
            manager.applicationLocales = LocaleList.forLanguageTags(normalize(legacy.getString(LEGACY_KEY, "").orEmpty()))
        }
    }

    fun currentDisplayName(context: Context): String {
        val selected = selectedTag(context)
        val shownTag = selected.ifBlank {
            if (Build.VERSION.SDK_INT >= 24) context.resources.configuration.locales[0].toLanguageTag()
            else @Suppress("DEPRECATION") context.resources.configuration.locale.toLanguageTag()
        }
        val locale = Locale.forLanguageTag(shownTag)
        return locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private fun normalize(tag: String): String {
        val normalized = tag.replace('_', '-')
        return when {
            normalized.equals("pt", true) -> "pt"
            normalized.startsWith("pt-BR", true) -> "pt-BR"
            supportedTags.any { it.equals(normalized, true) } -> supportedTags.first { it.equals(normalized, true) }
            normalized.isBlank() -> ""
            else -> "en"
        }
    }
}
