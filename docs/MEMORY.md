# Sanad-Agent — Project Memory

Persistent project memory. Read first, update at the end of every phase (see AGENTS.md).

Last updated: 2026-11-14 (stable v1.0.3 published; branch ui-cleanup-1.0.4 prepared with Arabic-first language fix + UI cleanup — PR open, not merged)

## Project Identity

- Fork of **agents-io/PokeClaw** (Apache-2.0), cloned from upstream `main` @ commit `936eca3` ("Add encrypted .env secrets (SOPS + age)"), app version v0.7.1 / versionCode 29.
- Working copy: `E:\Sanad-Claw`. Upstream remote `origin` = https://github.com/agents-io/PokeClaw.git (no personal fork repo existed on GitHub at clone time — see Known Issues).
- **App display name: Sanad Claw / سند كلو.** GitHub repo: **https://github.com/Altamimi79/Sanad-Claw** (public; `origin` = that repo, `upstream` = agents-io/PokeClaw). Package id intentionally stays `io.agents.pokeclaw` (data/settings continuity).
- Must keep: `LICENSE` (Apache-2.0 full text), `NOTICE` (agents.io attribution), per-file Apache headers, CLA machinery. README Trademark section applies to naming decisions.

## Current Status

| Phase | Status |
|---|---|
| P1 Setup/audit | **DONE 2026-11-10** (branch `phase-1-setup`, merged into local `main` @ `187a065`) |
| P2 Arabic + chat + provider settings | **CODE COMPLETE 2026-11-10** on branch `phase-2-arabic-chat` (7 commits, build+unit tests green). On-device E2E QA pending — no adb device connected yet. |
| P3 Voice input | **CODE COMPLETE 2026-11-10** on branch `phase-3-voice` (3 commits, build + 101 unit tests green). Device QA pending. |
| P4 Agent safety/security/stability | **CODE COMPLETE 2026-11-10** on branch phase-4-safety (build + 127 unit tests green). Device QA pending. |
| P5 Final build/tests/release docs | **DONE (code/release docs) 2026-11-10** on branch phase-5-release: 127 tests green, v1.0.0 APK built, Arabic README + USER_GUIDE, tag 1.0.0 local. Push/GitHub release blocked (no fork, gh missing); device QA not run. |
| **Fork UI/provider round 2026-11-12** | **DONE + DEVICE-VERIFIED** on `main`: branding, i18n + language-switch fix, cloud providers with live model lists + search + per-provider persistence, configured-model chat switcher, models-page dark contrast + cloud-only layout, monitor UI removed, foldable row fix. 127 tests green. Published: repo `Altamimi79/Sanad-Claw`, release **v1.0.1** with APK. |

## What's Done (Phase 1)

