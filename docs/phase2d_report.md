# Phase 2d — Items 1 and 2, unit-level completion report

Scope: items 1 and 2 only. Items 3-8 not touched. Device QA not executed.

## Baseline (Step 0)

`.\gradlew.bat testDebugUnitTest` → **188 tests, 37 classes, 0 failures** (matches expected).

## Diff by step

| Step | File | Lines (approx) | Tests | Result |
|---|---|---|---|---|
| 1 | `safety/PausedTaskState.kt` | new, ~150 | — | green |
| 1 | `safety/PausedTaskStateTest.kt` | new | 14 | green |
| 1 | `agent/DefaultAgentService.kt` | +~35 (pause hook, substitution, clear at end) | — | green |
| 1 | `ui/chat/TaskFlowController.kt` | +~6 (route message while paused) | — | green |
| 1 | `ui/chat/ChatSessionController.kt` | +~5 (route message while paused) | — | green |
| 1 | `res/values/strings.xml`, `values-ar/strings.xml` | +3 strings each | — | green |
| 2 | `safety/SensitiveDataRedactor.kt` | card pass rewritten (fixes false negative) | (covered by 2 tests below) | green |
| 2 | `agent/CloudTransportPayloadTest.kt` | new | 2 | green |

Totals added this phase: **16 tests** (14 + 2). Expected 188 + 16 = 204.

## Full run (Step 4)

| Metric | Value |
|---|---|
| Classes | 39 |
| Tests | 204 |
| Failures | 0 |
| Skipped | 0 |

## Step 1: decision 2 — what the tests prove

| Requirement | Test | Result |
|---|---|---|
| Pause only when a placeholder parameter is present | `PausedTaskStateTest.pauseEnteredOnlyWhenPlaceholderPresent` | PASS |
| Next message becomes the value, substituted only into the placeholder parameter | `PausedTaskStateTest.resumeUsesNextMessageAsValueAndSubstitutesOnlyPlaceholder` | PASS |
| Non-placeholder parameters untouched | `PausedTaskStateTest.substitutionLeavesParamsWithoutPlaceholderUntouched` | PASS |
| Replacement keeps surrounding text (only the token replaced) | `PausedTaskStateTest.substitutionKeepsSurroundingTextAndReplacesOnlyTheToken` | PASS |
| Timeout cancels the task | `PausedTaskStateTest.timeoutCancelsAndReportsCancellation`, `awaitValueCancelsOnTimeout` | PASS (state); the emitted message is `pause_timeout_message` in `DefaultAgentService`, not directly unit-tested |
| Message while NOT paused is not a value | `PausedTaskStateTest.messageWhileNotPausedIsNotAValue` | PASS |
| Message after resume is not accepted | `PausedTaskStateTest.messageAfterResumeIsNotAccepted` | PASS |
| Empty message is not a value | `PausedTaskStateTest.emptyMessageIsNotAValue` | PASS |
| Value cleared after tool call | `PausedTaskStateTest.valueClearedAfterToolCall` | PASS |
| Value cleared at task end | `PausedTaskStateTest.valueClearedAtTaskEnd` | PASS |
| Value cleared on cancel | `PausedTaskStateTest.valueClearedOnCancel` | PASS |
| Value absent from ActionLog entries | `PausedTaskStateTest.valueNeverAppearsInActionLogLines` | PASS (formatter path) |
| Value absent from every log call | **NOT PROVEN** | No test captures `XLog` output. The brief's requirement is only partly met: the code never passes the value to `XLog` (verified by reading `DefaultAgentService` and the two chat hooks), but no automated assertion checks it. Reported as a gap. |
| Timeout wait is real (45 s, not a cancellation-only path) | `awaitValueCancelsOnTimeout` uses an injected fake clock | PASS (logic); real wall-clock timing not measured |

Limitation: `PausedTaskState.shared` is process-global. The agent worker and the chat handler reach it through the same object. Whether the chat hook runs on the UI thread concurrently with `awaitValue` on the worker is handled by `@Synchronized`; no concurrency test exists.

## Step 2: cloud transport

| Item | Value |
|---|---|
| Mock library added | **No.** Only the existing JUnit test dependency is used. |
| Request path covered | `OpenAiLlmClient.chat` → langchain4j OpenAI request builder → `OkHttpClientAdapter.execute` (production) → OkHttp `Call` → capturing `Interceptor` (replaces the socket only). |
| Production code reached without a network call | Yes. `OkHttpClientBuilderAdapter.build()` is overridden in the test subclass to return the production `OkHttpClientAdapter` wrapped around a client that has the capturing interceptor. |
| Not covered | The production `OkHttpClientBuilderAdapter.build()` itself (its logging interceptor and file-logging interceptor are bypassed by the override). The `AnthropicLlmClient` path is not covered. Streaming (`chatStreaming`) is not covered. |

