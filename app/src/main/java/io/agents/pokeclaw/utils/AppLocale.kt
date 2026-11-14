// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import android.content.Context
import java.util.Locale

/**
 * App language resolution for the Sanad fork: English is the default UI language.
 *
 * Stored in a tiny plain SharedPreferences file (not MMKV) because attachBaseContext
 * runs before KVUtils/MMKV initialization on every Context.
 *
 * Values: "en" (default) or "ar". Unknown values fall back to "en".
 * On an app update the language is reset to English (see [applyUpdateLanguageReset]).
 */
object AppLocale {

    private const val PREFS_NAME = "pokeclaw_locale"
    private const val KEY_LANGUAGE = "KEY_APP_LANGUAGE"
    private const val KEY_LAST_VERSION_CODE = "KEY_LAST_VERSION_CODE"
    const val LANGUAGE_ARABIC = "ar"
    const val LANGUAGE_ENGLISH = "en"

    fun getLanguage(context: Context): String {
        // NOTE: must not use context.applicationContext here — attachBaseContext runs
        // while the Application is still being attached, so applicationContext is null then.
        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_ENGLISH)
        return if (stored == LANGUAGE_ARABIC) LANGUAGE_ARABIC else LANGUAGE_ENGLISH
    }

    fun setLanguage(context: Context, language: String) {
        val normalized = if (language == LANGUAGE_ARABIC) LANGUAGE_ARABIC else LANGUAGE_ENGLISH
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            // commit(), not apply(): switchAppLanguage calls finishAffinity() right after,
            // and an async disk write can be lost when the OEM kills the process —
            // the relaunched app would then read the stale language and not switch.
            .commit()
    }

    /**
     * Reset the UI language to English when the app was updated (versionCode changed).
     * Called from attachBaseContext before the locale is applied, so the first launch
     * after an update already renders in English. First install just records the code.
     */
    fun applyUpdateLanguageReset(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCode = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) {
            return
        }
        val lastCode = prefs.getInt(KEY_LAST_VERSION_CODE, 0)
        if (lastCode == 0) {
            prefs.edit().putInt(KEY_LAST_VERSION_CODE, currentCode).commit()
        } else if (lastCode != currentCode) {
            prefs.edit()
                .putString(KEY_LANGUAGE, LANGUAGE_ENGLISH)
                .putInt(KEY_LAST_VERSION_CODE, currentCode)
                .commit()
        }
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
