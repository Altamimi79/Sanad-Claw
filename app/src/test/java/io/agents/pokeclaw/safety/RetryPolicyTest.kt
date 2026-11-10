// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun transientErrorsAreDetected() {
        assertTrue(RetryPolicy.isTransient("Socket timeout"))
        assertTrue(RetryPolicy.isTransient("connection reset by peer"))
        assertTrue(RetryPolicy.isTransient("service unavailable 503"))
        assertTrue(RetryPolicy.isTransient("انتهت المهلة"))
        assertFalse(RetryPolicy.isTransient("unknown permission denied"))
        assertFalse(RetryPolicy.isTransient(null))
        assertFalse(RetryPolicy.isTransient(""))
    }

    @Test
    fun onlySafeToolsAreRetryable() {
        assertTrue(RetryPolicy.isRetryableTool("get_screen_info"))
        assertTrue(RetryPolicy.isRetryableTool("tap"))
        assertFalse(RetryPolicy.isRetryableTool("send_message"))
        assertFalse(RetryPolicy.isRetryableTool("make_call"))
        assertFalse(RetryPolicy.isRetryableTool("finish"))
    }

    @Test
    fun retryIsBounded() {
        assertTrue(RetryPolicy.shouldRetry(1, "tap", "timeout"))
        assertFalse(RetryPolicy.shouldRetry(RetryPolicy.MAX_ATTEMPTS, "tap", "timeout"))
        assertFalse(RetryPolicy.shouldRetry(1, "tap", "hard failure"))
        assertFalse(RetryPolicy.shouldRetry(1, "send_message", "timeout"))
    }

    @Test
    fun backoffGrows() {
        assertEquals(400L, RetryPolicy.backoffMs(1))
        assertEquals(800L, RetryPolicy.backoffMs(2))
        assertEquals(400L, RetryPolicy.backoffMs(0))
    }
}
