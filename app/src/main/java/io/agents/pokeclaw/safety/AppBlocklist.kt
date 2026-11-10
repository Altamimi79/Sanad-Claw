// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Banking / password screen protection.
 *
 * Matching is structure-first (package name and app label), with an optional
 * screen-text fallback for password fields. Pure JVM, unit-tested.
 */
object AppBlocklist {

    /** Conservative defaults: banking, wallets and password managers. */
    val DEFAULT_PATTERNS: List<String> = listOf(
        "bank", "banking", "بنك", "مصرف", "البنك", "المصرف",
        "paypal", "wallet", "محفظة", "stc pay", "stcpay", "apple pay", "google pay",
        "1password", "bitwarden", "lastpass", "dashlane", "keepass", "keepsafe",
        "password", "passwords", "كلمات المرور", "كلمة المرور", "مدير كلمات المرور",
        "authenticator", "المصادقة", "otp", "توكن",
    )

    /** Password-ish screen markers used only as a secondary signal. */
    private val PASSWORD_SCREEN_MARKERS = listOf(
        "password", "passcode", "current password", "new password",
        "كلمة المرور", "رمز المرور", "الرقم السري", "رمز التحقق",
    )

    fun parse(raw: String?): List<String> = (raw ?: "")
        .split(',', '\n', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

    fun toStorage(patterns: List<String>): String = patterns
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")

    fun effectivePatterns(userRaw: String?): List<String> {
        val user = parse(userRaw)
        return if (user.isEmpty()) DEFAULT_PATTERNS else user
    }

    /**
     * @param packageName current foreground package (authoritative when present)
     * @param appLabel human app name (secondary)
     * @param screenText visible text, only used to catch password screens of unlisted apps
     */
    fun isBlocked(
        patterns: List<String>,
        packageName: String?,
        appLabel: String? = null,
        screenText: String? = null,
    ): Boolean {
        val pkg = packageName?.trim()?.lowercase().orEmpty()
        val label = appLabel?.trim()?.lowercase().orEmpty()

        if (patterns.any { it.isNotEmpty() && (pkg.contains(it) || (label.isNotEmpty() && label.contains(it))) }) {
            return true
        }

        // Secondary: an unlisted package showing a password screen. Requires a
        // password marker AND a password-ish node label; avoids blocking normal apps
        // that merely mention the word once in an article.
        val screen = screenText?.lowercase().orEmpty()
        if (screen.isEmpty()) return false
        val markerHits = PASSWORD_SCREEN_MARKERS.count { screen.contains(it) }
        return markerHits >= 2
    }
}
