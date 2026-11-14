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
 * Values: "ar" (default) or "en". A saved English is honored ONLY when the user
 * explicitly chose a language in Settings — recorded by KEY_LANGUAGE_CHOSEN, which
 * setLanguage() writes. Earlier builds wrote an implicit "en" on every update; that
 * legacy value is ignored while the flag is absent, so those users land on Arabic once.
 * An app update never changes the language — [applyUpdateLanguageReset] only tracks
 * the versionCode (see that function).
 */
object AppLocale {

    private const val PREFS_NAME = "pokeclaw_locale"
    private const val KEY_LANGUAGE = "KEY_APP_LANGUAGE"
    private const val KEY_LANGUAGE_CHOSEN = "KEY_APP_LANGUAGE_CHOSEN"
    private const val KEY_LAST_VERSION_CODE = "KEY_LAST_VERSION_CODE"
    const val LANGUAGE_ARABIC = "ar"
    const val LANGUAGE_ENGLISH = "en"

    fun getLanguage(context: Context): String {
        // NOTE: must not use context.applicationContext here — attachBaseContext runs
        // while the Application is still being attached, so applicationContext is null then.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, null)
        val chosen = prefs.getBoolean(KEY_LANGUAGE_CHOSEN, false)
        return resolveLanguage(stored, chosen)
    }

    fun setLanguage(context: Context, language: String) {
        val normalized = if (language == LANGUAGE_ARABIC) LANGUAGE_ARABIC else LANGUAGE_ENGLISH
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .putBoolean(KEY_LANGUAGE_CHOSEN, true)
            // commit(), not apply(): switchAppLanguage calls finishAffinity() right after,
            // and an async disk write can be lost when the OEM kills the process —
            // the relaunched app would then read the stale language and not switch.
            .commit()
    }

    /**
     * Tracks the app versionCode across updates. The UI language is NEVER touched here:
     * a user's saved language (Arabic or English) survives every update. On first install
     * the code is just recorded; on an update only the new code is stored.
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
        if (shouldRecordVersionOnly(lastCode, currentCode)) {
            prefs.edit().putInt(KEY_LAST_VERSION_CODE, currentCode).commit()
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

/**
 * Pure language resolution (no Android deps, unit-tested in AppLocaleLogicTest):
 * Arabic is the default. A saved English is honored only when the user explicitly
 * chose a language in Settings ([chosen] flag written by setLanguage). Legacy "en"
 * values written implicitly by earlier builds' update reset are ignored while the
 * flag is absent — those users move to Arabic once. Unknown/empty stored values
 * always resolve to Arabic.
 */
fun resolveLanguage(stored: String?, chosen: Boolean): String {
    if (!chosen) return AppLocale.LANGUAGE_ARABIC
    return if (stored == AppLocale.LANGUAGE_ENGLISH) AppLocale.LANGUAGE_ENGLISH else AppLocale.LANGUAGE_ARABIC
}

/**
 * Pure version-tracking decision (unit-tested): true when the stored versionCode
 * should be written — first install (lastCode == 0) or an update (codes differ).
 * It never implies any language write; equal codes mean no-op.
 */
fun shouldRecordVersionOnly(lastCode: Int, currentCode: Int): Boolean =
    lastCode == 0 || lastCode != currentCode
