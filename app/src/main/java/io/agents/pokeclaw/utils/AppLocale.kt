// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import android.content.Context
import java.util.Locale

/**
 * App language resolution for the Sanad fork: Arabic is the default UI language.
 *
 * Stored in a tiny plain SharedPreferences file (not MMKV) because attachBaseContext
 * runs before KVUtils/MMKV initialization on every Context.
 *
 * Values: "ar" (default) or "en". Unknown values fall back to "ar".
 */
object AppLocale {

    private const val PREFS_NAME = "pokeclaw_locale"
    private const val KEY_LANGUAGE = "KEY_APP_LANGUAGE"
    const val LANGUAGE_ARABIC = "ar"
    const val LANGUAGE_ENGLISH = "en"

    fun getLanguage(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_ARABIC)
        return if (stored == LANGUAGE_ENGLISH) LANGUAGE_ENGLISH else LANGUAGE_ARABIC
    }

    fun setLanguage(context: Context, language: String) {
        val normalized = if (language == LANGUAGE_ENGLISH) LANGUAGE_ENGLISH else LANGUAGE_ARABIC
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
    }

    fun locale(context: Context): Locale =
        if (getLanguage(context) == LANGUAGE_ENGLISH) Locale.ENGLISH else Locale("ar")

    /**
     * Wrap a base context with the app's configured locale. Call from attachBaseContext.
     */
    fun wrap(context: Context, newBase: Context): Context {
        val baseConfig = newBase.resources.configuration
        val config = android.content.res.Configuration(baseConfig)
        config.setLocale(locale(context))
        return newBase.createConfigurationContext(config)
    }
}
