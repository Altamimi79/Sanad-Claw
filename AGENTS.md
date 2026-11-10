# Sanad-Agent (PokeClaw fork) — Agent Rules

Fork of [agents-io/PokeClaw](https://github.com/agents-io/PokeClaw) — an open-source Android on-device AI phone agent (Kotlin + Java, Jetpack Compose, LiteRT-LM local models + optional cloud LLMs). Licensed under Apache-2.0 (see `LICENSE`, `NOTICE`).

## MEMORY PROTOCOL (MANDATORY)

**Always read `docs/MEMORY.md` first and update it at the end of every phase.** It is the persistent project memory: decisions, current status, what's done, what's next, known issues, build commands. Never rely on session memory for project state.

Also consult the upstream canonical docs before working: `CLAUDE.md` (working rules), `AI_INDEX.md` (repo map), `QA_CHECKLIST.md`, `BACKLOG.md`, `docs/PLAN.md` (5-phase plan), `docs/AUDIT.md` (architecture audit).

## Build Commands

Windows (this machine):
```powershell
.\gradlew.bat assembleDebug          # debug APK -> app\build\outputs\apk\debug\PokeClaw_v<ver>_<ts>.apk
.\gradlew.bat assembleRelease        # release (needs signing keys in local.properties, see RELEASING.md)
.\gradlew.bat test                   # JVM unit tests (app/src/test)
```
macOS/Linux: `./gradlew assembleDebug`.

Environment already configured on this machine (see docs/MEMORY.md for details):
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (set in `local.properties`, gitignored). Licenses accepted via `sdkmanager --licenses`.
- Java toolchain: Gradle needs JDK 21. System default is JDK 17. JDK 21 (Temurin) lives at `~\.gradle\jdks-manual\jdk-21.0.7+6`, wired through user-level `~\.gradle\gradle.properties` (`org.gradle.java.installations.paths`). Do not move it; do not commit machine-specific paths into the repo.
- Gradle wrapper: 9.3.1 from official `services.gradle.org` (upstream default pointed at a Tencent mirror that times out outside China — changed in phase 1).

## Coding Conventions

- Package root `io.agents.pokeclaw`. New files in the existing package for their concern (`agent/`, `tool/`, `ui/`, `service/`, `channel/`, …).
- Kotlin preferred for new code; existing services/tools are Java — match the file you're editing rather than rewriting wholesale.
- Keep the Apache-2.0 header comment on new files, as upstream files use: `// Copyright 2026 PokeClaw (agents.io). All rights reserved.` + license line. Keep `LICENSE` and `NOTICE` intact in all distributions.
- Logging is mandatory and structured: use `XLog.e/w/i/d` per the rules in `CLAUDE.md` — every entry point, branch decision, state change, external call, error, and permission check must be traceable via logcat alone.
- All errors must be user-visible (Toast/dialog/system message) — never silent failures. Check permissions before features and guide the user to the correct settings page.
- PokeClaw is a **generic mobile-agent harness**, not a pile of demo hacks: fix deterministic runtime/harness problems before tuning one stochastic prompt/task (see `README.md` Product Direction and `CLAUDE.md`).
- QA-first: every code change gets an E2E ADB test case added to `QA_CHECKLIST.md` and executed (per `CLAUDE.md`). Docs-only changes are exempt.
- If you spot an architecture problem while working on a feature, stop and flag it in docs/MEMORY.md before building on top of it.

## Git Rules

- Work per phase on a branch (`phase-N-…`), clear commit messages, push to origin, PR into `main`.
- **Never commit secrets**: no API keys, bot tokens, keystores, `.env` (plaintext), signing material. `local.properties`, `*.keystore`, `*.jks`, `keys/` are gitignored. Upstream's committed `.env.enc` is SOPS+age-encrypted — leave it alone; never decrypt it into the repo.
- Do not commit `app/build/`, `.gradle/`, APK outputs, or machine-level Gradle config.

## Phase Plan

See `docs/PLAN.md`: P1 setup/audit (done), P2 Arabic + chat + provider settings, P3 voice input, P4 agent safety/security/stability, P5 final build/tests/release docs.
