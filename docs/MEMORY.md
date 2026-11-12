# Sanad-Agent — Project Memory

Persistent project memory. Read first, update at the end of every phase (see AGENTS.md).

Last updated: 2026-11-12 (fork branding/UI round on HONOR device: committed + verified; GitHub push still pending)

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

1. Push target unresolved: `origin` = upstream `agents-io/PokeClaw`; push attempts return `remote: Permission to agents-io/PokeClaw.git denied to Altamimi79.` (403). GitHub account has no fork yet. When the fork exists (create at https://github.com/agents-io/PokeClaw → Fork), run:
   `git remote rename origin upstream` then `git remote add origin https://github.com/Altamimi79/<fork-name>.git` then `git push -u origin phase-1-setup` and `git push -u origin phase-2-arabic-chat`, and open PRs into `main`.
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
14. v1.0.0 is **not published**: tag exists locally only; `origin` is upstream (403) and `gh` is not installed. Create the fork, push `main` + `v1.0.0`, then `gh release create`.
15. v1.0.0 is **device-unverified**: no adb device; QA_CHECKLIST FA/VA/SA and the release smoke gate were not executed.

## Next Ideas / Roadmap

1. **Device QA first**: run FA/VA/SA on a real phone (Pixel/Samsung + a low-RAM device), record results, then gate the tag on green.
2. **Session/Action log UX**: group action-log entries per task, add filters, export share-sheet (still local-only, redacted).
3. **Prompt-injection hardening**: extend `DirectDeviceDataGuard` to treat screen/notification text as untrusted for risky tool arguments; require re-confirmation when arguments change after screen content.
4. **External automation security**: signature permission or pairing allowlist + rate limit, then relax the default-off requirement.
5. **Security follow-ups**: narrow cleartext with an explicit per-provider “allow http” toggle; redact AppLogStore; replace deprecated security-crypto (Tink direct / Keystore file).
6. **Voice**: streaming partial UX polish, per-device offline pack detection, optional Whisper via a pluggable STT interface once a device farm exists.
7. **Models**: download resume/corruption checks; per-device RAM guidance; DeepSeek reasoner streaming nuances.
8. **Chat**: de-god `ChatScreen.kt` into a ViewModel; message search; image attachments (currently unsupported).
9. **Release**: produce a signed release with a fork keystore; decide whether to keep package id `io.agents.pokeclaw` for channel compatibility.
