// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceErrorTest {

    @Test
    fun everyErrorHasNonBlankResName() {
        VoiceError.entries.forEach { error ->
            assertTrue("missing key for $error", error.messageKey.isNotBlank())
            assertTrue("key must be a string resource name: ${error.messageKey}", error.messageKey.startsWith("voice_error_"))
        }
    }

    @Test
    fun messageKeysAreUnique() {
        val keys = VoiceError.entries.map { it.messageKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun fromMessageKeyRoundTrips() {
        VoiceError.entries.forEach { error ->
            assertEquals(error, VoiceError.fromMessageKey(error.messageKey))
        }
        assertNull(VoiceError.fromMessageKey("voice_error_does_not_exist"))
    }
}