Fields checked in the captured request body (`CloudTransportPayloadTest.capturedCloudRequestBodyContainsNoSensitiveValuesAndHasPlaceholders`):

| Field / content | Assertion | Result |
|---|---|---|
| Card number (grouped) | body does not contain `4111` | PASS |
| Card number (compact) | body does not contain `4111111111111111` | PASS |
| IBAN | body does not contain `GB82WEST` | PASS |
| National ID | body does not contain `1098765432` | PASS |
| OTP code | body does not contain `483921` | PASS |
| Card placeholder | body contains `[REDACTED:card]` | PASS |
| IBAN placeholder | body contains `[REDACTED:iban]` | PASS |
| National-ID placeholder | body contains `[REDACTED:national_id]` | PASS |
| OTP placeholder | body contains `[REDACTED:otp]` | PASS |
| Untrusted delimiter | body contains `untrusted_content` | PASS |
| Request URL | ends with `/chat/completions` (`requestPathIsTheOpenAiChatCompletionsEndpoint`) | PASS |

Finding during this step: the first run **failed** on `4111` in the body. The cause was a real redactor false negative (a Luhn-valid compact card adjacent to a digit was not redacted). Fixed in `SensitiveDataRedactor` by replacing the greedy regex with an explicit scan, and proven by the transport test.

## Step 3: corpus integrity (read-only)

| File | SHA-256 (working tree) | Matches `docs/phase2b_corpus_audit.txt` | Matches commit `96a736f` (raw bytes) |
|---|---|---|---|
| `injection/hostile.txt` | `137DE4B5A902F75FA1136DCABCE45B180E20E5400DCE3FA0654D532936AF3BA2` | **MATCH** | N/A — never committed (untracked) |
| `injection/benign.txt` | `75FD63FB32895B1F94576B1BEA7A4DD31ACC38FA97FB0DAEBFB52BFBC117D08D` | **MATCH** | N/A — never committed (untracked) |
| `injection_heldout/hostile.txt` | `E244539C4367161DBDB59A6C99B4D56184AE06B4B7B7A0B51F40EA7F51A6014D` | N/A — created after the audit file | **MATCH** |
| `injection_heldout/benign.txt` | `CC68C18CA8674D78D5F5080FF7597FF4730CF4986381008BDC0DD20B7944CE6D` | N/A — created after the audit file | **MATCH** |

Notes: `injection/` was never committed, so commit-level integrity cannot be proven for it; only the audit-file hash applies. The held-out hashes were computed from the raw committed bytes (`git show 96a736f:<path>` piped to a SHA-256). A temp-file method first gave false mismatches because of re-encoding; the raw method is the authoritative check.

Held-out benign set: **39 entries**, not 40. Known, frozen deviation.

## Step 4 and Step 5: acceptance (see table below)

## Final acceptance table

Legend: PASS = a named test proves it. FAIL = a check is false. NOT EXECUTED = no test or device run.

