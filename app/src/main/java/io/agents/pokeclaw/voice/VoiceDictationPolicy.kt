// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

/**
 * Decision logic for voice dictation. Pure + unit-tested.
 */
object VoiceDictationPolicy {

    /**
     * Auto-send is allowed only when the user enabled it, there is a transcript,
     * the composer accepts input, no reply/task is in flight, and the text is
     * not just whitespace. Otherwise the transcript is left in the box for editing.
     */
    fun shouldAutoSend(
        autoSendEnabled: Boolean,
        transcript: String,
        inputEnabled: Boolean,
        isAwaitingReply: Boolean,
        isTaskRunning: Boolean = false,
    ): Boolean = autoSendEnabled &&
        transcript.isNotBlank() &&
        inputEnabled &&
        !isAwaitingReply &&
        !isTaskRunning
}
