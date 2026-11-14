// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.agents.pokeclaw.utils.XLog
import java.time.ZonedDateTime

/**
 * Schedules one exact alarm per enabled routine.
 *
 * When an alarm fires, [RoutineAlarmReceiver] only notifies the user. It never runs the
 * prompt itself: execution stays behind the normal chat path, the safety gate and the
 * confirmation dialog. Unattended actions are not allowed.
 */
object RoutineScheduler {

    private const val TAG = "RoutineScheduler"
    const val ACTION_FIRE = "io.agents.pokeclaw.ROUTINE_FIRE"
    const val EXTRA_ROUTINE_ID = "routine_id"

    /** Replaces all alarms for [routines]: cancels every known routine, then re-arms enabled ones. */
    fun rescheduleAll(context: Context, routines: List<ScheduledRoutine>, now: ZonedDateTime = ZonedDateTime.now()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        routines.forEach { cancel(context, am, it.id) }
        routines.forEach { r ->
            val next = r.nextRunAfter(now) ?: return@forEach
            arm(context, am, r, next.toInstant().toEpochMilli())
        }
    }

    fun cancel(context: Context, am: AlarmManager, routineId: String) {
        am.cancel(pendingIntent(context, routineId))
    }

    private fun arm(context: Context, am: AlarmManager, r: ScheduledRoutine, atMillis: Long) {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(context, r.id))
            XLog.i(TAG, "Armed routine id=${r.id} at=$atMillis")
        } catch (e: SecurityException) {
            // Exact alarms can be revoked on Android 14+. Fail visibly in the log; no silent fallback.
            XLog.e(TAG, "Exact alarm not permitted for routine id=${r.id}", e)
        }
    }

    private fun pendingIntent(context: Context, routineId: String): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ROUTINE_ID, routineId)
        // Distinct request code per routine so each routine has its own alarm slot.
        val requestCode = routineId.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
