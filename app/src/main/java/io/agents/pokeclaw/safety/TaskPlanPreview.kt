// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.safety

/**
 * Pure model for the "preview before execution" gate (roadmap item 5).
 *
 * A task is first turned into a [Plan] of human-readable steps. The plan is shown to the
 * user, and the agent loop starts only after an explicit approval. [Decision] is the only
 * way to move from preview to execution; anything else (deny, timeout, empty plan) keeps
 * the task from running.
 */
object TaskPlanPreview {

    /** One planned step, phrased for the user. [sensitive] marks steps that need confirmation. */
    data class Step(val index: Int, val text: String, val sensitive: Boolean)

    data class Plan(val steps: List<Step>) {
        val isEmpty: Boolean get() = steps.isEmpty()
        val hasSensitiveSteps: Boolean get() = steps.any { it.sensitive }
    }

    enum class Decision { APPROVE, DENY, TIMEOUT }

    /** Result of the gate: execute only on [Outcome.RUN]. */
    enum class Outcome { RUN, BLOCKED_EMPTY, BLOCKED_DENIED }

    /** Upper bound on plan length, so a runaway planner cannot flood the preview screen. */
    const val MAX_STEPS = 12

    private const val MAX_STEP_CHARS = 200

    /**
     * Builds a plan from raw planner lines. Blank lines are dropped, each step is trimmed,
     * control and bidi characters are stripped, and the list is capped at [MAX_STEPS].
     * A step is marked sensitive when [isSensitive] returns true for its text.
     */
    fun buildPlan(rawSteps: List<String>, isSensitive: (String) -> Boolean): Plan {
        val cleaned = rawSteps
            .map { sanitize(it) }
            .filter { it.isNotEmpty() }
            .take(MAX_STEPS)
        val steps = cleaned.mapIndexed { i, text ->
            Step(index = i + 1, text = text, sensitive = isSensitive(text))
        }
        return Plan(steps)
    }

    /**
     * Gate decision. Runs only after an explicit approval of a non-empty plan.
     * Timeout and denial both block execution.
     */
    fun gate(plan: Plan, decision: Decision): Outcome {
        if (plan.isEmpty) return Outcome.BLOCKED_EMPTY
        return when (decision) {
            Decision.APPROVE -> Outcome.RUN
            Decision.DENY, Decision.TIMEOUT -> Outcome.BLOCKED_DENIED
        }
    }

    /** Removes control, bidi-override and zero-width characters, collapses spaces, caps length. */
    internal fun sanitize(raw: String): String {
        val noControl = buildString {
            for (ch in raw) {
                val type = Character.getType(ch)
                val bad = type == Character.CONTROL.toInt() ||
                    type == Character.FORMAT.toInt() && ch != '\u200D'
                if (!bad) append(ch)
            }
        }
        val collapsed = noControl.replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length > MAX_STEP_CHARS) collapsed.take(MAX_STEP_CHARS) else collapsed
    }
}
