// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import android.content.Context
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.R
import io.agents.pokeclaw.service.ClawAccessibilityService
import io.agents.pokeclaw.utils.XLog

/**
 * Android glue for the pre-execution safety gate: blocklist check + confirmation.
 * Pure decisions live in RiskClassifier/AppBlocklist/SafetySettings (unit-tested).
 */
object SafetyGate {

    private const val TAG = "SafetyGate"

    data class Decision(
        val allow: Boolean,
        val blockedByApp: Boolean,
        val message: String?,
        val assessment: RiskAssessment,
    )

    /**
     * Decision order for a tool call (Phase 2g item 4):
     *  1. Blocklist match: blocked, always (no switch can override it).
     *  2. Tainted task and sensitive kind: enforced by DefaultAgentService via ScreenContentGuard
     *     (taint is per-task state owned there). Not repeated here.
     *  3. kind == UNKNOWN: confirmation required, always.
     *  4. Otherwise: confirmation required only if SafetySettings.confirmationFor(kind).
     */
    fun evaluate(toolName: String, params: Map<String, Any?>, taskPrompt: String): Decision {
        val app = ClawApplication.instance
        val patterns = SafetySettings.blocklistPatterns()
        val pkg = currentForegroundPackage()
        val label = pkg?.let { safeAppLabel(it) }

        // Rule 1: blocklist, always enforced.
        if (pkg != null && AppBlocklist.isBlocked(patterns, pkg, label)) {
            val shown = label ?: pkg
            val msg = app.getString(R.string.safety_blocked_app, shown)
            XLog.w(TAG, "blocked tool=$toolName in blocked app pkg=$pkg prompt='${taskPrompt.take(60)}'")
            return Decision(
                allow = false,
                blockedByApp = true,
                message = msg,
                assessment = RiskAssessment(risky = true, kind = null, requiresConfirmation = false, label = "blocked_app"),
            )
        }

        val assessment = RiskClassifier.assess(toolName, params)
        if (!assessment.risky) {
            return Decision(true, false, null, assessment)
        }

        // Rule 3 and 4: UNKNOWN always confirms; other kinds follow their switch.
        val kind = assessment.kind
        val switchOn = kind?.let { SafetySettings.confirmationFor(it) } ?: false
        val needsConfirmation = SafetySettings.requiresConfirmation(kind, switchOn)
        if (!needsConfirmation) {
            XLog.i(TAG, "confirmation not required by user setting; allowing $toolName (${assessment.label})")
            return Decision(true, false, null, assessment)
        }

        val riskLabel = riskLabel(app, assessment.kind)
        val message = app.getString(R.string.safety_confirm_message, riskLabel, toolName)
        XLog.i(TAG, "requesting confirmation for tool=$toolName risk=${assessment.label}")
        val approved = SafetyCoordinator.requestConfirmation(message, assessment.label)
        return if (approved) {
            XLog.i(TAG, "user approved risky tool=$toolName risk=${assessment.label}")
            Decision(true, false, null, assessment)
        } else {
            XLog.w(TAG, "user denied/timed out risky tool=$toolName risk=${assessment.label}")
            Decision(
                allow = false,
                blockedByApp = false,
                message = app.getString(R.string.safety_action_denied),
                assessment = assessment,
            )
        }
    }

    fun recordAction(
        taskPrompt: String,
        toolName: String,
        risk: String,
        outcome: String,
        detail: String?,
        reason: ActionReason = ActionReason.UNKNOWN_LEGACY,
        result: ActionResult = ActionResult.PENDING,
    ) {
        ActionLog.record(taskPrompt, toolName, risk, outcome, detail, reason, result)
    }

    private fun riskLabel(app: Context, kind: RiskKind?): String = app.getString(
        when (kind) {
            RiskKind.SEND_MESSAGE -> R.string.risk_send_message
            RiskKind.SEND_FILE -> R.string.risk_send_file
            RiskKind.CALL -> R.string.risk_call
            RiskKind.DELETE -> R.string.risk_delete
            RiskKind.PAYMENT -> R.string.risk_payment
            RiskKind.INSTALL -> R.string.risk_install
            RiskKind.PUBLISH -> R.string.risk_publish
            RiskKind.UNKNOWN, null -> R.string.risk_generic
        }
    )

    private fun currentForegroundPackage(): String? = try {
        ClawAccessibilityService.getInstance()?.rootInActiveWindow?.packageName?.toString()
    } catch (e: Exception) {
        XLog.w(TAG, "could not read foreground package: ${e.message}")
        null
    }

    private fun safeAppLabel(packageName: String): String? = try {
        val pm = ClawApplication.instance.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        null
    }
}
