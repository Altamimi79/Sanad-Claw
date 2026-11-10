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

## P2 — Arabic + Chat + Provider Settings

- [ ] Inventory all user-facing strings; move hardcoded strings to `strings.xml`; add `values-ar` with full Arabic translations
- [ ] RTL verification pass on chat/settings/guide screens (Compose + Views), mirror icons where needed (AutoMirrored)
- [ ] Arabic-aware text matching: audit `UiActionMatchUtils.java`, `ContactListUiUtils.java`, `UiTextMatchUtils` for Arabic diacritics/normalization (structure-first per BACKLOG "Structure-first UI matching")
- [ ] Chat improvements: session sidebar polish, keyboard dismissal (BACKLOG P2), long model-name layout fix (BACKLOG P2)
- [ ] Provider settings: improve `LlmConfigActivity` (provider tabs, custom base URL flow, test-connection UX), extend `CloudProvider.kt` catalog as needed for Arabic-region-friendly providers
- [ ] QA: add E2E cases to QA_CHECKLIST.md (Arabic locale switch, RTL chat, provider save/test)

## P3 — Voice Input

- [ ] Mic button in chat composer (push-to-talk first; no wake-word per upstream guidance)
- [ ] Cloud STT path when API key configured (pick provider consistent with LangChain4j stack); record transcript → chat input
- [ ] Permission flow: RECORD_AUDIO request + error UX (note: RECORD_AUDIO not yet in manifest — add deliberately)
- [ ] Optional local STT spike (on-device Whisper/LiteRT option) — evaluate, gate on device RAM
- [ ] QA: voice input E2E section in QA_CHECKLIST.md

## P4 — Agent Safety / Security / Stability

- [ ] API keys → EncryptedSharedPreferences or MMKV encryption layer; stop serving raw keys via ConfigServer GET (mask everywhere)
- [ ] ConfigServer: add local auth token or restrict to debug builds; revisit `cleartextTrafficPermitted=true`
- [ ] External automation: sender verification (signature/permission or explicit pairing), rate limiting, task review UX before execution
- [ ] Prompt-injection hardening: strengthen guard set for screen/notification/web content (extend DirectDeviceDataGuard pattern)
- [ ] Stability: address top BACKLOG P1 items reachable without upstream device farm (GPU fallback logging truth, download resume/corruption checks, accessibility rebind grace)
- [ ] De-god `ChatScreen.kt` / extract ChatViewModel (BACKLOG P3) if time allows
- [ ] QA: full checklist pass

## P5 — Final Build / Tests / Release Docs

- [ ] Green `assembleRelease` with fork keystore story (documented, gitignored; historical lost-key caveat documented for users)
- [ ] Unit test expansion (agent loop edge cases, guards, parsers) + keep ADB E2E gates in QA_CHECKLIST.md
- [ ] Fork release docs: RELEASING fork variant, CHANGELOG release entry, versioning scheme decision (keep upstream base + fork suffix)
- [ ] Final audit delta vs docs/AUDIT.md; tag `v<fork-version>`
