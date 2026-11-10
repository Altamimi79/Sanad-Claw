# PokeClaw Architecture Audit (Phase 1)

Audit of the fork base: upstream `agents-io/PokeClaw` @ `936eca3` (main, v0.7.1, versionCode 29). App package `io.agents.pokeclaw`, single Gradle module `:app`. Android app (minSdk 28, targetSdk 36, compileSdk 36.1), Kotlin + Java mix, Jetpack Compose (BOM 2025.05.00) + classic Views activities coexisting.

## 1. License & Notices

- **Apache-2.0** (`LICENSE`, full text). `NOTICE` file: "PokeClaw Copyright 2026 agents.io" — must be retained in distributions (Apache-2.0 §4).
- Source files carry header: `// Copyright 2026 PokeClaw (agents.io). All rights reserved.` + Apache-2.0 line — keep on new files; fork changes may add their own notice lines but not remove upstream ones.
- `CLA.md` + `signatures/cla.json` + `.github/workflows/cla.yml`: contributor license agreement enforcement.
- README has a **Trademark** section (PokeClaw/PocketClaw names belong to agents.io) — rebranding decisions for the Sanad fork must respect this.
- `.env.enc` is committed intentionally: SOPS+age encrypted secrets. Never decrypt into the repo; never rotate its age key without upstream owner coordination. Treat as opaque.

## 2. Modules (app/src/main/java/io/agents/pokeclaw)

| Package | Responsibility / key classes |
|---|---|
| root | `ClawApplication`, `AppViewModel`, `AppCapabilityCoordinator`, `TaskOrchestrator` (399), `TaskEvent`, `TaskSessionStore` |
| `agent/` | Agent loop: `AgentService`(iface)+`AgentServiceFactory`→`DefaultAgentService` (778); routing/guards/budgets: `PipelineRouter`, `TaskClassifier`, `TaskParser`, `TaskPromptEnvelope`, `TaskShortcuts` (290), `PlaybookManager`, `PromptUtils`, `StuckDetector`, `TaskBudget`, `TokenMonitor`, `ModelPricing`; guards: `DirectDeviceDataGuard`, `EmailComposeGuard`, `InAppSearchGuard`; `CloudProvider.kt` (provider/model catalog + pricing) |
| `agent/llm/` | `LlmClient`(iface), `LlmClientFactory`, `OpenAiLlmClient`, `AnthropicLlmClient`, `LocalLlmClient` (554, LiteRT-LM), `LocalModelManager` (520, HF downloads), `LocalModelRuntime`, `EngineHolder`, `LlmSessionManager`, `LocalBackendHealth` (GPU→CPU fallback state), `ModelConfigRepository`, `StreamingListener`, `LlmResponse` |
| `agent/skill/` | `Skill`, `SkillRegistry`, `BuiltInSkills`, `SkillExecutor` — markdown skill files per `docs/skill-spec.md` |
| `agent/knowledge/` | `KBManager` + `Kb*Tool` (todo/append/read/search/write) — local knowledge base |
| `agent/langchain/` | `LangChain4jToolBridge.java` — adapts internal tools to LangChain4j tool calling |
| `tool/` | `BaseTool`, `ToolParameter`, `ToolRegistry`, `ToolResult` + 35 concrete tools (see §4) |
| `service/` | `ClawAccessibilityService.java` (739), `ClawNotificationListener`, `AutoReplyManager.java` (1060 — WhatsApp monitoring/auto-reply), `ForegroundService` (specialUse), `KeepAliveJobService`, `BootReceiver` |
| `channel/` | `ChannelManager`, `ChannelHandler`, `ChannelSetup`; `telegram/` (bot polling), `discord/` (`DiscordGatewayClient` 361, gateway WS), `wechat/` (`WeChatChannelHandler` 401, `WeChatApiClient` 313) |
| `ui/` | `splash/SplashActivity`, `guide/GuideActivity`, `chat/ComposeChatActivity` (391) + `ChatScreen.kt` (2385!) + `ChatSessionController` (658), `TaskFlowController` (412), `ChatHistoryManager`; `settings/SettingsActivity` (773), `LlmConfigActivity` (603), `ChannelConfigActivity`, `ThemeActivity`, `SettingsViewModel`; `web/WebActivity` |
| `automation/` | External intent API: `ExternalAutomationActivity`+`ExternalAutomationReceiver`→`ExternalAutomationEntrypoint`, contract in `ExternalAutomationContract` (b64 payloads, request-id callbacks to caller packages) |
| `debug/` | `DebugTaskReceiver` (302), `TaskTriggerReceiver.java` — ADB task/config triggers (both gated by `BuildConfig.DEBUG`) |
| `server/` | `ConfigServer` (NanoHTTPD on **127.0.0.1:9527**, LAN config UI backend; serves/saves LLM keys + bot tokens), `ConfigServerManager` (WiFi-gated auto-start) |
| `floating/` | `FloatingCircleManager` (452) — EasyFloat overlay control circle |
| `widget/` | `InputDialog` etc. |
| `base/`, `support/`, `utils/` | `BaseActivity`/`AbstractBaseActivity`/`BaseApp`; debug report manager (`support/DebugReportManager`); `KVUtils` (319, MMKV-backed settings incl. API keys), `XLog`, UI-matching utils (`UiActionMatchUtils.java` 312, `ContactListUiUtils.java` 432) |

