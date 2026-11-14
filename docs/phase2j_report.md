# Phase 2j report — dev build 1.0.3-dev

Status: DEV BUILD READY. DEVICE QA PENDING. NOT FOR DISTRIBUTION.

## Part A: voice-confirm capture ordering
Before fix: handleVoiceFinal called esetVoiceUi() (ChatScreen.kt:960), which sets oiceCapturedConfirmId = null (:891), before handleVoiceCommand read the field (previously :925). The captured id was always null on the final-transcript path, so CONFIRM could never approve.

resetVoiceUi() call sites: :960 (handleVoiceFinal, the bug), :990 (handleVoiceTimeoutPartial), :1001 (handleVoiceError), :1053 (startVoiceInput failure). Only :960 could run between startVoiceInput and handleVoiceCommand.

Fix (ChatScreen.kt only):
- handleVoiceFinal: al capturedConfirmId = voiceCapturedConfirmId as the first statement, before resetVoiceUi.
- handleVoiceCommand signature: (transcript: String, captured: Long?); field read removed.
- call: handleVoiceCommand(transcript, capturedConfirmId).

Suite: 271 / 51 / 0 twice; per-test identical to docs/phase2j_before.txt (docs/phase2j_after.txt).

## Part B: dev APK
Env (this build only): POKECLAW_VERSION_NAME=1.0.3-dev, POKECLAW_VERSION_CODE=101. No edit to build.gradle.kts, no signing.
- File: app/build/outputs/apk/debug/PokeClaw_v1.0.3-dev_20261113_073755.apk
- Size: 157147204 bytes
- SHA-256: 413EB5AFF757927A3C67C1A73AA8D329271DD0C94F10AC9BD5E7F63291DB9DC7
- aapt2 dump badging: name=io.agents.pokeclaw, versionCode=101, versionName=1.0.3-dev

## Part C: docs
- docs/CHANGELOG.md: 1.0.3-dev entry with status table and known limits.
- docs/DEVICE_QA_SESSION.md: 32 IDs (IJ1-IJ6, SD1-SD5, FL3, QP1, QP1a, QP2-QP8, QL1-QL2, QV1-QV9), copied from QA_CHECKLIST.md. MISSING: none.
- docs/MEMORY.md: Phase 2j entry.

## Acceptance
- Captured id read before reset: PASS (ChatScreen.kt:958 before reset; the ordering is verified by inspection because ChatScreen has no JVM test).
- Suite unchanged: PASS.
- Device QA: NOT EXECUTED.