// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDictationPolicyTest {

    @Test
    fun autoSendRequiresSettingOn() {
        assertFalse(
            VoiceDictationPolicy.shouldAutoSend(
                autoSendEnabled = false,
                transcript = "مرحبا",
                inputEnabled = true,
                isAwaitingReply = false,
            )
        )
        assertTrue(
            VoiceDictationPolicy.shouldAutoSend(
                autoSendEnabled = true,
                transcript = "مرحبا",
                inputEnabled = true,
                isAwaitingReply = false,
            )
        )
    }

    @Test
    fun blankTranscriptNeverSends() {
        assertFalse(
            VoiceDictationPolicy.shouldAutoSend(
                autoSendEnabled = true,
                transcript = "   ",
                inputEnabled = true,
                isAwaitingReply = false,
            )
        )
    }

    @Test
    fun blockedComposerNeverSends() {
        assertFalse(
            VoiceDictationPolicy.shouldAutoSend(
                autoSendEnabled = true,
                transcript = "مرحبا",
                inputEnabled = false,
                isAwaitingReply = false,
            )
        )
    }

    @Test
    fun pendingReplyNeverSends() {
        assertFalse(
            VoiceDictationPolicy.shouldAutoSend(
                autoSendEnabled = true,
                transcript = "مرحبا",
                inputEnabled = true,
                isAwaitingReply = true,
            )
        )
    }

    @Test
    fun runningTaskNeverSends() {
        assertFalse(
            VoiceDictationPolicy.shouldAutoSend(
                autoSendEnabled = true,
                transcript = "مرحبا",
                inputEnabled = true,
                isAwaitingReply = false,
                isTaskRunning = true,
            )
        )
    }
}
