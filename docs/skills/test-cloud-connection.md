# Skill: Test Cloud Connection (provider settings)

Verified against code at phase 2 (branch `phase-2-arabic-chat`).

## Where it lives

`agent/llm/CloudConnectionTester.kt` — the ONLY sanctioned way to validate provider credentials/endpoint. `LlmConfigActivity` "Test" button calls it on the activity's single-thread executor. The old fake check (key length ≥ 10) was removed; never reintroduce a non-network "test".

## How the probe works

- `normalizeBaseUrl(raw)` (pure, unit-tested): adds `https://` when scheme missing, strips trailing slashes, and strips `/chat/completions`, `/completions`, `/responses` suffixes — langchain4j appends the endpoint itself, so users may paste either form.
- `test(providerName, baseUrl, apiKey, modelId)`:
  - ANTHROPIC provider → `AnthropicChatModel`; everything else (OPENAI, DEEPSEEK, GOOGLE-style gateways, CUSTOM) → `OpenAiChatModel` OpenAI-compatible path.
  - Both builders must use `.httpClientBuilder(OkHttpClientBuilderAdapter())` — the JDK http-client is excluded for Android (see app/build.gradle.kts); a probe without the adapter fails at runtime on device.
  - One minimal chat request (`"ping"`, temperature 0, maxTokens 16, 25s timeout).
- Status classification → `Status.{OK, UNAUTHORIZED, MODEL_NOT_FOUND, TIMEOUT, NETWORK, GENERIC}` matched from the exception cause chain (401/invalid key; 404/model not found; SocketTimeout/timed out; IOException/connect refused/unresolved host).

## UI contract (LlmConfigActivity)

- Preflight toasts: missing API key → `provider_enter_api_key_first`; empty Base URL → `provider_missing_base_url`; custom provider without model name → `provider_missing_model_id`.
- During probe: `provider_testing`, test button disabled. Results map 1:1 to strings `provider_connection_ok` / `_unauthorized` / `_model_not_found` (takes modelId) / `_timeout` / `_network_error` (takes detail) / `_generic` (takes detail) — present in en + ar.
- Base URL field is visible for every provider and prefilled from `KVUtils.getBaseUrlForProvider(name)` → saved cloud seed → provider default, in that order.

## Security rules

- The API key must never appear in results or logs. `sanitize()` masks `sk-…` patterns in the exception chain and caps at 240 chars. Keep this when extending classification.
- Status strings are localized; add new ones to BOTH `values/strings.xml` and `values-ar/strings.xml`.

## Tests

`app/src/test/.../CloudConnectionTesterTest.kt` covers normalizeBaseUrl edge cases and short-circuits (blank key → UNAUTHORIZED, blank URL → GENERIC) without network. Run: `.\gradlew.bat testDebugUnitTest`. Device E2E: QA_CHECKLIST §FA4–FA7.
