// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

/**
 * Recording timer formatting. Pure + unit-tested.
 */
object VoiceTimerFormat {

    /** mm:ss for a whole number of elapsed seconds; negatives clamp to 00:00. */
    fun format(elapsedSeconds: Int): String {
        val safe = elapsedSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** Elapsed seconds for a duration in millis, never negative. */
    fun elapsedSeconds(startedAtMs: Long, nowMs: Long): Int {
        val delta = nowMs - startedAtMs
        if (delta <= 0L) return 0
        return (delta / 1000L).toInt()
    }
}
