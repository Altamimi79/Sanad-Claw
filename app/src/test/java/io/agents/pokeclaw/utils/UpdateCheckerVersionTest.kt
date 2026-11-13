// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {

    @Test
    fun `newer patch release is an update`() {
        assertTrue(UpdateChecker.isNewer("1.0.3", "1.0.2"))
    }

    @Test
    fun `older release is not an update over a newer dev build`() {
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.3-dev-5"))
    }

    @Test
    fun `stable release beats a prerelease of the same base`() {
        assertTrue(UpdateChecker.isNewer("1.0.3", "1.0.3-dev-5"))
    }

    @Test
    fun `prerelease is not an update over the stable release`() {
        assertFalse(UpdateChecker.isNewer("1.0.3-dev-5", "1.0.3"))
    }

    @Test
    fun `major bump is an update`() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `equal versions are not an update`() {
        assertFalse(UpdateChecker.isNewer("1.0.3", "1.0.3"))
    }
}