## 3. Agent Loop (task lifecycle)

Entry points: chat UI (`ComposeChatActivity`→`AppViewModel.startTask`), external API (`automation/`), debug broadcasts. Then:
1. `TaskOrchestrator` / `AppViewModel` route the text: monitor keyword shortcuts, quick tasks (`TaskShortcuts`), skills/playbooks (`PipelineRouter`, `PlaybookManager`), else full agent loop via `DefaultAgentService.executeTask`.
2. `DefaultAgentService.runAgentLoop` (agent/DefaultAgentService.kt:438): builds system prompt (`PromptUtils` + global prompt from KVUtils + device context `buildDeviceContext`), then `while (iterations < maxIterations && !cancelled)` (DefaultAgentService.kt:546) calling `chatWithRetry` (3 attempts, backoff) against the active `LlmClient`.
3. Tool calls arrive via callbacks (`onToolCall`/`onToolResult`), executed through `ToolRegistry`; results fed back as `ToolExecutionResultMessage`; history compressed (`compressHistoryForSend`).
4. Safety nets: `StuckDetector` (round fingerprints → warn→recover→AUTO_KILL, DefaultAgentService.kt:815), `TaskBudget`/`TokenMonitor` (cost/token ceiling, budget-limit stop at :590), guards (`DirectDeviceDataGuard` blocks leaking device data to cloud prompts; `EmailComposeGuard`, `InAppSearchGuard`), `onSystemDialogBlocked`.
5. Completion: `onComplete` → final answer + cost report; `TaskSessionStore`/chat history markdown persists sessions.

Local vs cloud: same loop, different `LlmClient` from `LlmClientFactory`; local path uses LiteRT-LM sessions (`LocalModelRuntime`, `EngineHolder`) with GPU→CPU fallback tracked by `LocalBackendHealth`.

## 4. Tool System

`ToolRegistry.getInstance().registerAllTools(DeviceType)` → common + TV/mobile sets. 35 tools in `tool/impl` (+ key sub-tools):
- Screen/UI: `GetScreenInfoTool`, `FindNodeInfoTool`, `TapTool`, `TapNodeTool`, `LongPressTool`, `SwipeTool`, `ScrollToFindTool`, `FindAndTapTool`, `RepeatActionsTool`, `InputTextTool`, `WaitTool`
- Device/system: `GetDeviceInfoTool`, `GetInstalledAppsTool`, `OpenAppTool`, `MakeCallTool`, `SystemKeyTool` (+ `BaseKeyTool`: Dpad×4, PressMenu/Power, Volume×2), `ClipboardTool`(+`ClipboardReaderActivity`), `TakeScreenshotTool`, `GetNotificationsTool`
- Comms/files: `SendMessageTool` (341), `SendFileTool`, `AutoReplyTool`
- Agent bookkeeping: `FinishTool`; knowledge: `Kb*Tool`×5 (agent/knowledge)
Tools expose name/description/parameters (`ToolParameter`) → serialized as OpenAI-style function schemas for cloud (via LangChain4j bridge) and LiteRT-LM tool calling (`OpenApiTool` in LocalLlmClient). Results = `ToolResult{data|error}` returned to the model + UI.

## 5. LLM Providers