- Repo cloned, full read-through; license & notices verified (Apache-2.0 + NOTICE + CLA + `.env.enc` is SOPS-encrypted and stays untouched).
- Created: `AGENTS.md`, `docs/MEMORY.md`, `docs/PLAN.md`, `docs/AUDIT.md`, `docs/CHANGELOG.md`, `docs/skills/build-apk.md`, `docs/skills/add-llm-provider.md`, `docs/skills/add-agent-tool.md`.
- `.gitignore` hardened: added `*.keystore`, `*.p12`, `*.pem`, `keys/`, `secrets/`, `signing/`, `keystores/`, `.env.local`, `.env.*.local`, `**/build/`, `**/.gradle/`, `app/release/`, `*.apk`, `*.aab`; explicit `!.env.enc` exception for upstream's SOPS file. Existing rules (`.env`, `local.properties`, `*.jks`) kept.
- Fixed Gradle wrapper: upstream pointed at `mirrors.cloud.tencent.com` (10s timeout outside China) → switched to official `https://services.gradle.org/distributions/gradle-9.3.1-all.zip`, networkTimeout 120000.
- Build verified: `.\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL in 2m42s** (2026-11-10), APK `app\build\outputs\apk\debug\PokeClaw_v0.7.1_20261110_134716.apk`. Only warnings (deprecations, native lib strip notes).

## What's Done (Phase 2) — branch `phase-2-arabic-chat`

Commits: `8cc0123` feat(arabic) · `cdd57cb` feat(prompt) · `75c6310` feat(security) · `811de4e` fix(arabic-typography) · `a0952ae` fix(security-types) · `8a68f16` feat(provider-settings) · `49ca390` feat(chat).

- **Arabic default**: `utils/AppLocale.kt` (plain SharedPreferences `pokeclaw_locale`, key `KEY_APP_LANGUAGE`, default Arabic unless user picked English); `attachBaseContext` overrides in `BaseActivity`/`ComposeChatActivity`/`ClawApplication`. Full `values-ar/strings.xml`; new keys added to both locales. Noto Naskh Arabic variable TTF bundled (OFL at `docs/licenses/NotoNaskhArabic-OFL.txt`), applied via `themes.xml` fontFamily (Views) + `PokeclawTypography` (Compose; material3 1.3 removed `Typography(defaultFontFamily=…)` → font applied via `copy()` on all text styles). Localized literals in ChatScreen/ChatSessionController/LlmConfigActivity. RTL works via existing `supportsRtl`; some upstream icons still need AutoMirrored swap (flagged).
- **Gulf language policy**: `agent/LanguagePolicy.kt` — Khaleeji/Gulf-dialect comprehension + Modern-Standard-Arabic written replies; composed by `PromptUtils.applyGlobalPrompt` as base → policy → user global instructions (user prompt last so it can override). Wired into both cloud `SystemMessage` sites in ChatSessionController and the local `ConversationConfig` path.
- **Encrypted secrets** (P4 item delivered early): `utils/SecureStore.kt` = EncryptedSharedPreferences (AES256-GCM keys+values, SIV key names, Android Keystore master key) with private-mode fallback + one-time migration of plaintext MMKV secrets (removed from MMKV after copy). `utils/SecretsMasker.kt` masks ConfigServer GET secret values; masked values detected so POST never overwrites real keys. No secret values ever logged. `security-crypto:1.1.0` added.
- **Provider settings**: DEEPSEEK added to `agent/CloudProvider.kt` (`deepseek-chat` recommended, `deepseek-reasoner`; OpenAI-compatible). Base URL now editable for **every** provider (incl. Claude/OpenAI proxies), persisted per provider via `KVUtils KEY_LLM_BASE_URL_<PROVIDER>` resolved in `ModelConfigRepository.buildCloudConfig`. Real test-connection: `agent/llm/CloudConnectionTester.kt` sends one minimal chat request (langchain4j OpenAI/Anthropic client behind `OkHttpClientBuilderAdapter`, 25s timeout) and classifies OK / UNAUTHORIZED(401) / MODEL_NOT_FOUND(404) / TIMEOUT / NETWORK / GENERIC into localized strings; API key never in results/logs.
- **Chat upgrade**: cloud replies stream token-by-token into the assistant bubble via `chatStreaming` (120ms-throttled partial updates, placeholder finalized in place); Markdown rendering in `AssistantBubble` through new pure-JVM `ui/chat/MarkdownRender.kt` (`markdownToSegments`/`parseInlineMarkdown`: bold/italic/bold-italic/inline code/fenced blocks/links/headings→bold/blockquotes/bullets/HR; links clickable via Compose `LinkAnnotation` + `BasicText`); errors now localized SYSTEM bubbles (`chat_error_prefix`) instead of fake assistant "Error:" messages.
- **Unit tests**: 4 new classes — `LanguagePolicyTest`, `SecretsMaskerTest`, `CloudConnectionTesterTest`, `MarkdownRenderTest`. Full suite green: 17 classes / 61 tests, 0 failures (`.\gradlew.bat testDebugUnitTest`).
- **Build verified**: `assembleDebug` → BUILD SUCCESSFUL (~11–19s incremental) → `PokeClaw_v0.7.1_20261110_154327.apk`. Only pre-existing deprecation warnings.
- **QA cases added** (not yet executed — no device): QA_CHECKLIST.md section **FA** (FA1–FA13).

## What's Done (Phase 3: Arabic voice input) — branch `phase-3-voice` (from `main` after P2 merge)

Commits: `4018013` feat(voice core) · `e46c2fa` feat(voice controller) · `ad184e1` feat(voice-ui + auto-send setting + manifest).

- **In-app Arabic dictation** replaces the old system `RecognizerIntent` dialog: `voice/SpeechRecognizerController` wraps Android `SpeechRecognizer` with **ar-SA**, partial results, tuned silence (complete 1.5s / possibly 0.9s / minimum 0.8s), 60s hard cap, graceful stop, cancel, and a timeout fallback that keeps the last partial transcript.
- **Pure logic (unit-tested)**: `VoiceTextNormalizer` (trim, strip bidi/zero-width, collapse whitespace, cap), `VoiceTranscriptAssembler` (partials replace, finals win, multiple finals joined), `VoiceDictationPolicy` (auto-send gate), `VoiceTimerFormat` (mm:ss), `VoiceError` (10 error kinds with string-resource names).
- **Recording UI**: `ui/voice/VoiceRecordingBar` — pulsing mic ring driven by RMS, mm:ss timer, live partial transcript, Cancel; shown above the composer while listening/processing. Mic tint turns accent during capture; tapping mic again stops gracefully.
- **Transcript flow**: final text is normalized and appended to whatever the user already typed; it stays editable. Auto-send is a new setting `KVUtils.KEY_VOICE_AUTO_SEND` (default **off**) exposed at Settings → Tools → “Auto-send voice input”, and only fires when the composer is free (`VoiceDictationPolicy.shouldAutoSend`).
- **Permissions**: `RECORD_AUDIO` added to the manifest plus `<queries>` for `android.speech.RecognitionService` (Android 11+ visibility). Runtime request on first mic tap; denial and unavailability show localized Arabic messages.
- **Errors**: no-match, timeout, network, permission, busy, audio, server, client, language, unknown → `voice_error_*` strings in en + ar; mapped by `voiceErrorRes()` in ChatScreen.
- **Replies stay written Arabic only**: no TextToSpeech anywhere (verified by grep). Voice only produces text.
- **Offline Whisper intentionally skipped** (documented in `docs/skills/voice-input.md` §Offline Whisper): LiteRT-LM has no audio encoder; a usable Arabic Whisper model is 0.5–1.5 GB and unverified on-device; Android's own recognizer already runs offline when the ar-SA pack is installed.
- **Tests**: 5 new classes (`VoiceTextNormalizerTest`, `VoiceTranscriptAssemblerTest`, `VoiceDictationPolicyTest`, `VoiceTimerFormatTest`, `VoiceErrorTest`). Full suite: **101 tests / 0 failures** (`.\gradlew.bat testDebugUnitTest`).
- **Build**: `assembleDebug` green → `PokeClaw_v0.7.1_20261110_162107.apk`.
- **QA**: QA_CHECKLIST §VA1–VA10 added (device execution pending).

## What's Done (Phase 4: safety, security, stability) — branch `phase-4-safety`

Commits: `(core)` feat(safety-core) · `(agent)` feat(safety-agent) · `(ui)` feat(safety-ui) · `(security)` fix(config-server-auth) · docs.

- **Safety gate** (`safety/SafetyGate`) runs before every tool call in `DefaultAgentService.runAgentLoop`: app blocklist + risk classification + confirmation. Deny/block stops the task with a localized message; the model cannot route around it.
- **Confirmations** (`SafetyCoordinator` + `SafetyConfirmationHost` in ChatScreen): send message/file, call, payment/transfer, delete, install, publish. Blocks the worker thread on a latch (45s), UI approves/denies; **no UI ⇒ denied** (`ComposeChatActivity.onPause` clears availability). Fail-safe, never auto-approve.
- **Blocklist** (`AppBlocklist`): defaults cover banking/wallets/password managers/authenticators in Arabic + English; user CSV via Settings → Tools → Blocked apps; package/app-label match, password-screen fallback. Blocks all tools while such an app is foreground (incl. clipboard).
- **Timeout** (`TaskTimeoutPolicy`): default 180s, presets 120/180/300/600 cycled in Settings; checked each loop iteration.
- **Emergency stop**: red chat banner, notification action (`EmergencyStopReceiver`, exported=false → `EmergencyStop.trigger`), floating pill. Stops task + monitoring + service and logs the event.
- **Action log** (`ActionLog`): local MMKV ring buffer (200), redacted details (`ActionLogFormatter` masks sk-…, token/password/secret=, bearer, card-like digits), viewer `ActionLogActivity` in Settings → Tools with Clear. No network.
- **Retry** (`RetryPolicy`): ≤2 attempts, safe read/navigation tools only, transient markers only; risky tools never retried.
- **Battery**: ForegroundService adaptive health polling 5s (monitor) / 15s (task) / 30s (idle) instead of fixed 5s.
- **ConfigServer hardening**: loopback-only (verified) + per-process token required on every `/api/*` (401 otherwise); token injected only into the served HTML; H5 updated.
- **Docs**: `docs/SECURITY.md` (findings S1–S12 with status + residuals), `docs/skills/safety-guardrails.md`, QA §SA1–SA13.
- **Tests**: 6 new classes — RiskClassifier, AppBlocklist, TaskTimeoutPolicy, RetryPolicy, ActionLogFormatter, SafetyCoordinator. Full suite **127 tests / 0 failures**.
- **Build**: `assembleDebug` green → `PokeClaw_v0.7.1_20261110_163624.apk`.

## What's Done (Fork branding + i18n + cloud-only UI round) — 2026-11-12, on `main`

Commits: `4cce784` feat(branding) · `fd00d28` fix(i18n) · `3829652` feat(llm-config fetch) · `74ba251` fix(i18n commit()) · `a99314b` feat(chat topbar) · `89d27fb` fix(models-page dark/cloud-only) · `b46a2f2` fix(fetch label one-line) · `79308a4` feat(providers live lists).

- **Branding**: app_name → "Sanad Claw" (en/ar; ja/zh → "Sanad"); new logo + full mipmap icon set (mdpi→xxxhdpi, untracked dirs now committed); adaptive-icon background = `launcher_background` black (no white edges in light mode — verified on launcher home screen, light + dark).
- **AppLocale fix**: `attachBaseContext` must NOT use `applicationContext` (null then) — read prefs from the base context directly. Language switch row added in Settings → Appearance (ar↔en, restarts app). **Race fixed**: `setLanguage` now uses `commit()` — `apply()` + immediate `finishAffinity()` lost the write on HONOR and the switch silently reverted. Verified both directions on device (ar→en earlier round; en→ar twice this round, pref persisted). Current setting: **ar**.
- **Chat top bar**: Local/Cloud toggle REMOVED per user (cloud-first fork; local management still in Settings → Model); title single-line ellipsized; model chip (`qwen3.8-flash-next-iq3_s · Cloud`) SemiBold + textSecondary + `weight(1f,fill=false)`+Ellipsis so long ids can't wrap the toolbar.
- **Models page (LlmConfigActivity) dark-mode overhaul**: replaced hardcoded `#8b949e`/`#2A1F1A` with theme-derived colors (`metaGrayFor(tc)` = aiText@205); `retintStaticTexts()` walks the inflated tree and swaps static light-mode text colors (colorTextSecondary #AD000000 / Tertiary #61000000) + EditText text/hints for theme-derived ones — labels/fields now readable on Abyss dark; model names bold; active-card avatar = app logo (was 🦞 emoji); localized leftovers ("No model selected", "Download a model below", "● Ready/Missing file/Not configured", "· Cloud" suffix, "This phone reports NGB") via new keys in en+ar.
- **Cloud-only layout**: default-local row + divider + "Available Models" header + download list hidden (GONE) in the models page; local engine untouched — flip visibilities to re-expose. User confirmed cloud-only usage (HONOR MBH-N49, ~15.6GB RAM).
- **Fetch available models button** (Settings → Model → provider fields): GET {baseUrl}/models OpenAI-compatible, Bearer key when present, AlertDialog list fills model id; errors via Toast; key never logged. **Verified on device**: HTTP 200 against `ai.plexgrab.uk/v1/models`, dialog listed the model + localized إلغاء.
- **Providers live lists (`79308a4`)**: added `OPENROUTER` (https://openrouter.ai/api/v1) and `OPENCODE` (OpenCode Zen, https://opencode.ai/zen/v1) tabs; GOOGLE base URL moved to the OpenAI-compatible layer (`…/v1beta/openai`). Fetch button (btnFetchModels) moved out of the Custom-only block so **every** provider shows it; the model list auto-fetches once a key exists (per provider), caches per base URL in KVUtils (`getFetchedModelsForBaseUrl`), and renders selectable rows (id label, radio dot, ✓). Shared logic in `agent/llm/CloudModelsFetcher.kt` (provider-aware auth: Anthropic `x-api-key`+version, others Bearer; parses OpenAI `data[]` and Google-native `models[]`). The chat model switcher uses the same cache and auto-fetches on open, with a "جلب الموديلات المتاحة" refresh item. ComposeChatActivity now supplies a dark M3 `colorScheme` so DropdownMenu/dialogs are not white on dark; provider chips restyled (12sp, bold selected, brand pill) with auto-scroll to the selected tab.
- **Cloud LLM review + configured-model switcher (`a11a4e1`, `d472a9a`)** — verified on device: fixed a stale-list bug (model cache was read before the base-URL field was rewritten on tab switch); added a **model search field** (`etModelSearch`) filtering the fetched list live (OpenRouter returns hundreds); fetch now renders rows + "N models found" toast instead of a duplicate dialog; auto-fetch on key paste (900 ms debounce); picking a row **persists immediately** via `ModelConfigRepository.saveCloudDefault` + per-provider `KVUtils.setModelNameForProvider`. The **chat switcher now lists only configured models** (one per provider that has a saved key+model, tag e.g. OPENROUTER/CUSTOM) plus "Show all models (Settings)"; choosing an entry calls `ComposeChatActivity.selectCloudModel` which activates that provider's saved key/base URL/model. Verified end-to-end: openrouter/free (OPENROUTER) + qwen3.8-flash-next-iq3_s (CUSTOM) both listed; switching updates KV + chat log. Also removed the **Monitor & Auto-Reply UI entirely** (chat background card, workflows card, dialog; engine code stays), fixed folded-screen settings rows (single-line ellipsized title/trailing in `widget_menu_item.xml`), localized capability statuses, and made the M3 scheme luminance-aware (light/dark).
- **Device QA executed this round** (HONOR MBH-N49, AWRT025805000710): icon light+dark, language ar↔en, fetch-models dialog, models-page contrast screenshots before/after. VA section exempt (HONOR has no speech recognition service). Unit tests re-run: **127 tests / 28 classes / 0 failures**.
- **Tablet mode**: user reports no layout problems when the phone is in tablet mode (informational, not separately tested by us).

## What's Done (Phase 5: final release) — branch `phase-5-release`

- **Verification**: `.\gradlew.bat testDebugUnitTest` (forced re-run) → **127 tests / 28 classes / 0 failures**; `assembleDebug` green → `app/build/outputs/apk/debug/PokeClaw_v1.0.0_20261110_164051.apk` (~149 MB).
- **Version bump**: `versionName 1.0.0`, `versionCode 100` (fork release line; up from upstream 0.7.1/29). Overridable via `POKECLAW_VERSION_NAME` / `POKECLAW_VERSION_CODE`.
- **Project review fixes**:
  - Removed the “Manage Tools — coming soon” stub: Settings → Tools now lists the actually registered tools (count + dialog) from `ToolRegistry.getAllTools()`.
  - Attachment button no longer claims a future feature; shows a clear Arabic/English “not supported yet” message (documented limitation).
  - Scanned for TODO/FIXME/unimplemented placeholders: remaining matches were false positives (`addTodo` knowledge tool, “Workflows” experimental badge).
  - Fixed a compile break in `ComposeChatActivity` (missing `R` import) introduced by the review edit.
- **Docs**: Arabic `README.md` (what/build/install/permissions/API key/limits); upstream English README preserved as `docs/README.upstream.md`; Arabic `docs/USER_GUIDE.md`; `docs/RELEASING.md` (versioning, artifacts, signing, GitHub steps).
- **Release**: tag `v1.0.0` created locally on `main`. Push to origin fails (403, no fork); `gh` CLI is not installed → no GitHub Release created. APK path given to the user.
- **Device QA**: still not executed (no adb device) — v1.0.0 is code-complete but device-unverified.

## Phase 2 (items 1-2: injection defense + sensitive-data protection) — 2026-11-13, NOT device-tested

- **Item 1**: `safety/PromptInjectionGuard` (pure detector, EN+AR patterns, diacritics stripped); `safety/ScreenContentGuard` wraps screen/tool text in `<untrusted_content>` (angle brackets escaped), sets a sticky per-task taint on flagged text, and decides sensitive actions (`REQUIRE_CONFIRMATION` when tainted). Hooked in `DefaultAgentService` at the pre-warm screen, before each tool call (confirmation via `SafetyCoordinator`), and on tool results. System rule added to the prompt.
- **Item 2**: `safety/SensitiveDataRedactor` (Luhn cards, mod-97 IBAN, 10-digit national IDs starting 1/2, OTP digits near keywords), `safety/OtpGuard` (`[OTP hidden]` in `GetNotificationsTool`). Redaction runs on tool results before cloud send and inside `ActionLogFormatter.sanitize`.
- **ConfigServer wording**: comments fixed to loopback-only; `ChannelConfigActivity` hint now shows the `127.0.0.1` URL (not the Wi-Fi IP) and states it is not reachable from other devices. Binding unchanged.
- **Corpus** (`app/src/test/resources/injection/`): hostile 25 EN + 25 AR, benign 50. Measured: hostile flagged 47/50 (94.0%), benign flagged 0/50 (0.0%), sensitive-action violations 0/329 checks.
- **Known gaps (open)**:
  - 3 hostile samples are missed by the detector; they do not taint the task and are not gated. Residual risk, not mitigated by a hard block.
  - Decision 2 "pause and ask when a task needs a redacted value" is NOT implemented. Redaction currently replaces the value silently for the cloud path.
  - `ConfigServerManager.getAddress` still returns the Wi-Fi IP (informational); only the copy-URL uses the loopback address.
- Tests: 177 total, 0 failures.

## Phase 2d (items 1-2 unit-complete, device QA pending) — 2026-11-13

- **Decision 2 (pause and ask)** implemented as `safety/PausedTaskState` (pure JVM). Trigger: a tool call whose params contain a `[REDACTED:*]` token only. Next chat message within 45 s is the value, substituted only into placeholder tokens of that call; cleared after the call, at task end, and on cancel. Hooks: `DefaultAgentService` (pause before execution, timeout cancels) and `TaskFlowController.sendTask` / `ChatSessionController.sendChat` (message routing while paused).
- **Card redaction bug fixed**: the greedy separator-tolerant regex let a Luhn-valid card next to a digit (e.g. `card2 4111...`) escape. Replaced with an explicit scan. Found by the transport test.
- **Cloud transport check** (`CloudTransportPayloadTest`): real `OpenAiLlmClient` + production `OkHttpClientAdapter`; only the socket is replaced by an interceptor. No mock library added.
- **Held-out benign set has 39 entries, not 40.** Deviation from the spec, frozen by design (committed `96a736f`). Not corrected.
- **Held-out detector result**: hostile 2/40 flagged (5.0%), benign 0/39 (0.0%), sensitive actions blocked by taint 40/40. Detector is weak on this set; enforcement carries the guarantee.
- **Open**: taint cost (every screen-reading task requires confirmation before a sensitive action) is an OPEN DECISION, see plan file.
- Device QA (IJ1–IJ6, SD1–SD5) NOT EXECUTED.
- **Row 14 (value never logged) CLOSED at unit level** (Phase 2e): `XLog` now has a test-only Sink (15 log methods; `setDEBUG` untouched); `PausedTaskLogCaptureTest` (4 tests) asserts zero canary occurrences across pause, resume, substitution, clear and timeout, plus an active-capture assertion. Device logcat not checked.
- **P1 defect registered (not fixed)**: `FILE_LOGGING_ENABLED` (debug builds, `ClawApplication.kt:50`) writes full request/response bodies and **all un-masked request headers including `Authorization`** to `cacheDir/http_logs/` (`FileLoggingInterceptor.java:130-149`). See plan file "Known defects outside items 1-2" and QA FL1/FL2 (NOT EXECUTED).
- **FILE_LOGGING P0 fixed at unit level (2026-11-13)**: headers masked, bodies omitted, curl and URL query params redacted, exception text class-name only; `testOptions.unitTests.isReturnDefaultValues` added to `app/build.gradle.kts`; suite 218 tests / 41 classes / 0 failures. Device QA FL3 pending.

## Phase 2g item 4 (per-category confirmation permissions) — 2026-11-13, UNIT-COMPLETE, device QA PENDING

- Replaced the single `KEY_SAFETY_CONFIRM_RISKY` switch with seven keys `KEY_SAFETY_CONFIRM_<KIND>` (SEND_MESSAGE, SEND_FILE, CALL, DELETE, PAYMENT, INSTALL, PUBLISH), default true. UNKNOWN has no switch and always confirms.
- `SafetySettings.confirmationFor(kind)` and pure `requiresConfirmation(kind, switchOn)`; `SafetyGate` decision order: blocklist (always) → UNKNOWN/null (always) → per-kind switch. PAYMENT placeholder removed from the blocked-app path (now `kind = null`).
- **Migration** (`SafetySettings.migrateLegacyConfirmation`, run in `SettingsActivity.onCreate`): does NOT copy the old value; removes the legacy key; if the old value was false, shows a one-time bilingual notice (flag `KEY_SAFETY_PERMISSIONS_MIGRATION_NOTICE_SHOWN`). Idempotent.
- **Settings**: seven tap-to-toggle rows under Tools (not real Switch widgets; `MenuItem` has no switch and is outside the allowed files).
- Tests: `SafetyPermissionsPolicyTest` (7), `SafetyPermissionsMigrationTest` (4). Suite: **229 tests / 43 classes / 0 failures** (baseline 218/41/0; no baseline test changed state). `assembleDebug` green.
- Device QA QP1–QP8 (QA_CHECKLIST §QP) NOT EXECUTED.
- **OPEN DECISION (taint placement):** rule 2 (tainted task + sensitive kind → confirm, regardless of switch) is enforced in `DefaultAgentService` (lines 768-769) through `ScreenContentGuard.decide`, not inside `SafetyGate`, because taint is per-task state that `SafetyGate` cannot see. Unchanged from Phase 2b and tested by `taintedTaskRequiresConfirmationRegardlessOfSwitch`. Decide whether to accept this location or to pass taint into `SafetyGate` (requires editing DefaultAgentService).
- **Plan-file conflict**: `.kilo/plans/...-plan.md` Item 4 specifies copying the legacy value and a new `CategoryPermissionPolicy.kt`; both were superseded by the brief (no copy; file not allowed). Plan not edited.
- Report: `docs/phase2g_report.md`; per-test: `docs/phase2g_before.txt`, `docs/phase2g_after.txt`.
- **Phase 2g-fix (same day):** (1) migration notice flag now cleared only after the dialog presents (`SafetySettings.presentMigrationNoticeIfPending`, tested by `noticeSurvivesDialogFailure`); (2) null-kind grep done, no production change needed (table in report); (3) taint test calls production `ScreenContentGuard.decide` directly; DefaultAgentService 768-769 is verified by inspection, NOT tested; (4) each row shows state text (`safety_row_state_on/off`) and a content description. Suite **231 tests / 43 classes / 0 failures**. Device QA still NOT EXECUTED.

## Build Environment (Windows machine — how it was made to work)

- `local.properties` (gitignored): `sdk.dir=C:\Users\Altamimi\AppData\Local\Android\Sdk`.
- Android SDK had only platform-tools; installed **cmdline-tools** (commandlinetools-win-15859902_latest.zip → `Sdk\cmdline-tools\latest`) and ran `sdkmanager --licenses` (all accepted). Gradle then auto-installed Build-Tools 36.0.0 + Platform 36.1 on first successful build.
- Java: system default JDK 17; project needs **JDK 21 toolchain** (foojay auto-download failed on extract/move). Fix: extracted the already-downloaded Temurin 21 zip to `~\.gradle\jdks-manual\jdk-21.0.7+6` and set in **user-level** `~\.gradle\gradle.properties` (not committed):
  `org.gradle.java.installations.paths=C:/Users/Altamimi/.gradle/jdks-manual/jdk-21.0.7+6` + `org.gradle.java.installations.auto-download=false`.
- Build commands: see AGENTS.md "Build Commands".

## Key Architecture Facts (details in docs/AUDIT.md)

- Single module `:app`; agent loop = `DefaultAgentService.runAgentLoop` (while iterations < maxIterations, StuckDetector + TaskBudget/TokenMonitor + guards), tools via `ToolRegistry` (35 tools), skills as markdown (`docs/skill-spec.md`).
- Local LLM: LiteRT-LM 0.10.0, Gemma 4 E2B/E4B `.litertlm` from HuggingFace `litert-community`; GPU→CPU fallback tracked by `LocalBackendHealth`.
- Cloud: LangChain4j OpenAI + Anthropic + custom OpenAI-compatible base URLs; catalog/pricing in `agent/CloudProvider.kt` (now incl. DeepSeek). Per-provider editable Base URL (`KEY_LLM_BASE_URL_<PROVIDER>`); real probe via `agent/llm/CloudConnectionTester`. Chat streams cloud replies token-by-token (`ChatSessionController.sendChat` → `chatStreaming`); Markdown via `ui/chat/MarkdownRender.kt`.
- State/settings: MMKV via `KVUtils` for non-secret settings; secrets (API keys, bot tokens) in `utils/SecureStore` EncryptedSharedPreferences since phase 2 (migrated from plaintext MMKV). Voice: `voice/SpeechRecognizerController` (Android SpeechRecognizer, ar-SA) + pure helpers in `voice/`; auto-send flag `KEY_VOICE_AUTO_SEND` (default off). UI: Compose chat (`ChatScreen.kt`) + Views settings. Channels: Telegram/Discord/WeChat. External automation intent API (user-toggle, default off).

## Known Issues / Risks (carried into later phases)

1. ~~Push target unresolved~~ **RESOLVED 2026-11-12**: repo `Altamimi79/Sanad-Claw` created (public); `origin` = fork, `upstream` = agents-io/PokeClaw; pushed `main`, `v1.0.0`, `v1.0.1`.
2. Upstream signing key lost historically → upgrade gap requires one-time uninstall/reinstall (BACKLOG P1). Affects P5 release story.
3. Security debt for P4: plaintext API keys in MMKV, unauthenticated loopback ConfigServer HTTP, cleartext permitted globally, exported RUN_TASK components without sender verification, prompt-injection surface from screen/notification content (guards exist: DirectDeviceDataGuard etc.).
4. God classes: `ChatScreen.kt` 2385L, `AutoReplyManager.java` 1060L, `DefaultAgentService.kt` 778L, `SettingsActivity` 773L.
5. Local model RAM floor (E2B/E4B heavy for low-RAM devices); MediaTek/Samsung engine failures open upstream (BACKLOG P1).
6. `.env.enc` age-encrypted to upstream recipient — fork cannot rotate those secrets; P4 needs a fork-local secret story if cloud features are used in CI.
7. JITPack dependency (EasyFloat) + Chinese-mirror history = supply-chain watch item.
8. Phase 2 E2E QA (QA_CHECKLIST §FA) not executed: no Android device/emulator connected via adb on this Windows machine at phase close (`adb` at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). JVM unit tests cover the pure logic.
9. `androidx.security-crypto` EncryptedSharedPreferences is deprecated upstream (last release 1.1.0): functional today with private-mode fallback; revisit storage approach in P4.
10. Phase 3 voice QA (QA_CHECKLIST §VA) not executed: no adb device. Additionally, real recognition depends on the device speech service having the **Arabic (ar-SA)** language pack; some OEMs are online-only. Could not verify latency, accuracy, permission dialog, 60s cap, or error toasts on hardware.
11. Offline Whisper not implemented (deliberate; rationale in `docs/skills/voice-input.md`). Revisit only with a device farm + a measured Arabic WER/latency budget.
12. Phase 4 device QA (QA_CHECKLIST §SA) not executed: no adb device. Confirmation dialogs, blocklist enforcement, emergency stop, timeout, notification action and the ConfigServer token were not exercised on hardware.
13. Residual security risks tracked in `docs/SECURITY.md`: global cleartext (S3), external-automation sender verification/rate limiting (S4), AppLogStore content redaction (S7), security-crypto deprecation (S1 residual).
14. ~~v1.0.0 not published~~ **RESOLVED**: public repo `Altamimi79/Sanad-Claw` with releases v1.0.0 (notes) and v1.0.1 (APK `SanadClaw-v1.0.1.apk`, 157 MB debug build). `gh` 2.103.3 installed + authenticated (scopes repo, workflow).
15. Device QA is **partial**: fork UI/providers/models flows verified on HONOR MBH-N49 (see QA_CHECKLIST round section). QA_CHECKLIST §FA2+/§SA and VA still pending; VA exempt on HONOR (no speech service). No signed release build yet (debug APK only).

## Next Ideas / Roadmap (priority order, 2026-11-12)

Readiness snapshot: **~72% overall** / **~90% for daily cloud-chat use** (UI 90, cloud models 90, agent tasks 70, safety-verification 60, voice 35, tests/docs/release 75, local models 40).

**P0 — identity & trust**
1. **Voice wake word ("هلا سند" / "Hi Sanad")**: hotword engine (Porcupine/openWakeWord) + bundled STT (Vosk/whisper.cpp) because OEM speech services are absent on HONOR; foreground service + battery exemption + privacy toggle. Biggest missing feature.
2. **Finish device QA**: QA_CHECKLIST §FA2+ and §SA (confirmations, blocklist, emergency stop, timeout, notification action, ConfigServer token) + an ADB E2E script for critical paths. VA exempt on HONOR.
3. **Signed release APK**: `assembleRelease` with a fork keystore (RELEASING.md), attach to GitHub Releases; keep `io.agents.pokeclaw` package id.

**P1 — product value**
4. **Task recipes**: save a successful task as a reusable parameterized routine; run without an LLM (fast, cheap, deterministic).
5. **Model routing/fallback**: auto-switch provider on 429/failure; per-task and per-chat model selection; cost/token caps per task (budget UI exists).
6. **Context/memory**: app→activity map, contact resolution, compact screen-state summaries to cut tokens, saved user preferences.

**P2 — platform**
7. **Channels**: complete Discord/Telegram/LAN; optional WhatsApp inbound commands with a strict allowlist (instead of the removed open auto-reply).
8. **Arabic voice output**: short TTS replies; finish ja/zh strings; RTL audit.
9. **Security**: biometric app lock, log redaction in debug reports, per-app permission matrix, replace deprecated security-crypto.
10. **Local models (optional)**: re-expose Gemma management (currently hidden), RAM-aware guidance, idle unload.
11. **CI**: GitHub Actions build + unit tests + changelog; automated release upload.

**Code-quality follow-ups from the fork round**
- Dead code left behind after UI removals: `MonitorDialog` composable, `onStartMonitor` param, static-catalogue rendering branch in `LlmConfigActivity` (catalogues are now empty). Remove in a dedicated cleanup commit.
- `ChatScreen.kt` remains a god class (~2.6k lines) — split into ViewModel + sections.
- Cloud cost display now shows $0 for models without static pricing; wire pricing from `/models` metadata if providers expose it.
- Fetched model cache is keyed **per provider** (`KEY_CLOUD_MODELS_<PROVIDER>`); a legacy URL-keyed cache is migrated on read. Refreshing a provider list is manual when a cache exists.
- `README.md` is English (repo-facing) with the Arabic overview in `docs/README.ar.md`.

## Phase 2h Item 6 (Arabic action log: reason, result, v2 format, redacted export) — 2026-11-13, UNIT-COMPLETE, DEVICE QA PENDING
- v2 line 2 | ts | task | tool | risk | reason_key | result_key | detail; v1 lines read as reason UNKNOWN_LEGACY, result null when unknown. Reason and result are fixed enums (ActionReason, ActionResult); no free text.
- Export: ActionLogFormatter.buildExport, redacted per field; wired in ActionLogActivity via ACTION_CREATE_DOCUMENT text/plain. Labels: ActionLogLabels (pure map, EN/AR).
- Call sites argument-only: DefaultAgentService 571/754/784/809/843, EmergencyStop 38, SafetyGate.recordAction forwards reason/result.
- Tests: 242 / 45 / 0 (baseline 231 / 43 / 0). Per-test: docs/phase2h_before.txt vs docs/phase2h_after.txt. Report: docs/phase2h_report.md. QA rows QL1, QL2: NOT EXECUTED.
- Known: formatAndParseRoundTrip unchanged and passing (v1 format restored byte-identical to HEAD).

## Phase 2i Item 3 (Arabic/English voice STOP and CONFIRM) — 2026-11-13, UNIT-COMPLETE, DEVICE QA PENDING

- Added pure safety/VoiceCommandParser (STOP / CONFIRM / NONE, exact match after normalization) and safety/VoiceCommandPolicy (EXECUTE_STOP / EXECUTE_CONFIRM / REJECT_NO_PENDING / IGNORE_NO_TASK / IGNORE).
- ChatScreen command path: STOP only while isTaskRunning, CONFIRM only while SafetyCoordinator.pending is set. Otherwise toast, no action.
- SpeechRecognizerController: the hard-cap timeout fallback now calls the new Listener.onTimeoutPartial instead of onFinalText. Only the dictation listener (ChatScreen) implements it, inserts text into the field, and never runs the command path or auto-send.
- ChatScreen listener closures read isTaskRunning/inputEnabled/isAwaitingReply through ememberUpdatedState.
- Tests: VoiceCommandParserTest (9), VoiceCommandPolicyTest (7), TimeoutPartialRoutingTest (3, fake listener; real controller is not JVM-testable). Suite: 261 / 48 / 0, twice; baseline 242 all still PASS.
- Spec note: "نعم سند" is a CONFIRM phrase, so the canary asserts it never reaches the command path, not that it parses as NONE.
- Device QA QV1-QV6: NOT EXECUTED. Report: docs/phase2i_report.md.

## Phase 2i item 3 — review closed (2026-11-13)

- SafetyCoordinator: request id already existed (ConfirmationRequest.id, AtomicLong). Added currentPendingId() (read-only) and pprovePendingIfMatches(id) under the same lock as creation and resolution. approvePending/denyPending, timeout and latch unchanged.
- ChatScreen voice command path: oiceCapturedConfirmId captured in startVoiceInput (mic); cleared in esetVoiceUi. CONFIRM approves only via pprovePendingIfMatches(captured). VoiceCommandPolicy.confirmationPendingFor (pure) gates the policy input.
- docs/PLAN.md reverted to HEAD (appended section removed).
- Tests: SafetyCoordinatorRequestIdTest (5), VoiceConfirmCaptureTest (3). Suite 271 / 51 / 0 twice.
- QA QV8, QV9 added (NOT EXECUTED); QV1-QV7 NOT EXECUTED.
- Device QA pending.

## Phase 2j — dev build 1.0.3-dev (2026-11-13) — DEV BUILD READY, DEVICE QA PENDING, NOT FOR DISTRIBUTION

- Part A: ordering bug found and fixed. handleVoiceFinal cleared oiceCapturedConfirmId (via resetVoiceUi) before handleVoiceCommand read it. Captured id is now read into a local at the top of handleVoiceFinal and passed as a parameter (ChatScreen.kt:958, :966, :922).
- Suite 271 / 51 / 0 both runs; per-test unchanged.
- APK: PokeClaw_v1.0.3-dev_20261113_073755.apk, versionCode 101, versionName 1.0.3-dev (aapt2-verified), SHA-256 413EB5AFF757927A3C67C1A73AA8D329271DD0C94F10AC9BD5E7F63291DB9DC7. Not signed for release, not committed, not pushed.
- docs/CHANGELOG.md 1.0.3-dev entry; docs/DEVICE_QA_SESSION.md lists 32 NOT EXECUTED IDs; none missing.
- Device QA NOT EXECUTED.

## Phase 2k — preserve work locally (2026-11-13)

- Branch sanad/dev-1.0.3, commit 92799e1 (1.0.3-dev code, 80 files). Not pushed.
- Frozen corpora tracked; SHA-256 match audit (injection) and phase2d (injection_heldout).
- APK preserved in dist-dev/ (gitignored) with .sha256; SHA-256 413EB5AF...DB9DC7 verified.
- app/build.gradle.kts still modified (testOptions line from an earlier phase); not committed; decision pending.
- No QV10 added: QV3 already covers the scenario. Device QA NOT EXECUTED.


## Handoff (2026-11-13)

- Current HEAD on sanad/dev-1.0.3: 14b719c (pushed). Tags v1.0.3-dev and v1.0.3-dev-2 on origin.
- Pre-release v1.0.3-dev-2 published with APK + SHA-256 (verified after download). Latest stays v1.0.2.
- Device QA: NOT EXECUTED (no device at build time).
- Resume instructions: docs/RESUME.md.


## Session 2026-11-13 (second machine handoff, device QA round 2)

- Branch sanad/dev-1.0.3 now includes: 44c3ec6 (templates collapsed by default, SanadClaw APK name), 2fc50df (QA doc), and the Arabic export label fix (??????, replacing ?????).
- Device: HONOR MBH-N49, installed SanadClaw build versionCode 103 (1.0.3-qa). Old GitHub 1.0.3-dev-2 build was replaced; app data reset (API key re-entered by user).
- Verified on device: Quick Task Templates collapsed on launch; Arabic action-log screen renders (??? ????????? (???? ???)); Settings > Tools rows; unit tests 271/0.
- FL3 fixed: pre-fix device logs held full OpenRouter key; current build writes Authorization: ***. Device logs cleared.
- gh CLI installed (GitHub.cli) and authenticated as Altamimi79.
- Open: rotate pre-fix OpenRouter key; QP1a TalkBack; QP3-QP8; IJ/SD/QV (need manual interaction); QP7/QP8 need prior build.

## Roadmap status 2026-11-13 (session 3)

| Item | Status | Evidence |
|---|---|---|
| 3. Voice commands '???? ???' / '??? ???' | DONE (code + unit) | VoiceCommandParser / VoiceCommandPolicy / approvePendingIfMatches wired in ChatScreen.handleVoiceCommand; unit tests green. Device check QV1-QV9 still NOT RUN. |
| 5. Plan preview before execution | PARTIAL | TaskPlanPreview pure model + 8 unit tests (safety/TaskPlanPreview.kt). NOT wired into DefaultAgentService.executeTask; no approval UI yet. |
| 8. Scheduled routines | PARTIAL | ScheduledRoutine pure model (routine/ScheduledRoutine.kt) + 8 unit tests. No AlarmManager/WorkManager scheduling, no execution via safety gate, no UI yet. |

Unit tests: 288 pass, 0 fail.

Still open for item 5 and 8: wire preview gate into executeTask and approval screen (5); persist routines, schedule with AlarmManager, route execution through SafetyGate and confirmation, add UI (8). Both require device verification.

- 2026-11-13 (session 3, follow-up): item 5 gate setting added: KVUtils.isPlanPreviewEnabled (KEY_PLAN_PREVIEW_BEFORE_RUN, default false) + TaskPlanPreview.requiresPreview. Default behaviour unchanged. Unit tests: 289 pass. NOT yet wired: planner LLM call before executeTask, approval UI. Device verification pending.

- 2026-11-13 (item 8 core): routine store (routine/RoutineStore, KV JSON, malformed entries skipped), exact-alarm scheduler (routine/RoutineScheduler, setExactAndAllowWhileIdle), non-exported RoutineAlarmReceiver that posts a reminder and NEVER executes the prompt (execution stays behind chat + safety gate), BootReceiver re-arms alarms. Unit tests: 302 pass. NOT done: routine creation/edit UI, device verification of alarms, Android 14+ exact-alarm permission prompt.

- 2026-11-13 (plan preview UI): Settings > Tools row '?????? ????? ??? ???????' added (EN+AR), default off. Verified on device: row visible, toggles ????? <-> ?????, persisted to MMKV (KEY_PLAN_PREVIEW_BEFORE_RUN). Left disabled. Unit tests: 302 pass. Build: SanadClaw v1.0.3-dev-3 versionCode 104.

- 2026-11-13 (plan preview device test): FIXED NetworkOnMainThreadException by moving the plan gate onto the background executor. Device-verified: planner returns steps off the UI thread; DENY -> BLOCKED_DENIED, task not started; ALLOW (real tap) -> RUN, task completed. One earlier run (Tokyo) reached RUN without an observed tap; not explained from logs, needs investigation. Unit tests: 302 pass.

## Release 1.0.3-dev-4 (versionCode 105), 2026-11-13

Changes since 1.0.3-dev-3:
- Plan preview: gate runs on background executor (fixed NetworkOnMainThreadException). Device-verified: deny blocks, allow (real tap) runs.
- Plan preview setting row in Settings > Tools (default off).
- Scheduled routines core: store, exact-alarm scheduler, reminder-only receiver (never executes unattended), boot re-arm. No creation UI yet.
- Voice commands and plan approval reuse SafetyCoordinator.
- Text size setting (normal / slightly larger / larger / largest) applied via density scale to all sp text. Font family choice was removed after review: only Naskh is bundled; system and monospace looked too similar in Arabic to justify a choice.
- Message bubbles and input now take font family from theme typography (previously hard-coded).

Tests: 310 pass, 0 fail.
Device: HONOR MBH-N49, versionCode 105 installed, launches with no fatal errors.

Known open items:
- Routine creation UI not built; alarm delivery not device-verified.
- QA not run: QV1-QV9 (voice), IJ/SD (hostile screens, SMS), QP3-QP8, QP1a (TalkBack), QL2 (Arabic action log labels on real entries).
- One earlier plan-preview run (Tokyo) reached RUN without an observed tap; unexplained from logs.
- Rotate the OpenRouter key that appeared in pre-fix debug logs.
- Release is debug-signed; no fork signing key.

## Session 2026-11-13 (late): chat copy/delete, plan gate, Tokyo investigation

Changes (uncommitted, debug build):
- Chat bubbles wrapped in SelectionContainer (user + assistant text selectable).
- Long-press on a message opens a menu: Copy text / Delete message / Cancel (EN+AR strings). Delete removes the message from _messages and saves the chat (ComposeChatActivity.onDeleteMessage).
- SafetyCoordinator.requestConfirmation waits up to 15s for chat UI to return (polls every 250ms) instead of denying immediately when the chat activity is paused. Previously the plan gate denied at once when the chat went to Settings for the accessibility redirect.

Device verification (HONOR MBH-N49):
- Copy: long-press user bubble, Copy text, paste into input: PASS.
- Delete: long-press assistant bubble, Delete message: PASS; deletion persisted across force-stop and relaunch.
- Tokyo plan gate: send -> planner call (200) -> "Review this plan" dialog -> Allow tapped -> log "Plan preview outcome=RUN steps=5" -> answer shown. PASS for the Allow path.
- Earlier denial "Plan not approved" came from the dialog closing before a decision (SafetyCoordinator 45s timeout); not confirmed by log line.

Tokyo root cause status:
- No VoiceCommand line in any captured run; approval in the verified run was a tap.
- The earlier RUN without an observed tap is still NOT reproduced. Candidate remains approvePendingIfMatches via voice (ChatScreen handleVoiceCommand), not confirmed.

Automation notes:
- Send button is a FloatingActionButton with clickable=false in the accessibility tree; its position moves when the keyboard opens (y 2176 closed, ~1305 open). Enter inserts a newline (multi-line field), it does not send.
- accessibility setting is cleared by reinstall with -r; re-enable with settings put secure enabled_accessibility_services.
- Java for Gradle: JDK 21 at C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot (the path in AGENTS.md does not exist on this machine).

Still NOT run: QV1-QV9, SD1-SD5, IJ1-IJ6, QP3-QP8, QP1a (TalkBack), QL2 (Arabic labels on real entries), text-size visual measurement, routine creation UI, alarm on device.

## Session 2026-11-13 (continued): About cleanup, update check, routines UI, text size

About section (Settings):
- Reduced to two rows: version and "Check for updates". Removed GitHub, Report issue, Share debug report, Developer, Built by, plus their helper functions, URL constants and 5 unused imports.
- "Check for updates" now calls UpdateChecker.checkNow: queries GitHub latest release, shows version + release notes (body) with Update / Later, "up to date" dialog, or an error dialog. Device result: "GitHub responded with 404" because the repo path has no published release. The newer-version dialog is NOT device-verified (no release to compare).

Routines (roadmap item 8) UI:
- New RoutinesActivity (view-based, like ActionLogActivity), manifest entry exported=false, row in Settings > Tools.
- Create (name, prompt, HH:mm, weekdays), list, enable/disable, delete. Every change calls RoutineScheduler.rescheduleAll.
- Device verified: saved routine armed as RTC_WAKEUP ROUTINE_FIRE for the next weekday 08:30 (UTC+4); a 2-minute test routine posted its reminder notification at 15:11:00 ("Routine reminder posted"); deleting it removed its system alarm. Reminder only notifies; prompt is not executed.
- Test routines removed; "Morning check" (08:30 Mon-Fri) left enabled on the phone.

Text size measurement (Device, HONOR MBH-N49), error-bubble wrapped height / Tokyo single-line height:
- Normal: 257 / 178
- Slightly larger: not captured / 204
- Larger: 388 / 230
- Largest: 481 / 179
The wrapped error bubble grows monotonically (257 -> 388 -> 481), so the scale applies. Single-line height is not reliable at Largest (bubble widens, line no longer wraps). Setting restored to Normal after testing.
- Note: the scale applies on cold start; the chat activity does not re-render it when returning from Settings without restart.

Known open items after this session:
- Slightly larger error-bubble height not captured.
- Newer-version update dialog not device-verified (needs a published GitHub release).
- Tokyo: plan gate RUN path verified with Allow tap (log "Plan preview outcome=RUN"); the earlier RUN without tap is NOT reproduced.
- Not run: QV1-QV9, SD1-SD5, IJ1-IJ6, QP3-QP8, QP1a (TalkBack), QL2.
- No release version created yet (user will decide).

## Session 2026-11-13 (security tests start): QP3, crash, router gap

QP3 (Confirm sending files OFF):
- Switch toggled OFF on device: row shows "Disabled / موقوف". PASS for the setting itself.
- Could NOT verify the runtime behaviour: TaskParser.matchSendMessage (agent/TaskParser.kt) maps any "send ... to ..." phrase to send_message. There is no send_file rule, so a file request from chat never reaches the send_file tool. The only path to send_file is DebugTaskReceiver "tool" extra, which calls executeTool directly and bypasses SafetyGate, so it is not valid evidence for QP3.
- Gap: chat cannot trigger send_file. Needs a product decision (add a file route, or accept that file sending is out of chat scope).

Crash observed (once, not reproduced in a second run):
- FATAL EXCEPTION main: android.app.RemoteServiceException ForegroundServiceDidNotStartInTimeException. Process io.agents.pokeclaw PID 14521 died at 15:22:27 right after a chat send.
- Notification permission was granted at the time (checked), so the onCreate stopSelf branch was not the cause.
- ForegroundService.onCreate calls startForeground only when permission is granted; if the service started without it, Android kills the process. Needs investigation of the start path (ForegroundService.start called from where, and why startForeground is not reached in time).
- Second identical send did not crash (PID 15740 stayed alive).

Not yet run from the security list: QP5, QP6, IJ1, IJ2, QV3, QV4.

## Session 2026-11-13 (late, security + send-media): full status

Stopped before sending. NO message was sent to any real contact in the last test rounds, except ONE earlier message (see below).

IMPORTANT, real message sent by mistake:
- Text "Send media file /sdcard/Download/send_media_test.txt to My Heartbeat on WhatsApp" was delivered to the WhatsApp Business chat "My Heartbeat" (w4b). It was a test request that should not have been sent. Cause: the phrase reached plain send_message, not send_media, and no confirmation dialog appeared. Action: inform the contact owner if needed.

Security findings (fixed in code, built, unit tests pass, NOT yet device-verified):
1. Tier-1 direct tools (PipelineRouter.executeTool) bypassed SafetyGate: no confirmation for risky tools (send_message, send_file, make_call). Fix: PipelineRouter.executeTool now calls SafetyGate.evaluate before running the tool. The agent loop already calls SafetyGate, so no double prompt.
2. ForegroundService.onCreate skipped startForeground when notification permission was missing, causing ForegroundServiceDidNotStartInTimeException (observed once). Fix: always call startForeground first, then stopSelf if permission missing.

Feature work (built, unit-tested, device send NOT verified):
- New tool SendMediaTool (send_media): opens app, finds contact, taps Attach then Document, picks file by name. Classified SEND_MESSAGE in RiskClassifier.
- TaskParser.matchSendMedia routes "send media file <path> to <contact> [on <app>]" to send_media. Unit tests added in TaskParserTest.
- OpenAppTool: WhatsApp resolves to whichever package is installed (com.whatsapp or com.whatsapp.w4b).

Input focus issue (open, not solved):
- On this phone, text typed via adb into the chat input goes to the fallback input connection (mServedView=null). The input never gets focus, so the text does not land in the field the send uses.
- Gesture fix applied in ChatScreen.dismissKeyboardOnBackgroundTap (do not clear focus on a consumed tap). Built and installed; did NOT resolve the adb typing problem.
- Manual typing on the phone works: the text appeared in the field and was verified by UI dump.

Security checklist status (from the user's list):
- QP3: switch "Confirm sending files" set OFF and shown as "Disabled / موقوف". Behaviour not verified (no real send_file path from chat).
- QP5, QP6, IJ1, IJ2, QV3, QV4: NOT run.

Next steps (new chat):
1. Confirm that the Tier-1 SafetyGate fix shows a confirmation dialog before a send (device test with a harmless target the user chooses).
2. Verify send_media end to end with a test contact the user approves.
3. Run QP5, QP6, IJ1, IJ2, QV3, QV4.
4. Resolve adb/IME focus issue or test manual typing flow.
5. No release until 1-4 are done.

## Session 2026-11-13 (device QA + signed release + CI fix)

Device: HONOR MBH-N49, Android 17, installed SanadClaw 1.0.3-dev-5 (code 106).

### Done
- **CI JDK fixed**: all four workflows (build, release, emulator-matrix, firebase-test-lab) now use JDK 21 (project toolchain). Release notes extraction made robust (README -> docs/CHANGELOG -> fallback) and dev/rc tags publish as prerelease.
- **Signed release keystore created**: keys/sanad-release.jks (gitignored), alias sanad, RSA 4096, 10000 days. Credentials in keys/KEYSTORE_INFO.txt (gitignored). local.properties wired. assembleRelease green -> SanadClaw_v1.0.3-dev-5_20261113_161615.apk (102 MB), apksigner verified (CN=Sanad Claw, cert SHA-256 7de78eda...), APK SHA-256 A7C13A3AFCB6DADACD9099F379936942F488D65647154C3DB099CFD95E3E6502.
- **Accessibility state bug fixed** (AppCapabilityCoordinator.bindingState): a live binding now wins over the stale pending-repair flag. Before: after re-enabling accessibility the app stayed "Connecting"/"Disconnected" for up to 120 s and blocked task mode. Verified on device: Settings shows "Enabled" after re-enable.
- **i18n gaps fixed**: ChatScreen quick-task composer + skill card + experimental badge now use string resources (send_word_on, send_example_phrase, skill_send_message_desc, skills_experimental_badge) in en+ar. Action-log export filename -> sanadclaw-action-log.txt.
- **Device QA executed**:
  - QP1 PASS: all 7 per-category confirm rows present (messages/files/calls/delete/payments/install/publish).
  - QP2 PASS: toggle persists across force-stop + relaunch.
  - QL1 PASS: redacted export written (v2 format, no card/IBAN/OTP/token values).
  - FL3 PASS: http_logs show "Authorization: ***", body omitted, set-cookie masked; no sk- keys.
  - App launch PASS (no FATAL).
  - Plan preview gate PASS (Allow path; task ran and answered).
  - Task execution PASS (Tokyo time answered via OpenRouter).
- **Device finding**: HONOR force-stop revokes the accessibility secure setting (accessibility_enabled=0). The app correctly shows "Task mode needs Accessibility Service enabled. Opening Settings...". Re-enable via ADB or Settings after any force-stop.
- **Device finding**: uiautomator dump temporarily unbinds the accessibility service (UiAutomation), so UI dumps can show a false "Disconnected"; screenshots are the reliable check.
- **Device finding**: send_message failed on this device ("Could not reach a searchable WhatsApp chat list", "Failed to open WA Business. Is it installed?") even though com.whatsapp.w4b is installed. Needs investigation (SendMessageTool / OpenAppTool WhatsApp resolution).
- **Device finding**: adb typing works with %s for spaces; the send FAB moves with the keyboard (tap ~971,1260 when open).

### Not done / needs approval
- send_media + SafetyGate end-to-end send: NOT executed (user requires explicit approval before any message/file is sent).
- QP3-QP8, IJ1-IJ6, SD1-SD5, QV1-QV9: not executed (need manual interaction / hostile screens / SMS / voice / prior build).
- QP5 blocklist: attempted twice; timing-sensitive via ADB, inconclusive (unit-tested only).
- OpenRouter key rotation: still pending (user action).
- Release APK not installed on device (signature differs from debug build; would require uninstall + data loss).

### Tests
- 312 tests / 56 classes / 0 failures after all changes.

### Follow-up fix (same session)
- OpenAppTool.resolveAppName now maps "WA Business" (the label get_installed_apps returns for com.whatsapp.w4b) to the installed WhatsApp package. Previously send_message with app="WA Business" fell through to a literal package name and failed with "Failed to open WA Business. Is it installed?".
- Rebuilt + reinstalled debug APK; 312 tests / 56 classes / 0 failures.

### Final signed release (all fixes)
- APK: app/build/outputs/apk/release/SanadClaw_v1.0.3-dev-5_20261113_164309.apk (102 MB)
- Signed: CN=Sanad Claw, cert SHA-256 7de78edaf87a79dd829866284134fa0b4b4d2c85ec94b2a829b245bd9208cf6b
- APK SHA-256: 832F8EDD3F17ABC0A5BFD4F4A79EE6A9DC2F1A77FE77604E5041E1E92612C87E
- Includes: accessibility state fix, i18n fixes, WhatsApp "WA Business" resolution, CI JDK 21.

### send_message end-to-end fix + verification (same session)
Root cause chain found on HONOR + WhatsApp Business (com.whatsapp.w4b):
1. OpenAppTool.resolveAppName did not map the label "WA Business" -> added.
2. ContactListUiUtils.isContactLookupReady required clickable text rows; WhatsApp exposes row text on non-clickable TextViews -> relaxed to `scrollableContainers > 0 && visibleTextRows >= 3` (fallback `visibleTextRows >= 6 && clickableRows >= 1`).
3. prepareForContactLookup pressed Back before the list rendered -> added a settle re-check before Back.
4. Search results include message previews ("From X") -> findBestVisibleContactNode now skips "From "/"من " rows.
5. isAlreadyInChatWith scanned 300px and matched chat-list rows -> narrowed to the 250px toolbar band.
6. typeInBottomEditText picked the search box -> now skips search fields and requires the composer in the bottom half of the screen.
7. Added a post-tap safety check: if the opened chat title does not match the contact, abort before typing (prevents sending to the wrong chat/group).

Verified on device: send_message to "أحمد التميمي خاص" via WhatsApp Business succeeded; message "رسالة اختبار من سند — يرجى تجاهلها" delivered (double checkmarks). SafetyGate confirmation appeared and was approved by the user. No message was sent to the wrong chat.

Final signed release: app/build/outputs/apk/release/SanadClaw_v1.0.3-dev-5_20261113_171335.apk (102 MB), SHA-256 B189F9D718E7EB7AC0B04E7C5FE781017636BDFB04C7D258B0CD59F701EEC10A. Tests: 312 / 56 / 0 failures.

Note: implicit `am broadcast` to DebugTaskReceiver is blocked on this device; use an explicit component (`-n io.agents.pokeclaw/.debug.DebugTaskReceiver`).

### Release 1.0.3-dev-6 (2026-11-13)
- Fixed UpdateChecker.isNewer: strips -dev/-rc/-beta suffixes before parsing; stable beats prerelease of the same base; latest tag keeps its prerelease suffix. Added 6 unit tests (UpdateCheckerVersionTest).
- Version bumped to 1.0.3-dev-6 (code 107).
- Signed release: app/build/outputs/apk/release/SanadClaw_v1.0.3-dev-6_20261113_173020.apk (102 MB), SHA-256 CD9A0B82FC9419ECBBCE9394CE86E5C4FF4D9B35B1E1994551F945B97BD3A0CD.
- Tests: 318 / 57 classes / 0 failures.
- Latest GitHub release before this: v1.0.2; the in-app check correctly reports "no update" for 1.0.3-dev-5 because 1.0.2 < 1.0.3.

### Final cleanup + publish (2026-11-13)
- CI root cause fixed: android-actions/setup-android@v3 -> @v4 + pinned packages platform-tools platforms;android-36 build-tools;36.0.0. SDK setup now passes; the only remaining CI blocker is the four signing secrets (ANDROID_KEYSTORE_B64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD).
- Release published manually via the GitHub API because the Actions secrets are not configured: tag v1.0.3-dev-6 (prerelease), asset SanadClaw_v1.0.3-dev-6_20261113_173020.apk (102 MB, SHA-256 CD9A0B82FC9419ECBBCE9394CE86E5C4FF4D9B35B1E1994551F945B97BD3A0CD).
- Signing material kept in keys/ (gitignored): sanad-release.jks, KEYSTORE_INFO.txt, GITHUB_SECRETS.txt (copy-paste values for the four Actions secrets).
- Cleanup: gradlew clean, .gradle cache, build/ and temp capture files removed. Keystore and secrets file intentionally kept. Device-side exported action log: pending (device disconnected during cleanup).
### Update channel + release 1.0.3-dev-7 (2026-11-13)
- Added a user-selectable update channel in Settings -> About: Stable or Beta (KVUtils KEY_UPDATE_CHANNEL, defaults to Beta on dev/rc builds).
- UpdateChecker: Stable reads /releases/latest; Beta reads /releases and picks the newest including pre-releases. This fixes "no update" on dev builds, which happened because /releases/latest excludes pre-releases.
- Verified on device: Beta -> Latest 1.0.3-dev-6; Stable -> Latest 1.0.2; toggle persists.
- Version bumped to 1.0.3-dev-7 (code 108).
### UX round (2026-11-13, pre-dev-7)
- Default UI language switched to English (AppLocale). Arabic is remembered until the next update; on versionCode change `applyUpdateLanguageReset` resets to English before the locale is applied.
- Chat toolbar: replaced the settings icon with a sun/moon theme toggle and an EN/ع language toggle (Settings remains in the sidebar). Both restart the app to apply.
- Voice dictation patience: complete 3.0s / possibly 2.0s / minimum 1.5s; hard cap 120s.
- Light themes: text colors now derive from each palette (no hardcoded warm brown on blue/green/neutral).
- Models page: selected provider tab and model radios use the active theme accent instead of the generic purple.
- Verified on device: theme toggle dark<->light, language toggle EN<->AR (RTL), Models light mode readable with orange accent.
### Chat/Agent UX + attachments (2026-11-13)
- Chat/Agent segmented control is now shown for every model (cloud included); active tab uses the accent color, inactive uses a border. Agent mode uses a theme-aware background in light themes.
- Removed the send FAB. Sending is via Enter / IME Send (`KeyboardOptions(imeAction = Send)` + `onPreviewKeyEvent`).
- Attach button opens the system document picker (OpenDocument, "*/*"), copies the file to cache/attachments, and shows a removable chip. Text files are inlined (up to 4000 chars); images are referenced by path.
- Verified on device: Enter sends in Chat mode ("what is 2 plys 2" -> "2 plus 2 is 4."), attach opens DocumentsUI, Chat/Agent colors are clear in light mode.
- Note: GetContent("*/*") could resolve to WhatsApp once; switched to OpenDocument.
### Update check + light status bar + composer polish (2026-11-13)
- Fixed `UpdateChecker.isNewer`: pre-release numbers are compared when bases are equal (dev-9 > dev-8 > dev-7). This was why a dev-7 build saw "no update" while dev-8 was published.
- Update dialog localized (en/ar) with Update / Ignore this version / Later; ignored versions are suppressed on automatic checks.
- Light-mode status bar icons: `ThemeManager.applySystemBarIcons` sets `isAppearanceLightStatusBars` from THEME_ID (not system night mode); called from BaseActivity and ComposeChatActivity. Verified: time/battery/signal are dark on the light bar.
- Composer placeholder removed so switching Chat/Agent does not resize the box.
- Settings -> Appearance now only has Text size; Theme and Language live in the chat toolbar.
- Gradle metaspace raised to 1024m (release builds were dying with "out of JVM Metaspace").
- Version 1.0.3-dev-9 (code 110); 321 tests / 0 failures.
### Final pass (2026-11-13, dev-9)
- In-app updater: the Update button downloads the release APK to externalFilesDir/Download and launches the system installer via FileProvider; falls back to the release page. Dialog buttons use the theme accent.
- Composer shows a fixed "Ask Sanad" / "اسأل سند" hint (no mode-dependent placeholder).
- Settings -> Appearance only has Text size.
- New launcher icon generated from Desktop/Sanad.png at mdpi..xxxhdpi (square + round) + 512px adaptive foreground.
- 321 tests / 0 failures. Release dev-9 with all fixes.
- Remaining for tomorrow: select-all/archive for conversations, Memory section, Skills polish, manual safety QA (IJ/SD/QV/QP3-8), GitHub Actions signing secrets.
## Release 1.0.3-dev-10 (2026-11-13)

