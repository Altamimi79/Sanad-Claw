// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.routine.RoutineScheduler
import io.agents.pokeclaw.routine.RoutineStore
import io.agents.pokeclaw.routine.ScheduledRoutine
import io.agents.pokeclaw.ui.chat.ThemeManager
import io.agents.pokeclaw.ui.chat.ThemeManager.ChatColors
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.KButton
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Creates, lists, enables and deletes scheduled routines (roadmap item 8).
 *
 * A routine only sends a reminder at its time; the prompt is never executed here. Every change
 * re-arms the exact alarms through [RoutineScheduler.rescheduleAll].
 */
class RoutinesActivity : BaseActivity() {

    private companion object {
        const val TAG = "RoutinesActivity"
    }

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }

        val toolbar = CommonToolbar(this).apply {
            setTitle(getString(R.string.routines_title))
            showBackButton(true) { finish() }
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            findViewById<android.widget.ImageView>(R.id.ivBack)?.setColorFilter(tc.aiText)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listContainer)
        root.addView(scroll)

        emptyView = TextView(this).apply {
            text = getString(R.string.routines_empty)
            setTextColor(Color.parseColor("#8b949e"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }

        val addBtn = KButton(this).apply {
            text = getString(R.string.routines_add)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
                .apply { setMargins(dp(14), dp(6), dp(14), dp(14)) }
            setOnClickListener { showAddDialog() }
        }
        root.addView(addBtn)

        setContentView(root)
        render()
    }

    private fun render() {
        val tc = ThemeManager.getColors()
        listContainer.removeAllViews()
        val routines = RoutineStore.loadAll()
        if (routines.isEmpty()) {
            listContainer.addView(emptyView)
            return
        }
        routines.forEach { r -> listContainer.addView(rowFor(r, tc)) }
    }

    private fun rowFor(r: ScheduledRoutine, tc: ChatColors): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(tc.aiBubble)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, dp(8)) }
        }
        row.addView(TextView(this).apply {
            text = r.name
            setTextColor(tc.aiText)
            textSize = 15f
        })
        row.addView(TextView(this).apply {
            text = "${r.time}  ${r.days.joinToString(", ") { it.name.take(3) }}"
            setTextColor(Color.parseColor("#8b949e"))
            textSize = 12f
        })
        row.addView(TextView(this).apply {
            text = r.prompt
            setTextColor(tc.aiText)
            textSize = 13f
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(KButton(this).apply {
            text = getString(if (r.enabled) R.string.routines_disable else R.string.routines_enable)
            setOnClickListener {
                save(RoutineStore.loadAll().map { if (it.id == r.id) it.copy(enabled = !r.enabled) else it })
            }
        })
        actions.addView(KButton(this).apply {
            text = getString(R.string.routines_delete)
            setOnClickListener {
                save(RoutineStore.loadAll().filterNot { it.id == r.id }, removedId = r.id)
            }
        })
        row.addView(actions)
        return row
    }

    /** Persists the list, re-arms alarms, and refreshes the screen. */
    private fun save(routines: List<ScheduledRoutine>, removedId: String? = null) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        removedId?.let { RoutineScheduler.cancel(this, am, it) }
        RoutineStore.saveAll(routines)
        RoutineScheduler.rescheduleAll(this, routines, ZonedDateTime.now())
        XLog.i(TAG, "routines saved count=${routines.size}")
        render()
    }

    private fun showAddDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val nameField = EditText(this).apply { hint = getString(R.string.routines_field_name) }
        val promptField = EditText(this).apply {
            hint = getString(R.string.routines_field_prompt)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val timeField = EditText(this).apply {
            hint = getString(R.string.routines_field_time)
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
        }
        form.addView(nameField)
        form.addView(promptField)
        form.addView(timeField)

        val dayBoxes = DayOfWeek.values().associateWith { day ->
            CheckBox(this).apply {
                text = day.name.lowercase().replaceFirstChar { it.uppercase() }
                isChecked = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
            }
        }
        dayBoxes.values.forEach { form.addView(it) }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.routines_add)
            .setView(form)
            .setPositiveButton(R.string.routines_save) { _, _ ->
                val name = nameField.text.toString().trim()
                val prompt = promptField.text.toString().trim()
                val time = ScheduledRoutine.parseTime(timeField.text.toString())
                val days = dayBoxes.filterValues { it.isChecked }.keys
                when {
                    name.isEmpty() || prompt.isEmpty() -> toast(R.string.routines_error_missing)
                    time == null -> toast(R.string.routines_error_time)
                    days.isEmpty() -> toast(R.string.routines_error_days)
                    else -> {
                        val routine = ScheduledRoutine(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            prompt = prompt,
                            time = time,
                            days = days,
                            enabled = true,
                        )
                        save(RoutineStore.loadAll() + routine)
                        toast(R.string.routines_saved)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
