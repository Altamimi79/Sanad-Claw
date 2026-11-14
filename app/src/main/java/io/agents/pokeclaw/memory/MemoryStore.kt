// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.memory

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.UUID

/**
 * Persistent user memory shown in the chat sidebar.
 *
 * Entries are short, user-authored facts/preferences ("أحب القهوة السادة", "My car is a white
 * Patrol"). They are stored as JSON in MMKV (non-secret) and appended to the system prompt of
 * every chat/task through [io.agents.pokeclaw.agent.PromptUtils].
 *
 * Security: this is plain KV storage, so the UI warns the user not to store secrets. Entries are
 * still user data sent to the chosen cloud provider, exactly like the global prompt.
 */
object MemoryStore {

    private const val TAG = "MemoryStore"
    private const val KEY_MEMORIES = "KEY_USER_MEMORIES_JSON"

    /** Hard cap so the prompt cannot grow without bound. */
    const val MAX_ENTRIES = 50
    const val MAX_TEXT_LENGTH = 500

    private val gson = Gson()

    data class Entry(
        val id: String,
        val text: String,
        val createdAt: Long,
    )

    /** Plain JSON shape; fields are nullable so malformed/legacy JSON never crashes the app.
     *  Explicit names keep release (R8-minified) and debug builds reading the same JSON. */
    data class Stored(
        @SerializedName(value = "id", alternate = ["a"]) val id: String? = null,
        @SerializedName(value = "text", alternate = ["b"]) val text: String? = null,
        @SerializedName(value = "createdAt", alternate = ["c"]) val createdAt: Long? = null,
    )

    fun load(): List<Entry> {
        val json = KVUtils.getString(KEY_MEMORIES, "")
        return parse(json)
    }

    /** Adds one entry; blank text is ignored and an exact duplicate is skipped. */
    fun add(raw: String): List<Entry> {
        val text = normalizeText(raw) ?: return load()
        val current = load()
        if (current.any { it.text.equals(text, ignoreCase = true) }) return current
        val updated = (current + Entry(UUID.randomUUID().toString(), text, System.currentTimeMillis()))
            .takeLast(MAX_ENTRIES)
        save(updated)
        XLog.i(TAG, "Memory added (${updated.size}/$MAX_ENTRIES entries)")
        return updated
    }

    fun remove(id: String): List<Entry> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        XLog.i(TAG, "Memory removed; ${updated.size} entries remain")
        return updated
    }

    fun clear() {
        KVUtils.putString(KEY_MEMORIES, "")
        XLog.i(TAG, "Memory cleared")
    }

    /** Block appended to the system prompt; empty string means "no memory". */
    fun forPrompt(): String = formatPrompt(load())

    internal fun parse(json: String): List<Entry> {
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<Stored>>() {}.type
        val stored: List<Stored> = try {
            gson.fromJson<List<Stored>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to parse stored memories; ignoring", e)
            return emptyList()
        }
        return stored.mapNotNull { toEntry(it) }
    }

    /** Trims and collapses whitespace, rejects blank/null input, caps length. */
    internal fun normalizeText(raw: String?): String? {
        val text = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (text.isEmpty()) return null
        return if (text.length <= MAX_TEXT_LENGTH) text else text.take(MAX_TEXT_LENGTH)
    }

    internal fun toEntry(stored: Stored): Entry? {
        val text = normalizeText(stored.text)
        if (stored.id.isNullOrBlank() || text == null) {
            XLog.w(TAG, "Skipping invalid memory id=${stored.id}")
            return null
        }
        return Entry(stored.id, text, stored.createdAt ?: 0L)
    }

    internal fun formatPrompt(entries: List<Entry>): String {
        if (entries.isEmpty()) return ""
        return entries.joinToString(separator = "\n") { "- ${it.text}" }
    }

    private fun save(entries: List<Entry>) {
        val stored = entries.map { Stored(it.id, it.text, it.createdAt) }
        KVUtils.putString(KEY_MEMORIES, gson.toJson(stored))
    }
}
