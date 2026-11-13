// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One-time migration of the Phase 4 single switch (Phase 2g item 4). Pure JVM. */
class SafetyPermissionsMigrationTest {

    /** Store that mirrors KVUtils keys: legacy key, notice flag, seven category switches. */
    private class MemoryStore(
        var legacy: Boolean? = null,
        var noticeFlag: Boolean? = null,
    ) : SafetyPermissionStore {
        val switches: MutableMap<RiskKind, Boolean> = mutableMapOf()
        var legacyRemovals = 0
        var flagWrites = 0

        override fun hasLegacyConfirmRisky() = legacy != null
        override fun getLegacyConfirmRisky() = legacy ?: true
        override fun removeLegacyConfirmRisky() {
            legacy = null
            legacyRemovals++
        }
        override fun hasMigrationNoticeFlag() = noticeFlag != null
        override fun getMigrationNoticeShown() = noticeFlag ?: true
        override fun setMigrationNoticeShown(shown: Boolean) {
            noticeFlag = shown
            flagWrites++
        }
        override fun getCategorySwitch(kind: RiskKind): Boolean = switches[kind] ?: true
        override fun setCategorySwitch(kind: RiskKind, enabled: Boolean) {
            switches[kind] = enabled
        }
    }

    @Test
    fun migrationDoesNotCopyFalseIntoNewKeys() {
        // Old value false: the seven new keys must stay at default true, not become false.
        val store = MemoryStore(legacy = false)
        SafetySettings.migrateLegacyConfirmation(store)
        assertTrue("no switch may inherit false", store.switches.isEmpty())
        for (kind in RiskKind.values().filter { it != RiskKind.UNKNOWN }) {
            assertTrue("$kind must default to confirm", SafetySettings.confirmationFor(kind, store))
        }
    }

    @Test
    fun migrationShowsNoticeOnlyWhenOldValueWasFalse() {
        // Old value false: notice owed.
        val offStore = MemoryStore(legacy = false)
        assertTrue("notice owed when old value was false", SafetySettings.migrateLegacyConfirmation(offStore))
        assertTrue(SafetySettings.isMigrationNoticePending(offStore))

        // Old value true: no notice.
        val onStore = MemoryStore(legacy = true)
        assertFalse("no notice when old value was true", SafetySettings.migrateLegacyConfirmation(onStore))
        assertFalse(SafetySettings.isMigrationNoticePending(onStore))
        assertEquals(null, onStore.noticeFlag)

        // Notice shown once: marking it clears the pending state permanently.
        SafetySettings.markMigrationNoticeShown(offStore)
        assertFalse(SafetySettings.isMigrationNoticePending(offStore))
    }

    @Test
    fun migrationRemovesOldKey() {
        val store = MemoryStore(legacy = false)
        SafetySettings.migrateLegacyConfirmation(store)
        assertFalse("legacy key must be removed", store.hasLegacyConfirmRisky())
        assertEquals(1, store.legacyRemovals)

        val onStore = MemoryStore(legacy = true)
        SafetySettings.migrateLegacyConfirmation(onStore)
        assertFalse("legacy key removed even when true", onStore.hasLegacyConfirmRisky())
    }

    @Test
    fun noticeSurvivesDialogFailure() {
        // Owed notice (old value false). The dialog fails to show: flag must stay owed.
        val store = MemoryStore(legacy = false)
        SafetySettings.migrateLegacyConfirmation(store)
        assertTrue(SafetySettings.isMigrationNoticePending(store))

        val returnsFalse = object : MigrationNoticePresenter {
            override fun present(): Boolean = false
        }
        assertFalse("failed presentation reports not shown", SafetySettings.presentMigrationNoticeIfPending(returnsFalse, store))
        assertTrue("flag must stay owed after returned failure", SafetySettings.isMigrationNoticePending(store))

        val throws = object : MigrationNoticePresenter {
            override fun present(): Boolean = throw IllegalStateException("window token invalid")
        }
        assertFalse("thrown failure reports not shown", SafetySettings.presentMigrationNoticeIfPending(throws, store))
        assertTrue("flag must stay owed after thrown failure", SafetySettings.isMigrationNoticePending(store))

        // Next launch: presentation succeeds, flag is cleared, and it does not repeat.
        val succeeds = object : MigrationNoticePresenter {
            override fun present(): Boolean = true
        }
        assertTrue(SafetySettings.presentMigrationNoticeIfPending(succeeds, store))
        assertFalse("flag cleared only after success", SafetySettings.isMigrationNoticePending(store))
        assertFalse("does not repeat once shown", SafetySettings.presentMigrationNoticeIfPending(succeeds, store))
    }

    @Test
    fun migrationIsIdempotent() {
        val store = MemoryStore(legacy = false)
        val first = SafetySettings.migrateLegacyConfirmation(store)
        val snapshotSwitches = store.switches.toMap()
        val snapshotFlag = store.noticeFlag
        val removalsAfterFirst = store.legacyRemovals
        val writesAfterFirst = store.flagWrites

        // Second run: no legacy key, so nothing may change.
        val second = SafetySettings.migrateLegacyConfirmation(store)

        assertTrue("first run owes the notice", first)
        assertTrue("second run still reports the owed notice, unchanged", second)
        assertEquals("switches unchanged on second run", snapshotSwitches, store.switches)
        assertEquals("notice flag unchanged on second run", snapshotFlag, store.noticeFlag)
        assertEquals("no extra legacy removal on second run", removalsAfterFirst, store.legacyRemovals)
        assertEquals("no extra flag write on second run", writesAfterFirst, store.flagWrites)
    }
}
