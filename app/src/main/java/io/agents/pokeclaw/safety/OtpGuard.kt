// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Hides OTP / verification-code notifications from the agent. Pure JVM, unit-tested.
 * A notification is OTP-bearing when its text has an OTP keyword AND a 4-8 digit code.
 */
object OtpGuard {

    const val HIDDEN_PLACEHOLDER = "[OTP hidden]"

    private val KEYWORDS = Regex(
        """(?i)\b(otp|one[- ]time (password|code)|verification code|verify code|security code|passcode|login code|code is)\b""" +
            """|رمز التحقق|رمز التأكيد|رمز التفعيل|كود التحقق|كود التأكيد|رمز الدخول|كلمة المرور لمرة واحدة""",
    )
    private val CODE = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    /** True when the text looks like an OTP / verification code message. */
    fun isOtpBearing(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return KEYWORDS.containsMatchIn(text) && CODE.containsMatchIn(text)
    }

    /** Returns the placeholder for OTP-bearing text, otherwise the text unchanged. */
    fun filter(text: String?): String {
        if (text.isNullOrEmpty()) return text.orEmpty()
        return if (isOtpBearing(text)) HIDDEN_PLACEHOLDER else text
    }
}
