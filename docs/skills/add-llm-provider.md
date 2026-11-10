# Skill: Add an LLM Provider

Verified against code at phase 1 (v0.7.1). Two layers exist: the **runtime client layer** (`agent/llm/`) and the **catalog/settings layer** (`agent/CloudProvider.kt`, `ModelConfigRepository`, settings UI). A new provider touches both.

## 1. Runtime client

1. If the provider is OpenAI-compatible (OpenRouter, Groq, local Ollama…), no new client is needed: it runs through `OpenAiLlmClient` with a custom `baseUrl`. Skip to step 2.
2. Otherwise create `agent/llm/<Name>LlmClient.kt` implementing `LlmClient` (see `OpenAiLlmClient.kt` / `AnthropicLlmClient.kt` as templates; both use LangChain4j with the JDK http-client excluded in app/build.gradle.kts:125-130 — follow that pattern so Android networking keeps working).
3. Add the enum value to `LlmProvider` (used by `AgentConfig`) and wire it in `agent/llm/LlmClientFactory.kt:19-22` (`when (config.provider) -> new client`).
4. If tool calling differs, check the bridges: `agent/langchain/LangChain4jToolBridge.java` (cloud) and `OpenApiTool` usage in `LocalLlmClient.kt` (local).

## 2. Catalog / pricing / settings

5. Add the provider to `agent/CloudProvider.kt` enum with its model list (`CloudModel(id, label, input$/M, output$/M, ModelTier, contextWindow, recommended)`); update `fromName()` mapping.
6. Keys are stored per provider automatically via `KVUtils.getApiKeyForProvider/setApiKeyForProvider` (`KEY_LLM_API_KEY_<NAME>`, backed by `SecureStore` encrypted prefs since phase 2). Base URLs persist per provider via `KVUtils.getBaseUrlForProvider/setBaseUrlForProvider` (`KEY_LLM_BASE_URL_<NAME>`), resolved in `ModelConfigRepository.buildCloudConfig`.
7. Settings UI: `ui/settings/LlmConfigActivity.kt` builds provider tabs from the `CloudProvider` enum (tabViews map) — a new enum entry appears; Base URL field is shown for every provider, free-text model name only for `CUSTOM`. Test Connection runs `agent/llm/CloudConnectionTester.test(...)` (see skill `test-cloud-connection.md`).
8. Debug/QA hook: `debug/DebugTaskReceiver.kt` `config:` broadcast accepts `--es provider "<NAME>"` for headless setup (debug builds only).

## 3. QA (mandatory per CLAUDE.md)

- Add case to `QA_CHECKLIST.md`: configure new provider via Settings → save key → Test Connection → run a chat + a tool task; repeat with wrong key (error must be user-visible Toast, not silent).
- Verify budget/cost display uses new pricing (`agent/ModelPricing.kt` / TokenMonitor status).
- Run `.\gradlew.bat test` (guard/parser tests must stay green).