| # | Check | Status | Proving test / reason |
|---|---|---|---|
| 1 | Untrusted-content wrapping with escaping | PASS | `ScreenContentGuardTest.escapesAngleBracketsSoContentCannotCloseTheTag`, `wrapsInUntrustedDelimiters` |
| 2 | Source-based taint, incl. 3 missed samples (IDs 13, 18, 19) | PASS | `TaintPolicyTest.missedSamplesStillTaintAndRequireConfirmation` |
| 3 | Sensitive action blocked after screen-sourced result without confirmation | PASS | `TaintPolicyTest.benignScreenResultAlsoTaints`; `InjectionSensitiveActionPolicyTest.sensitiveActionAfterFlaggedResultIsBlockedWithoutConfirmation`. Scope: policy function, not the live agent loop. |
| 4 | Non-sensitive action after taint: warning only | PASS | `TaintPolicyTest.nonSensitiveActionAfterTaintProceedsWithWarning` |
| 5 | Luhn cards | PASS | `SensitiveDataRedactorTest.redactsLuhnValidCard`, `luhnEdgeCases`; compact-adjacent case via `CloudTransportPayloadTest` |
| 6 | mod-97 IBAN | PASS | `SensitiveDataRedactorTest.redactsValidIban`, `ibanMod97EdgeCases` |
| 7 | National IDs | PASS | `SensitiveDataRedactorTest.redactsSaudiNationalId`, `nationalIdMustStartWithOneOrTwo` |
| 8 | OTP near keywords | PASS | `SensitiveDataRedactorTest.redactsOtpNearEnglishKeyword`, `redactsOtpNearArabicKeyword` |
| 9 | OTP notifications hidden from the agent | PASS (unit) | `OtpGuardTest.hidesEnglishOtpNotification`, `hidesArabicOtpNotification`. The tool wiring in `GetNotificationsTool` is not executed by any test. |
| 10 | Redaction before ActionLog | PASS | `ActionLogFormatterTest.actionLogNeverReceivesOtpOrIbanOrNationalIdValues` |
| 11 | Decision 2: pause | PASS | `PausedTaskStateTest.pauseEnteredOnlyWhenPlaceholderPresent` |
| 12 | Decision 2: resume with next message, scoped substitution | PASS | `PausedTaskStateTest.resumeUsesNextMessageAsValueAndSubstitutesOnlyPlaceholder` |
| 13 | Decision 2: timeout cancels | PASS (state) | `PausedTaskStateTest.awaitValueCancelsOnTimeout`. Emitted message not unit-tested. |
| 14 | Decision 2: value never logged | **FAIL (partial)** | Not proven against `XLog`. ActionLog is covered by `valueNeverAppearsInActionLogLines`. A test that captures every `XLog` call is absent, so this row cannot be PASS. |
| 15 | Cloud payload: fields checked | PASS | `CloudTransportPayloadTest.capturedCloudRequestBodyContainsNoSensitiveValuesAndHasPlaceholders` (fields listed in Step 2 table) |
| 16 | Cloud payload: path covered | PASS with limitation | `CloudTransportPayloadTest.requestPathIsTheOpenAiChatCompletionsEndpoint`. Limitation: Anthropic and streaming paths not covered. |
| 17 | Corpus SHA-256 integrity | PASS | Step 3 table (audit-file match for `injection/`; commit match for `injection_heldout/`) |
| 18 | Held-out: hostile detected by pattern detector | **Reported: 2/40 = 5.0%** | `HeldOutCorpusTest.reportHeldOutDetectionNumbers` (reports, does not gate) |
| 19 | Held-out: sensitive actions blocked by taint (must be 100%) | PASS: **40/40 = 100%** | `HeldOutCorpusTest.reportHeldOutDetectionNumbers` (`enforced_sensitive_block=40/40`) |
| 20 | Mandatory: zero sensitive actions without confirmation from screen-sourced text | PASS: **350 checks, 0 violations** | `InjectionCorpusTest.zeroSensitiveActionsExecutedWithoutConfirmationFromFlaggedHostileText` (50 hostile × 7 kinds). Scope: the policy function applied per sample, not the live agent loop. |
| 21 | ConfigServer binding unchanged (127.0.0.1) | PASS | grep: `server/ConfigServer.kt:25` `NanoHTTPD("127.0.0.1", port)` |
| 22 | No "LAN" claim remains in the three named places | PASS | grep over `server/ConfigServer.kt`, `server/ConfigServerManager.kt`, `ui/settings/ChannelConfigActivity.kt`: no matches |
| 23 | IJ1 device | NOT EXECUTED | device |
| 24 | IJ2 device | NOT EXECUTED | device |
| 25 | IJ3 device | NOT EXECUTED | device |
| 26 | IJ4 device | NOT EXECUTED | device; no unit test reads the system prompt |
| 27 | IJ5 device | NOT EXECUTED | device |
| 28 | IJ6 device | NOT EXECUTED | device |
| 29 | SD1 device | NOT EXECUTED | device |
| 30 | SD2 device | NOT EXECUTED | device |
| 31 | SD3 device | NOT EXECUTED | device |
| 32 | SD4 device | NOT EXECUTED | device |
| 33 | SD5 device | NOT EXECUTED | device |

## Mandatory criterion

Zero sensitive actions executed without confirmation when derived from screen-sourced text: **350 checks, 0 violations.** Non-zero would have been FAIL; it is zero. Caveat (row 3 and 20): proven at the policy-function level, not through the live agent loop.

## Findings and risks (not hidden)

1. **Row 14 is FAIL (partial):** no automated check that the value never reaches `XLog`. Needs a log-capture test before this can be PASS.
2. **Taint cost is an OPEN DECISION**, not settled: every task that reads screen content requires confirmation before a sensitive action.
3. **Redactor false negative found and fixed** (card adjacent to a digit). It was caught only because the transport test ran a real payload. The `SensitiveDataRedactorTest` suite did not catch it. Suggests the unit suite under-covers adjacency.
4. **`FILE_LOGGING_ENABLED`** (`DefaultAgentService.kt`, default false) causes `FileLoggingInterceptor` to write raw request/response data to files when enabled. Not changed (outside allowed files). Relevant to item 2's "no sensitive data logged" rule if ever turned on.
5. **Production `OkHttpClientBuilderAdapter.build()` is bypassed in the transport test** by the subclass override; its logging interceptor is not exercised.
6. **Held-out detector is weak**: 2/40 hostile flagged. Enforcement, not detection, carries the guarantee. Held-out set not tuned, by design.
7. **Held-out benign set: 39 entries**, not 40. Frozen.

## Verdict

Items 1 and 2 are **not fully complete**: row 14 (value never logged to `XLog`) is FAIL (partial). Everything else in the acceptance table is PASS or NOT EXECUTED with a named test.
