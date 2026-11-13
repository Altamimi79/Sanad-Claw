# Phase 2g — Item 4: per-category confirmation permissions

Scope: item 4 only (unit level). Device QA is NOT EXECUTED. Items 3, 5, 6, 7, 8 are out of scope and untouched.

## 1. Step 0 baseline

- Command: `.\gradlew.bat testDebugUnitTest`
- Result: **218 tests, 41 classes, 0 failures, 0 errors, 0 skipped**. Matches the required baseline, so work proceeded.
- Per-test state saved to `docs/phase2g_before.txt` before any edit (218 PASS lines).

## 2. Diff per file

| File | Lines (+/-) | Result |
|---|---|---|
| `utils/KVUtils.kt` | +33 / -2 | 7 keys `KEY_SAFETY_CONFIRM_<KIND>`, default true, getter+setter each. Legacy key kept as read-only migration accessors. Notice flag accessors. Old single getter/setter removed. |
| `safety/SafetySettings.kt` | +98 / -1 | `SafetyPermissionStore` interface (KVUtils-backed production store, map-backed in tests). `confirmationFor(kind)` (UNKNOWN always true). Pure `requiresConfirmation(kind, switchOn)`. `migrateLegacyConfirmation()`. `isMigrationNoticePending()`, `markMigrationNoticeShown()`, `setCategorySwitch()`. `confirmationsEnabled()` removed. |
| `safety/SafetyGate.kt` | +18 / -4 | PAYMENT placeholder removed from blocked-app path (now `kind = null`, `label = "blocked_app"`). Decision order applied to the confirmation block. Taint note added in the doc comment. |
| `safety/RiskClassifier.kt` | 0 | Not changed. `RiskAssessment.kind` was already nullable. |
| `ui/settings/SettingsActivity.kt` | +67 / -21 | Single toggle replaced by 7 category rows in the Tools group (before the timeout row). `addSafetyCategoryRow` helper. Migration call in `onCreate`. One-time notice via existing `AlertDialog.show`. |
| `res/layout/activity_settings.xml` | 0 | Not changed. Rows are added programmatically into the existing `@+id/toolsGroup` MenuGroup. |
| `res/values/strings.xml` | +16 / -2 | 7 row labels, migration title, migration message (English only). Two lines reflow for the message. |
| `res/values-ar/strings.xml` | +16 / -2 | Same 9 keys, Arabic only. |
| `app/src/test/.../safety/SafetyPermissionsPolicyTest.kt` | new, 125 lines, 7 tests | Required test names. |
| `app/src/test/.../safety/SafetyPermissionsMigrationTest.kt` | new, 101 lines, 4 tests | Required test names. |

Note: `RiskClassifier.kt` was not edited, so no RiskClassifier diff exists to report.

Every edit is within ALLOWED FILES. No file outside the allowed list was modified by this item. Other uncommitted changes in the working tree (DefaultAgentService, ConfigServer, FileLoggingInterceptor, and others) predate this item from earlier phases and were not touched here.

## 3. Test results by name

Class `SafetyPermissionsPolicyTest` (run alone first: 7/7):
- PASS defaultsAreAllTrue
- PASS unknownAlwaysRequiresConfirmationEvenWithoutSwitch
- PASS disabledCategoryDoesNotRequireConfirmation
- PASS enabledCategoryRequiresConfirmation
- PASS blocklistBlocksRegardlessOfSwitches
- PASS taintedTaskRequiresConfirmationRegardlessOfSwitch
- PASS blockedAppAssessmentHasNullKind

Class `SafetyPermissionsMigrationTest` (run alone first: 4/4):
- PASS migrationDoesNotCopyFalseIntoNewKeys
- PASS migrationShowsNoticeOnlyWhenOldValueWasFalse
- PASS migrationRemovesOldKey
- PASS migrationIsIdempotent

## 4. Per-test comparison against baseline

Compared every test in `docs/phase2g_before.txt` against the final run (`docs/phase2g_after.txt`).

- Baseline tests still present and PASS: **218 of 218**.
- Baseline tests that changed state: **0**.
- Pass-to-fail: **0**. No STOP condition triggered.
- New tests: 11 (all PASS).
- Totals after: 229 tests, 43 classes, 0 failures.

