# Changelog — Sanad-Agent (PokeClaw fork)

Format based on Keep a Changelog; upstream PokeClaw history lives in `README.md` §Changelog (v0.1.0 → v0.7.1).

## [Unreleased] � 2026-11-13 (device QA, signed release, CI fix)

### Added
- Fork release keystore workflow: keys/sanad-release.jks (gitignored) + keys/KEYSTORE_INFO.txt; assembleRelease now produces a signed APK (CN=Sanad Claw).
- New string resources: send_word_on, send_example_phrase, skill_send_message_desc, skills_experimental_badge (en + ar).

### Fixed
- AppCapabilityCoordinator.bindingState: a live accessibility binding now wins over the stale pending-repair flag, so re-enabling accessibility no longer leaves the app stuck on "Connecting"/"Disconnected" for up to 120 s.
- ChatScreen quick-task composer, skill card description and experimental badge now use localized string resources instead of hardcoded English.
- Action-log export filename: pokeclaw-action-log.txt -> sanadclaw-action-log.txt.

### Changed
- All GitHub Actions workflows use JDK 21 (matches the project toolchain).
- Release workflow: robust release-notes extraction (README -> docs/CHANGELOG -> fallback) and dev/rc tags publish as prerelease.

### Verified (device: HONOR MBH-N49, Android 17)
- QP1 PASS (7 confirm rows), QP2 PASS (persistence), QL1 PASS (redacted export), FL3 PASS (Authorization masked, body omitted), app launch PASS, plan-preview gate PASS, task execution PASS.
- Unit tests: 312 / 56 classes / 0 failures.

### Known
- HONOR force-stop revokes the accessibility secure setting; re-enable after force-stop.
- send_media (file) end-to-end not executed; send_message (text) verified on device.
- OpenAppTool.resolveAppName now maps "WA Business" to the installed WhatsApp package (com.whatsapp.w4b), fixing send_message when the agent passes the app label.

- send_message end-to-end fixed on WhatsApp Business: "WA Business" label resolution, chat-list readiness detection, settle-before-Back, skip "From ..." message previews, toolbar-only chat verification, composer selection (skip search box, require bottom half), and a post-tap wrong-chat abort guard.
- Verified on device: message delivered to "أحمد التميمي خاص" via WhatsApp Business; SafetyGate confirmation shown and approved.

## [Unreleased] — 2026-11-10 (Phase 1: setup/audit)

### Added
- `AGENTS.md` — fork agent rules, build commands, coding conventions, mandatory docs/MEMORY.md protocol.
- `docs/MEMORY.md` — persistent project memory (decisions, status, environment, known issues).
- `docs/PLAN.md` — 5-phase plan with checkboxes (P1 complete).
- `docs/AUDIT.md` — full architecture audit: modules, agent loop, 35-tool system, local/cloud LLM layers, UI, permissions/security, weaknesses & risks.
- `docs/CHANGELOG.md` — this file.
- `docs/skills/build-apk.md`, `docs/skills/add-llm-provider.md`, `docs/skills/add-agent-tool.md`.

### Fixed
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.3.1 distribution now fetched from official `services.gradle.org` (upstream Tencent mirror timed out outside China); wrapper networkTimeout 10s → 120s.
- `.gitignore`: hardened — `*.keystore`, `*.jks`, `*.p12`, `*.pem`, `keys/`, `secrets/`, `signing/`, `keystores/`, `.env.local`, `.env.*.local`, `**/build/`, `**/.gradle/`, `app/release/`, `*.apk`, `*.aab`; explicit `!.env.enc` keeps upstream SOPS-encrypted file committable.

### Verified
- Debug build: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL in 2m42s → `PokeClaw_v0.7.1_20261110_134716.apk`.
- License compliance: Apache-2.0 `LICENSE` + `NOTICE` retained; per-file headers intact.

### Notes
- Local (uncommitted, machine-only) setup: `local.properties` sdk.dir; user-level `~\.gradle\gradle.properties` JDK 21 toolchain path; SDK licenses accepted; cmdline-tools installed into the SDK.
- Git: branch `phase-1-setup` created with commits `af376f0` (chore/setup) + `f3a9b50` (docs/phase-1). Push to origin failed — `Permission to agents-io/PokeClaw.git denied to Altamimi79.` (403; no fork of upstream exists under the account yet). See docs/MEMORY.md Known Issues for the exact commands to rerun once the fork exists.

