# Phase 2c Step 0 — Unit test count reconciliation

## 1. Base commit

- **Base:** `1a8b426` — `docs(qa+memory): round log for the fork UI/providers round ...`
- **Selection:** parent of `96a736f` (the held-out corpus commit). It is the last commit before any Phase 2 test/corpus file was tracked.
- **Caveat (not certain by git history alone):** the Phase 2 safety files (`PromptInjectionGuard.kt`, `ScreenContentGuard.kt`, `SensitiveDataRedactor.kt`, `OtpGuard.kt`, and all Phase 2 tests) were **never committed**. `git log -- PromptInjectionGuard.kt` is empty, so no commit "added" it. The brief's rule ("commit before the first commit that added PromptInjectionGuard.kt") cannot be applied literally. The base was chosen as the last commit before the first tracked Phase 2 artifact (`96a736f`).
- **Independent confirmation:** `1a8b426` tracks 28 test source files under `app/src/test` and contains 127 `@Test` annotations, matching the trusted snapshot reference (28 classes, 127 tests).

## 2. Per-class table (only rows that are NOT unchanged)

Columns: base = count at `1a8b426`; HEAD = count in the working tree (committed + uncommitted; `HEAD` of `96a736f` plus edits).

| Class | Base | HEAD | Category | Delta | Notes |
|---|---|---|---|---|---|
| ActionLogFormatterTest | 4 | 5 | CHANGED | +1 | added `actionLogNeverReceivesOtpOrIbanOrNationalIdValues` (uncommitted) |
| PromptInjectionGuardTest | — | 9 | NEW | +9 | Phase 2 |
| ScreenContentGuardTest | — | 9 | NEW | +9 | Phase 2 |
| SensitiveDataRedactorTest | — | 17 | NEW | +17 | Phase 2 |
| OtpGuardTest | — | 6 | NEW | +6 | Phase 2 |
| InjectionCorpusTest | — | 4 | NEW | +4 | Phase 2 |
| InjectionSensitiveActionPolicyTest | — | 5 | NEW | +5 | Phase 2 |
| TaintPolicyTest | — | 6 | NEW | +6 | Phase 2b |
| CloudPayloadTest | — | 3 | NEW | +3 | Phase 2b |
| HeldOutCorpusTest | — | 1 | NEW | +1 | Phase 2b |

All other 28 base classes are **UNCHANGED** (same count at base and HEAD) and are omitted per the brief.

REMOVED classes: **none** (0). All 28 base test files exist in the working tree.

## 3. Per-class detail for CHANGED classes

### ActionLogFormatterTest (CHANGED)
- Base: 4 tests
- HEAD: 5 tests
- Added: `actionLogNeverReceivesOtpOrIbanOrNationalIdValues`
- Removed: none

Note on the earlier Step 0 table: the first probe read the **committed** `HEAD` version (4). The working tree has 5. The brief asks for HEAD to include uncommitted changes, so 5 is the correct value. This is why the earlier table missed it.

## 4. Balance equation

| Term | Value |
|---|---|
| Base total (`1a8b426`) | 127 |
| + sum of NEW class counts | 60 (9 classes) |
| + sum of CHANGED deltas | 1 (ActionLogFormatterTest) |
| − sum of REMOVED | 0 |
| **= computed HEAD total** | **188** |
| Measured HEAD total (working tree `@Test` count) | 188 |
| **Residual** | **0** |

NEW breakdown: 9 + 9 + 17 + 6 + 4 + 5 + 6 + 3 + 1 = **60**.

Files at HEAD (working tree): **37** (28 base + 9 NEW). Equals the XML-reported 37 classes from the previous run.

## 5. Explanation of the earlier discrepancy

The previous Step 0 reported "+11 vs 177" and could not identify the 11th test. The source of the extra test is:
- **+10** from the three Phase 2b classes (`TaintPolicyTest` 6, `CloudPayloadTest` 3, `HeldOutCorpusTest` 1), plus
- **+1** from `ActionLogFormatterTest` (added by Phase 2, uncommitted).

The "177" figure in the earlier report was not a base measurement. It was a figure recorded at the end of Phase 2, before Phase 2b added its tests, and it did not include the `ActionLogFormatterTest` addition. This report uses the independently measured base (127), not 177.

## 6. Verdict

**RECONCILED** — base 127 + NEW 60 + CHANGED 1 − REMOVED 0 = HEAD 188, residual 0, all 37 classes accounted for.

Caveat: the base was selected by the parent-of-first-tracked-artifact rule and independently confirmed by the 28-class / 127-test reproduction. No commit exists that introduces the Phase 2 safety files, so the base is not traceable from git history.
