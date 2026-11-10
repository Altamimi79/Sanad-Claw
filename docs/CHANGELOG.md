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
