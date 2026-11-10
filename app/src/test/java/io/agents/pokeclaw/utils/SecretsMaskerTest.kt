// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretsMaskerTest {

    @Test
    fun mask_keepsOnlyLastFourChars() {
        assertEquals("********cdef", SecretsMasker.mask("abcdefghcdef"))
    }

    @Test
    fun mask_emptyStaysEmpty_shortSecretUnchanged() {
        assertEquals("", SecretsMasker.mask(""))
        assertEquals("abcd", SecretsMasker.mask("abcd"))
        assertEquals("abc", SecretsMasker.mask("abc"))
    }

    @Test
    fun isMasked_detectsMaskedRoundTrip() {
        val masked = SecretsMasker.mask("sk-supersecretvalue1234")
        assertTrue(SecretsMasker.isMasked(masked))
        assertFalse(SecretsMasker.isMasked("sk-supersecretvalue1234"))
    }

    @Test
    fun mask_neverLeaksMiddleCharacters() {
        val secret = "sk-proj-abcdefghijklmnop"
        val masked = SecretsMasker.mask(secret)
        assertEquals(secret.length, masked.length)
        assertTrue(masked.startsWith("*".repeat(secret.length - 4)))
        assertTrue(masked.endsWith("mnop"))
    }
}
