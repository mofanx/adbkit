package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.ui.strings.AppStrings
import com.adbkit.app.ui.strings.EnStrings
import com.adbkit.app.ui.strings.ZhStrings
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Base ViewModel that observes the in-app language setting and exposes an
 * up-to-date [AppStrings] instance for status/error messages.
 */
abstract class LocalizedViewModel : ViewModel() {

    protected var strings: AppStrings = resolveLanguage("system")
        private set

    init {
        viewModelScope.launch {
            SettingsRepository(AdbKitApplication.instance).language.collect { lang ->
                strings = resolveLanguage(lang)
            }
        }
    }

    private fun resolveLanguage(language: String): AppStrings = when (language) {
        "zh" -> ZhStrings
        "en" -> EnStrings
        else -> if (Locale.getDefault().language == "zh") ZhStrings else EnStrings
    }
}
