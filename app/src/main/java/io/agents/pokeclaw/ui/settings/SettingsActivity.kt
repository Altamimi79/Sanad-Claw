// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.widget.AlertDialog
import io.agents.pokeclaw.widget.ConfirmDialog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.InputDialog
import io.agents.pokeclaw.widget.MenuGroup
import io.agents.pokeclaw.widget.MenuItem
import io.agents.pokeclaw.AppCapabilityCoordinator
import io.agents.pokeclaw.AppRequirement
import io.agents.pokeclaw.appViewModel
import io.agents.pokeclaw.server.ConfigServerManager
import io.agents.pokeclaw.service.ForegroundService
import io.agents.pokeclaw.support.DebugReportManager
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings screen
 */
class SettingsActivity : BaseActivity() {

    companion object {
        /** Repository that owns this fork's releases and issues. */
        private const val REPO_URL = "https://github.com/Altamimi79/Sanad-Claw"
        private const val RELEASES_URL = "https://github.com/Altamimi79/Sanad-Claw/releases"
    }

    // Poll permissions every second (same as original HomeActivity)
    private val handler = Handler(Looper.getMainLooper())
    private val permPoller = object : Runnable {
        override fun run() {
            refreshPermissions()
            handler.postDelayed(this, 1000)
        }
    }

    // Permission menu items — kept for onResume refresh
    private var permAccessibility: io.agents.pokeclaw.widget.MenuItem? = null
    private var permNotification: io.agents.pokeclaw.widget.MenuItem? = null
    private var permNotifAccess: io.agents.pokeclaw.widget.MenuItem? = null
    private var permOverlay: io.agents.pokeclaw.widget.MenuItem? = null
    private var permBattery: io.agents.pokeclaw.widget.MenuItem? = null
    private var permStorage: io.agents.pokeclaw.widget.MenuItem? = null
    private var externalAutomationItem: io.agents.pokeclaw.widget.MenuItem? = null
    private var globalPromptItem: io.agents.pokeclaw.widget.MenuItem? = null
    private var customModelUrlItem: io.agents.pokeclaw.widget.MenuItem? = null

    private val viewModel by lazy {
        ViewModelProvider(this)[SettingsViewModel::class.java]
    }

    // Keep MenuItem references for dynamic updates
    private val menuItems = mutableMapOf<String, MenuItem>()

