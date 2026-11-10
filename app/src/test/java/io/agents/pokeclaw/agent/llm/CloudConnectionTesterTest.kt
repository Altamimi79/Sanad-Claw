// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudConnectionTesterTest {

    @Test
    fun normalizeBaseUrl_addsSchemeAndStripsTrailingSlashes() {
        assertEquals("https://api.deepseek.com/v1", CloudConnectionTester.normalizeBaseUrl("api.deepseek.com/v1/"))
        assertEquals("https://api.openai.com/v1", CloudConnectionTester.normalizeBaseUrl("  https://api.openai.com/v1///  "))
    }

    @Test
    fun normalizeBaseUrl_stripsEndpointSuffixesLangchainAppends() {
        assertEquals("https://api.openai.com/v1", CloudConnectionTester.normalizeBaseUrl("https://api.openai.com/v1/chat/completions"))
        assertEquals("http://localhost:11434/v1", CloudConnectionTester.normalizeBaseUrl("http://localhost:11434/v1/chat/completions/"))
        assertEquals("https://api.openai.com/v1", CloudConnectionTester.normalizeBaseUrl("https://api.openai.com/v1/completions"))
        assertEquals("https://api.openai.com/v1", CloudConnectionTester.normalizeBaseUrl("https://api.openai.com/v1/responses"))
    }

    @Test
    fun normalizeBaseUrl_keepsHostWhenNoVersionPath() {
        assertEquals("https://my-proxy.example.com", CloudConnectionTester.normalizeBaseUrl("my-proxy.example.com/"))
    }

    @Test
    fun normalizeBaseUrl_blankStaysBlank() {
        assertEquals("", CloudConnectionTester.normalizeBaseUrl(""))
        assertEquals("", CloudConnectionTester.normalizeBaseUrl("   "))
    }

    @Test
    fun test_missingApiKeyIsUnauthorizedWithoutNetwork() {
        val result = CloudConnectionTester.test("OPENAI", "https://api.openai.com/v1", "", "gpt-4o-mini")
        assertEquals(CloudConnectionTester.Status.UNAUTHORIZED, result.status)
    }

    @Test
    fun test_missingBaseUrlIsGenericWithoutNetwork() {
        val result = CloudConnectionTester.test("CUSTOM", "", "sk-somekey", "some-model")
        assertEquals(CloudConnectionTester.Status.GENERIC, result.status)
    }
}
