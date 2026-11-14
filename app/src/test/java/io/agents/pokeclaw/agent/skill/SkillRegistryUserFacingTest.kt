// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRegistryUserFacingTest {

    @Test
    fun builtInSkills_includeUserFacingEntries() {
        SkillRegistry.clear()
        SkillRegistry.loadBuiltInSkills()
        val userFacing = SkillRegistry.getUserFacing()
        assertEquals(9, userFacing.size)
    }

    @Test
    fun eachUserFacingSkill_hasExamplePhrase() {
        SkillRegistry.clear()
        SkillRegistry.loadBuiltInSkills()
        SkillRegistry.getUserFacing().forEach { skill ->
            assertTrue("missing userExample for ${skill.id}", skill.userExample.isNotBlank())
        }
    }
}