The brief anticipated a change in the PAYMENT placeholder test. No baseline test asserted the blocked-app placeholder, because that path requires Android (`ClawApplication`, the accessibility service) and is not JVM-testable. The placeholder removal is therefore verified by the new `blockedAppAssessmentHasNullKind` test and by code inspection, not by a changed baseline test. `RiskClassifierTest` (which asserts PAYMENT classification of payment text) is intentionally unchanged: payment *detection* stays; only the blocked-app placeholder was removed.

## 5. Decision order in SafetyGate

| Rule | Implemented in | Location |
|---|---|---|
| 1. Blocklist match: blocked, always | `SafetyGate.evaluate` | `safety/SafetyGate.kt` lines 41-42 (blocklist check returns `allow=false` before any switch is read) |
| 2. Tainted task + sensitive kind: confirmation, always | **`DefaultAgentService.kt` lines 768-769**, not SafetyGate. | `ScreenContentGuard.decide` (`safety/ScreenContentGuard.kt:50`) with `SENSITIVE_KINDS`. See deviation below. |
| 3. kind == UNKNOWN: confirmation, always | `SafetyGate.evaluate` via `SafetySettings.requiresConfirmation` | `safety/SafetyGate.kt` lines 59-62; `safety/SafetySettings.kt` `requiresConfirmation` (UNKNOWN and null return true) |
| 4. Otherwise: confirmation only if `confirmationFor(kind)` | `SafetyGate.evaluate` | `safety/SafetyGate.kt` lines 61-62 (`switchOn = SafetySettings.confirmationFor(it)`) |

**Deviation (rule 2):** the brief lists rule 2 under SafetyGate. Taint is per-task state held by `ScreenContentGuard` inside `DefaultAgentService`, and `SafetyGate.evaluate` receives only tool name, params, and prompt, with no taint input. Moving taint into SafetyGate requires changing `DefaultAgentService`, which is outside the allowed list. I did not make that change. Rule 2 is enforced by the existing Phase 2b code path, which this item did not modify, so the taint guarantee holds as before. This needs a decision: either accept the current location, or permit a follow-up edit to pass taint into `SafetyGate`.

Unit coverage for rule 2 is the `taintedTaskRequiresConfirmationRegardlessOfSwitch` test, which exercises `ScreenContentGuard.decide` with each sensitive kind and the switch conceptually off (the guard does not read switches at all, so the switch cannot override it).

## 6. Migration: exact behavior tested

`SafetySettings.migrateLegacyConfirmation()` runs in `SettingsActivity.onCreate`.

- If the legacy key `KEY_SAFETY_CONFIRM_RISKY` is absent: no change to the seven keys. Returns whether a notice is still owed.
- If present:
  - Does not copy its value into the seven new keys. They keep default true (`migrationDoesNotCopyFalseIntoNewKeys`).
  - If the old value was **false**: sets the notice flag to false (notice owed). If true: no notice (`migrationShowsNoticeOnlyWhenOldValueWasFalse`).
  - Always removes the legacy key (`migrationRemovesOldKey`).
- Notice: shown once via `AlertDialog.show`, after `markMigrationNoticeShown()` clears the owed state. Bilingual through the two locale files (en and ar, each in its own language; Android resource fallback selects the language).
- Idempotent (`migrationIsIdempotent`): a second run with no legacy key changes no switch, no flag, and does not remove again.

## 7. Full suite result, twice

- Run A (`testDebugUnitTest --rerun-tasks`): 43 classes, 229 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.
- Run B (`testDebugUnitTest --rerun-tasks`): 43 classes, 229 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.
- `assembleDebug`: BUILD SUCCESSFUL, APK `PokeClaw_v1.0.0_20261113_063719.apk`.

Note: a first attempt at run B was killed because it was launched in parallel with run A and collided on the Gradle build directory. It was re-run alone and passed; the figures above are from the clean run.

## 8. Acceptance

| Criterion | Result | Evidence |
|---|---|---|
| Per-category switches | **PASS** | `defaultsAreAllTrue`, `disabledCategoryDoesNotRequireConfirmation`, `enabledCategoryRequiresConfirmation` |
| UNKNOWN always confirms | **PASS** | `unknownAlwaysRequiresConfirmationEvenWithoutSwitch` |
| Blocklist unaffected by switches | **PASS** | `blocklistBlocksRegardlessOfSwitches` (unit). Gate ordering verified by code inspection. |
| Taint overrides switches | **PASS** | `taintedTaskRequiresConfirmationRegardlessOfSwitch` (unit, `ScreenContentGuard`). Enforced in DefaultAgentService, see deviation in section 5. |
| Migration correct and idempotent | **PASS** | `migrationDoesNotCopyFalseIntoNewKeys`, `migrationShowsNoticeOnlyWhenOldValueWasFalse`, `migrationRemovesOldKey`, `migrationIsIdempotent` |
| Settings UI rendering | **NOT EXECUTED (device pending)** | Rows and notice compile and are placed correctly in code; rendering, tap behavior, and notice display are not verified on hardware. |

