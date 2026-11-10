// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCoordinatorTest {

    @Test
    fun failsSafeWhenNoUiIsAvailable() {
        SafetyCoordinator.setUiAvailable(false)
        assertFalse(SafetyCoordinator.requestConfirmation("allow send?", "send_message", timeoutMs = 50))
    }

    @Test
    fun approvalResolvesBlockingRequest() {
        SafetyCoordinator.setUiAvailable(true)
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread {
            result.set(SafetyCoordinator.requestConfirmation("allow?", "send_message", timeoutMs = 2_000))
        }
        worker.start()
        // Wait until the dialog request is published, then approve it.
        val deadline = System.currentTimeMillis() + 2_000
        while (SafetyCoordinator.pending.value == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        SafetyCoordinator.approvePending()
        worker.join(2_000)
        assertTrue(result.get())
        assertFalse(worker.isAlive)
        SafetyCoordinator.setUiAvailable(false)
    }
}
