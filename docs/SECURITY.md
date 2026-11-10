# Sanad-Agent — Security Review (Phase 4)

Date: 2026-11-10 · Scope: fork of PokeClaw v0.7.1 (`io.agents.pokeclaw`) — full source tree, manifest, resources, Gradle config, assets/H5.
Method: manual review of entry points (manifest components, services, receivers), secret storage and flow, logging paths, network surface, permission inventory, and the agent tool execution loop; verified with `grep` sweeps for hardcoded keys and secret logging.

Legend: **FIXED** = changed in this repo · **MITIGATED** · **OPEN** (documented residual risk with a plan).

## Summary of findings

| ID | Severity | Finding | Status |
|---|---|---|---|
| S1 | High | API keys and bot tokens were stored in plaintext MMKV | **FIXED** (Phase 2) |
| S2 | High | Loopback ConfigServer `/api/*` accepted any local caller; GET returned secrets | **FIXED** (Phase 2 mask + Phase 4 token) |
| S3 | Medium | Global `cleartextTrafficPermitted=true` | **OPEN** (needed for LAN/local LLM) |
| S4 | Medium | `ExternalAutomationReceiver/Activity` exported and accepts task intents | **MITIGATED** (opt-in, targeted broadcast) / plan P5 |
| S5 | Medium | Exported debug receivers could trigger tasks | **FIXED**/bounded — both return early unless `BuildConfig.DEBUG` |
| S6 | Medium | Over-broad permissions (all-files, QUERY_ALL_PACKAGES, install packages, contacts, phone state) | **OPEN** (feature-driven; document for store review) |
| S7 | Medium | `XLog.i` content (LLM replies, screen text) persisted in AppLogStore even in release → debug-report export | **OPEN** (local-only, user-initiated export; recommendation below) |
| S8 | Medium | Clipboard tool can read clipboard contents (may include passwords) | **MITIGATED** (blocklist gate blocks all tools while a blocked app is foreground) |
| S9 | Medium | Prompt injection from screen/notification content can steer risky actions | **MITIGATED** (existing guards + new confirmation gate) |
| S10 | Low | Action history could leak sensitive text | **FIXED** — local ring buffer, redacted (keys/tokens/passwords/card-like numbers), 200 entries, clearable |
| S11 | Low | Hardcoded secrets in source | None found (grep `sk-`, `api_key`, `token`, `.env` in sources) |
| S12 | Info | Upstream release signing key lost; `.env.enc` age recipient is upstream-only | **OPEN** (P5 release plan) |

## Details

### S1 — Secret storage (FIXED in Phase 2)
- `utils/SecureStore` uses EncryptedSharedPreferences (AES256-GCM values, AES256-SIV key names, Android Keystore master key), with a private-mode fallback when the keystore is unavailable. Plaintext MMKV secrets are migrated once and removed.
- `KVUtils` secret accessors (`getLlmApiKey`, `getApiKeyForProvider`, bot tokens, `getBaseUrlForProvider` is non-secret) delegate to SecureStore.
- No secret value is ever logged; masked values (`***`) prevent a GET→POST round-trip from overwriting a real key.
- Residual: `androidx.security:security-crypto` is deprecated upstream (1.1.0 final). Re-evaluate in P5 (Tink direct or Keystore-backed file).

### S2 — ConfigServer (FIXED)
- Binds to `127.0.0.1` (loopback only) — verified in `ConfigServer` constructor; not reachable from the LAN.
- Phase 2: all GET responses mask secret values; POST ignores masked values.
- Phase 4: per-process random `apiToken`; **every `/api/*` request** must present `X-PokeClaw-Token` (or `?token=`). The served HTML embeds the token; unauthenticated callers get 401. Debug endpoints remain `BuildConfig.DEBUG`-gated.
- CORS still `*`: acceptable because the surface is loopback + token; tighten if a browser-based attack becomes plausible.

### S3 — Cleartext (OPEN)
- `res/xml/network_security_config.xml` sets `cleartextTrafficPermitted="true"` globally, required for user-configured OpenAI-compatible endpoints on `http://` (Ollama/LM Studio on a LAN IP).
- Plan: default to HTTPS-only for known providers (already) and require an explicit per-provider "allow insecure endpoint" toggle before accepting `http://` base URLs; then narrow the network security config. Until then, document clearly in provider settings.

