// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.safety.VoiceCommandParser.Command
import io.agents.pokeclaw.safety.VoiceCommandPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the routing that ChatScreen.handleVoiceFinal depends on:
 * ordinary speech parses as NONE, handleVoiceCommand then returns false, and the
 * original dictation path (appendTranscript + auto-send gate) runs unchanged.
 * A NONE result must never reach an EXECUTE_* or REJECT_* decision.
 */
class VoiceDictationRoutingTest {

    private val ordinarySpeech = listOf(
        "hello how are you",
        "افتح الإعدادات",
        "please stop the music",
        "لا تتوقف سند عن العمل",
    )

    @Test
    fun ordinarySpeechParsesAsNone() {
        ordinarySpeech.forEach { assertEquals(it, Command.NONE, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun ordinarySpeechNeverProducesAnActionDecision() {
        val states = listOf(
            Pair(false, false),
            Pair(true, false),
            Pair(true, true),
        )
        for ((task, pending) in states) {
            ordinarySpeech.forEach { speech ->
                val decision = VoiceCommandPolicy.decide(
                    VoiceCommandParser.parse(speech), taskActive = task, confirmationPending = pending,
                )
                assertEquals("speech='$speech' task=$task pending=$pending", Decision.IGNORE, decision)
            }
        }
    }
}
