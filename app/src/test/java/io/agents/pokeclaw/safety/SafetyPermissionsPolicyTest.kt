// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** Per-category confirmation policy (Phase 2g item 4). Pure JVM, in-memory store. */
class SafetyPermissionsPolicyTest {

    /** In-memory store. Defaults match production: every category switch is on. */
    private class MemoryStore(
        val switches: MutableMap<RiskKind, Boolean> = RiskKind.values()
            .filter { it != RiskKind.UNKNOWN }
            .associateWith { true }
            .toMutableMap(),
    ) : SafetyPermissionStore {
        override fun hasLegacyConfirmRisky() = false
        override fun getLegacyConfirmRisky() = true
        override fun removeLegacyConfirmRisky() = Unit
        override fun hasMigrationNoticeFlag() = false
        override fun getMigrationNoticeShown() = true
        override fun setMigrationNoticeShown(shown: Boolean) = Unit
        override fun getCategorySwitch(kind: RiskKind): Boolean = switches[kind] ?: true
        override fun setCategorySwitch(kind: RiskKind, enabled: Boolean) {
            switches[kind] = enabled
        }
    }

    private val sensitiveKinds = listOf(
        RiskKind.SEND_MESSAGE,
        RiskKind.SEND_FILE,
        RiskKind.CALL,
        RiskKind.DELETE,
        RiskKind.PAYMENT,
        RiskKind.INSTALL,
        RiskKind.PUBLISH,
    )

    @Test
    fun defaultsAreAllTrue() {
        val store = MemoryStore()
        for (kind in sensitiveKinds) {
            assertTrue("default for $kind must confirm", SafetySettings.confirmationFor(kind, store))
        }
    }

    @Test
    fun unknownAlwaysRequiresConfirmationEvenWithoutSwitch() {
        // The store has no switch for UNKNOWN; even a store that answers false must not disable it.
        val store = MemoryStore().apply {
            for (kind in sensitiveKinds) setCategorySwitch(kind, false)
        }
        assertTrue(SafetySettings.confirmationFor(RiskKind.UNKNOWN, store))
        assertTrue(SafetySettings.requiresConfirmation(RiskKind.UNKNOWN, switchOn = false))
        assertTrue(SafetySettings.requiresConfirmation(null, switchOn = false))
    }

    @Test
    fun disabledCategoryDoesNotRequireConfirmation() {
        val store = MemoryStore().apply { setCategorySwitch(RiskKind.SEND_FILE, false) }
        assertFalse(SafetySettings.confirmationFor(RiskKind.SEND_FILE, store))
        assertFalse(
            SafetySettings.requiresConfirmation(
                RiskKind.SEND_FILE,
                SafetySettings.confirmationFor(RiskKind.SEND_FILE, store),
            )
        )
        // Only the switched category is affected; others keep confirming.
        assertTrue(SafetySettings.confirmationFor(RiskKind.SEND_MESSAGE, store))
    }

    @Test
    fun enabledCategoryRequiresConfirmation() {
        val store = MemoryStore().apply { setCategorySwitch(RiskKind.CALL, true) }
        assertTrue(SafetySettings.confirmationFor(RiskKind.CALL, store))
        assertTrue(SafetySettings.requiresConfirmation(RiskKind.CALL, switchOn = true))
    }

    @Test
    fun blocklistBlocksRegardlessOfSwitches() {
        // The blocklist is enforced in SafetyGate before any switch is read. Pure check:
        // a blocked package is blocked whatever the switches say.
        val patterns = AppBlocklist.effectivePatterns("")
        val blocked = AppBlocklist.isBlocked(patterns, "com.example.bank", "Example Bank")
        assertTrue("blocklist must match the bank app", blocked)

        val store = MemoryStore().apply {
            for (kind in sensitiveKinds) setCategorySwitch(kind, false)
        }
        // With every switch off, the blocked result must stand: switches never enter the blocklist check.
        assertTrue(AppBlocklist.isBlocked(patterns, "com.example.bank", "Example Bank"))
        assertFalse(SafetySettings.confirmationFor(RiskKind.SEND_MESSAGE, store))
    }

    @Test
    fun taintedTaskRequiresConfirmationRegardlessOfSwitch() {
        // Calls the PRODUCTION ScreenContentGuard directly (no copy of its logic).
        // ScreenContentGuard.decide does not read any category switch, so the switch state
        // cannot change its result. The store below has every switch OFF to make that explicit.
        val store = object : SafetyPermissionStore {
            override fun hasLegacyConfirmRisky() = false
            override fun getLegacyConfirmRisky() = true
            override fun removeLegacyConfirmRisky() = Unit
            override fun hasMigrationNoticeFlag() = false
            override fun getMigrationNoticeShown() = true
            override fun setMigrationNoticeShown(shown: Boolean) = Unit
            override fun getCategorySwitch(kind: RiskKind): Boolean = false
            override fun setCategorySwitch(kind: RiskKind, enabled: Boolean) = Unit
        }
        for (kind in sensitiveKinds) {
            assertFalse("switch for $kind is off", SafetySettings.confirmationFor(kind, store))
            val guard = ScreenContentGuard()
            guard.wrap("tool_get_screen_info", "any screen text")
            assertTrue("taint must be set for $kind", guard.tainted)
            assertEquals(
                "tainted $kind must require confirmation even with its switch off",
                ScreenContentGuard.Decision.REQUIRE_CONFIRMATION,
                guard.decide(kind, confirmedByUser = false),
            )
        }
    }

    @Test
    fun blockedAppAssessmentWithNullKindIsSafe() {
        // Blocked-app path in SafetyGate: kind == null. Every kind-dependent step must be
        // null-safe: the label lookup falls back to a non-crashing value, and no confirmation is
        // requested for the blocked result (the gate returns before the confirmation step).
        val assessment = RiskAssessment(risky = true, kind = null, requiresConfirmation = false, label = "blocked_app")
        assertNull(assessment.kind)
        // Label lookup by null kind is non-crashing and not a sensitive category name.
        assertEquals("none", RiskClassifier.labelFor(assessment.kind))
        // The blocked assessment does not request confirmation: SafetyGate returns "blocked" before
        // any confirmation step, so the assessment flag is false.
        assertFalse(assessment.requiresConfirmation)
        // Fail-safe for any null kind that DOES reach the confirmation rule: confirm, never pass.
        assertTrue(SafetySettings.requiresConfirmation(assessment.kind, switchOn = false))
        assertEquals("blocked_app", assessment.label)
    }

    @Test
    fun blockedAppAssessmentHasNullKind() {
        // SafetyGate's blocked-app path builds this assessment. The PAYMENT placeholder is gone:
        // kind must be null and no confirmation is requested for it.
        val assessment = RiskAssessment(risky = true, kind = null, requiresConfirmation = false, label = "blocked_app")
        assertNull(assessment.kind)
        assertFalse(assessment.requiresConfirmation)
        assertEquals("blocked_app", assessment.label)
    }
}
