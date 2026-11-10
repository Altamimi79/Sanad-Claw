# Sanad-Agent 5-Phase Plan

Rules per phase: branch `phase-N-…`, QA-first (AGENTS.md), update `docs/MEMORY.md` + `docs/CHANGELOG.md` at end, commit/push, PR into `main`.

## P1 — Setup / Audit ✅ DONE 2026-11-10

- [x] Read whole repo; verify Apache-2.0 license, NOTICE, CLA, `.env.enc` handling
- [x] Create `AGENTS.md` (rules, build commands, conventions, MEMORY protocol)
- [x] Create `docs/MEMORY.md`, `docs/PLAN.md`, `docs/AUDIT.md`, `docs/CHANGELOG.md`, `docs/skills/` (build-apk, add-llm-provider, add-agent-tool)
- [x] Harden `.gitignore` (keys/local.properties/*.keystore/build outputs; keep `.env.enc` exception)
- [x] Fix Gradle wrapper mirror (Tencent → services.gradle.org); fix JDK 21 toolchain locally; accept SDK licenses
- [x] Verify build: `gradlew.bat assembleDebug` → BUILD SUCCESSFUL (v0.7.1 debug APK)
- [x] Branch `phase-1-setup`, commit, push/PR toward `main` (see CHANGELOG for remote status)

## P2 — Arabic + Chat + Provider Settings — CODE COMPLETE 2026-11-10 (device QA pending)

- [x] Inventory user-facing strings; hardcoded literals moved/localized; `values-ar` full Arabic translations added
- [~] RTL verification: works via existing `supportsRtl` + Arabic locale switching; remaining AutoMirrored icon swaps tracked as a flag in docs/MEMORY.md (upstream deprecation warnings visible in build)
- [ ] Arabic-aware text matching audit (`UiActionMatchUtils`, `ContactListUiUtils`, `UiTextMatchUtils`) — deferred (structure-first matching backlog; `UiTextMatchUtilsTest` exists)
- [~] Chat improvements: streaming replies, Markdown bubbles, localized error SYSTEM bubbles, loading states done; session sidebar polish / keyboard dismissal / long model-name layout fixes still open (BACKLOG P2)
- [x] Provider settings: DeepSeek added; per-provider editable Base URL; real test-connection (`CloudConnectionTester`); Claude/OpenAI/DeepSeek key+model+URL config
- [x] QA: E2E cases FA1–FA13 added to QA_CHECKLIST.md — **execution blocked** until an Android device/emulator is connected via adb
- [x] Gulf dialect language policy (`LanguagePolicy` wired into all system prompts)
- [x] Encrypted API-key/token storage (`SecureStore`, early P4 item) + ConfigServer secret masking
- [x] Unit tests green (61 tests); `assembleDebug` green

## P3 — Voice Input — CODE COMPLETE 2026-11-10 (device QA pending)

- [x] Mic button in chat composer (tap-to-start, tap-to-stop; no wake-word)
- [x] On-device Arabic STT via Android `SpeechRecognizer` with **ar-SA**, partial results, silence tuning, 60s cap + partial-transcript fallback
- [x] Permission flow: `RECORD_AUDIO` in manifest + `<queries>` for RecognitionService; runtime request with Arabic denial/unavailable messages
- [x] Transcript → composer (editable, appended); auto-send setting (default off) at Settings → Tools
- [x] Recording UI: pulsing animation (RMS-driven), mm:ss timer, live partials, cancel
- [x] Error taxonomy (no-match/timeout/network/permission/busy/audio/server/client/language/unknown) with Arabic messages
- [~] Optional on-device Whisper: **skipped deliberately** (LiteRT-LM has no audio encoder; model size/RAM + unverifiable on device) — rationale in `docs/skills/voice-input.md`
- [x] Unit tests for all non-UI logic (5 classes, 30 tests)
- [x] QA: E2E section VA1–VA10 added — execution blocked (no device)

## P4 — Agent Safety / Security / Stability — CODE COMPLETE 2026-11-10 (device QA pending)

- [x] API keys → SecureStore (done in P2); ConfigServer GET masks secrets (P2); ConfigServer now requires a per-process token on every `/api/*` request
- [~] ConfigServer: loopback-only binding verified + token auth added; `cleartextTrafficPermitted=true` still global (needed for LAN/local LLM) — documented in SECURITY.md S3
- [~] External automation: user toggle + targeted broadcast + base64 contract; sender verification/rate limiting deferred to P5 (SECURITY.md S4)
- [x] Risky-action confirmation gate (send/file/call/payment/delete/install/publish) with fail-safe background denial
- [x] Banking/password app blocklist (defaults + configurable CSV), enforced before every tool
- [x] Action log screen (local, redacted, clearable) + visible emergency stop (banner/notification/floating pill) + configurable task timeout
- [x] Bounded retry for transient failures; Arabic error messages for timeout/denial/blocked app
- [x] Battery-conscious foreground polling (adaptive 5/15/30 s)
- [x] Security review documented in `docs/SECURITY.md` (S1–S12, fixes + residuals)
- [x] Unit tests for safety logic (6 new classes; 127 total green)
- [ ] Full on-device QA pass (blocked: no device)

## P5 — Final Build / Tests / Release Docs

- [ ] Green `assembleRelease` with fork keystore story (documented, gitignored; historical lost-key caveat documented for users)
- [ ] Unit test expansion (agent loop edge cases, guards, parsers) + keep ADB E2E gates in QA_CHECKLIST.md
- [ ] Fork release docs: RELEASING fork variant, CHANGELOG release entry, versioning scheme decision (keep upstream base + fork suffix)
- [ ] Final audit delta vs docs/AUDIT.md; tag `v<fork-version>`
