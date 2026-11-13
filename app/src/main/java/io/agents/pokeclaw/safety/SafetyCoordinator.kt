// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges the agent worker thread (blocking) to the chat UI (Compose dialog).
 *
 * The worker calls [requestConfirmation] and blocks until the user taps allow/deny
 * or the timeout elapses. If no UI is available the request is denied immediately
 * (fail-safe: risky actions never run unattended).
 */
object SafetyCoordinator {

    private const val TAG = "SafetyCoordinator"

    data class ConfirmationRequest(
        val id: Long,
        val message: String,
        val risk: String,
    )

    private val ids = AtomicLong(0)
    private val _pending = MutableStateFlow<ConfirmationRequest?>(null)
    val pending: StateFlow<ConfirmationRequest?> = _pending.asStateFlow()

    @Volatile
    private var uiAvailable = false

    private var latch: CountDownLatch? = null

    @Volatile
    private var approved = false

    fun setUiAvailable(available: Boolean) {
        uiAvailable = available
    }

    fun isUiAvailable(): Boolean = uiAvailable

    fun requestConfirmation(message: String, risk: String, timeoutMs: Long = 45_000L): Boolean {
        if (!uiAvailable) return false
        val request = ConfirmationRequest(ids.incrementAndGet(), message, risk)
        val newLatch = CountDownLatch(1)
        synchronized(this) {
            latch = newLatch
            approved = false
            _pending.value = request
        }
        return try {
            val completed = newLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                _pending.value = null
                false
            } else {
                approved
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } finally {
            synchronized(this) {
                _pending.value = null
                latch = null
            }
        }
    }

    fun approvePending() = resolve(true)

    fun denyPending() = resolve(false)

    /** Id of the currently pending request, or null. Read-only; for the UI and voice path. */
    fun currentPendingId(): Long? = _pending.value?.id

    /**
     * Approves only if the currently pending request has exactly [id]. Runs under the same
     * lock as request creation and resolution, so a stale id can never resolve a newer
     * request. Returns false (and changes nothing) when nothing is pending or the id differs.
     */
    fun approvePendingIfMatches(id: Long): Boolean {
        synchronized(this) {
            val current = _pending.value ?: return false
            if (current.id != id) return false
            approved = true
            _pending.value = null
            latch?.countDown()
            return true
        }
    }

    private fun resolve(value: Boolean) {
        synchronized(this) {
            approved = value
            _pending.value = null
            latch?.countDown()
        }
    }
}