## Known limits and deviations

1. **Taint location (rule 2):** enforced in DefaultAgentService, not SafetyGate. Needs a decision (section 5).
2. **Settings rows are not switch widgets.** `MenuItem` has no switch support and is outside the allowed list. The rows follow the existing tap-to-toggle pattern with trailing Enabled/Disabled text. The brief says "seven switch rows"; this is a row-toggle, not a `Switch` widget. A real switch needs `MenuItem` changes (outside allowed files).
3. **Plan file conflict:** `.kilo/plans/...-plan.md` Item 4 specifies copying the legacy value to all seven keys and adds `safety/CategoryPermissionPolicy.kt`. The brief forbids both (no copy; file not in allowed list). The brief was followed; the plan file was not edited to match.
4. **Legacy string key** `safety_confirm_risky_setting` remains in strings.xml (no longer referenced). Left in place to avoid churn.
5. **Blocked-app path** is not JVM-testable (needs Android). Verified by the assessment-shape test and by code.
6. **Migration notice text** is bilingual across the two locale files, not in a single string.
7. **Notice ordering:** `showSafetyMigrationNoticeIfPending` clears the owed flag before the dialog opens, so the notice can never repeat. If the dialog failed to render, the notice would be lost. Accepted as the brief requires "shown once"; QP7 checks the display on device.

## Pending

- Device QA for the seven rows, toggle persistence, migration notice display (NOT EXECUTED).
- Decision on rule 2 placement (section 5).

ITEM 4 UNIT-COMPLETE. DEVICE QA PENDING.

---

# Phase 2g-fix: three fixes plus row state text

Baseline before fixes: 229 tests, 43 classes, 0 failures (verified). Final: **231 tests, 43 classes, 0 failures**, two runs.

## 1. Diff per file (cumulative vs HEAD, plus this round)

| File | Lines (+/-) | Result |
|---|---|---|
| `safety/SafetySettings.kt` | +124 / -1 | Added `MigrationNoticePresenter` and `presentMigrationNoticeIfPending` (flag cleared only on success). |
| `ui/settings/SettingsActivity.kt` | +74 / -21 | Notice ordering: presenter wraps `AlertDialog.show`; flag no longer cleared before show. Row `render()` sets trailing state text and `contentDescription`. |
| `safety/SafetyGate.kt` | +18 / -4 (unchanged this round) | No edit needed for null handling (see section 2). |
| `res/values/strings.xml` | +19 / -2 | Added `safety_row_state_on`, `safety_row_state_off`, `safety_row_desc_format`. |
| `res/values-ar/strings.xml` | +19 / -2 | Same three keys. |
| `SafetyPermissionsPolicyTest.kt` | new, 154 lines | Added `blockedAppAssessmentWithNullKindIsSafe`; rewrote `taintedTaskRequiresConfirmationRegardlessOfSwitch` to use the production store and `decide`. |
| `SafetyPermissionsMigrationTest.kt` | new, 129 lines | Added `noticeSurvivesDialogFailure`. |

No file outside ALLOWED FILES was touched. Files that read `assessment.kind` were not edited.

## 2. Fix 2 grep table: every production use of kind that dereferences or branches on it