## [Unreleased] — 2026-11-10 (Phase 2: Arabic + chat + provider settings) — branch `phase-2-arabic-chat`

### Added
- Arabic-first localization: `utils/AppLocale` (default Arabic, user switch to English persisted), full `values-ar/strings.xml`, new keys in both locales; localized literals in ChatScreen / ChatSessionController / LlmConfigActivity. Noto Naskh Arabic variable font bundled (OFL, `docs/licenses/NotoNaskhArabic-OFL.txt`) and applied in Views (`themes.xml`) + Compose (`PokeclawTypography`).
- Gulf dialect policy: `agent/LanguagePolicy` — understands Khaleeji/Gulf dialects, answers in written Arabic; composed in every chat/task system prompt before the user's global instructions (override preserved).
- Encrypted secrets (early P4 delivery): `utils/SecureStore` (EncryptedSharedPreferences AES256-GCM + private fallback + one-time migration off plaintext MMKV), `utils/SecretsMasker`; ConfigServer GET masks secret values and never overwrites real keys with masked POSTs. Dependency `androidx.security:security-crypto:1.1.0`.
- Provider settings: DeepSeek provider (`deepseek-chat`, `deepseek-reasoner`); Base URL editable for every provider and persisted per provider (`KEY_LLM_BASE_URL_<PROVIDER>` via `KVUtils`/`ModelConfigRepository`); real test-connection probe `agent/llm/CloudConnectionTester` (single minimal chat request; classifies 401 / model-not-found / timeout / network / generic into localized strings; API keys never logged).
- Chat: cloud replies stream token-by-token into the assistant bubble (`chatStreaming`, 120ms-throttled partial updates); Markdown rendering in chat bubbles via pure-JVM `ui/chat/MarkdownRender` (bold/italic/code/fenced blocks/links/headings/lists/quotes/HR; links clickable via Compose LinkAnnotation); failures surface as localized SYSTEM bubbles (`chat_error_prefix`) instead of fake assistant "Error:" messages.
- Unit tests: `LanguagePolicyTest`, `SecretsMaskerTest`, `CloudConnectionTesterTest`, `MarkdownRenderTest`.
- QA: fork section **FA** (FA1–FA13) added to `QA_CHECKLIST.md` (execution pending a connected device).

### Fixed
- `PokeclawTypography`: material3 1.3 dropped `Typography(defaultFontFamily=…)` — font now applied via `copy()` on all text styles (compile break found by first phase-2 build).
- `SecureStore` MMKV migration block: explicit `List<String>` typing (Kotlin inference failure).

### Verified
- `assembleDebug` → BUILD SUCCESSFUL → `PokeClaw_v0.7.1_20261110_154327.apk`.
- `testDebugUnitTest` → 17 test classes / 61 tests, all green.

### Notes
- On-device E2E QA (FA1–FA13) not executed at phase close: no Android device/emulator connected via adb on this machine.
- Push to GitHub still blocked (no fork under `Altamimi79`); branches exist locally. See docs/MEMORY.md Known Issue 1.

## [Unreleased] — 2026-11-10 (Phase 3: Arabic voice input) — branch `phase-3-voice`

### Added
- In-app Arabic dictation: `voice/SpeechRecognizerController` wraps Android `SpeechRecognizer` with **ar-SA**, partial hypotheses, tuned silence windows (1.5s complete / 0.9s possibly-complete / 0.8s minimum), 60s hard cap, graceful stop (`stopListening`), cancel, and a timeout fallback that returns the last partial transcript instead of discarding speech.
- Pure, unit-tested dictation core: `VoiceTextNormalizer` (trim, strip bidi/zero-width marks, collapse whitespace, cap), `VoiceTranscriptAssembler` (partials replace, finals win, multi-final join), `VoiceDictationPolicy` (auto-send gate), `VoiceTimerFormat` (mm:ss/elapsed), `VoiceError` (10 kinds mapped to localized messages).
- Recording UI: `ui/voice/VoiceRecordingBar` with RMS-driven pulsing mic, mm:ss timer, live partial transcript and Cancel, shown above the composer; mic tap while recording stops gracefully.
- Transcript insertion: final text is appended to whatever the user typed and stays editable. New setting **Settings → Tools → Auto-send voice input** (`KEY_VOICE_AUTO_SEND`, default off); when enabled the message sends immediately once the composer is free.
- Permissions: `RECORD_AUDIO` manifest permission + `<queries>` for `android.speech.RecognitionService`; runtime request on first mic tap with Arabic denial/unavailable messages.
- Error UX: no-match, timeout, network, permission, busy, audio, server, client, language, unknown → `voice_error_*` strings in `values/` and `values-ar/`.
- QA: fork section **VA1–VA10** in `QA_CHECKLIST.md`; skill `docs/skills/voice-input.md`.
- Unit tests: `VoiceTextNormalizerTest`, `VoiceTranscriptAssemblerTest`, `VoiceDictationPolicyTest`, `VoiceTimerFormatTest`, `VoiceErrorTest`.

