# Phase 2i item 3 — Arabic and English voice STOP / CONFIRM (unit-level)

Status: **UNIT-COMPLETE. DEVICE QA PENDING** (QV1-QV6 NOT EXECUTED).

## 0. Baseline
242 tests / 45 classes / 0 failures. Per-test: `docs/phase2i_before.txt`.

## 1. Listener grep (SpeechRecognizerController.Listener and onFinalText)

| file | line | role | onTimeoutPartial handled |
|---|---|---|---|
| ui/chat/ChatScreen.kt | 939 (object : Listener) | dictation (input field) | yes (line ~958, `handleVoiceTimeoutPartial`, dictation only, no auto-send, no command path) |
| voice/SpeechRecognizerController.kt | 199 (was onFinalText) | controller, now onTimeoutPartial | n/a (producer) |
| voice/SpeechRecognizerController.kt | 235 | normal final result | n/a |

No other implementation of the Listener exists (grep across app/src, including tests). Other `onPartialText` hits are LLM streaming listeners and are unrelated.

## 2. Diff per file

| File | Lines | Result |
|---|---|---|
| voice/SpeechRecognizerController.kt | interface +7 (onTimeoutPartial default no-op), timeout branch: `onFinalText(fallback)` -> `onTimeoutPartial(fallback)` | timeout never calls onFinalText |
| ui/chat/ChatScreen.kt | imports +5; rememberUpdatedState for isTaskRunning/inputEnabled/isAwaitingReply after textFieldFocus; `handleVoiceCommand` (parser + policy + EmergencyStop.trigger / approvePending / toast); `handleVoiceFinal` routes non-empty text to command path first; `handleVoiceTimeoutPartial` (dictation only); listener override onTimeoutPartial; autoSend and sendCurrentMessage read live values | Compiles |
| safety/VoiceCommandParser.kt | NEW, pure | STOP / CONFIRM / NONE, exact match after normalization |
| safety/VoiceCommandPolicy.kt | NEW, pure | EXECUTE_STOP / EXECUTE_CONFIRM / REJECT_NO_PENDING / IGNORE_NO_TASK / IGNORE |
| values/strings.xml, values-ar/strings.xml | +2 keys each: voice_command_no_pending, voice_command_no_task | bilingual |
| test: safety/VoiceCommandParserTest.kt | NEW, 9 tests | PASS |
| test: safety/VoiceCommandPolicyTest.kt | NEW, 7 tests | PASS |
| test: voice/TimeoutPartialRoutingTest.kt | NEW, 3 tests | PASS |

## 3. New tests (PASS)

VoiceCommandParserTest: stopArabicIsRecognized, stopReversedArabicIsRecognized, stopEnglishIsRecognized, confirmArabicIsRecognized, confirmEnglishIsRecognized, diacriticsAndPunctuationIgnored, unrelatedSpeechReturnsNone, substringOfCommandInLongSentenceReturnsNone, emptyAndNullReturnNone. **9 PASS.**

VoiceCommandPolicyTest: stopWhileTaskActiveExecutesStop, stopWhileNoTaskIgnored, confirmWhenPendingExecutesConfirm, confirmWhenNotPendingIsRejectedNotExecuted, confirmWhenNoTaskAndNoPendingIsRejected, noneIsAlwaysIgnored, recognitionFailureNeverConfirms. **7 PASS.**

TimeoutPartialRoutingTest: timeoutPartialIsDeliveredToOnTimeoutPartial, timeoutPartialNeverReachesOnFinalText, timeoutCanaryNeverReachesCommandPath. **3 PASS.**

Limitation: the real controller cannot run on the JVM (no Robolectric; needs Context, Handler, SpeechRecognizer). TimeoutPartialRoutingTest verifies the routing contract with a fake listener. The controller branch itself is verified by code review only (SpeechRecognizerController.kt onError).

Spec deviation: the brief asked that the canary return NONE through the command path. "نعم سند" is a CONFIRM phrase, so the parser correctly returns CONFIRM. The test asserts the real invariant instead: the canary never reaches the command path (onFinalText receives nothing).

## 4. Per-test comparison
Full suite run twice: **261 tests / 48 classes / 0 failures** both runs. Compared against `docs/phase2i_before.txt`: all 242 baseline lines present as identical PASS lines; 0 pass-to-fail. 19 new lines (9 + 7 + 3). Output: `docs/phase2i_after.txt`.

## 5. Build
`assembleDebug`: BUILD SUCCESSFUL.

## 6. Acceptance

- Timeout partial never reaches onFinalText: verified by inspection of `voice/SpeechRecognizerController.kt` (lines 196-202: the hard-cap branch calls `listener.onTimeoutPartial(fallback)`; line 199 `onFinalText` is no longer on that path), NOT by a controller test. `TimeoutPartialRoutingTest` exercises a fake listener only.
- Timeout partial never reaches the command path: **PASS by grep**. `handleVoiceCommand` is called only from `handleVoiceFinal`, which is only called from `onFinalText`. `onTimeoutPartial` calls only `handleVoiceTimeoutPartial`, which does not call the command path.
- STOP only while task active: **PASS** (VoiceCommandPolicyTest.stopWhileNoTaskIgnored; stopWhileTaskActiveExecutesStop; ChatScreen uses live currentTaskRunning).
- CONFIRM only when pending: **PASS** (VoiceCommandPolicyTest.confirmWhenNotPendingIsRejectedNotExecuted, confirmWhenNoTaskAndNoPendingIsRejected).
- Unrelated speech never acts: **PASS** (VoiceCommandParserTest.unrelatedSpeechReturnsNone; substringOfCommandInLongSentenceReturnsNone).
- Recognition failure never confirms: **PASS** (VoiceCommandPolicyTest.recognitionFailureNeverConfirms; failures map to NONE).
- Dictation still receives timeout text: **PASS by grep** (ChatScreen `onTimeoutPartial` -> `handleVoiceTimeoutPartial` -> `appendTranscript`).
- Device QV1-QV6: **NOT EXECUTED**.

