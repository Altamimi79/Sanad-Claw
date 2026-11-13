// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.routine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog

/**
 * Fires when a routine's alarm goes off. It only posts a reminder that opens the app.
 * The routine prompt is not executed here: the user must start it from the chat, where the
 * normal safety gate and confirmations apply.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RoutineAlarmReceiver"
        const val CHANNEL_ID = "sanad_routine_channel"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RoutineScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(RoutineScheduler.EXTRA_ROUTINE_ID)
        val routine = RoutineStore.loadAll().firstOrNull { it.id == id }
        if (routine == null || !routine.enabled) {
            XLog.w(TAG, "Alarm for missing or disabled routine id=$id; ignored")
            return
        }
        ensureChannel(context)
        notify(context, routine)
        XLog.i(TAG, "Routine reminder posted id=${routine.id}")
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Routine reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun notify(context: Context, routine: ScheduledRoutine) {
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context,
            routine.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(routine.name)
            .setContentText(routine.prompt)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(routine.id.hashCode(), notification)
        } catch (e: SecurityException) {
            XLog.e(TAG, "Notification permission missing; reminder not shown", e)
        }
    }
}