    // Register launcher to refresh after returning from LLM config screen
    private val llmConfigLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        viewModel.refresh()
    }

    // Register channel config result callback
    private val channelConfigLauncher = ChannelConfigActivity.registerLauncher(this) { result ->
        result?.let {
            // Refresh settings after successful config (refresh "Bound"/"Unbound" status)
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force match theme from ThemeManager
        val themeColors = io.agents.pokeclaw.ui.chat.ThemeManager.getColors()
        window.statusBarColor = themeColors.toolbarBg
        window.decorView.setBackgroundColor(themeColors.bg)

        setContentView(R.layout.activity_settings)

        // One-time migration of the Phase 4 single switch (idempotent; notice shown once).
        val migrationOwed = io.agents.pokeclaw.safety.SafetySettings.migrateLegacyConfirmation()
        XLog.i("SettingsActivity", "safety migration checked, notice_owed=$migrationOwed")

        // Override XML backgrounds with ThemeManager colors
        val contentFrame = findViewById<android.view.ViewGroup>(android.R.id.content)
        contentFrame?.setBackgroundColor(themeColors.bg)
        // Root LinearLayout has android:background="@color/colorBgPrimary" — override it
        (contentFrame?.getChildAt(0) as? android.view.View)?.setBackgroundColor(themeColors.bg)

        initToolbar()
        initMenuGroups()
        applyThemeToGroups(themeColors)
        observeViewModel()
        showSafetyMigrationNoticeIfPending()
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
        refreshPermissions()
        refreshExternalAutomation()
        handler.removeCallbacks(permPoller)
        handler.postDelayed(permPoller, 1000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(permPoller)
    }

    /** Map the English capability labels produced by AppCapabilityCoordinator/TaskBudget to the app locale. */
    private fun localizedCapabilityLabel(raw: String): String = when (raw) {
        "Enabled" -> getString(R.string.settings_enabled)
        "Disabled" -> getString(R.string.settings_disabled)
        "Unrestricted" -> getString(R.string.status_unrestricted)
        "Restricted" -> getString(R.string.status_restricted)
        "Unlimited" -> getString(R.string.settings_unlimited)
        else -> raw
    }

    private fun refreshPermissions() {
        val capabilities = AppCapabilityCoordinator.snapshot(this)
            permAccessibility?.setTrailingText(localizedCapabilityLabel(capabilities.accessibilityStatusLabel))
            permNotification?.setTrailingText(localizedCapabilityLabel(capabilities.notificationPermissionStatusLabel))
            permNotifAccess?.setTrailingText(localizedCapabilityLabel(capabilities.notificationAccessStatusLabel))
        permOverlay?.setTrailingText(if (capabilities.overlayGranted) getString(R.string.settings_enabled) else getString(R.string.settings_disabled))
        permBattery?.setTrailingText(if (capabilities.batteryOptimizationIgnored) getString(R.string.status_unrestricted) else getString(R.string.status_restricted))
        permStorage?.setTrailingText(if (capabilities.storageAccessGranted) getString(R.string.settings_enabled) else getString(R.string.settings_disabled))
    }

    private fun refreshExternalAutomation() {
        externalAutomationItem?.setTrailingText(
            if (KVUtils.isExternalAutomationEnabled()) getString(R.string.settings_enabled) else getString(R.string.settings_disabled)
        )
    }

    /** Toggles app UI language between Arabic and English, then restarts the app so every
     *  activity re-attaches its base context with the new locale. */
    private fun switchAppLanguage() {
        val current = io.agents.pokeclaw.utils.AppLocale.getLanguage(this)
        val next = if (current == io.agents.pokeclaw.utils.AppLocale.LANGUAGE_ENGLISH)
            io.agents.pokeclaw.utils.AppLocale.LANGUAGE_ARABIC
        else
            io.agents.pokeclaw.utils.AppLocale.LANGUAGE_ENGLISH
        io.agents.pokeclaw.utils.AppLocale.setLanguage(this, next)
        XLog.i("SettingsActivity", "language switched to $next; restarting app")
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            finishAffinity()
            startActivity(launch)
        } else {
            recreate()
        }
    }

    /** Refreshes the trailing label on the global-prompt row (#45). */
    private fun refreshGlobalPromptStatus() {
        val current = KVUtils.getGlobalPrompt()
        val label = if (current.isBlank()) {
            getString(R.string.global_prompt_not_set)
        } else {
            getString(R.string.global_prompt_set_status, current.length)
        }
        globalPromptItem?.setTrailingText(label)
    }

    /** Refreshes the trailing label on the custom-model-URL row (#36). */
    private fun refreshCustomModelUrlStatus() {
        val current = KVUtils.getCustomLocalModelUrl()
        val label = if (current.isBlank()) {
            getString(R.string.custom_local_model_url_not_set)
        } else {
            getString(R.string.custom_local_model_url_set)
        }
        customModelUrlItem?.setTrailingText(label)
    }

    private fun initToolbar() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.settings_title))
            showBackButton(true) { finish() }
        }
    }

    private fun applyThemeToGroups(tc: io.agents.pokeclaw.ui.chat.ThemeManager.ChatColors) {
        val groups = listOf(
            R.id.permissionsGroup, R.id.channelGroup, R.id.modelGroup,
            R.id.appearanceGroup, R.id.toolsGroup, R.id.remoteGroup, R.id.aboutGroup
        )
        for (id in groups) {
            val g = findViewById<MenuGroup>(id) ?: continue
            g.setTitleColor(tc.aiText)
            g.setCardBackgroundColor(tc.toolbarBg)
            for (i in 0 until g.getMenuItemCount()) {
                g.getMenuItemAt(i)?.apply {
                    setTitleColor(tc.aiText)
                    setTrailingTextColor(tc.sendColor)
                    setLeadingIconColor(tc.aiText)
                    setTrailingIconColor(tc.aiText)
                }
            }
        }
        // Toolbar
        findViewById<CommonToolbar>(R.id.toolbar)?.apply {
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            findViewById<android.widget.ImageView>(R.id.ivBack)?.setColorFilter(tc.aiText)
        }
    }

    private fun refreshSettings() {
        viewModel.refresh()
    }

    private fun toggleExternalAutomation() {
        if (KVUtils.isExternalAutomationEnabled()) {
            KVUtils.setExternalAutomationEnabled(false)
            refreshExternalAutomation()
            Toast.makeText(this, "External Automation disabled", Toast.LENGTH_SHORT).show()
            return
        }

        ConfirmDialog.showWarm(
            context = this,
            title = getString(R.string.enable_external_automation_question),
            message = "This lets trusted apps like Tasker, MacroDroid, or ADB start PokeClaw tasks with explicit Android intents. Keep it off unless you control the automation that will call it.",
            actionTitle = "Enable",
            cancelTitle = getString(R.string.common_cancel),
            onAction = {
                KVUtils.setExternalAutomationEnabled(true)
                refreshExternalAutomation()
                Toast.makeText(this, "External Automation enabled", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun initMenuGroups() {
        // Permissions
        val permissionsGroup = findViewById<MenuGroup>(R.id.permissionsGroup)
        permissionsGroup.setTitle(getString(R.string.settings_group_permissions))

        permAccessibility = permissionsGroup.addMenuItem(
            leadingIcon = R.drawable.ic_accessibility,
            title = getString(R.string.home_card_accessibility_title),
            onClick = {
                AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.ACCESSIBILITY)
                Toast.makeText(this, R.string.home_enable_accessibility, Toast.LENGTH_LONG).show()
            },
            showDivider = true
        )

        permNotification = permissionsGroup.addMenuItem(
            leadingIcon = R.drawable.ic_notification,
            title = getString(R.string.home_card_notification_title),
            onClick = {
                if (!AppCapabilityCoordinator.isNotificationPermissionGranted(this@SettingsActivity)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                    }
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.home_notification_enabled, Toast.LENGTH_SHORT).show()
                }
            },
            showDivider = true
        ).apply {
            setTrailingText(
                if (AppCapabilityCoordinator.isNotificationPermissionGranted(this@SettingsActivity)) localizedCapabilityLabel("Enabled") else localizedCapabilityLabel("Disabled")
            )
        }

        permNotifAccess = permissionsGroup.addMenuItem(
            leadingIcon = R.drawable.ic_notification,
            title = getString(R.string.notif_access_title),
            onClick = {
                AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.NOTIFICATION_ACCESS)
            },
            showDivider = true
        )

        permOverlay = permissionsGroup.addMenuItem(
            leadingIcon = R.drawable.ic_window,
            title = getString(R.string.home_card_system_window_title),
            onClick = {
                if (AppCapabilityCoordinator.snapshot(this@SettingsActivity).overlayGranted) {
                    Toast.makeText(this@SettingsActivity, R.string.home_overlay_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    AppCapabilityCoordinator.openSystemSettings(this@SettingsActivity, AppRequirement.OVERLAY)
                }
            },
            showDivider = true
        )

        permBattery = permissionsGroup.addMenuItem(
            leadingIcon = R.drawable.ic_battery,
            title = getString(R.string.home_card_battery_title),
            onClick = {
                if (AppCapabilityCoordinator.snapshot(this@SettingsActivity).batteryOptimizationIgnored) {
                    Toast.makeText(this@SettingsActivity, R.string.home_battery_ignored, Toast.LENGTH_SHORT).show()
                } else {
                    AppCapabilityCoordinator.openSystemSettings(this@SettingsActivity, AppRequirement.BATTERY_OPTIMIZATION)
                }
            },
            showDivider = true
        )

        permStorage = permissionsGroup.addMenuItem(
            leadingIcon = R.drawable.ic_storage,
            title = getString(R.string.home_card_storage_title),
            onClick = {
                if (AppCapabilityCoordinator.snapshot(this@SettingsActivity).storageAccessGranted) {
                    Toast.makeText(this@SettingsActivity, R.string.home_storage_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    AppCapabilityCoordinator.openSystemSettings(this@SettingsActivity, AppRequirement.STORAGE)
                }
            },
            showDivider = false
        )

        // Channel (hidden)
        val channelGroup = findViewById<MenuGroup>(R.id.channelGroup)
        channelGroup.setTitle(getString(R.string.settings_group_channel))

        menuItems[SettingsViewModel.MenuAction.DISCORD.name] = channelGroup.addMenuItem(
            leadingIcon = R.drawable.ic_channel_discord,
            title = getString(R.string.menu_discord),
            onClick = { viewModel.onMenuItemClick(SettingsViewModel.MenuAction.DISCORD) },
            showDivider = true
        )
        menuItems[SettingsViewModel.MenuAction.TELEGRAM.name] = channelGroup.addMenuItem(
            leadingIcon = R.drawable.ic_channel_telegram,
            title = getString(R.string.menu_telegram),
            onClick = { viewModel.onMenuItemClick(SettingsViewModel.MenuAction.TELEGRAM) },
            showDivider = true
        )
        menuItems[SettingsViewModel.MenuAction.WECHAT.name] = channelGroup.addMenuItem(
            leadingIcon = R.drawable.ic_channel_wechat,
            title = getString(R.string.menu_wechat),
            onClick = { viewModel.onMenuItemClick(SettingsViewModel.MenuAction.WECHAT) },
            showDivider = true
        )
        menuItems[SettingsViewModel.MenuAction.LAN_CONFIG.name] = channelGroup.addMenuItem(
            leadingIcon = R.drawable.ic_lan_config,
            title = getString(R.string.menu_lan_config),
            onClick = { viewModel.onMenuItemClick(SettingsViewModel.MenuAction.LAN_CONFIG) },
            showDivider = false
        )
        menuItems[SettingsViewModel.MenuAction.LAN_CONFIG.name]?.setLeadingIconColor(getColor(R.color.colorTextPrimary))


        val modelGroup = findViewById<MenuGroup>(R.id.modelGroup)
        modelGroup.setTitle(getString(R.string.settings_group_model))

        menuItems[SettingsViewModel.MenuAction.LLM_CONFIG.name] = modelGroup.addMenuItem(
            leadingIcon = R.drawable.icon_current_model,
            title = getString(R.string.menu_llm_config),
            onClick = { viewModel.onMenuItemClick(SettingsViewModel.MenuAction.LLM_CONFIG) },
            showDivider = false
        )
        menuItems[SettingsViewModel.MenuAction.LLM_CONFIG.name]?.setLeadingIconColor(getColor(R.color.colorTextPrimary))

        // Task Budget (inline in model group)
        modelGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_recent_history,
            title = getString(R.string.task_budget_title),
            onClick = { showBudgetDialog() },
            showDivider = true
        ).apply {
            setTrailingText(localizedCapabilityLabel(io.agents.pokeclaw.agent.TaskBudget.describeCurrentBudget()))
        }

        // Global Prompt (#45) — user-defined persistent instructions
        globalPromptItem = modelGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_edit,
            title = getString(R.string.global_prompt_title),
            onClick = {
                val current = KVUtils.getGlobalPrompt()
                XLog.i("SettingsActivity", "open global prompt dialog: current.len=${current.length}")
                InputDialog.show(
                    context = this@SettingsActivity,
                    title = getString(R.string.global_prompt_dialog_title),
                    presetText = current,
                    hint = getString(R.string.global_prompt_hint),
                    maxLength = 2000,
                ) { text ->
                    KVUtils.setGlobalPrompt(text)
                    XLog.i("SettingsActivity", "global prompt saved: new.len=${text.length}, hasPrompt=${KVUtils.hasGlobalPrompt()}")
                    refreshGlobalPromptStatus()
                }
            },
            showDivider = false
        )
        globalPromptItem?.setLeadingIconColor(getColor(R.color.colorTextPrimary))
        refreshGlobalPromptStatus()

        // Custom Local Model URL (#36) — advanced: lets users add their own model download URL
        customModelUrlItem = modelGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_share,
            title = getString(R.string.custom_local_model_url_title),
            onClick = {
                val current = KVUtils.getCustomLocalModelUrl()
                XLog.i("SettingsActivity", "open custom model url dialog: current.len=${current.length}")
                InputDialog.show(
                    context = this@SettingsActivity,
                    title = getString(R.string.custom_local_model_url_dialog_title),
                    presetText = current,
                    hint = getString(R.string.custom_local_model_url_hint),
                    maxLength = 1000,
                    inputValidate = { text ->
                        val lower = text.trim().lowercase()
                        if (lower.isEmpty()) {
                            // Empty = clear; allow
                            io.agents.pokeclaw.widget.InputDialog.ValidateResult(true, null)
                        } else if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                            io.agents.pokeclaw.widget.InputDialog.ValidateResult(
                                false,
                                getString(R.string.custom_local_model_url_invalid)
                            )
                        } else {
                            io.agents.pokeclaw.widget.InputDialog.ValidateResult(true, null)
                        }
                    },
                ) { text ->
                    // Normalize the protocol prefix to lowercase (Android keyboard auto-cap
                    // can produce "HTTPS://..."). Rest of the URL is case-preserved.
                    val trimmed = text.trim().let { raw ->
                        when {
                            raw.startsWith("HTTPS://", ignoreCase = false) -> "https://" + raw.substring(8)
                            raw.startsWith("HTTP://", ignoreCase = false) -> "http://" + raw.substring(7)
                            else -> raw
                        }
                    }
                    KVUtils.setCustomLocalModelUrl(trimmed)
                    XLog.i(
                        "SettingsActivity",
                        "custom local model url saved: new.len=${trimmed.length}, hasUrl=${KVUtils.hasCustomLocalModelUrl()}"
                    )
                    refreshCustomModelUrlStatus()
                }
            },
            showDivider = false
        )
        customModelUrlItem?.setLeadingIconColor(getColor(R.color.colorTextPrimary))
        refreshCustomModelUrlStatus()

        // Appearance
        val appearanceGroup = findViewById<MenuGroup>(R.id.appearanceGroup)
        appearanceGroup.setTitle(getString(R.string.appearance_title))

        appearanceGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_slideshow,
            title = getString(R.string.theme_title),
            onClick = {
                startActivity(Intent(this, ThemeActivity::class.java))
            },
            showDivider = false
        ).apply {
            val themeId = KVUtils.getString("THEME_ID", "abyss_dark")
            val label = themeId.replace("_", " ").replaceFirstChar { it.uppercase() }
            setTrailingText(label)
        }

        // App language (Sanad fork): Arabic default, switchable to English.
        val currentLang = io.agents.pokeclaw.utils.AppLocale.getLanguage(this)
        appearanceGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_view,
            title = getString(R.string.settings_language),
            trailingText = if (currentLang == io.agents.pokeclaw.utils.AppLocale.LANGUAGE_ENGLISH)
                getString(R.string.language_name_english)
            else
                getString(R.string.language_name_arabic),
            onClick = { switchAppLanguage() },
            showDivider = false,
        )

        // Tools
        val toolsGroup = findViewById<MenuGroup>(R.id.toolsGroup)
        toolsGroup.setTitle(getString(R.string.tools_group_title))

        val registeredTools = io.agents.pokeclaw.tool.ToolRegistry.getInstance().getAllTools()
        toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_manage,
            title = getString(R.string.tools_available_title),
            onClick = {
                val names = registeredTools
                    .map { it.getDisplayName() }
                    .sorted()
                    .joinToString("\n")
                android.app.AlertDialog.Builder(this)
                    .setTitle("${getString(R.string.tools_dialog_title)} (${registeredTools.size})")
                    .setMessage(names)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            },
            trailingText = registeredTools.size.toString(),
        )

        // Voice input auto-send (phase 3) — off by default so dictation stays editable.
        var voiceAutoSendItem: MenuItem? = null
        voiceAutoSendItem = toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_btn_speak_now,
            title = getString(R.string.voice_auto_send_title),
            onClick = {
                val next = !KVUtils.isVoiceAutoSendEnabled()
                KVUtils.setVoiceAutoSendEnabled(next)
                voiceAutoSendItem?.setTrailingText(
                    getString(if (next) R.string.settings_enabled else R.string.settings_disabled)
                )
                Toast.makeText(
                    this,
                    getString(
                        if (next) R.string.voice_auto_send_enabled_toast
                        else R.string.voice_auto_send_disabled_toast
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            },
        ).apply {
            setTrailingText(
                getString(
                    if (KVUtils.isVoiceAutoSendEnabled()) R.string.settings_enabled
                    else R.string.settings_disabled
                )
            )
        }

        // Text size (applied when the chat screen starts).
        val sizeLabels = listOf(
            getString(R.string.text_size_normal) to "normal",
            getString(R.string.text_size_slightly_larger) to "slightly_larger",
            getString(R.string.text_size_larger) to "larger",
            getString(R.string.text_size_largest) to "largest",
        )
        fun showChoice(titleRes: Int, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
            val labels = options.map { it.first }.toTypedArray()
            val checked = options.indexOfFirst { it.second == current }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    onPick(options[which].second)
                    Toast.makeText(this, R.string.text_appearance_applied, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .show()
        }

        // Unknown or missing stored keys fall back to the first option, never a crash.
        fun labelFor(options: List<Pair<String, String>>, key: String): String =
            (options.firstOrNull { it.second == key } ?: options.first()).first

        var sizeItem: MenuItem? = null
        sizeItem = toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_zoom,
            title = getString(R.string.text_size_title),
            onClick = {
                showChoice(R.string.text_size_title, sizeLabels, KVUtils.getTextSizeKey()) { key ->
                    KVUtils.setTextSizeKey(key)
                    sizeItem?.setTrailingText(labelFor(sizeLabels, key))
                }
            },
        ).apply {
            setTrailingText(labelFor(sizeLabels, KVUtils.getTextSizeKey()))
        }

        // Plan preview (roadmap item 5) — off by default; a task then waits for plan approval.
        var planPreviewItem: MenuItem? = null
        planPreviewItem = toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_agenda,
            title = getString(R.string.plan_preview_title),
            onClick = {
                val next = !KVUtils.isPlanPreviewEnabled()
                KVUtils.setPlanPreviewEnabled(next)
                planPreviewItem?.setTrailingText(
                    getString(if (next) R.string.settings_enabled else R.string.settings_disabled)
                )
                Toast.makeText(
                    this,
                    getString(
                        if (next) R.string.plan_preview_enabled_toast
                        else R.string.plan_preview_disabled_toast
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            },
            showDivider = false
        ).apply {
            setTrailingText(
                getString(
                    if (KVUtils.isPlanPreviewEnabled()) R.string.settings_enabled
                    else R.string.settings_disabled
                )
            )
        }

        // Phase 4/2g safety controls: one confirmation switch per risk category.
        // UNKNOWN has no switch by design: it always requires confirmation.
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.SEND_MESSAGE,
            R.string.safety_confirm_send_message, android.R.drawable.ic_menu_send)
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.SEND_FILE,
            R.string.safety_confirm_send_file, android.R.drawable.ic_menu_upload)
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.CALL,
            R.string.safety_confirm_call, android.R.drawable.ic_menu_call)
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.DELETE,
            R.string.safety_confirm_delete, android.R.drawable.ic_menu_delete)
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.PAYMENT,
            R.string.safety_confirm_payment, android.R.drawable.ic_menu_view)
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.INSTALL,
            R.string.safety_confirm_install, android.R.drawable.ic_menu_add)
        addSafetyCategoryRow(toolsGroup, io.agents.pokeclaw.safety.RiskKind.PUBLISH,
            R.string.safety_confirm_publish, android.R.drawable.ic_menu_share)

        var timeoutItem: MenuItem? = null
        timeoutItem = toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_recent_history,
            title = getString(R.string.safety_timeout_setting),
            onClick = {
                val next = io.agents.pokeclaw.safety.TaskTimeoutPolicy.nextPreset(
                    KVUtils.getSafetyTaskTimeoutSec()
                )
                KVUtils.setSafetyTaskTimeoutSec(next)
                timeoutItem?.setTrailingText(getString(R.string.safety_seconds_format, next))
            },
            showDivider = true
        ).apply {
            setTrailingText(
                getString(R.string.safety_seconds_format, KVUtils.getSafetyTaskTimeoutSec())
            )
        }

        var blocklistItem: MenuItem? = null
        blocklistItem = toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_manage,
            title = getString(R.string.safety_blocklist_setting),
            onClick = {
                InputDialog.show(
                    context = this,
                    title = getString(R.string.safety_blocklist_setting),
                    presetText = KVUtils.getSafetyBlocklistRaw(),
                    hint = getString(R.string.safety_blocklist_hint),
                    confirmText = getString(R.string.safety_saved),
                ) { text ->
                    KVUtils.setSafetyBlocklistRaw(text)
                    val count = io.agents.pokeclaw.safety.AppBlocklist.effectivePatterns(text).size
                    blocklistItem?.setTrailingText(getString(R.string.safety_blocklist_count, count))
                    Toast.makeText(this, R.string.safety_saved, Toast.LENGTH_SHORT).show()
                }
            },
            showDivider = true
        ).apply {
            val count = io.agents.pokeclaw.safety.AppBlocklist.effectivePatterns(
                KVUtils.getSafetyBlocklistRaw()
            ).size
            setTrailingText(getString(R.string.safety_blocklist_count, count))
        }

        toolsGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_sort_by_size,
            title = getString(R.string.safety_action_log_setting),
            onClick = { startActivity(Intent(this, ActionLogActivity::class.java)) },
            showDivider = false
        )

        // Remote Control
        val remoteGroup = findViewById<MenuGroup>(R.id.remoteGroup)
        remoteGroup.setTitle(getString(R.string.remote_group_title))

        remoteGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_send,
            title = getString(R.string.telegram_bot_title),
            onClick = {
                channelConfigLauncher.launch(ChannelConfigActivity.ChannelType.TELEGRAM)
            },
            showDivider = true
        ).apply {
            val token = KVUtils.getTelegramBotToken()
            setTrailingText(if (token.isNotEmpty()) getString(R.string.settings_connected) else getString(R.string.settings_not_connected))
        }

        externalAutomationItem = remoteGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_share,
            title = getString(R.string.external_automation_title),
            onClick = { toggleExternalAutomation() },
            showDivider = true
        ).apply {
            setTrailingText(if (KVUtils.isExternalAutomationEnabled()) getString(R.string.settings_enabled) else getString(R.string.settings_disabled))
        }

        remoteGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_call,
            title = "WhatsApp",
            onClick = { },
            showDivider = true
        ).apply {
            setTrailingText(getString(R.string.coming_soon))
        }

        remoteGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_myplaces,
            title = getString(R.string.web_dashboard_title),
            onClick = { },
            showDivider = false
        ).apply {
            setTrailingText(getString(R.string.coming_soon))
        }

        // About: app identity, update check, repository, support, credits.
        val aboutGroup = findViewById<MenuGroup>(R.id.aboutGroup)
        aboutGroup.setTitle(getString(R.string.about_group_title))

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_info_details,
            title = getString(R.string.about_app_name),
            onClick = { },
            showDivider = true
        ).apply {
            setTrailingText("${getString(R.string.about_version_label)} ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}")
        }

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_popup_sync,
            title = getString(R.string.about_check_update),
            onClick = { openUrl(RELEASES_URL) },
            showDivider = true
        ).apply {
            setTrailingText(getString(R.string.about_check_update_hint))
        }

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_share,
            title = getString(R.string.about_github),
            onClick = { openUrl(REPO_URL) },
            showDivider = true
        ).apply {
            setTrailingText(getString(R.string.about_github_hint))
        }

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_send,
            title = getString(R.string.about_report_issue),
            onClick = { reportBug() },
            showDivider = true
        ).apply {
            setTrailingText(getString(R.string.about_report_issue_hint))
        }

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_upload,
            title = getString(R.string.share_debug_report_title),
            onClick = { shareDebugReport() },
            showDivider = true
        ).apply {
            setTrailingText("ZIP")
        }

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_compass,
            title = getString(R.string.about_developer),
            onClick = { openUrl(REPO_URL) },
            showDivider = true
        ).apply {
            setTrailingText(getString(R.string.about_base_fork))
        }

        aboutGroup.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_info_details,
            title = getString(R.string.built_by_title),
            onClick = { openUrl(REPO_URL) },
            showDivider = false
        ).apply {
            setTrailingText("Altamimi79")
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            XLog.w("SettingsActivity", "No browser for $url")
            Toast.makeText(this, getString(R.string.about_no_browser), Toast.LENGTH_LONG).show()
        }
    }

    private fun reportBug() {
        buildSupportBundle(
            preparingToast = "Preparing bug report…"
        ) { report ->
            AlertDialog.show(
                context = this@SettingsActivity,
                title = "Bug report ready",
                message = """
                    ${report.name} is ready.

                    Open GitHub Issue to file the bug now.
                    If your browser or GitHub app makes attachment upload awkward, tap Share ZIP instead and send the report manually.
                """.trimIndent(),
                actionTitle = "Open GitHub Issue",
                cancelTitle = "Share ZIP",
                onAction = { openGitHubIssue(report) },
                onCancel = {
                    shareReportFile(
                        report = report,
                        chooserTitle = "Share bug report ZIP",
                        subject = "PokeClaw bug report ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}",
                        body = """
                            Attach this ZIP to your GitHub issue:
                            https://github.com/Altamimi79/Sanad-Claw/issues/new
                        """.trimIndent()
                    )
                }
            )
        }
    }

    private fun shareDebugReport() {
        buildSupportBundle(
            preparingToast = "Preparing debug report…",
        ) { report ->
            shareReportFile(
                report = report,
                chooserTitle = "Share debug report",
                subject = "PokeClaw debug report ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}",
                body = "Attach this debug report when reporting a PokeClaw issue."
            )
        }
    }

    private fun buildSupportBundle(
        preparingToast: String,
        onReportReady: (java.io.File) -> Unit,
    ) {
        lifecycleScope.launch {
            Toast.makeText(this@SettingsActivity, preparingToast, Toast.LENGTH_SHORT).show()
            runCatching {
                withContext(Dispatchers.IO) {
                    DebugReportManager.buildReport(this@SettingsActivity)
                }
            }.onSuccess { report ->
                onReportReady(report)
            }.onFailure { error ->
                XLog.e("SettingsActivity", "Failed to build debug report", error)
                Toast.makeText(this@SettingsActivity, "Failed to build debug report", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openGitHubIssue(report: java.io.File) {
        val issueUri = "https://github.com/Altamimi79/Sanad-Claw/issues/new".toUri()
            .buildUpon()
            .appendQueryParameter(
                "title",
                "[Bug] ${Build.MANUFACTURER} ${Build.MODEL} - "
            )
            .appendQueryParameter("body", buildGitHubIssueBody(report))
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, issueUri))
            Toast.makeText(
                this,
                "Attach ${report.name} to the GitHub issue after the page opens",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app available to open GitHub", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildGitHubIssueBody(report: java.io.File): String {
        return """
            ## What happened
            -

            ## What you expected
            -

            ## Exact steps to reproduce
            1.
            2.
            3.

            ## Device
            - Manufacturer: ${Build.MANUFACTURER}
            - Model: ${Build.MODEL}
            - Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})

            ## Attachments
            - Attach this ZIP from PokeClaw: `${report.name}`
            - If this looks device-specific and you have ADB available, also attach `adb logcat`

            Generated by PokeClaw ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}.
        """.trimIndent()
    }

    private fun shareReportFile(
        report: java.io.File,
        chooserTitle: String,
        subject: String,
        body: String,
    ) {
        val uri = FileProvider.getUriForFile(
            this@SettingsActivity,
            "${packageName}.fileprovider",
            report
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this@SettingsActivity, "No app available to share the report", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe settings changes and dynamically update UI
                launch {
                    viewModel.settingItems.collect { items ->
                        items.forEach { (key, value) ->
                            when (value) {
                                is SettingsViewModel.SettingValue.Text -> {
                                    menuItems[key]?.setTrailingText(value.text)
                                }
                                is SettingsViewModel.SettingValue.Switch -> {
                                    // Update switch state here if needed
                                }
                            }
                        }
                    }
                }

                // Observe H5 config changes (includes LLM/channel), refresh UI and re-initialize Agent and channels
                launch {
                    ConfigServerManager.configChanged.collect {
                        viewModel.refresh()
                        appViewModel.initAgent()
                        appViewModel.afterInit()
                    }
                }

                // Observe menu click events
                launch {
                    viewModel.menuClickEvent.collect { action ->
                        when (action) {
                            SettingsViewModel.MenuAction.WECHAT -> {
                                if (viewModel.isWechatBound()) {
                                    showUnbindDialog(getString(R.string.channel_wechat)) {
                                        viewModel.unbindWeChat()
                                        Toast.makeText(this@SettingsActivity, R.string.common_unbound_success, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.startWeChatQrLogin(this@SettingsActivity)
                                }
                            }
                            SettingsViewModel.MenuAction.DISCORD -> {
                                if (viewModel.isDiscordBound()) {
                                    showUnbindDialog(getString(R.string.channel_discord)) {
                                        viewModel.unbindDiscord()
                                        Toast.makeText(this@SettingsActivity, R.string.common_unbound_success, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    channelConfigLauncher.launch(ChannelConfigActivity.ChannelType.DISCORD)
                                }
                            }
                            SettingsViewModel.MenuAction.TELEGRAM -> {
                                if (viewModel.isTelegramBound()) {
                                    showUnbindDialog(getString(R.string.channel_telegram)) {
                                        viewModel.unbindTelegram()
                                        Toast.makeText(this@SettingsActivity, R.string.common_unbound_success, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    channelConfigLauncher.launch(ChannelConfigActivity.ChannelType.TELEGRAM)
                                }
                            }
                            SettingsViewModel.MenuAction.LAN_CONFIG -> {
                                val result = viewModel.toggleConfigServer(this@SettingsActivity)
                                if (result == getString(R.string.lan_config_no_wifi)) {
                                    Toast.makeText(this@SettingsActivity, R.string.lan_config_no_wifi, Toast.LENGTH_SHORT).show()
                                }
                            }
                            SettingsViewModel.MenuAction.LLM_CONFIG -> {
                                llmConfigLauncher.launch(Intent(this@SettingsActivity, LlmConfigActivity::class.java))
                            }
                            null -> {}
                            else -> {}
                        }
                        viewModel.clearMenuClickEvent()
                    }
                }
            }
        }
    }

    /**
     * Show unbind confirmation dialog
     */
    /**
     * One Settings row per risk category. Toggles only its own key; the row shows
     * Enabled/Disabled like the other rows in this group.
     */
    private fun addSafetyCategoryRow(
        group: MenuGroup,
        kind: io.agents.pokeclaw.safety.RiskKind,
        titleRes: Int,
        iconRes: Int,
    ) {
        val store = io.agents.pokeclaw.safety.SafetySettings
        val title = getString(titleRes)
        var item: MenuItem? = null
        // Applies the row state as text and as the accessibility description.
        fun render(row: MenuItem, on: Boolean) {
            val state = getString(
                if (on) R.string.safety_row_state_on else R.string.safety_row_state_off
            )
            row.setTrailingText(state)
            row.contentDescription = getString(R.string.safety_row_desc_format, title, state)
        }
        item = group.addMenuItem(
            leadingIcon = iconRes,
            title = title,
            onClick = {
                val next = !store.confirmationFor(kind)
                store.setCategorySwitch(kind, next)
                item?.let { render(it, next) }
                XLog.i("SettingsActivity", "safety category=${kind.name} confirmation=$next")
            },
            showDivider = true
        ).apply {
            render(this, store.confirmationFor(kind))
        }
    }

    /** Shows the one-time migration notice (bilingual) when the owed flag is set. */
    private fun showSafetyMigrationNoticeIfPending() {
        val presenter = object : io.agents.pokeclaw.safety.MigrationNoticePresenter {
            override fun present(): Boolean {
                // AlertDialog.show calls dialog.show() synchronously; a throw means it did not display.
                AlertDialog.show(
                    context = this@SettingsActivity,
                    title = getString(R.string.safety_migration_title),
                    message = getString(R.string.safety_migration_message),
                )
                return true
            }
        }
        val presented = io.agents.pokeclaw.safety.SafetySettings.presentMigrationNoticeIfPending(presenter)
        XLog.i("SettingsActivity", "safety migration notice presented=$presented")
    }

    private fun showUnbindDialog(channelName: String, onUnbind: () -> Unit) {
        AlertDialog.showWarm(
            context = this,
            title = getString(R.string.unbind_title),
            message = getString(R.string.unbind_message, channelName, channelName),
            actionTitle = getString(R.string.unbind_action),
            onAction = onUnbind
        )
    }

    private fun showBudgetDialog() {
        val currentTokens = io.agents.pokeclaw.agent.TaskBudget.getConfiguredMaxTokens()
        val currentCost = io.agents.pokeclaw.agent.TaskBudget.getConfiguredMaxCost()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tokenLabel = android.widget.TextView(this).apply {
            text = getString(R.string.max_tokens_per_task)
            setTextColor(getColor(R.color.colorTextPrimary))
        }
        layout.addView(tokenLabel)

        val tokenOptions = arrayOf("Unlimited", "10K", "50K", "100K", "200K", "250K", "500K")
        val tokenValues = arrayOf<Int?>(null, 10_000, 50_000, 100_000, 200_000, 250_000, 500_000)
        val selectedTokenIndex = when (currentTokens) {
            null -> 0
            else -> tokenValues.indexOfFirst { it == currentTokens }.takeIf { it >= 0 }
                ?: tokenValues.indices
                    .filter { tokenValues[it] != null }
                    .minByOrNull { kotlin.math.abs((tokenValues[it] ?: 0) - currentTokens) }
                ?: 0
        }

        val tokenSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, tokenOptions)
            setSelection(selectedTokenIndex)
        }
        layout.addView(tokenSpinner)

        val costLabel = android.widget.TextView(this).apply {
            text = "\nMax cost per task (USD)"
            setTextColor(getColor(R.color.colorTextPrimary))
        }
        layout.addView(costLabel)

        val costInput = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.blank_no_cost_cap)
            setText(currentCost?.let { String.format("%.2f", it) } ?: "")
            setTextColor(getColor(R.color.colorTextPrimary))
        }
        layout.addView(costInput)

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.task_budget_title))
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newTokens = tokenValues[tokenSpinner.selectedItemPosition]
                val newCost = costInput.text.toString().trim().toDoubleOrNull()

                when (newTokens) {
                    null -> io.agents.pokeclaw.agent.TaskBudget.clearMaxTokens()
                    else -> io.agents.pokeclaw.agent.TaskBudget.saveMaxTokens(newTokens)
                }
                when {
                    newCost == null || newCost <= 0.0 -> io.agents.pokeclaw.agent.TaskBudget.clearMaxCost()
                    else -> io.agents.pokeclaw.agent.TaskBudget.saveMaxCost(newCost)
                }

                val summary = io.agents.pokeclaw.agent.TaskBudget.describeCurrentBudget()
                Toast.makeText(this, "Budget: $summary", Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
