# Phase 2h Item 6 — Arabic action log: reason, result, versioned format, redacted export

Status: UNIT-COMPLETE. Device QA: NOT EXECUTED (rows QL1, QL2 in QA_CHECKLIST.md).

## 1. Baseline
- Before this item: 231 tests / 43 classes / 0 failures. Per-test list: `docs/phase2h_before.txt`.
- After: 242 tests / 45 classes / 0 failures. Per-test list: `docs/phase2h_after.txt`.

## 2. Call-site table (argument-only edits)

| File | Line | Arguments added |
|---|---|---|
| agent/DefaultAgentService.kt | 571 | `ActionReason.TIMEOUT`, `ActionResult.FAILED` |
| agent/DefaultAgentService.kt | 754 | `BLOCKED_BY_APP` if `blockedByApp` else `SAFETY_DENIED`; `ActionResult.DENIED` |
| agent/DefaultAgentService.kt | 784 | `ActionReason.BLOCKED_BY_TAINT`, `ActionResult.DENIED` |
| agent/DefaultAgentService.kt | 809 | `ActionReason.TIMEOUT`, `ActionResult.CANCELLED` |
| agent/DefaultAgentService.kt | 843 | `ActionReason.COMPLETED`; `SUCCESS` if `result.isSuccess` else `FAILED` |
| safety/EmergencyStop.kt | 38 | `ActionReason.EMERGENCY_STOP`, `ActionResult.CANCELLED` |
| safety/SafetyGate.kt | 86–95 | `recordAction` gains `reason`, `result` (defaulted), forwarded to `ActionLog.record` |

Grep for `ActionLog.record|recordAction` before edits: 8 hits, all on the approved list. No other call sites.

## 3. Diff per file

| File | Lines | Result |
|---|---|---|
| safety/ActionLogFormatter.kt | +72, 0 removed | `format`/`parse` (v1) unchanged; added enums, `ActionLogEntryV2`, `formatV2`, `parseV2`, `buildExport`, `field` |
| safety/ActionLog.kt | rewritten | `record(...)` takes `reason`/`result` (defaults), writes v2; `readEntriesMixed` dispatches on `v2 |` prefix; legacy result via `legacyResultOrNull` (fixed words only) |
| safety/ActionLogLabels.kt | new | pure key→label maps, EN and AR |
| safety/SafetyGate.kt | 86–95 | argument forwarding only |
| safety/EmergencyStop.kt | 38 | argument only |
| agent/DefaultAgentService.kt | 571, 754, 784, 809, 843 | argument only |
| ui/settings/ActionLogActivity.kt | render + export | labels via `ActionLogLabels` in app language; export button with `ACTION_CREATE_DOCUMENT` (text/plain) |
| res/values/strings.xml, values-ar/strings.xml | +3 each | export button, exported toast, export failed toast |
| test/.../ActionLogV2Test.kt | new | 5 tests |
| test/.../ActionLogExportTest.kt | new | 6 tests |
| test/.../ActionLogFormatterTest.kt | 0 | not changed by this item (the +18 lines in its diff are phase 2 item 2) |
| QA_CHECKLIST.md | +2 rows | QL1, QL2 |

## 4. New test results (by name)
- ActionLogV2Test: v2LineRoundTripsAllFields PASS, v1LineParsesAsUnknownLegacyWithoutCrashing PASS, mixedV1AndV2LinesReadTogetherInOrder PASS, reasonAndResultAreKeysNotFreeText PASS, noRawSecretReachesStoredLine PASS
- ActionLogExportTest: exportContainsEntriesInOrder PASS, exportRedactsCardIbanAndOtpValues PASS, exportNeverContainsUnredactedDetail PASS, exportOfEmptyLogIsValidEmptyDocument PASS, exportIsIdempotent PASS, labelsLocalizeWithLanguageKey PASS

## 5. Per-test comparison vs docs/phase2h_before.txt
- 231 baseline entries: all unchanged (same class#name, all PASS).
- 11 added entries (the new tests), all PASS.
- 0 removed, 0 pass-to-fail.

## 6. Full suite and build
- `testDebugUnitTest --rerun-tasks`, run 1: 242 / 45 / 0, BUILD SUCCESSFUL.
- `testDebugUnitTest --rerun-tasks`, run 2: 242 / 45 / 0, BUILD SUCCESSFUL.
- `assembleDebug`: BUILD SUCCESSFUL.

## 7. Acceptance
- Reason and result stored as keys: PASS — v2LineRoundTripsAllFields, reasonAndResultAreKeysNotFreeText.
- No raw secret stored: PASS — noRawSecretReachesStoredLine (canary `CANARY_LOG_8e4b` absent; card absent).
- Legacy v1 lines readable: PASS — v1LineParsesAsUnknownLegacyWithoutCrashing, mixedV1AndV2LinesReadTogetherInOrder.
- Export redacted: PASS — exportRedactsCardIbanAndOtpValues, exportNeverContainsUnredactedDetail.
- Labels follow language: PASS — labelsLocalizeWithLanguageKey.
- Export button on device: NOT EXECUTED (QA row QL1).

## Notes and deviations
- v1 `format`/`parse` are byte-identical to the committed HEAD form. The v2 layout is additive (`formatV2`/`parseV2`).
- v1 result is nullable (`null` = unknown). The result enum is exactly SUCCESS, FAILED, DENIED, CANCELLED, PENDING.
- No outcome string is derived from a result key. Legacy result comes only from fixed legacy outcome words (`ok`, `error`, `blocked`, `cancelled`, `stopped`); other words stay unknown.
- `ActionLogFormatterTest.formatAndParseRoundTrip` passes unchanged.
- `sanitize()` runs `SensitiveDataRedactor` first (phase 2 item 2 change, present before this item).
- Field-level pipes are replaced with `/` in v2 task/tool/risk fields so the 8-field split stays valid.
