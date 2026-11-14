// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Recognizes the only two voice commands accepted by the safety layer:
 *  - STOP:    "توقف سند", "سند توقف", "stop"  (emergency stop)
 *  - CONFIRM: "نعم سند", "confirm"            (approve a pending confirmation)
 *
 * Matching is exact after normalization. A command word embedded in a longer
 * sentence is NOT a command (e.g. "لا تتوقف سند عن العمل" -> NONE).
 * Pure Kotlin; no Android imports.
 */
object VoiceCommandParser {

    enum class Command { STOP, CONFIRM, NONE }

    private val stopPhrases = setOf("توقف سند", "سند توقف", "stop")
    private val confirmPhrases = setOf("نعم سند", "confirm")

    fun parse(raw: String?): Command {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return Command.NONE
        return when (normalized) {
            in stopPhrases -> Command.STOP
            in confirmPhrases -> Command.CONFIRM
            else -> Command.NONE
        }
    }

    /**
     * Strips Arabic diacritics (tashkeel, U+064B..U+065F, U+0670), tatweel (U+0640) and
     * zero-width/BOM characters, lowercases Latin, replaces punctuation with spaces, and
     * collapses whitespace.
     */
    internal fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val code = ch.code
            when {
                code in 0x064B..0x065F || code == 0x0670 -> Unit // diacritics
                code == 0x0640 -> Unit // tatweel
                code == 0x200B || code == 0x200C || code == 0x200D || code == 0xFEFF -> Unit
                ch.isLetterOrDigit() -> sb.append(ch.lowercaseChar())
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(' ') // punctuation and symbols
            }
        }
        return sb.toString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