### S4 — External automation (MITIGATED)
- `ExternalAutomationReceiver` / `ExternalAutomationActivity` accept `RUN_TASK`/`RUN_CHAT` intents from other apps only when the user enabled External Automation (default off); requests must be targeted (`-p io.agents.pokeclaw`); payloads are base64 extras with size limits.
- Residual: any app on the device can invoke it while enabled, launching UI and driving the agent. Plan (P5): signature-level permission or explicit package pairing/allowlist, plus rate limiting.

### S5 — Debug receivers (bounded)
- `DebugTaskReceiver` and `TaskTriggerReceiver` immediately return unless `BuildConfig.DEBUG`; they cannot execute tasks in release builds.
- Keep them unexported in release via build-variant manifest override if Play review objects.

### S6 — Permission inventory (OPEN)
- Declared: INTERNET, WAKE_LOCK, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, foreground service (+specialUse), battery optimization, READ_PHONE_STATE, READ_CONTACTS, READ/WRITE/MANAGE_EXTERNAL_STORAGE, REQUEST_INSTALL_PACKAGES, network/wifi state, QUERY_ALL_PACKAGES, boot, exact alarms, RECORD_AUDIO (Phase 3).
- Each is used by an automation feature (accessibility screen reading, downloads, voice), but the set is broad. P5: move MANAGE_EXTERNAL_STORAGE / REQUEST_INSTALL_PACKAGES usage behind explicit user flows and justify in store listing.

### S7 — Logging of sensitive content (OPEN)
- `XLog.i/d` write to logcat only in debug, but **AppLogStore persists info logs in release** for the user-triggered debug report.
- `DefaultAgentService` logs truncated LLM responses and tool output; `GetScreenInfoTool` may include on-screen text (could contain OTPs/messages).
- Mitigations present: logs are local-only, never uploaded automatically; report is user-initiated.
- Recommended next step: route tool output and LLM text through a redactor before `AppLogStore.log` (reuse `SecretsMasker` + `ActionLogFormatter`).

### S5b/Safety controls added (this phase)
- Pre-execution gate (`safety/SafetyGate`) in the agent loop:
  - **Blocklist**: if the foreground package/app label matches the banking/password list (defaults + user CSV), the task stops with a localized message and the action is never executed.
  - **Confirmation**: sending messages/files, calls, payments, deletes, installs, publishes require an explicit in-app approve. No UI attached ⇒ denied (fail-safe).
- **Task timeout** (default 180s, user-selectable 120/180/300/600) aborts the loop with a localized error.
- **Emergency stop**: chat banner, floating pill, and a foreground-notification action (`EmergencyStopReceiver`, not exported) stop the task, monitoring and the service.
- **Action log**: local ring buffer of tool/risk/outcome with redaction; viewer at Settings → Tools → Action log; clearable.
- **Retry**: bounded (max 2) and limited to safe read/navigation tools; risky tools are never auto-retried.

### Secret handling rules (enforced)
- Never log secret values; log key names only.
- Never place secrets in Toasts/dialogs/user-visible errors.
- New secrets must go through `SecureStore` (see `docs/skills/encrypted-secrets.md`).
- ConfigServer must keep the authenticated + masked-value behavior for any new endpoint.

## Verification performed
- `grep` for `sk-`, `api_key`, `password`, `token=` in source: only resource keys/UI labels, no real values.
- `grep` for `TextToSpeech`: none (replies stay written).
- Manifest audit: receivers/activities reviewed; exported automation requires the user toggle; debug receivers are DEBUG-gated; new receiver is `exported=false`.
- Unit tests cover risk classification, blocklist matching, timeout policy, retry policy, action-log redaction, and the fail-safe confirmation coordinator (`.\gradlew.bat testDebugUnitTest` — 127 tests green).

## Residual risks to track in P5
1. Move automation/external intents behind a signature permission or pairing.
2. Narrow cleartext to explicit user-approved endpoints.
3. Redact `AppLogStore` content or make content logging opt-in.
4. Re-evaluate SecureStore dependency (security-crypto deprecation).
5. Release signing/migration story and `.env.enc` handling for the fork.
