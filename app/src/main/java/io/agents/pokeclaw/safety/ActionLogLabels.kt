// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Pure mapping from a stored reason/result key to a label key for the current language.
 * The UI resolves the label key to a string resource; this object has no Android dependency.
 * Language "ar" selects the Arabic label set, anything else the English set.
 */
object ActionLogLabels {

    private val reasonEn = mapOf(
        ActionReason.USER_REQUESTED to "Requested by user",
        ActionReason.SAFETY_CONFIRMED to "Confirmed by user",
        ActionReason.SAFETY_DENIED to "Denied by safety check",
        ActionReason.BLOCKED_BY_APP to "Blocked app",
        ActionReason.BLOCKED_BY_TAINT to "Blocked: untrusted screen content",
        ActionReason.TIMEOUT to "Timed out",
        ActionReason.EMERGENCY_STOP to "Emergency stop",
        ActionReason.COMPLETED to "Completed",
        ActionReason.UNKNOWN_LEGACY to "Unknown (older entry)",
    )
    private val reasonAr = mapOf(
        ActionReason.USER_REQUESTED to "طلبه المستخدم",
        ActionReason.SAFETY_CONFIRMED to "أكّده المستخدم",
        ActionReason.SAFETY_DENIED to "رفضه فحص الأمان",
        ActionReason.BLOCKED_BY_APP to "تطبيق محظور",
        ActionReason.BLOCKED_BY_TAINT to "محظور: محتوى شاشة غير موثوق",
        ActionReason.TIMEOUT to "انتهت المهلة",
        ActionReason.EMERGENCY_STOP to "إيقاف طارئ",
        ActionReason.COMPLETED to "اكتمل",
        ActionReason.UNKNOWN_LEGACY to "غير معروف (سجل قديم)",
    )
    private val resultEn = mapOf(
        ActionResult.SUCCESS to "Success",
        ActionResult.FAILED to "Failed",
        ActionResult.DENIED to "Denied",
        ActionResult.CANCELLED to "Cancelled",
        ActionResult.PENDING to "Pending",
    )
    private val resultAr = mapOf(
        ActionResult.SUCCESS to "نجح",
        ActionResult.FAILED to "فشل",
        ActionResult.DENIED to "مرفوض",
        ActionResult.CANCELLED to "أُلغي",
        ActionResult.PENDING to "قيد الانتظار",
    )

    fun reasonLabel(reason: ActionReason, language: String): String =
        (if (language == "ar") reasonAr else reasonEn).getValue(reason)

    fun resultLabel(result: ActionResult?, language: String): String {
        val table = if (language == "ar") resultAr else resultEn
        val unknown = if (language == "ar") "غير معروف" else "Unknown"
        return if (result == null) unknown else table.getValue(result)
    }
}
