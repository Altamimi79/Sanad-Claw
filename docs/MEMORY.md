# Sanad-Agent — Project Memory

Persistent project memory. Read first, update at the end of every phase (see AGENTS.md).

Last updated: 2026-11-10 (Phase 3 code-complete; device QA pending)

## Project Identity

- Fork of **agents-io/PokeClaw** (Apache-2.0), cloned from upstream `main` @ commit `936eca3` ("Add encrypted .env secrets (SOPS + age)"), app version v0.7.1 / versionCode 29.
- Working copy: `E:\Sanad-Agent`. Upstream remote `origin` = https://github.com/agents-io/PokeClaw.git (no personal fork repo existed on GitHub at clone time — see Known Issues).
- Must keep: `LICENSE` (Apache-2.0 full text), `NOTICE` (agents.io attribution), per-file Apache headers, CLA machinery. README Trademark section applies to naming decisions.

## Current Status

| Phase | Status |
|---|---|
| P1 Setup/audit | **DONE 2026-11-10** (branch `phase-1-setup`, merged into local `main` @ `187a065`) |
| P2 Arabic + chat + provider settings | **CODE COMPLETE 2026-11-10** on branch `phase-2-arabic-chat` (7 commits, build+unit tests green). On-device E2E QA pending — no adb device connected yet. |
| P3 Voice input | **CODE COMPLETE 2026-11-10** on branch `phase-3-voice` (3 commits, build + 101 unit tests green). Device QA pending. |
| P4 Agent safety/security/stability | Not started (note: secrets-encryption item already delivered early in P2) |
| P5 Final build/tests/release docs | Not started |

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

## Next Up

- **Phase 2/3 close-out**: connect a device/emulator, execute QA_CHECKLIST §FA (FA1–FA13) and §VA (VA1–VA10), record results; push `phase-1-setup`, `phase-2-arabic-chat`, `phase-3-voice` once the GitHub fork exists (commands in Known Issue 1).
- **Phase 4 preview**: ConfigServer auth/restrict-to-debug, external-automation sender verification + rate limiting, prompt-injection hardening, stability fixes (GPU fallback logging, download resume, accessibility rebind), optional ChatScreen de-godding; revisit SecureStore deprecation and Whisper decision. Plan details: docs/PLAN.md.
