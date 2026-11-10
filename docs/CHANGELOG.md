# Changelog — Sanad-Agent (PokeClaw fork)

Format based on Keep a Changelog; upstream PokeClaw history lives in `README.md` §Changelog (v0.1.0 → v0.7.1).

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
