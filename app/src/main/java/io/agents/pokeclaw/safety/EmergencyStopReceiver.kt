// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Notification-action receiver for the emergency stop. Not exported.
 */
class EmergencyStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        EmergencyStop.trigger(context, "notification action")
    }

    companion object {
        const val ACTION = "io.agents.pokeclaw.EMERGENCY_STOP"
    }
}
