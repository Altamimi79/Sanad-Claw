// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.safety.VoiceCommandParser.Command
import io.agents.pokeclaw.safety.VoiceCommandPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceCommandPolicyTest {

    @Test
    fun stopWhileTaskActiveExecutesStop() {
        assertEquals(
            Decision.EXECUTE_STOP,
            VoiceCommandPolicy.decide(Command.STOP, taskActive = true, confirmationPending = false),
        )
    }

    @Test
    fun stopWhileNoTaskIgnored() {
        assertEquals(
            Decision.IGNORE_NO_TASK,
            VoiceCommandPolicy.decide(Command.STOP, taskActive = false, confirmationPending = false),
        )
    }

    @Test
    fun confirmWhenPendingExecutesConfirm() {
        assertEquals(
            Decision.EXECUTE_CONFIRM,
            VoiceCommandPolicy.decide(Command.CONFIRM, taskActive = true, confirmationPending = true),
        )
    }

    @Test
    fun confirmWhenNotPendingIsRejectedNotExecuted() {
        val decision = VoiceCommandPolicy.decide(
            Command.CONFIRM, taskActive = true, confirmationPending = false,
        )
        assertEquals(Decision.REJECT_NO_PENDING, decision)
        // Only EXECUTE_CONFIRM produces an approve call; REJECT_NO_PENDING must not.
        assertNotEquals(Decision.EXECUTE_CONFIRM, decision)
    }

    @Test
    fun confirmWhenNoTaskAndNoPendingIsRejected() {
        assertEquals(
            Decision.REJECT_NO_PENDING,
            VoiceCommandPolicy.decide(Command.CONFIRM, taskActive = false, confirmationPending = false),
        )
    }

    @Test
    fun noneIsAlwaysIgnored() {
        for (task in listOf(true, false)) {
            for (pending in listOf(true, false)) {
                assertEquals(
                    Decision.IGNORE,
                    VoiceCommandPolicy.decide(Command.NONE, taskActive = task, confirmationPending = pending),
                )
            }
        }
    }

    @Test
    fun recognitionFailureNeverConfirms() {
        // A recognition failure (timeout, no service, error) is passed as Command.NONE.
        // Even with a confirmation pending, it must never produce EXECUTE_CONFIRM.
        val decision = VoiceCommandPolicy.decide(
            Command.NONE, taskActive = true, confirmationPending = true,
        )
        assertEquals(Decision.IGNORE, decision)
        assertNotEquals(Decision.EXECUTE_CONFIRM, decision)
    }
}