### Changed
- The mic button no longer launches the system `RecognizerIntent` dialog; recognition happens in-app so the recording animation/timer/cancel and Arabic error handling are under our control. Replies remain written text only (no TTS exists in the app).

### Skipped (documented)
- Offline Whisper: LiteRT-LM has no audio encoder path, a usable Arabic model is 0.5–1.5 GB (RAM risk on target devices), and no device was available to validate accuracy/latency. Rationale + revisit plan in `docs/skills/voice-input.md`.

### Verified
- `assembleDebug` → BUILD SUCCESSFUL → `PokeClaw_v0.7.1_20261110_162107.apk`.
- `testDebugUnitTest` → 101 tests / 0 failures.

### Notes
- Device E2E (VA1–VA10) not executed: no adb device. ar-SA recognition needs a device speech service with the Arabic language pack; some OEMs are online-only.

## [Unreleased] — 2026-11-10 (Phase 4: safety, security, stability) — branch `phase-4-safety`

### Added
- Pre-execution safety gate in the agent loop (`safety/SafetyGate`): every tool call is checked against the app blocklist and risk classifier before execution; blocked/denied actions stop the task with a localized message.
- Risky-action confirmations: sending messages/files, calls, payments/transfers, deletes, app installs and publishing require explicit in-app approval (`SafetyCoordinator` + Compose dialog). No UI attached ⇒ denied (fail-safe), never auto-approved.
- Configurable app blocklist (`AppBlocklist`): conservative banking/wallet/password-manager defaults (Arabic + English patterns) plus a user CSV in Settings → Tools → Blocked apps. All tools are refused inside blocked apps (also protects clipboard reads).
- Configurable task timeout (`TaskTimeoutPolicy`, default 180 s; presets 120/180/300/600) enforced at loop start and before each tool.
- Emergency stop: red chat banner while a task runs, foreground-notification action (`EmergencyStopReceiver`, not exported) and the existing floating pill; stops task, monitoring and service.
- Local-only action log (`ActionLog`) with redaction (`ActionLogFormatter`: API keys, tokens/passwords, bearer tokens, card-like numbers → `***`, 200-entry cap) and a viewer screen (`ActionLogActivity`) with clear.
- Bounded retry for transient failures (`RetryPolicy`): max 2 attempts, safe read/navigation tools only, risky tools never retried.
- Battery-conscious foreground service polling (5 s monitoring / 15 s task / 30 s idle instead of a fixed 5 s loop).
- ConfigServer hardening: per-process API token required on every `/api/*` request (401 otherwise), token embedded only in the served HTML; H5 page updated to send it.
- Unit tests: `RiskClassifierTest`, `AppBlocklistTest`, `TaskTimeoutPolicyTest`, `RetryPolicyTest`, `ActionLogFormatterTest`, `SafetyCoordinatorTest`.
- `docs/SECURITY.md` (full review, findings S1–S12 with status), `docs/skills/safety-guardrails.md`, QA section SA1–SA13.

### Verified
- `assembleDebug` → BUILD SUCCESSFUL → `PokeClaw_v0.7.1_20261110_163624.apk`.
- `testDebugUnitTest` → 127 tests / 0 failures.

### Notes
- Device E2E (SA1–SA13) not executed: no adb device.
- Residual security items tracked in docs/SECURITY.md (cleartext policy, external-automation sender verification, AppLogStore content redaction, security-crypto deprecation).

## [1.0.0] — 2026-11-10 (fork release)

