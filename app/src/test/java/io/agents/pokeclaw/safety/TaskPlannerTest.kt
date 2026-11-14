// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPlannerTest {

    @Test
    fun parsesNumberedAndBulletedSteps() {
        val reply = "1. Open Settings\n2) Tap Wi-Fi\n- Toggle off\n* Confirm"
        assertEquals(
            listOf("Open Settings", "Tap Wi-Fi", "Toggle off", "Confirm"),
            TaskPlanner.parseSteps(reply),
        )
    }

    @Test
    fun ignoresProseLines() {
        val reply = "Sure, here is the plan:\n1. Open the app\nHope this helps!\n2. Search"
        assertEquals(listOf("Open the app", "Search"), TaskPlanner.parseSteps(reply))
    }

    @Test
    fun emptyOrNullReply_yieldsNoSteps() {
        assertTrue(TaskPlanner.parseSteps(null).isEmpty())
        assertTrue(TaskPlanner.parseSteps("   ").isEmpty())
        assertTrue(TaskPlanner.parseSteps("no list here").isEmpty())
    }

    @Test
    fun capsSteps() {
        val reply = (1..40).joinToString("\n") { "$it. step $it" }
        assertEquals(TaskPlanPreview.MAX_STEPS, TaskPlanner.parseSteps(reply).size)
    }

    @Test
    fun arabicStepsAreKept() {
        val reply = "1. افتح الإعدادات\n2. فعّل الواي فاي"
        assertEquals(listOf("افتح الإعدادات", "فعّل الواي فاي"), TaskPlanner.parseSteps(reply))
    }

    @Test
    fun userMessage_trimsAndWrapsTask() {
        val msg = TaskPlanner.buildUserMessage("  send hi to Mom  ")
        assertTrue(msg.startsWith("Request: send hi to Mom"))
        assertTrue(msg.endsWith("List the steps."))
    }

    @Test
    fun instructionForbidsExecution() {
        assertTrue(TaskPlanner.PLANNING_INSTRUCTION.contains("Do NOT execute"))
        assertEquals(true, TaskPlanner.PLANNING_INSTRUCTION.contains(TaskPlanPreview.MAX_STEPS.toString()))
    }
}
