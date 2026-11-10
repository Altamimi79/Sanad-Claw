// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

/**
 * Pure secret-display masking shared by ConfigServer responses and any UI that must
 * show a saved secret without exposing it. Never use masked values as real values:
 * isMasked() identifies strings that came from mask().
 */
object SecretsMasker {

    fun mask(secret: String): String {
        if (secret.isEmpty()) return ""
        if (secret.length <= 4) return secret
        return "*".repeat(secret.length - 4) + secret.takeLast(4)
    }

    fun isMasked(value: String): Boolean = value.contains("*")
}
