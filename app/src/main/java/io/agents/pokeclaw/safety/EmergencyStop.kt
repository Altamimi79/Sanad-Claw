// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import android.content.Context
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.service.AutoReplyManager
import io.agents.pokeclaw.service.ForegroundService
import io.agents.pokeclaw.utils.XLog

/**
 * One-call kill switch used by the chat banner, the floating pill and the
 * notification action. Stops the agent task, monitoring and the foreground
 * service, and records the event in the local action log.
 */
object EmergencyStop {

    private const val TAG = "EmergencyStop"

    fun trigger(context: Context, reason: String) {
        XLog.w(TAG, "emergency stop triggered: $reason")
        try {
            ClawApplication.appViewModelInstance.stopTask()
        } catch (e: Exception) {
            XLog.w(TAG, "stopTask failed: ${e.message}")
        }
        try {
            AutoReplyManager.getInstance().stopAll()
        } catch (e: Exception) {
            XLog.w(TAG, "stopAll monitoring failed: ${e.message}")
        }
        try {
            ForegroundService.stop(context)
        } catch (e: Exception) {
            XLog.w(TAG, "foreground stop failed: ${e.message}")
        }
        ActionLog.record(
            taskSnippet = "emergency-stop",
            toolName = "emergency_stop",
            risk = "none",
            outcome = "stopped",
            detail = reason,
            reason = ActionReason.EMERGENCY_STOP,
            result = ActionResult.CANCELLED,
        )
    }
}
