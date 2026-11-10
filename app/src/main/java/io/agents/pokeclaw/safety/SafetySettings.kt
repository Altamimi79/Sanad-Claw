// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.utils.KVUtils

/** Safety preferences, all stored locally via KVUtils. */
object SafetySettings {

    fun confirmationsEnabled(): Boolean = KVUtils.isSafetyConfirmRiskyEnabled()

    fun taskTimeoutSeconds(): Int = KVUtils.getSafetyTaskTimeoutSec()

    fun blocklistPatterns(): List<String> =
        AppBlocklist.effectivePatterns(KVUtils.getSafetyBlocklistRaw())

    fun userBlocklistRaw(): String = KVUtils.getSafetyBlocklistRaw()

    fun actionLogEnabled(): Boolean = KVUtils.isSafetyActionLogEnabled()
}
