// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Hard wall-clock budget for a single agent task. Pure JVM, unit-tested.
 */
object TaskTimeoutPolicy {

    const val DEFAULT_TIMEOUT_SEC: Int = 180
    val ALLOWED_TIMEOUT_SEC: List<Int> = listOf(120, 180, 300, 600)

    fun timeoutMs(seconds: Int): Long = seconds.coerceIn(30, 3_600) * 1000L

    fun remainingMs(startedAtMs: Long, nowMs: Long, timeoutMs: Long): Long =
        (timeoutMs - (nowMs - startedAtMs)).coerceAtLeast(0L)

    fun isTimedOut(startedAtMs: Long, nowMs: Long, timeoutMs: Long): Boolean =
        nowMs - startedAtMs >= timeoutMs

    fun nextPreset(currentSeconds: Int): Int {
        val idx = ALLOWED_TIMEOUT_SEC.indexOf(currentSeconds)
        return if (idx < 0) DEFAULT_TIMEOUT_SEC
        else ALLOWED_TIMEOUT_SEC[(idx + 1) % ALLOWED_TIMEOUT_SEC.size]
    }
}
