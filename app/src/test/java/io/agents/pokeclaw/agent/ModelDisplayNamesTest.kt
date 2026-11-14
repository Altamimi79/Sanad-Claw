// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNamesTest {

    @Test
    fun knownLocalModelUsesFriendlyName() {
        assertEquals("Qwen 3.8 Flash-Next", ModelDisplayNames.forModelId("qwen3.8-flash-next-iq3_s"))
    }

    @Test
    fun providerPrefixedIdStillResolves() {
        assertEquals("Qwen 3.8 Flash-Next", ModelDisplayNames.forModelId("local/qwen3.8-flash-next-iq3_s"))
    }

    @Test
    fun unknownModelKeepsRawId() {
        assertEquals("deepseek/deepseek-v4.1-flash-1017", ModelDisplayNames.forModelId("deepseek/deepseek-v4.1-flash-1017"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", ModelDisplayNames.forModelId(""))
        assertEquals(" ", ModelDisplayNames.forModelId(" "))
    }
}