First fork release (code-complete; device QA not executed).

### Added
- Arabic-first experience: default Arabic UI, RTL, Noto Naskh Arabic font, full `values-ar` strings.
- Gulf-dialect language policy (understands Khaleeji, replies in written Arabic).
- Chat upgrades: token streaming, Markdown bubbles (bold/italic/code/links/lists), localized error bubbles.
- Provider settings: OpenAI, Anthropic, Google, DeepSeek + custom OpenAI-compatible Base URL; per-provider editable endpoint; real connection test; encrypted secrets.
- Arabic voice dictation (SpeechRecognizer, ar-SA) with recording bar, timer, cancel, auto-send setting, Arabic error messages.
- Safety: risky-action confirmations, banking/password app blocklist, local redacted action log, emergency stop (banner/notification/floating pill), task timeout, bounded retry.
- Security: EncryptedSharedPreferences secret storage, ConfigServer token auth + masked GET, `docs/SECURITY.md`.
- Docs: Arabic `README.md` and `docs/USER_GUIDE.md`, `docs/RELEASING.md`, `docs/skills/*`.

### Verified
- `testDebugUnitTest` → 127 tests / 28 classes / 0 failures (forced re-run).
- `assembleDebug` → `PokeClaw_v1.0.0_20261110_164051.apk (SHA-256 16077DB4541D7B860258CB5A3A18110B5F2B86E872E14EFA770FBC259E48B899)`.

### Not verified
- Any on-device flow (no adb device): permissions, accessibility tasks, voice, safety dialogs, emergency stop, notification action.
- GitHub push/release (no fork under the account; `gh` not installed).

### Known limitations
- Image attachments unsupported; offline Whisper not bundled; local models need sufficient RAM; external automation off by default; global cleartext traffic remains for custom local endpoints.

## [1.0.3-dev] — 2026-11-13 (development build, not for distribution)

| item | change | status |
|---|---|---|
| Prompt-injection defense (source-based taint) | Taint tracking and sensitive-action gate for screen-sourced text | unit-tested, device pending; enforcement in DefaultAgentService wiring: verified by inspection only |
| Sensitive data and OTP redaction | Card, IBAN, national-ID and OTP redaction before cloud send and logs | unit-tested, device pending |
| FileLoggingInterceptor masking and body omission | Authorization masked, request/response bodies omitted in debug logs | unit-tested, device pending |
| Per-category confirmation switches | Seven category switches replace the single confirm toggle; migration notice | unit-tested, device pending |
| Arabic action log v2 and export | Reason and result labels, redacted export | unit-tested, device pending |
| Voice STOP and CONFIRM (one-shot mic) | STOP while task active; CONFIRM only for a pending request captured at listen start | unit-tested, device pending; timeout-partial separation in SpeechRecognizerController: verified by inspection only |
| Plan preview, wake word, scheduled routines | Items 5, 7, 8 | not implemented |

Build: versionName 1.0.3-dev, versionCode 101 (set via POKECLAW_VERSION_NAME / POKECLAW_VERSION_CODE for this build only).

### Known limits
- The pattern detector catches only 5% of the held-out hostile samples; protection comes from source-based taint, not detection.
- Redaction and URL masking are name/pattern based and can miss unlisted formats.
- HONOR speech service availability is unverified.
- Debug builds enable FILE_LOGGING.
- No device QA has been executed (see docs/DEVICE_QA_SESSION.md).

### Preserved build record (1.0.3-dev)
- Code commit (local branch `sanad/dev-1.0.3`): `92799e1087006edcada90740feb828515544be9e` — "1.0.3-dev: items 1, 2, 3, 4, 6 and FileLoggingInterceptor fix (unit-tested; device QA pending)". Not pushed.
- APK: `PokeClaw_v1.0.3-dev_20261113_073755.apk`, SHA-256 `413EB5AFF757927A3C67C1A73AA8D329271DD0C94F10AC9BD5E7F63291DB9DC7`, 157147204 bytes. Preserved at `dist-dev/` (gitignored) with a `.sha256` file.
- The APK was built from the working tree that this commit captured. Verified build-free: no file under `app/src` has a modification time after the APK (newest: `app/src/main/assets/.pcfp`, build fingerprint, gitignored). The source was unchanged between build and commit.
