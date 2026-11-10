# Sanad-Agent — Project Memory

Persistent project memory. Read first, update at the end of every phase (see AGENTS.md).

Last updated: 2026-11-10 (Phase 1 close-out)

## Project Identity

- Fork of **agents-io/PokeClaw** (Apache-2.0), cloned from upstream `main` @ commit `936eca3` ("Add encrypted .env secrets (SOPS + age)"), app version v0.7.1 / versionCode 29.
- Working copy: `E:\Sanad-Agent`. Upstream remote `origin` = https://github.com/agents-io/PokeClaw.git (no personal fork repo existed on GitHub at clone time — see Known Issues).
- Must keep: `LICENSE` (Apache-2.0 full text), `NOTICE` (agents.io attribution), per-file Apache headers, CLA machinery. README Trademark section applies to naming decisions.

## Current Status

| Phase | Status |
|---|---|
| P1 Setup/audit | **DONE 2026-11-10** (branch `phase-1-setup`) |
| P2 Arabic + chat + provider settings | Not started |
| P3 Voice input | Not started |
| P4 Agent safety/security/stability | Not started |
| P5 Final build/tests/release docs | Not started |

## What's Done (Phase 1)

- Repo cloned, full read-through; license & notices verified (Apache-2.0 + NOTICE + CLA + `.env.enc` is SOPS-encrypted and stays untouched).
- Created: `AGENTS.md`, `docs/MEMORY.md`, `docs/PLAN.md`, `docs/AUDIT.md`, `docs/CHANGELOG.md`, `docs/skills/build-apk.md`, `docs/skills/add-llm-provider.md`, `docs/skills/add-agent-tool.md`.
- `.gitignore` hardened: added `*.keystore`, `*.p12`, `*.pem`, `keys/`, `secrets/`, `signing/`, `keystores/`, `.env.local`, `.env.*.local`, `**/build/`, `**/.gradle/`, `app/release/`, `*.apk`, `*.aab`; explicit `!.env.enc` exception for upstream's SOPS file. Existing rules (`.env`, `local.properties`, `*.jks`) kept.
- Fixed Gradle wrapper: upstream pointed at `mirrors.cloud.tencent.com` (10s timeout outside China) → switched to official `https://services.gradle.org/distributions/gradle-9.3.1-all.zip`, networkTimeout 120000.
- Build verified: `.\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL in 2m42s** (2026-11-10), APK `app\build\outputs\apk\debug\PokeClaw_v0.7.1_20261110_134716.apk`. Only warnings (deprecations, native lib strip notes).

## Build Environment (Windows machine — how it was made to work)

- `local.properties` (gitignored): `sdk.dir=C:\Users\Altamimi\AppData\Local\Android\Sdk`.
- Android SDK had only platform-tools; installed **cmdline-tools** (commandlinetools-win-15859902_latest.zip → `Sdk\cmdline-tools\latest`) and ran `sdkmanager --licenses` (all accepted). Gradle then auto-installed Build-Tools 36.0.0 + Platform 36.1 on first successful build.
- Java: system default JDK 17; project needs **JDK 21 toolchain** (foojay auto-download failed on extract/move). Fix: extracted the already-downloaded Temurin 21 zip to `~\.gradle\jdks-manual\jdk-21.0.7+6` and set in **user-level** `~\.gradle\gradle.properties` (not committed):
  `org.gradle.java.installations.paths=C:/Users/Altamimi/.gradle/jdks-manual/jdk-21.0.7+6` + `org.gradle.java.installations.auto-download=false`.
- Build commands: see AGENTS.md "Build Commands".

## Key Architecture Facts (details in docs/AUDIT.md)

- Single module `:app`; agent loop = `DefaultAgentService.runAgentLoop` (while iterations < maxIterations, StuckDetector + TaskBudget/TokenMonitor + guards), tools via `ToolRegistry` (35 tools), skills as markdown (`docs/skill-spec.md`).
- Local LLM: LiteRT-LM 0.10.0, Gemma 4 E2B/E4B `.litertlm` from HuggingFace `litert-community`; GPU→CPU fallback tracked by `LocalBackendHealth`.
- Cloud: LangChain4j OpenAI + Anthropic + custom OpenAI-compatible base URLs; catalog/pricing in `agent/CloudProvider.kt`.
- State/settings: MMKV via `KVUtils` (API keys plaintext — P4 target). UI: Compose chat (`ChatScreen.kt`) + Views settings. Channels: Telegram/Discord/WeChat. External automation intent API (user-toggle, default off).

## Known Issues / Risks (carried into later phases)

1. Push target unresolved: `origin` = upstream `agents-io/PokeClaw`; push of `phase-1-setup` returned `remote: Permission to agents-io/PokeClaw.git denied to Altamimi79.` (403). GitHub account has no fork yet. When the fork exists (create at https://github.com/agents-io/PokeClaw → Fork), run:
   `git remote rename origin upstream` then `git remote add origin https://github.com/Altamimi79/<fork-name>.git` then `git push -u origin phase-1-setup` and open PR `phase-1-setup → main`.
2. Upstream signing key lost historically → upgrade gap requires one-time uninstall/reinstall (BACKLOG P1). Affects P5 release story.
3. Security debt for P4: plaintext API keys in MMKV, unauthenticated loopback ConfigServer HTTP, cleartext permitted globally, exported RUN_TASK components without sender verification, prompt-injection surface from screen/notification content (guards exist: DirectDeviceDataGuard etc.).
4. God classes: `ChatScreen.kt` 2385L, `AutoReplyManager.java` 1060L, `DefaultAgentService.kt` 778L, `SettingsActivity` 773L.
5. Local model RAM floor (E2B/E4B heavy for low-RAM devices); MediaTek/Samsung engine failures open upstream (BACKLOG P1).
6. `.env.enc` age-encrypted to upstream recipient — fork cannot rotate those secrets; P4 needs a fork-local secret story if cloud features are used in CI.
7. JITPack dependency (EasyFloat) + Chinese-mirror history = supply-chain watch item.

## Next Up (Phase 2 preview)

Arabic support groundwork: RTL (`supportsRtl` already true), strings.xml localization (currently mostly English + scattered Chinese comments), Arabic-aware UI matching utils (`UiActionMatchUtils`, `ContactListUiUtils` — structure-first per BACKLOG P1), chat UX polish, provider settings improvements in `LlmConfigActivity`/`CloudProvider.kt`. Plan details: docs/PLAN.md.