- Icon: removed the baked white/light frame from every rendition (mipmap mdpi..xxxhdpi square+round, legacy mipmap/, drawable-nodpi 512 adaptive foreground, drawable pokeclaw_avatar + pokeclaw_icon_small). Honor's launcher was rendering it as a white edge on four sides. Method: flood the neutral bright border region to black; for images whose plate is inset in black, darken neutral pixels within R of the dark outside, capped to the outer 16% so gold highlights are untouched. Scripts lived in %TEMP%\kilo (not committed).
- Icon palette: gold hue remapped into the app accent family (#E8845A / #D45A30): hue * 0.50, saturation * 0.85, value preserved (shading/highlights intact).
- Empty chat state: removed the Cloud AI/Local AI header and the hint line under "Sanad Claw" in ChatScreen.EmptyStateWithPrompts; logo + "Sanad Claw" remain. header_cloud_ai/header_local_ai strings are now unused (kept).
- Version 1.0.3-dev-10 (code 111). Signed release APK app/build/outputs/apk/release/SanadClaw_v1.0.3-dev-10_20261113_205220.apk (107 MB), SHA-256 9C91B881710D9385DBE468AA660897DB49A36ECE31558EEF664754F48EFC74AD, aapt2-verified versionCode 111 / versionName 1.0.3-dev-10. Signing config now enables v2+v3 (apksigner verifies v3; minSdk 28, so v3 is sufficient). Published as prerelease v1.0.3-dev-10 with the APK asset.
- Unit tests pass. Device (HONOR MBH-N49) was disconnected after the build; on-device verification of the new empty state and launcher icon is pending.

## Sidebar + Memory/Skills + safety QA (2026-11-14)

Status: ~85%. Sidebar/memory/skills work is done and device-verified; most manual safety QA is done. Stable-release blockers are now only voice QA (QV1-9) and two QA cases that cannot be executed with available artifacts (QP4 unreachable category, QP7/8 missing legacy build).

### Sidebar: select-all / multi-select + Archive
- `SidebarContent` now supports a Select mode: checkboxes, "Select all"/"Clear selection", selected count, bulk Archive and bulk Delete (confirmation shows the count).
- Archived conversations are a collapsible "Archived (N)" section with unarchive and delete actions. Data layer reused `ConversationStore.archiveConversation/unarchiveConversation/refreshArchived` unchanged.
- Archiving/deleting the currently open conversation now starts a fresh id via `ConversationStore.beginFreshConversation()` without re-saving the old file (previously any reset would recreate the deleted/archived chat).
- Bulk callbacks wired in `ComposeChatActivity` (`onDeleteConversations`, `onArchiveConversations`, `onUnarchiveConversation`) and `_archived` state is refreshed by `refreshSidebarHistory()`.
- Device verified: Select → Select all → Archive → Archived (2) → expand → Unarchive one → Recent contains it, Archive count 1.

### Sidebar: Memory + Skills
- New `memory/MemoryStore`: up to 50 entries, 500 chars each, exact-duplicate skip, JSON in MMKV. Entries are appended to the system prompt by `PromptUtils.applyGlobalPrompt` after LanguagePolicy and before the user's global instructions. UI is an AlertDialog from the sidebar (add/list/delete + warning not to store secrets).
- Crash found on device: release R8 minified Gson field names to `a/b/c`, so the debug build (installed over release) read nulls and `normalizeText(null)` crashed every LLM client creation. Fixed with nullable `Stored` fields + `@SerializedName(..., alternate=["a","b","c"])`, so both formats load. Unit tests cover null/malformed/legacy JSON.
- Memory injection verified end-to-end on device: saved "My name is Ahmed", asked "what is my name" on the qwen cloud model → "Your name is Ahmed".
- Skills panel was always empty: `BuiltInSkills` never set `userFacing = true` (default false). Marked all nine built-ins userFacing with a `userExample`; added `SkillRegistryUserFacingTest`. Sidebar Skills dialog now lists them; selecting one prefills the composer in Agent mode.

### Safety fixes found by manual QA
1. P0 data leak (SD2): the pre-warm screen attached on the first agent LLM call bypassed `SensitiveDataRedactor`. On the hostile page the cloud model received and repeated the exact card, IBAN, national ID and OTP. Fixed by redacting the wrapped pre-warm screen before building the prompt; log: `runAgentLoop: pre-warm screen redacted kinds=[CARD, IBAN, NATIONAL_ID, OTP]`. Retest: model only sees `[REDACTED:*]`.
2. `finish` false positive: `finish.summary` was classified as payment/delete when the page text mentioned "transfer"/"delete", blocking task completion on tainted pages. `RiskClassifier` now exempts `finish`; unit test added.
3. Pause-and-ask deadlock (SD4/SD5): `sendCurrentMessage()` refused to send while a task was running, so the user could never supply the paused value. The composer now allows sending when `PausedTaskState.phase() == PAUSED`. Verified: pause → reply accepted → `resumed: value received` → task completes; pause path logs no value. `finish` is excluded from pausing (a placeholder in a summary should not ask for input).
4. Added traceability logs: pre-warm redaction kinds, tainted non-sensitive warning, system-prompt untrusted-rule presence.

### Manual QA executed on HONOR (debug APK re-signed with the release key so it installs over dev-10 and keeps data)
- PASS: IJ1, IJ2, IJ3, IJ4, IJ5, IJ6; SD1, SD2 (after fix), SD3, SD4, SD5; QP2, QP3, QP5, QP6.
- PARTIAL: QP1/QP1a (English rows + bilingual state text; Arabic RTL and TalkBack not exercised).
- QP4 removed 2026-11-14 (stable 1.0.3): no current tool or parameter path returns `RiskKind.UNKNOWN`, so the case is unreachable and cannot be executed on device. Decision: keep the fail-safe rule in code (`SafetyGate`/`SafetySettings` treat null/UNKNOWN as "confirmation required", covered by unit tests) and drop the manual case from `QA_CHECKLIST.md`; no unknown-risk tool path was introduced because that would be a feature change outside the stable-release scope.
- NOT RUN: QP7/QP8 — every published release already contains the migration code; no single-toggle artifact exists. Unit tests cover migration.
- NOT EXECUTED: QV1-QV9 — device has recognizers but automation cannot speak into the mic. Needs a person; this is the last stable-release blocker.
- Evidence lives in QA_CHECKLIST.md rows.

### GitHub Actions
- The four signing secrets are now set on `Altamimi79/Sanad-Claw` via `gh secret set`: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Values were read from the gitignored `keys/GITHUB_SECRETS.txt` and never printed. Release workflow should now sign automatically.

### Build/verification
- 335 unit tests / 59 classes / 0 failures (`testDebugUnitTest`); `lintVitalRelease` passes.
- Device QA used a debug APK re-signed with the release keystore; the QA build was replaced by the final release build after restoring the seven switches.

### Final artifact after fixes (2026-11-14)
- `app/build/outputs/apk/release/SanadClaw_v1.0.3-dev-10_20261114_072413.apk`, SHA-256 `993D9DDF8890B84A6D51B3DAA57998C33076087A35BB6291C40676ED182EB243`.
- Installed over the QA debug build with `adb install -r` (same release certificate, app data preserved). Verified on device: app launches, sidebar shows Archive/Memory/Skills, saved memory "My name is Ahmed" survives, all seven safety switches restored to Enabled.### Final dev-11 artifact (2026-11-14)
- `app/build/outputs/apk/release/SanadClaw_v1.0.3-dev-11_20261114_074655.apk`, SHA-256 `5FBE49C3A44A3438F2ABC83766C6755C7F35512F08083360D74A2C58711961BC`.
- Version 1.0.3-dev-11 (code 112). Installed over dev-10 with the same release certificate; app data preserved.
- Device verified: sidebar Archive/Memory/Skills, saved memory survived, seven safety switches Enabled, in-app orange send button sends, Chat/Agent tabs themed, Models Save & Activate / Fetch are orange (night brand palette fixed).
- 335 tests / 59 classes / 0 failures; `lintVitalRelease` passes.

## Stable v1.0.3 published + 1.0.4 dev cycle (2026-11-14)

### Release v1.0.3 (stable, DONE)
- Branch `release/1.0.3` → PR #1 merged into `main` (merge commit `3a9f90e8`), tag **v1.0.3** pushed. GitHub Release: https://github.com/Altamimi79/Sanad-Claw/releases/tag/v1.0.3 — prerelease=false, assets: `SanadClaw_v1.0.3_20261114_082303.apk` (CI-built, SHA-256 `67a65d4f78cb10f6a051f91ad9070a83209892870bdc15d7a886835c7f1296f4`) + SHA256SUMS.txt. Release workflow run 43575477343 green; Firebase Test Lab self-skipped green via the guard job (GCP secrets still absent).
- Local verified build: `SanadClaw_v1.0.3_20261114_114819.apk`, SHA-256 `2CEDA98A612F8DD839751F161F0586C2F8A7E33BB9BC59985C5F5BDB91DA2C7C`. CI APK re-downloaded, hash-matched, installed on HONOR — data intact.
- One ANR during device QA (input-dispatch timeout under system Load ~23; HONOR killed the process despite "Wait" and auto-restored WhatsApp Business to the foreground — no chat opened, nothing sent). Re-tested send/reply after restart: clean. Environmental, same code as dev-11 which passed.
- cla-check (upstream CLA Assistant) fails on PRs: action crashes reading signatures from agents-io/PokeClaw (inaccessible from this fork). Non-required; fix later in a separate PR (point path-to-signatures at this repo or disable the workflow).

### Branch `ui-cleanup-1.0.4` (PR open against main, NOT merged — version still 113/1.0.3 by design)
Commits: `8f63d77` fix(ui) · `2eae248` feat(i18n) Arabic-first default + no reset on update + tests + docs · `6ebedd3` fix(i18n) explicit-choice flag migration.
- **UI cleanup:** `ic_arrow_right`/`ic_back` get `android:autoMirrored="true"`; dead drawables `bg_bubble_user.xml`/`bg_bubble_assistant.xml` deleted (zero refs repo-wide — the live Compose chat already uses RTL-correct start/end RoundedCornerShape; do NOT "fix" those XML files, they were dead); tertiary text alpha raised (`values-night #52→#8AFFFFFF`, `values #61→#99000000`) for WCAG AA on Views pages. Compose chat tertiary (aiText@60%) was already fine — the reviewer table's "#52FFFFFF on #0C111B" applies to Views resources only.
- **Language model now:** Arabic default; a saved "en" is honored ONLY when `KEY_APP_LANGUAGE_CHOSEN` is set (written by `setLanguage` in the same commit()). Legacy implicit "en" from the old update-reset is ignored → those users move to Arabic once (**accepted trade-off**: cannot be distinguished from an explicit choice made on old builds). `applyUpdateLanguageReset` never writes KEY_LANGUAGE; pure functions `resolveLanguage(stored, chosen)` + `shouldRecordVersionOnly(last,current)` are top-level and unit-tested (no Robolectric in this project — do not add it casually).
- **Tests:** 347 / 60 classes / 0 failures (`AppLocaleLogicTest` = 12). Old-behavior proof: copied the new test into a throwaway worktree at `8f63d77` with a semantics shim replicating old decisions; 4/12 failed exactly on `expected:<[ar]> but was:<[en]>` (incl. the legacy-implicit case) — the suite genuinely catches the migration bug. Worktree removed afterwards.
- **Device QA on HONOR (final build `SanadClaw_v1.0.3_20261114_131223.apk`, SHA-256 `EFFE4DF9802909F230F7AAC6C1886FE2C0639CC422BE998933FF30A4E98A7162`, installed -r over official 1.0.3, data preserved):** LN4 PASS — the device really carried implicit `en` from 1.0.3 and flipped to Arabic RTL on upgrade; QP9 PASS — chevrons/back arrow mirrored in Arabic, night-mode meta text readable (system night toggled via `cmd uimode night`); explicit EN then AR choices persist across force-stop/cold restart; final device state: explicit Arabic. LN1 skipped deliberately (`pm clear` would destroy QA data; same path covered by LN4 + unit tests). LN2/LN3 PARTIAL — cross-version persistence re-testable only after the versionCode bump.
- **Next for 1.0.4:** merge decision, then version bump (114 / e.g. `1.0.4-dev-12`) in a release task; re-run LN2/LN3 legs on that update; later: cla-check fix, color-token unification (Views vs ThemeManager duplication is real), Arabic UI font decision.