| file | line | handles null | action |
|---|---|---|---|
| agent/DefaultAgentService.kt | 768 | yes | `taintKind` is `RiskKind?`, passed to `decide(RiskKind?)`. No edit. |
| agent/DefaultAgentService.kt | 769 | yes | `screenGuard.decide(taintKind, ...)` accepts null; returns ALLOW_WITH_WARNING (ScreenContentGuard.kt:52). No edit. |
| agent/DefaultAgentService.kt | 775 | yes | `taintKind?.name?.lowercase()` is null-safe. No edit. |
| safety/SafetyGate.kt | 60-62 | yes | `kind?.let {...} ?: false`; `requiresConfirmation(null, _)` returns true (fail-safe). No edit. |
| safety/SafetyGate.kt | 68 | yes | `riskLabel(app, RiskKind?)` maps null to `risk_generic` (line 99). No edit. |
| safety/SafetyGate.kt | 41-50 | n/a | Blocked-app path builds `kind = null` and returns before any kind read. No edit. |
| safety/SafetyGate.kt | 90-99 | yes | `when (kind)` has `RiskKind.UNKNOWN, null -> R.string.risk_generic`. No edit. |
| safety/RiskClassifier.kt | 105-113 | yes | `labelFor(kind: RiskKind?)` has `null -> "none"`. No edit. |
| safety/RiskClassifier.kt | 23 | n/a | Declares `kind: RiskKind?`. No edit. |
| safety/SafetySettings.kt | 75-77 | yes | `requiresConfirmation(kind: RiskKind?, ...)`: `null, UNKNOWN -> true`. No edit. |
| safety/SafetySettings.kt | 38-58 | n/a (non-null) | Store methods take non-null `RiskKind`; callers pass non-null (gate guards null with `?.let`). No edit. |
| safety/ScreenContentGuard.kt | 50-52 | yes | `decide(kind: RiskKind?)`: `kind == null` returns ALLOW_WITH_WARNING. No edit. |
| safety/SafetyGate.kt | 64, 70-76 | uses `assessment.label` (String), not kind. No edit. |
| agent/DefaultAgentService.kt | 755, 846 | uses `assessment.label` (String). No edit. |
| ui/settings/SettingsActivity.kt | 546-558, 934 | non-null: the seven rows are fixed kinds. No edit. |

Result: zero production fixes needed. Every production dereference or branch on kind already handles null. The blocked-app label never reaches a kind lookup (the gate returns first), and the `blocked_app` string is consumed only by logging and ActionLog.

## 3. Test results (new and rewritten tests)

- PASS `SafetyPermissionsMigrationTest.noticeSurvivesDialogFailure`
- PASS `SafetyPermissionsPolicyTest.blockedAppAssessmentWithNullKindIsSafe`
- PASS `SafetyPermissionsPolicyTest.taintedTaskRequiresConfirmationRegardlessOfSwitch` (rewritten to call production code)
- All other tests: unchanged state (per-test comparison: zero state changes, zero pass-to-fail).

## 4. Final suite result, twice

- Run 1: 43 classes, 231 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.
- Run 2: 43 classes, 231 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.
- `assembleDebug`: BUILD SUCCESSFUL.

## 5. Acceptance

- Notice survives dialog failure: **PASS**, `SafetyPermissionsMigrationTest.noticeSurvivesDialogFailure`. Covers a presenter returning false and one throwing; flag stays owed; succeeds once and does not repeat. Production path: `SettingsActivity.showSafetyMigrationNoticeIfPending` → `SafetySettings.presentMigrationNoticeIfPending`.
- Null kind safe: **PASS**, `SafetyPermissionsPolicyTest.blockedAppAssessmentWithNullKindIsSafe`, plus the grep evidence in section 2 (no production change needed).
- Taint test calls production function: **PASS**, `SafetyPermissionsPolicyTest.taintedTaskRequiresConfirmationRegardlessOfSwitch`. The body calls `ScreenContentGuard()`, `guard.wrap(...)`, `guard.tainted`, and `guard.decide(kind, confirmedByUser = false)` directly. The switch is confirmed off through production `SafetySettings.confirmationFor`. No copy of the logic.
- Taint in DefaultAgentService: **verified by inspection, lines 768-769 (not tested).** No unit test exercises that wiring.
- Row state text present: **PASS**. Keys `safety_row_state_on` and `safety_row_state_off` (en: "Enabled / مفعّل", "Disabled / موقوف"; ar: "مفعّل / Enabled", "موقوف / Disabled"), rendered by `SettingsActivity.addSafetyCategoryRow.render()`. Content description from `safety_row_desc_format` ("%1$s: %2$s"). Device rendering: NOT EXECUTED.

## Notes

- Both locale files carry the bilingual state pair, matching the brief's wording. This is a display choice.
- `contentDescription` is set on the `MenuItem` root view from SettingsActivity; `MenuItem.kt` was not edited (outside allowed list).

ITEM 4 FIXES COMPLETE. UNIT-COMPLETE. DEVICE QA PENDING.
