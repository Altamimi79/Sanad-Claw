// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.safety

/**
 * Builds the planning prompt and parses the model reply into raw step lines.
 *
 * Pure functions only: the model call itself is made by the agent service, so this file has
 * no network or Android dependency and can be unit-tested. Parsing is deliberately strict:
 * anything that is not a numbered or bulleted line is ignored, so free-form prose from the
 * model can never become an executable step.
 */
object TaskPlanner {

    /** System instruction for the planning call. Asks for steps only, no execution. */
    const val PLANNING_INSTRUCTION =
        "You plan phone actions. Do NOT execute anything. Reply with a short numbered list " +
        "of the steps needed to complete the user's request, one step per line, at most " +
        "${TaskPlanPreview.MAX_STEPS} steps. No prose before or after the list."

    /** Builds the user message sent to the planner for [task]. */
    fun buildUserMessage(task: String): String =
        "Request: " + task.trim() + "\n\nList the steps."

    private val STEP_LINE = Regex("""^\s*(?:\d{1,2}[.)]|[-*•])\s+(.+?)\s*$""")

    /**
     * Extracts step texts from a model reply. Only lines that begin with a number or a bullet
     * count as steps. Lines are returned untrimmed of meaning; sanitizing is done by
     * [TaskPlanPreview.buildPlan].
     */
    fun parseSteps(reply: String?): List<String> {
        if (reply.isNullOrBlank()) return emptyList()
        return reply.lineSequence()
            .mapNotNull { line -> STEP_LINE.find(line)?.groupValues?.get(1) }
            .take(TaskPlanPreview.MAX_STEPS)
            .toList()
    }
}
