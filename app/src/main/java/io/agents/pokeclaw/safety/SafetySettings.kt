// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.utils.KVUtils

/** Storage seam for the per-category permission switches. KVUtils in production, a map in tests. */
interface SafetyPermissionStore {
    fun hasLegacyConfirmRisky(): Boolean
    fun getLegacyConfirmRisky(): Boolean
    fun removeLegacyConfirmRisky()
    fun hasMigrationNoticeFlag(): Boolean
    fun getMigrationNoticeShown(): Boolean
    fun setMigrationNoticeShown(shown: Boolean)
    fun getCategorySwitch(kind: RiskKind): Boolean
    fun setCategorySwitch(kind: RiskKind, enabled: Boolean)
}

/** Shows the one-time migration notice. Returns true only when the dialog was actually displayed. */
interface MigrationNoticePresenter {
    fun present(): Boolean
}

/** Safety preferences, all stored locally via KVUtils. */
object SafetySettings {

    /** Production store backed by KVUtils. */
    private val kvStore: SafetyPermissionStore = object : SafetyPermissionStore {
        override fun hasLegacyConfirmRisky() = KVUtils.hasLegacySafetyConfirmRisky()
        override fun getLegacyConfirmRisky() = KVUtils.getLegacySafetyConfirmRisky()
        override fun removeLegacyConfirmRisky() = KVUtils.removeLegacySafetyConfirmRisky()
        override fun hasMigrationNoticeFlag() = KVUtils.hasMigrationNoticeFlag()
        override fun getMigrationNoticeShown() = KVUtils.getMigrationNoticeShown()
        override fun setMigrationNoticeShown(shown: Boolean) {
            KVUtils.setMigrationNoticeShown(shown)
        }
        override fun getCategorySwitch(kind: RiskKind): Boolean = when (kind) {
            RiskKind.SEND_MESSAGE -> KVUtils.isSafetyConfirmSendMessageEnabled()
            RiskKind.SEND_FILE -> KVUtils.isSafetyConfirmSendFileEnabled()
            RiskKind.CALL -> KVUtils.isSafetyConfirmCallEnabled()
            RiskKind.DELETE -> KVUtils.isSafetyConfirmDeleteEnabled()
            RiskKind.PAYMENT -> KVUtils.isSafetyConfirmPaymentEnabled()
            RiskKind.INSTALL -> KVUtils.isSafetyConfirmInstallEnabled()
            RiskKind.PUBLISH -> KVUtils.isSafetyConfirmPublishEnabled()
            // UNKNOWN has no switch; it always confirms (see confirmationFor).
            RiskKind.UNKNOWN -> true
        }
        override fun setCategorySwitch(kind: RiskKind, enabled: Boolean) {
            when (kind) {
                RiskKind.SEND_MESSAGE -> KVUtils.setSafetyConfirmSendMessageEnabled(enabled)
                RiskKind.SEND_FILE -> KVUtils.setSafetyConfirmSendFileEnabled(enabled)
                RiskKind.CALL -> KVUtils.setSafetyConfirmCallEnabled(enabled)
                RiskKind.DELETE -> KVUtils.setSafetyConfirmDeleteEnabled(enabled)
                RiskKind.PAYMENT -> KVUtils.setSafetyConfirmPaymentEnabled(enabled)
                RiskKind.INSTALL -> KVUtils.setSafetyConfirmInstallEnabled(enabled)
                RiskKind.PUBLISH -> KVUtils.setSafetyConfirmPublishEnabled(enabled)
                RiskKind.UNKNOWN -> Unit
            }
        }
    }

    /** Returns true when the switch for [kind] requires confirmation. UNKNOWN always does. */
    fun confirmationFor(kind: RiskKind, store: SafetyPermissionStore = kvStore): Boolean {
        if (kind == RiskKind.UNKNOWN) return true
        return store.getCategorySwitch(kind)
    }

    /**
     * Pure rule for decision steps 3 and 4 of SafetyGate. Blocklist and taint are handled
     * by the caller (SafetyGate step 1, DefaultAgentService step 2).
     * - null or UNKNOWN: confirmation required, regardless of any switch.
     * - Otherwise: required only when the category switch is on.
     */
    fun requiresConfirmation(kind: RiskKind?, switchOn: Boolean): Boolean = when (kind) {
        null, RiskKind.UNKNOWN -> true
        else -> switchOn
    }

    /**
     * One-time migration from the single Phase 4 switch.
     * - Never copies the old value into the seven new keys (they keep default true).
     * - If the old value was false, sets the notice flag to false (notice owed).
     * - Always removes the old key.
     * - Idempotent: without the old key it changes nothing.
     * Returns true if the notice is owed to the user after this call.
     */
    fun migrateLegacyConfirmation(store: SafetyPermissionStore = kvStore): Boolean {
        if (!store.hasLegacyConfirmRisky()) {
            return store.hasMigrationNoticeFlag() && !store.getMigrationNoticeShown()
        }
        val oldValue = store.getLegacyConfirmRisky()
        store.removeLegacyConfirmRisky()
        if (!oldValue) {
            store.setMigrationNoticeShown(false)
        }
        return !oldValue
    }

    /** True when the migration notice is owed and has not been shown yet. */
    fun isMigrationNoticePending(store: SafetyPermissionStore = kvStore): Boolean =
        store.hasMigrationNoticeFlag() && !store.getMigrationNoticeShown()

    /** Writes the switch for [kind]. UNKNOWN has no switch and is ignored. */
    fun setCategorySwitch(kind: RiskKind, enabled: Boolean, store: SafetyPermissionStore = kvStore) {
        if (kind == RiskKind.UNKNOWN) return
        store.setCategorySwitch(kind, enabled)
    }

    /** Marks the one-time notice as shown so it never appears again. */
    fun markMigrationNoticeShown(store: SafetyPermissionStore = kvStore) {
        store.setMigrationNoticeShown(true)
    }

    /**
     * Presents the owed notice and clears the owed flag ONLY after the presenter reports
     * success. If presentation fails (or throws), the flag stays owed and the notice is
     * shown again on the next launch. Returns true if the notice was presented.
     */
    fun presentMigrationNoticeIfPending(
        presenter: MigrationNoticePresenter,
        store: SafetyPermissionStore = kvStore,
    ): Boolean {
        if (!isMigrationNoticePending(store)) return false
        val shown = try {
            presenter.present()
        } catch (e: Exception) {
            false
        }
        if (shown) {
            markMigrationNoticeShown(store)
        }
        return shown
    }

    fun taskTimeoutSeconds(): Int = KVUtils.getSafetyTaskTimeoutSec()

    fun blocklistPatterns(): List<String> =
        AppBlocklist.effectivePatterns(KVUtils.getSafetyBlocklistRaw())

    fun userBlocklistRaw(): String = KVUtils.getSafetyBlocklistRaw()

    fun actionLogEnabled(): Boolean = KVUtils.isSafetyActionLogEnabled()
}