## 7. Review findings (2026-11-13)

Ordinary dictation regression check (ChatScreen.kt `handleVoiceFinal` 944, `handleVoiceCommand` 918, `handleVoiceTimeoutPartial` 974):

| Transcript | Task running | Confirmation pending | Result |
|---|---|---|---|
| "hello how are you" | no | no | inserted into input; auto-send gate applied (original policy) |
| "hello how are you" | yes | no | inserted; auto-send blocked by `shouldAutoSend` (task running) |
| "hello how are you" | yes | yes | inserted; no approval, no auto-send |
| "stop" / "توقف سند" / "سند توقف" | no | no | not inserted; toast "No task is running to stop."; no action |
| "stop" / "توقف سند" / "سند توقف" | yes | no | not inserted; EmergencyStop.trigger |
| "stop" / "توقف سند" / "سند توقف" | yes | yes | not inserted; EmergencyStop.trigger; pending confirmation not approved |
| "نعم سند" / "confirm" | no | no | not inserted; toast "No action is pending confirmation."; no action |
| "نعم سند" / "confirm" | yes | no | not inserted; toast "No action is pending confirmation."; no action |
| "نعم سند" / "confirm" | yes | yes | not inserted; SafetyCoordinator.approvePending() |
| "please stop the music" / "لا تتوقف سند عن العمل" | any | any | NONE; inserted into input via original path (never a command) |

Finding: ordinary speech is NOT swallowed. `handleVoiceCommand` returns false for NONE and `handleVoiceFinal` then runs the original appendTranscript + auto-send path. No dictation fix needed. Evidence: ChatScreen.kt:920 (`if (handleVoiceCommand(transcript)) return`), :921 (`appendTranscript`). Regression proof: `VoiceDictationRoutingTest` (2 tests, PASS).

Known design choice (not changed): a dictated exactly "stop" or "نعم سند" with no task active and nothing pending is consumed (not inserted) and shows a toast. It is never sent to the agent.

Plan deviation: `.kilo/plans/...` Item 3 specified `ComposeChatActivity.kt` wiring and `SafetyCoordinator.approvePendingIfMatches(id)` plus a test "approve after timeout is no-op". Neither was implemented: the wiring is in ChatScreen and `approvePending()` is unguarded by request id. Open until approved or waived.

## Known issues
- Real mic, real speech engine and the 60 s cap are not exercised on JVM.
- `ChatInputBar` is still a single composable; the command path depends on `SafetyCoordinator.pending.value` read at call time.

## 8. Review closed: confirmation id capture (2026-11-13)

Baseline: 263 / 49 / 0; saved to docs/phase2i2_before.txt.

ConfirmationRequest shape (before and after): unchanged. Already had `val id: Long` from `AtomicLong ids` (SafetyCoordinator.kt:30, assigned at :50 on each request).

SafetyCoordinator.kt diff (added only):
- `fun currentPendingId(): Long? = _pending.value?.id`
- `fun approvePendingIfMatches(id: Long): Boolean` runs under `synchronized(this)`: returns false when nothing is pending or the id differs; otherwise sets approved=true, clears pending, counts down the latch, returns true.
- approvePending/denyPending/resolve, timeout and latch logic unchanged.

ChatScreen.kt (voice command path only):
- `var voiceCapturedConfirmId by remember { mutableStateOf<Long?>(null) }` (line ~888)
- resetVoiceUi clears it (line ~891)
- handleVoiceCommand: confirmationPending = VoiceCommandPolicy.confirmationPendingFor(captured, currentPendingId()) (lines ~925-929); EXECUTE_CONFIRM calls approvePendingIfMatches(captured), else toast voice_command_no_pending (lines ~933-940).
- startVoiceInput captures `SafetyCoordinator.currentPendingId()` before speechController.start() and clears it on start failure (lines ~1046-1048).
- The dialog button (SafetyConfirmationHost, approvePending) is unchanged.
- Dictation and timeout-partial paths unchanged.

Pure helper added: `VoiceCommandPolicy.confirmationPendingFor(capturedId, currentPendingId)`. `decide` signature and assertions unchanged.

Tests (PASS):
- SafetyCoordinatorRequestIdTest: approveIfMatchesApprovesTheMatchingRequest, approveIfMatchesAfterTimeoutIsNoOp, approveWithStaleIdDoesNotApproveNewerRequest, approveIfMatchesWithNothingPendingReturnsFalse, existingApprovePendingBehaviorUnchanged (5 PASS)
- VoiceConfirmCaptureTest: confirmWithCapturedIdNullIsRejected, confirmWithCapturedIdMatchingCurrentIsExecuted, confirmWithCapturedIdDifferentFromCurrentIsRejected (3 PASS)

Suite: 271 / 51 / 0, twice. All 263 baseline lines identical in docs/phase2i2_after.txt. No pass-to-fail.

Acceptance:
- Voice confirm cannot approve a request that appeared after listening began: PASS (VoiceConfirmCaptureTest.confirmWithCapturedIdNullIsRejected; confirmWithCapturedIdDifferentFromCurrentIsRejected).
- Stale id after timeout is a no-op: PASS (SafetyCoordinatorRequestIdTest.approveIfMatchesAfterTimeoutIsNoOp; approveWithStaleIdDoesNotApproveNewerRequest).
- Dialog buttons unchanged: PASS (SafetyCoordinatorRequestIdTest.existingApprovePendingBehaviorUnchanged).
- ChatScreen wiring: by inspection only; QV8 and QV9 NOT EXECUTED.
