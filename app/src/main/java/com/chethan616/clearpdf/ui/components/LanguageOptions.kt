package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.utils.LocaleHelper

/** The app's UI languages, in order, keyed by the tags [LocaleHelper.normalizeForUi] produces. */
val AppLanguageTags = listOf("en", "pt-BR", "es", "it", "ru")

/**
 * [AppLanguageTags] as dropdown rows, iOS-style: the name in the current UI language, with the
 * language's own name underneath whenever it differs — so someone who landed in the wrong language
 * can still find theirs.
 */
@Composable
fun rememberLanguageOptions(): List<GlassDropdownOption<String>> {
    val localized = listOf(
        stringResource(R.string.language_english),
        stringResource(R.string.language_portuguese),
        stringResource(R.string.language_spanish),
        stringResource(R.string.language_italian),
        stringResource(R.string.language_russian)
    )
    return AppLanguageTags.mapIndexed { i, tag ->
        val native = LocaleHelper.getLanguageDisplayName(tag)
        GlassDropdownOption(
            value = tag,
            label = localized[i],
            supporting = native.takeIf { !it.startsWith(localized[i], ignoreCase = true) }
        )
    }
}