- **Local**: LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.10.0`). Models downloaded from HuggingFace `litert-community/gemma-4-E2B-it-litert-lm` and `…E4B…` `.litertlm` files (`LocalModelManager.kt:81-90`) into app storage; GPU/CPU delegate selection + fallback truthfulness via `LocalBackendHealth`; session lifecycle in `LlmSessionManager`/`EngineHolder`.
- **Cloud**: LangChain4j 1.12.2 — `langchain4j-openai` + `langchain4j-anthropic` (JDK http-client excluded, OkHttp adapter). `CloudProvider.kt` catalog: OPENAI (+models/pricing/tiers LITE/FAST/SMART/PRO), ANTHROPIC, and OpenAI-compatible custom `base_url` (OpenRouter/Groq/Ollama etc. — used by DebugTaskReceiver config path and LlmConfigActivity). `ModelPricing.kt` + per-model input/output $/M tokens drive budget/cost UI.
- **Keys storage**: `KVUtils` → MMKV plaintext (`KEY_LLM_API_KEY`, `KEY_LLM_API_KEY_<PROVIDER>`, bot tokens). No EncryptedSharedPreferences.

## 6. UI

Classic-Views shell (`SettingsActivity`, `LlmConfigActivity`, `ChannelConfigActivity`, `ThemeActivity`, `GuideActivity`) + Compose chat (`ComposeChatActivity` hosting `ChatScreen.kt`: chatroom, task flow, monitor setup, model picker). EasyFloat floating circle for task control/status. Home-screen widget bits in `widget/`. Channels: Telegram bot (polling), Discord (gateway WS), WeChat — inbound remote control paths. Embedded web view (`WebActivity`) + LAN config server pairs with `docs/` landing-page-style config UI.

## 7. Permissions & Security (AndroidManifest.xml)

Permissions: INTERNET, WAKE_LOCK, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE(+SPECIAL_USE), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, READ_PHONE_STATE, READ_CONTACTS, READ/WRITE_EXTERNAL_STORAGE, **MANAGE_EXTERNAL_STORAGE**, **REQUEST_INSTALL_PACKAGES**, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, **QUERY_ALL_PACKAGES**, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM.

Components:
- Accessibility service `ClawAccessibilityService` (BIND_ACCESSIBILITY_SERVICE) + `ClawNotificationListener` (BIND_NOTIFICATION_LISTENER_SERVICE) — the core observation/actuation surface; `accessibility_service_config.xml`.
- `ForegroundService` type `specialUse`; `KeepAliveJobService` (JobScheduler); `BootReceiver` exported BOOT_COMPLETED.
- Exported without android:permission: `ExternalAutomationActivity` + `ExternalAutomationReceiver` (RUN_TASK/RUN_CHAT) — guarded in code by: targeting check (`isTargetedToPokeClaw`), user setting `KVUtils.isExternalAutomationEnabled()` (default off), single-task lock; **no sender identity/signature check**.
- `DebugTaskReceiver`, `TaskTriggerReceiver` — exported but first line returns unless `BuildConfig.DEBUG`. Note: in a *debug* build these accept arbitrary tasks and even `config:` API-key injection via broadcast.
- `ConfigServer`: NanoHTTPD bound to 127.0.0.1:9527 (loopback), GET/POST JSON exposing/saving LLM API keys and Telegram/Discord bot tokens; any app on the device can reach loopback ports — no auth token on the HTTP API.
- `network_security_config.xml`: **cleartextTrafficPermitted=true** globally.
- FileProvider (exported=false, grantUriPermissions) for SendFileTool.

## 8. Weaknesses & Risks

Architecture:
1. God classes: `ChatScreen.kt` 2385 lines; `AutoReplyManager.java` 1060; `DefaultAgentService.kt` 778 (loop+retry+compression+detection); `SettingsActivity` 773; `ClawAccessibilityService.java` 739. BACKLOG already tracks "ChatViewModel extraction" (P3).
2. Dual UI stack (Views activities + Compose chat) and mixed Java/Kotlin in the same layers (tools/services Java, agent Kotlin) — consistency cost for future work (relevant to P2 Arabic UI changes).
3. Chat/monitor/task state coordinated through global `appViewModel` singleton + MMKV keys — state spread across KVUtils, TaskSessionStore, managers; risk of leakage between runs (upstream QA tracks this class of bug).
4. LangChain4j + OkHttp + Retrofit + Gson all in parallel for LLM HTTP — duplicated HTTP stacks.

Security:
5. API keys / bot tokens stored **plaintext in MMKV**; also served over unauthenticated loopback HTTP by `ConfigServer`; `network_security_config` allows cleartext globally (cloud calls use HTTPS endpoints but the policy permits downgrade).
6. Exported RUN_TASK components rely on a user toggle, not sender verification — any app can attempt task injection when external automation is enabled; debug receivers fully open in debug builds (QA convenience vs risk of shipping debug APKs publicly — historical public builds were debug-signed: BACKLOG P1 "signing key lost → uninstall/reinstall gap").
7. Broad permission surface: MANAGE_EXTERNAL_STORAGE + QUERY_ALL_PACKAGES + REQUEST_INSTALL_PACKAGES + accessibility + notification listener = full phone read/act capability; prompt-injection risk from screen/notification content into the model loop (upstream mitigates with `DirectDeviceDataGuard` and "untrusted context" doctrine — keep and extend in P4).
8. `.env.enc` committed upstream: keys are age-encrypted but the *recipient* is upstream's; a fork cannot rotate them — flag for P4 (fork-local secrets story).

Stability/device:
9. GPU/LiteRT engine bring-up failures on MediaTek/Samsung (BACKLOG P1) — fallback must stay truthful; local model RAM floor (E2B/E4B too heavy for low-RAM phones).
10. Upstream wrapper defaulted to a Chinese Gradle mirror (timeout outside CN — fixed in this fork); `app/build.gradle.kts` uses deprecated `Runtime.exec` patterns and reads signing config from `local.properties`; many Kotlin deprecation warnings at build time (Compose icons, statusBarColor, SOFT_INPUT_ADJUST_RESIZE…) — tech-debt signal.
11. Tests: only 13 JVM unit tests (guards/parsers); real QA is manual/CI ADB E2E (`QA_CHECKLIST.md`, emulator-matrix + Firebase Test Lab workflows) — needs a physical device for full gates.

## 9. Build System

- Gradle wrapper 9.3.1 (official services.gradle.org after fork fix), AGP 9.1.0, Kotlin 2.1.21 (compose plugin), Java toolchain 21 via foojay resolver (overridden locally — see MEMORY.md), compileOptions Java 17.
- `settings.gradle.kts`: google + mavenCentral + **jitpack** (EasyFloat from jitpack — supply-chain consideration).
- Notable deps: litertlm-android 0.10.0, langchain4j 1.12.2 (openai/anthropic), okhttp/retrofit/gson, mmkv-static 2.3.0, easyfloat 2.0.4, nanohttpd 2.3.1, zxing 3.5.3, lark oapi-sdk 2.5.3, dingtalk stream client, utilcodex, glide.
- APK naming: `PokeClaw_v<versionName>_<timestamp>.apk`; version from `local.properties`/env (`POKECLAW_VERSION_NAME/CODE`, default 0.7.1/29); build fingerprint injected into assets `.pcfp` + BuildConfig (git hash, builder user — note: **builder username leaks into BuildConfig**; consider BUILDER_ID env in P4).
- Release signing: `signingConfigs.release` reads `KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD` from env or local.properties; CI signs via GitHub Actions secrets (RELEASING.md); minify+shrink enabled for release.
- CI workflows: `build.yml` (CI build), `cla.yml` (CLA check), `emulator-matrix.yml` (multi-API emulator QA), `firebase-test-lab.yml` (FTL matrix), `release.yml` (signed APK publish on tag).

## 10. Existing Docs Worth Keeping In Mind

- `docs/ARCHITECTURE_DECISIONS.md` D1–D8: local-first LiteRT default; 21 generic tools × core rules separation; playbooks split (Java closed / Markdown open — fork must decide stance); accessibility+notification not root/ADB; MMKV state + markdown chat history; community OEM coverage; provider-agnostic OpenAI-compatible cloud layer; same-day issue replies.
- `docs/AI_AGENT_PLAYBOOK.md`: ADB input pitfalls (`adb input tap/text` broken for composers — use accessibility SET_TEXT), release-build restrictions, permission grants via ADB, QA device notes.
- `docs/skill-spec.md`: markdown skill format (frontmatter, steps, routing prompt, valid tool identifiers) — the extension surface for new capabilities.
- `CLAUDE.md` / `QA_CHECKLIST.md` / `BACKLOG.md` / `RELEASING.md`: rules, QA gates, prioritized items, signing workflow (historical lost-key limitation documented).
