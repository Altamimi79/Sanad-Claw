// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Redacts sensitive values before text leaves the device or is written to a log.
 * Pure JVM, unit-tested. Redacted values are never sent to the cloud, never logged,
 * and never stored in ActionLog.
 */
object SensitiveDataRedactor {

    enum class Kind { CARD, IBAN, NATIONAL_ID, OTP }

    data class Result(val text: String, val counts: Map<Kind, Int>) {
        val changed: Boolean get() = counts.values.any { it > 0 }
        /** Categories that were redacted, for the "needs confirmation" prompt. */
        val kinds: Set<Kind> get() = counts.filterValues { it > 0 }.keys
    }

    private const val TAG_CARD = "[REDACTED:card]"
    private const val TAG_IBAN = "[REDACTED:iban]"
    private const val TAG_ID = "[REDACTED:national_id]"
    private const val TAG_OTP = "[REDACTED:otp]"

    private val CARD_CANDIDATE = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")
    private val IBAN = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b""")
    private val NATIONAL_ID = Regex("""(?<!\d)[12]\d{9}(?!\d)""")
    private val OTP_KEYWORD_NEAR = Regex(
        """(?i)(otp|one[- ]time|verification code|verify code|code is|passcode|""" +
            """رمز|كود|الرمز|التحقق|تحقق|للتحقق)""",
    )
    private val OTP_DIGITS = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    /**
     * Redacts all categories. Order matters: cards and IBANs first, then national IDs,
     * then OTP digits (which need a keyword nearby).
     */
    fun redact(input: String?): Result {
        if (input.isNullOrEmpty()) return Result(input.orEmpty(), emptyMap())
        val counts = mutableMapOf<Kind, Int>()
        var text = input

        text = redactCards(text, counts)

        text = IBAN.replace(text) { m ->
            if (ibanValid(m.value)) {
                counts[Kind.IBAN] = (counts[Kind.IBAN] ?: 0) + 1
                TAG_IBAN
            } else m.value
        }

        text = NATIONAL_ID.replace(text) { m ->
            counts[Kind.NATIONAL_ID] = (counts[Kind.NATIONAL_ID] ?: 0) + 1
            TAG_ID
        }

        text = redactOtp(text, counts)

        return Result(text, Kind.values().associateWith { counts[it] ?: 0 })
    }

    /**
     * Card pass. A greedy separator-tolerant match can swallow a Luhn-valid card that sits
     * next to another digit (e.g. "card2 4111..." matches "2 4111..."). So the pass tries
     * every digit-run start position and every end position in 13..19 digits, and redacts
     * the longest Luhn-valid candidate starting at each position that is not inside an
     * already-redacted span.
     */
    private fun redactCards(input: String, counts: MutableMap<Kind, Int>): String {
        val out = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (!input[i].isDigit() || (i > 0 && input[i - 1].isDigit())) {
                out.append(input[i]); i++; continue
            }
            // Try the longest candidate first, then shorter, starting at i.
            var matchedEnd = -1
            var j = i
            val digitIdx = ArrayList<Int>()
            while (j < input.length && digitIdx.size < 19) {
                if (input[j].isDigit()) digitIdx.add(j)
                else if (input[j] != ' ' && input[j] != '-') break
                j++
                if (digitIdx.size in 13..19) {
                    val digits = digitIdx.joinToString("") { input[it].toString() }
                    val lastDigitEnd = digitIdx.last() + 1
                    val nextIsDigit = lastDigitEnd < input.length && input[lastDigitEnd].isDigit()
                    if (!nextIsDigit && luhnValid(digits)) matchedEnd = lastDigitEnd
                }
            }
            if (matchedEnd > 0) {
                counts[Kind.CARD] = (counts[Kind.CARD] ?: 0) + 1
                out.append(TAG_CARD)
                i = matchedEnd
            } else {
                out.append(input[i]); i++
            }
        }
        return out.toString()
    }

    /** OTP digits are redacted only when an OTP keyword appears within 40 chars. */
    private fun redactOtp(text: String, counts: MutableMap<Kind, Int>): String {
        val keywordPositions = OTP_KEYWORD_NEAR.findAll(text).map { it.range.first }.toList()
        if (keywordPositions.isEmpty()) return text
        return OTP_DIGITS.replace(text) { m ->
            val near = keywordPositions.any { kw -> kotlin.math.abs(kw - m.range.first) <= 40 }
            if (near && !m.value.startsWith("[")) {
                counts[Kind.OTP] = (counts[Kind.OTP] ?: 0) + 1
                TAG_OTP
            } else m.value
        }
    }

    /** Luhn checksum. Input: digits only. */
    fun luhnValid(digits: String): Boolean {
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return false
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** IBAN mod-97 check (ISO 13616). Input: compact IBAN, uppercase letters and digits. */
    fun ibanValid(iban: String): Boolean {
        val s = iban.uppercase()
        if (s.length < 15 || s.length > 34) return false
        if (!s.take(2).all { it in 'A'..'Z' } || !s.substring(2, 4).all { it.isDigit() }) return false
        val rearranged = s.substring(4) + s.substring(0, 4)
        var remainder = 0
        for (ch in rearranged) {
            val expanded = if (ch.isDigit()) ch.toString() else (ch - 'A' + 10).toString()
            for (c in expanded) {
                remainder = (remainder * 10 + (c - '0')) % 97
            }
        }
        return remainder == 1
    }
}
