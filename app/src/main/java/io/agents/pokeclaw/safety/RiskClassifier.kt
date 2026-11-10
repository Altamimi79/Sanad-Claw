// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Classifies agent actions that need an explicit user confirmation.
 * Pure JVM, unit-tested. Never depends on Android or the LLM.
 */
enum class RiskKind {
    SEND_MESSAGE,
    SEND_FILE,
    CALL,
    DELETE,
    PAYMENT,
    INSTALL,
    PUBLISH,
    UNKNOWN,
}

data class RiskAssessment(
    val risky: Boolean,
    val kind: RiskKind?,
    val requiresConfirmation: Boolean,
    val label: String,
)

object RiskClassifier {

    private const val MAX_TEXT_SCAN = 600

    private val riskyTools: Map<String, RiskKind> = mapOf(
        "send_message" to RiskKind.SEND_MESSAGE,
        "send_file" to RiskKind.SEND_FILE,
        "make_call" to RiskKind.CALL,
        "search_app_in_store" to RiskKind.INSTALL,
    )

    private val paymentWords = listOf(
        "pay", "payment", "checkout", "purchase", "buy now", "transfer", "wire", "bank transfer",
        "دفع", "سداد", "تحويل", "شراء", "فاتورة", "محفظة", "بطاقة", "رصيد",
    )
    private val deleteWords = listOf(
        "delete", "erase", "wipe", "remove all", "clear all", "uninstall",
        "حذف", "امسح", "مسح", "إزالة", "إلغاء التثبيت",
    )
    private val installWords = listOf("install", "تثبيت")
    private val publishWords = listOf("publish", "post", "tweet", "share publicly", "نشر", "تغريدة")

    /** Classify a tool call by name + its JSON-ish parameters. */
    fun assess(toolName: String, params: Map<String, Any?>): RiskAssessment {
        val normalizedTool = toolName.trim().lowercase()
        riskyTools[normalizedTool]?.let { kind ->
            return RiskAssessment(true, kind, true, labelFor(kind))
        }

        val text = flatten(params)
        return assessText(toolName, text)
    }

    /** Classifies free text (task prompt, typed text, tapped label). */
    fun assessText(toolName: String, text: String?): RiskAssessment {
        val haystack = (text ?: "").lowercase().take(MAX_TEXT_SCAN)
        if (haystack.isBlank()) return RiskAssessment(false, null, false, labelFor(null))

        return when {
            containsAny(haystack, paymentWords) ->
                RiskAssessment(true, RiskKind.PAYMENT, true, labelFor(RiskKind.PAYMENT))
            containsAny(haystack, deleteWords) ->
                RiskAssessment(true, RiskKind.DELETE, true, labelFor(RiskKind.DELETE))
            containsAny(haystack, installWords) && isStoreLike(toolName) ->
                RiskAssessment(true, RiskKind.INSTALL, true, labelFor(RiskKind.INSTALL))
            containsAny(haystack, publishWords) && isInputTool(toolName) ->
                RiskAssessment(true, RiskKind.PUBLISH, true, labelFor(RiskKind.PUBLISH))
            else -> RiskAssessment(false, null, false, labelFor(null))
        }
    }

    private fun isStoreLike(toolName: String): Boolean {
        val t = toolName.lowercase()
        return t.contains("store") || t.contains("install") || t == "open_app"
    }

    private fun isInputTool(toolName: String): Boolean {
        val t = toolName.lowercase()
        return t == "input_text" || t == "send_message"
    }

    private fun containsAny(haystack: String, words: List<String>): Boolean =
        words.any { haystack.contains(it) }

    private fun flatten(params: Map<String, Any?>): String {
        val sb = StringBuilder()
        params.forEach { (key, value) ->
            if (sb.length > MAX_TEXT_SCAN) return@forEach
            if (value is String) {
                sb.append(key).append('=').append(value).append(' ')
            } else if (value is Map<*, *>) {
                value.forEach { (k, v) -> if (v is String) sb.append(k).append('=').append(v).append(' ') }
            }
        }
        return sb.toString()
    }

    fun labelFor(kind: RiskKind?): String = when (kind) {
        RiskKind.SEND_MESSAGE -> "send_message"
        RiskKind.SEND_FILE -> "send_file"
        RiskKind.CALL -> "call"
        RiskKind.DELETE -> "delete"
        RiskKind.PAYMENT -> "payment"
        RiskKind.INSTALL -> "install"
        RiskKind.PUBLISH -> "publish"
        RiskKind.UNKNOWN, null -> "none"
    }
}
