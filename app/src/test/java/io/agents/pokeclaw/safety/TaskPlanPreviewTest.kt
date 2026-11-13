// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.safety.TaskPlanPreview.Decision
import io.agents.pokeclaw.safety.TaskPlanPreview.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPlanPreviewTest {

    private val noSensitive: (String) -> Boolean = { false }

    @Test
    fun buildPlan_dropsBlankLinesAndNumbersSteps() {
        val plan = TaskPlanPreview.buildPlan(listOf("  open settings  ", "", "   ", "tap wifi"), noSensitive)
        assertEquals(2, plan.steps.size)
        assertEquals(1, plan.steps[0].index)
        assertEquals("open settings", plan.steps[0].text)
        assertEquals(2, plan.steps[1].index)
        assertEquals("tap wifi", plan.steps[1].text)
    }

    @Test
    fun buildPlan_capsStepCount() {
        val raw = (1..30).map { "step $it" }
        val plan = TaskPlanPreview.buildPlan(raw, noSensitive)
        assertEquals(TaskPlanPreview.MAX_STEPS, plan.steps.size)
        assertEquals("step ${TaskPlanPreview.MAX_STEPS}", plan.steps.last().text)
    }

    @Test
    fun buildPlan_marksSensitiveSteps() {
        val plan = TaskPlanPreview.buildPlan(
            listOf("read screen", "send message to Mom"),
            { it.contains("send") },
        )
        assertFalse(plan.steps[0].sensitive)
        assertTrue(plan.steps[1].sensitive)
        assertTrue(plan.hasSensitiveSteps)
    }

    @Test
    fun buildPlan_stripsControlAndBidiCharacters() {
        val plan = TaskPlanPreview.buildPlan(listOf("open\u202E app\u0000\u200B"), noSensitive)
        assertEquals("open app", plan.steps[0].text)
    }

    @Test
    fun buildPlan_truncatesLongSteps() {
        val long = "a".repeat(500)
        val plan = TaskPlanPreview.buildPlan(listOf(long), noSensitive)
        assertEquals(200, plan.steps[0].text.length)
    }

    @Test
    fun emptyPlan_isBlockedEvenIfApproved() {
        val plan = TaskPlanPreview.buildPlan(listOf("", "  "), noSensitive)
        assertTrue(plan.isEmpty)
        assertEquals(Outcome.BLOCKED_EMPTY, TaskPlanPreview.gate(plan, Decision.APPROVE))
    }

    @Test
    fun approveRuns_denyAndTimeoutBlock() {
        val plan = TaskPlanPreview.buildPlan(listOf("open settings"), noSensitive)
        assertEquals(Outcome.RUN, TaskPlanPreview.gate(plan, Decision.APPROVE))
        assertEquals(Outcome.BLOCKED_DENIED, TaskPlanPreview.gate(plan, Decision.DENY))
        assertEquals(Outcome.BLOCKED_DENIED, TaskPlanPreview.gate(plan, Decision.TIMEOUT))
    }

    @Test
    fun noSensitiveSteps_reportsFalse() {
        val plan = TaskPlanPreview.buildPlan(listOf("open settings"), noSensitive)
        assertFalse(plan.hasSensitiveSteps)
    }
}
