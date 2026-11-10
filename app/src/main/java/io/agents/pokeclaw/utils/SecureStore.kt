// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.agents.pokeclaw.utils.XLog

/**
 * Encrypted storage for secrets (API keys, bot tokens) backed by the Android Keystore.
 *
 * Uses EncryptedSharedPreferences (AES256-GCM keys + values). If the device keystore
 * fails, falls back to a private-mode SharedPreferences file and logs a warning
 * WITHOUT logging any secret value — availability is preferred over silent failure,
 * per project rules. Never log secret values from this class.
 */
object SecureStore {

    private const val TAG = "SecureStore"
    private const val FILE_NAME = "pokeclaw_secure"
    private const val FALLBACK_FILE_NAME = "pokeclaw_secure_fallback"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var encrypted = false

    /**
     * Initialize and migrate any legacy plaintext secrets that are still in MMKV.
     * Call after KVUtils.init(context).
     */
    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        prefs = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).also { encrypted = true }
        } catch (e: Exception) {
            XLog.w(TAG, "EncryptedSharedPreferences unavailable, using private fallback store: ${e.message}")
            appContext.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
        }
        migrateLegacySecretsFromMmkv(appContext)
        XLog.i(TAG, "SecureStore initialized encrypted=$encrypted")
    }

    fun isEncrypted(): Boolean = encrypted

    fun getString(key: String, defaultValue: String = ""): String {
        val store = prefs ?: run {
            XLog.w(TAG, "SecureStore.getString before init, returning default for key=$key")
            return defaultValue
        }
        return store.getString(key, defaultValue) ?: defaultValue
    }

    fun putString(key: String, value: String) {
        val store = prefs ?: run {
            XLog.w(TAG, "SecureStore.putString before init, dropping write for key=$key")
            return
        }
        store.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    private fun migrateLegacySecretsFromMmkv(context: Context) {
        val legacyKeys = try {
            KVUtils.getAllKeys().filter { isSecretKey(it) }
        } catch (e: Exception) {
            XLog.w(TAG, "Could not enumerate MMKV keys for secret migration: ${e.message}")
            emptyArray()
        }
        if (legacyKeys.isEmpty()) return
        var migrated = 0
        for (key in legacyKeys) {
            val value = KVUtils.getString(key, "")
            if (value.isNotEmpty() && getString(key, "").isEmpty()) {
                putString(key, value)
                migrated++
            }
            KVUtils.remove(key)
        }
        XLog.i(TAG, "Migrated $migrated legacy secret(s) from MMKV to SecureStore; MMKV copies removed")
    }

    private fun isSecretKey(key: String): Boolean {
        val upper = key.uppercase()
        return upper.startsWith("KEY_LLM_API_KEY") ||
            upper.endsWith("_BOT_TOKEN") ||
            upper == "DEFAULT_WECHAT_BOT_TOKEN"
    }
}
