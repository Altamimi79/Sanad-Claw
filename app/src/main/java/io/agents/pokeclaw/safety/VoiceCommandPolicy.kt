// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Pure decision for a recognized voice command. The caller performs the action.
 *
 * - STOP runs only while a task is active; otherwise ignored.
 * - CONFIRM runs only when a confirmation is pending; otherwise rejected (user is told).
 * - NONE and any unrecognized/failed input never act.
 */
object VoiceCommandPolicy {

    enum class Decision {
        EXECUTE_STOP,
        EXECUTE_CONFIRM,
        REJECT_NO_PENDING,
        IGNORE_NO_TASK,
        IGNORE,
    }

    /**
     * @param command result of [VoiceCommandParser.parse]; a recognition failure must be
     *                passed as [VoiceCommandParser.Command.NONE].
     */
    /**
     * A confirmation is approvable by an utterance only when one was pending when listening
     * started (captured id, not null) and that same request is still pending now.
     * Pure; ChatScreen passes the captured id and the current pending id.
     */
    fun confirmationPendingFor(capturedId: Long?, currentPendingId: Long?): Boolean =
        capturedId != null && capturedId == currentPendingId

    fun decide(
        command: VoiceCommandParser.Command,
        taskActive: Boolean,
        confirmationPending: Boolean,
    ): Decision = when (command) {
        VoiceCommandParser.Command.STOP ->
            if (taskActive) Decision.EXECUTE_STOP else Decision.IGNORE_NO_TASK
        VoiceCommandParser.Command.CONFIRM ->
            if (confirmationPending) Decision.EXECUTE_CONFIRM else Decision.REJECT_NO_PENDING
        VoiceCommandParser.Command.NONE -> Decision.IGNORE
    }
}
