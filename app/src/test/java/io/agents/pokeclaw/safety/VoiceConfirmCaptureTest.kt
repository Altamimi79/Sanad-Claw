// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.safety.VoiceCommandPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure routing for the captured-confirmation-id rule used by ChatScreen:
 * a confirmation is approvable by an utterance only if it was pending when listening
 * started and is still the same request.
 */
class VoiceConfirmCaptureTest {

    @Test
    fun confirmWithCapturedIdNullIsRejected() {
        // Pending appeared after listening started: captured id is null.
        val pending = VoiceCommandPolicy.confirmationPendingFor(capturedId = null, currentPendingId = 7L)
        assertFalse(pending)
        val decision = VoiceCommandPolicy.decide(
            VoiceCommandParser.Command.CONFIRM, taskActive = true, confirmationPending = pending,
        )
        assertEquals(Decision.REJECT_NO_PENDING, decision)
    }

    @Test
    fun confirmWithCapturedIdMatchingCurrentIsExecuted() {
        val pending = VoiceCommandPolicy.confirmationPendingFor(capturedId = 7L, currentPendingId = 7L)
        assertTrue(pending)
        val decision = VoiceCommandPolicy.decide(
            VoiceCommandParser.Command.CONFIRM, taskActive = true, confirmationPending = pending,
        )
        assertEquals(Decision.EXECUTE_CONFIRM, decision)
    }

    @Test
    fun confirmWithCapturedIdDifferentFromCurrentIsRejected() {
        // Captured request A (id 7) timed out; newer request B (id 8) is pending now.
        val pending = VoiceCommandPolicy.confirmationPendingFor(capturedId = 7L, currentPendingId = 8L)
        assertFalse(pending)
        val decision = VoiceCommandPolicy.decide(
            VoiceCommandParser.Command.CONFIRM, taskActive = true, confirmationPending = pending,
        )
        assertEquals(Decision.REJECT_NO_PENDING, decision)
    }
}
