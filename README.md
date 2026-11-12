# Sanad Claw (سند كلو) — an Arabic-first phone agent

**Sanad Claw** is an Android on-device phone agent: chat with a model, or let the agent operate
your phone (open apps, type, tap, scroll, read the screen, send messages, search) through the
Accessibility service — with confirmation gates before sensitive actions.

A fork of [agents-io/PokeClaw](https://github.com/agents-io/PokeClaw) (Apache-2.0), focused on a
fully Arabic interface (RTL) and a cloud-first model experience. The Arabic overview lives in
[`docs/README.ar.md`](docs/README.ar.md); the upstream English README is kept in
[`docs/README.upstream.md`](docs/README.upstream.md).

- Package: `io.agents.pokeclaw` (kept intentionally for settings/data continuity)
- Version: **1.0.0** (versionCode 100) · Min SDK 28 (Android 8.0) · Target SDK 36
- License: **Apache-2.0** — see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE)

## What it does

1. **Chat** with cloud models — OpenAI, Anthropic, DeepSeek, Google, **OpenRouter**,
   **OpenCode Zen**, or any OpenAI-compatible endpoint — or with on-device models
   (Gemma via LiteRT-LM, optional).
2. **Phone tasks**: the agent reads the screen and presses/types/scrolls to complete tasks such
   as "send a message to X" or "open Y and search for Z".
3. **Safety rails**: confirmation before sending messages/files, calls, payments, deletions and
   installs; blocked-app list (banking/password apps); local action log; emergency stop; task timeout.
4. **Secrets**: API keys and bot tokens are stored via EncryptedSharedPreferences (Android
   Keystore) and are never written to logs.

## Models — paste a key, pick a model

Every cloud provider tab has a pre-filled Base URL, so the flow is:

1. Open **Settings → Model → Cloud LLM**, pick a provider tab.
2. Paste your API key (the model list is fetched automatically from `GET {baseUrl}/models`;
   the "Fetch available models" action re-runs it manually).
3. Search/filter the returned list (OpenRouter returns hundreds) and tap the model to select it —
   the choice is saved for that provider immediately.

The chat model switcher lists only the models you have configured (one entry per provider with a
saved key + model, tagged with the provider name) and activates the right provider configuration
when you switch. The full catalogue stays in Settings.

Provider-aware fetching: Anthropic uses `x-api-key` + `anthropic-version`; other providers use
`Authorization: Bearer`. Keys are never logged.

## Arabic UI and branding

- Full Arabic UI with `values-ar` (plus `en`, `ja`, `zh`); RTL layout; in-app language switch
  (Settings → Appearance → Language) that persists across restarts.
- App name and icon: **Sanad Claw** (Arabic: سند كلو), adaptive icon with a black background.
- Foldable-friendly: settings rows use single-line ellipsized labels so narrow cover screens stay
  readable.

## Build from source

Requirements: **JDK 21**, Android SDK (licenses accepted). Gradle comes from the wrapper.

```powershell
.\gradlew.bat assembleDebug      # debug APK -> app\build\outputs\apk\debug\
.\gradlew.bat testDebugUnitTest  # JVM unit tests (127 tests)
```

macOS/Linux: `./gradlew assembleDebug`.

## Repository layout

- `app/src/main/java/io/agents/pokeclaw/` — agent, tools, channels, UI (Compose + Views)
- `app/src/main/res/` — resources, themes, localized strings, icons
- `docs/MEMORY.md` — project memory (decisions, status, known issues)
- `QA_CHECKLIST.md` — manual QA cases (device-verified items are marked as executed)

## Known limits

- Voice **wake word** ("Hi Sanad") is not implemented; the in-app microphone uses the system
  speech-recognition service, which some OEMs (e.g. HONOR) do not expose.
- Local (on-device) model downloads are hidden in this fork's UI; the engine remains in the code.

## Attribution

Forked from PokeClaw © agents.io, licensed under Apache-2.0. The original `LICENSE` and `NOTICE`
are preserved; upstream trademarks remain with their owner. See
[`NOTICE`](NOTICE) for details.
