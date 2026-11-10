// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.safety.ActionLog
import io.agents.pokeclaw.ui.chat.ThemeManager
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.KButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4: on-device action log viewer. Entries are local-only and redacted by
 * [io.agents.pokeclaw.safety.ActionLogFormatter] before storage.
 */
class ActionLogActivity : BaseActivity() {

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
            setTitle(getString(R.string.safety_action_log_title))
            showBackButton(true) { finish() }
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            findViewById<android.widget.ImageView>(R.id.ivBack)?.setColorFilter(tc.aiText)
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listContainer)
        root.addView(scroll)

        emptyView = TextView(this).apply {
            text = getString(R.string.safety_action_log_empty)
            setTextColor(Color.parseColor("#8b949e"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }

        val clearBtn = KButton(this).apply {
            text = getString(R.string.safety_action_log_clear)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { setMargins(dp(14), dp(6), dp(14), dp(14)) }
            setOnClickListener {
                ActionLog.clear()
                Toast.makeText(this@ActionLogActivity, R.string.safety_action_log_cleared, Toast.LENGTH_SHORT).show()
                render()
            }
        }
        root.addView(clearBtn)

        setContentView(root)
        render()
    }

    private fun render() {
        val tc = ThemeManager.getColors()
        listContainer.removeAllViews()
        val entries = ActionLog.readEntries().asReversed()
        if (entries.isEmpty()) {
            listContainer.addView(emptyView)
            return
        }
        val timeFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
        entries.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(tc.toolbarBg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }
            card.addView(TextView(this).apply {
                text = "${timeFormat.format(Date(entry.timestampMs))} · ${entry.toolName} · ${entry.outcome}"
                setTextColor(tc.aiText)
                textSize = 13f
            })
            card.addView(TextView(this).apply {
                text = getString(R.string.risk_generic) + ": " + entry.risk
                setTextColor(Color.parseColor("#8b949e"))
                textSize = 11f
            })
            if (entry.detail.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = entry.detail
                    setTextColor(Color.parseColor("#8b949e"))
                    textSize = 11f
                })
            }
            listContainer.addView(card)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
