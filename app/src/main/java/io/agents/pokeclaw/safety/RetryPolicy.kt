// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Bounded retry policy for transient tool failures. Pure JVM, unit-tested.
 *
 * Only read/navigation tools are retried: repeating a risky action after a
 * partial success could send a message twice or double-submit a payment.
 */
object RetryPolicy {

    const val MAX_ATTEMPTS: Int = 2

    private val RETRYABLE_TOOLS = setOf(
        "get_screen_info",
        "get_notifications",
        "open_app",
        "tap", "tap_node", "swipe", "input_text", "scroll_to_find",
    )

    private val TRANSIENT_MARKERS = listOf(
        "timeout", "timed out", "temporarily", "try again",
        "connection reset", "socket", "econnreset",
        "service unavailable", "503", "502", "504", "network",
        "انتهت المهلة", "مؤقت", "الشبكة",
    )

    fun isRetryableTool(toolName: String): Boolean =
        RETRYABLE_TOOLS.contains(toolName.trim().lowercase())

    fun isTransient(error: String?): Boolean {
        val text = error?.lowercase() ?: return false
        if (text.isBlank()) return false
        return TRANSIENT_MARKERS.any { text.contains(it) }
    }

    /** attempt starts at 1 for the first call. */
    fun shouldRetry(attempt: Int, toolName: String, error: String?): Boolean =
        attempt < MAX_ATTEMPTS &&
            isRetryableTool(toolName) &&
            isTransient(error) &&
            !RiskClassifier.assess(toolName, emptyMap()).requiresConfirmation

    fun backoffMs(attempt: Int): Long = 400L * attempt.coerceAtLeast(1)
}
