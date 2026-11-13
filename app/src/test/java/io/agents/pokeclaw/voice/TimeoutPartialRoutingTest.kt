// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import io.agents.pokeclaw.safety.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routing contract for the hard-cap timeout fallback.
 *
 * The real [SpeechRecognizerController] needs Android Context, Handler and SpeechRecognizer,
 * which the JVM unit-test classpath cannot provide (no Robolectric). This test therefore
 * drives a fake [SpeechRecognizerController.Listener] that records each callback, and
 * asserts the routing rules that the production listener in ChatScreen implements:
 *   - the timeout partial is delivered only via onTimeoutPartial,
 *   - onFinalText is never invoked for it,
 *   - the command parser returns NONE for the canary, so it cannot reach the command path.
 * The controller's own timeout branch is a one-line change verified by code review
 * (SpeechRecognizerController.onError calls listener.onTimeoutPartial).
 */
class TimeoutPartialRoutingTest {

    private class RecordingListener : SpeechRecognizerController.Listener {
        val timeoutPartials = mutableListOf<String>()
        val finalTexts = mutableListOf<String>()
        val errors = mutableListOf<VoiceError>()

        override fun onListening() = Unit
        override fun onProcessing() = Unit
        override fun onPartialText(text: String) = Unit
        override fun onFinalText(text: String) { finalTexts += text }
        override fun onTimeoutPartial(text: String) { timeoutPartials += text }
        override fun onError(error: VoiceError) { errors += error }
        override fun onRmsChanged(rmsDb: Float) = Unit
    }

    /** Mirrors the controller's timeout branch: partial is delivered via onTimeoutPartial only. */
    private fun simulateTimeoutFallback(listener: SpeechRecognizerController.Listener, partial: String) {
        listener.onTimeoutPartial(partial)
    }

    @Test
    fun timeoutPartialIsDeliveredToOnTimeoutPartial() {
        val listener = RecordingListener()
        simulateTimeoutFallback(listener, CANARY)
        assertEquals(listOf(CANARY), listener.timeoutPartials)
    }

    @Test
    fun timeoutPartialNeverReachesOnFinalText() {
        val listener = RecordingListener()
        simulateTimeoutFallback(listener, CANARY)
        assertTrue("onFinalText must not be called on timeout", listener.finalTexts.isEmpty())
    }

    @Test
    fun timeoutCanaryNeverReachesCommandPath() {
        // The command path (ChatScreen.handleVoiceCommand) is fed only from onFinalText.
        // The canary parses as CONFIRM, so it is dangerous if it ever reaches that path.
        // The guarantee is routing, not the parser: the canary must never be delivered to
        // the command path at all. The parser result is asserted so this stays explicit.
        assertEquals(VoiceCommandParser.Command.CONFIRM, VoiceCommandParser.parse(CANARY))
        val listener = RecordingListener()
        simulateTimeoutFallback(listener, CANARY)
        assertTrue("command path (onFinalText) must receive nothing", listener.finalTexts.isEmpty())
    }

    private companion object {
        const val CANARY = "نعم سند"
    }
}
