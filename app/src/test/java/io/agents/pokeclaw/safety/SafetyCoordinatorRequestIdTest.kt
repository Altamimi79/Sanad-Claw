// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SafetyCoordinatorRequestIdTest {

    @Before
    fun setUp() {
        SafetyCoordinator.setUiAvailable(true)
    }

    @After
    fun tearDown() {
        // Resolve anything left pending so no worker thread leaks into other tests.
        SafetyCoordinator.denyPending()
        SafetyCoordinator.setUiAvailable(false)
    }

    /** Starts a blocking request on a worker thread and waits until it is published. */
    private fun startRequest(timeoutMs: Long): Pair<Thread, AtomicBoolean> {
        val result = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val worker = Thread {
            started.countDown()
            result.set(SafetyCoordinator.requestConfirmation("allow?", "send_message", timeoutMs))
        }
        worker.start()
        started.await(2, TimeUnit.SECONDS)
        val deadline = System.currentTimeMillis() + 2_000
        while (SafetyCoordinator.pending.value == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertNotNull("request should be pending", SafetyCoordinator.pending.value)
        return Pair(worker, result)
    }

    @Test
    fun approveIfMatchesApprovesTheMatchingRequest() {
        val (worker, result) = startRequest(timeoutMs = 2_000)
        val id = SafetyCoordinator.currentPendingId()
        assertNotNull(id)
        assertTrue(SafetyCoordinator.approvePendingIfMatches(id!!))
        worker.join(2_000)
        assertTrue(result.get())
        assertNull(SafetyCoordinator.pending.value)
    }

    @Test
    fun approveIfMatchesAfterTimeoutIsNoOp() {
        val (worker, result) = startRequest(timeoutMs = 50)
        val staleId = SafetyCoordinator.currentPendingId()!!
        worker.join(2_000)
        assertFalse("timed-out request must be denied", result.get())
        assertFalse(SafetyCoordinator.approvePendingIfMatches(staleId))
        assertNull(SafetyCoordinator.pending.value)
    }

    @Test
    fun approveWithStaleIdDoesNotApproveNewerRequest() {
        val (workerA, resultA) = startRequest(timeoutMs = 50)
        val idA = SafetyCoordinator.currentPendingId()!!
        workerA.join(2_000)
        assertFalse(resultA.get())

        val (workerB, resultB) = startRequest(timeoutMs = 2_000)
        val idB = SafetyCoordinator.currentPendingId()!!
        assertTrue("B must have a different id", idA != idB)

        assertFalse("stale id A must not resolve B", SafetyCoordinator.approvePendingIfMatches(idA))
        assertEquals(idB, SafetyCoordinator.pending.value?.id)
        assertTrue("B must still be unresolved", workerB.isAlive)

        // Cleanup: deny B through the dialog path.
        SafetyCoordinator.denyPending()
        workerB.join(2_000)
        assertFalse(resultB.get())
    }

    @Test
    fun approveIfMatchesWithNothingPendingReturnsFalse() {
        assertNull(SafetyCoordinator.pending.value)
        assertNull(SafetyCoordinator.currentPendingId())
        assertFalse(SafetyCoordinator.approvePendingIfMatches(12345L))
    }

    @Test
    fun existingApprovePendingBehaviorUnchanged() {
        val (worker, result) = startRequest(timeoutMs = 2_000)
        // Dialog-button path: approvePending() approves the current request, no id needed.
        SafetyCoordinator.approvePending()
        worker.join(2_000)
        assertTrue(result.get())
        assertFalse(worker.isAlive)
    }
}
