# Skill: Safety Guardrails (Phase 4)

Verified against code at phase 4 (branch `phase-4-safety`).

## Where the gate lives

`safety/SafetyGate.evaluate(toolName, params, taskPrompt)` is called by `DefaultAgentService.runAgentLoop` **before every** `ToolRegistry.executeTool`. It returns `Decision(allow, blockedByApp, message, assessment)`.
Pure decision logic (unit-tested): `RiskClassifier`, `AppBlocklist`, `TaskTimeoutPolicy`, `RetryPolicy`, `ActionLogFormatter`, `SafetySettings`.

## Confirmation flow (blocking → UI)

1. Worker thread calls `SafetyGate.evaluate`; risky tool → `SafetyCoordinator.requestConfirmation(message, risk)`.
2. `SafetyCoordinator.pending` StateFlow emits; `ChatScreen` shows `SafetyConfirmationHost` (Arabic title/allow/deny).
3. Allow → tool executes. Deny or 45s timeout → tool blocked, task stops with `safety_action_denied`.
4. No UI attached (`ComposeChatActivity.onPause` sets availability false) → **denied immediately**. Never auto-approve.
5. Deny stops the whole task (the model must not route around a refusal).

Do not call `ToolRegistry.executeTool` from new code paths without the gate.

## Risky actions (RiskClassifier)

- Tools: `send_message`, `send_file`, `make_call`, `search_app_in_store`.
- Text heuristics on params: payment words (pay/transfer/دفع/تحويل/سداد…), delete words (delete/حذف/امسح…), install words for store-like tools, publish words for input tools.
- Add new categories in `RiskClassifier` **and** the `risk_*` strings in both locales.

## Blocklist (banking/password)

- `AppBlocklist.effectivePatterns(raw)` = user CSV from Settings → Tools → Blocked apps, else conservative defaults (bank/بنك/مصرف, wallets, 1Password/Bitwarden/LDAP-style password managers, authenticators).
- Foreground package comes from `ClawAccessibilityService.getInstance().rootInActiveWindow.packageName`; app label is a secondary match; a two-marker password-screen fallback handles unlisted apps.
- Blocked app ⇒ every tool is refused and the task stops. This also protects clipboard reads.
- Prefer package/label matching over screen text (structure-first); do not add generic words like "account".

## Task timeout

`TaskTimeoutPolicy` + `KVUtils.getSafetyTaskTimeoutSec()` (default 180s; presets 120/180/300/600). Checked at the top of every loop iteration and before each tool; timeout aborts with `safety_task_timeout`.

## Retry

`RetryPolicy.shouldRetry(attempt, tool, error)` — max 2 attempts, only safe read/navigation tools, only transient markers (timeout/network/5xx/…), never for risky tools. Backoff 400ms × attempt.

## Emergency stop

- Chat: red banner with `safety_emergency_stop` while `isTaskRunning`.
- Foreground notification: action wired to `EmergencyStopReceiver` (`exported=false`, `io.agents.pokeclaw.EMERGENCY_STOP`).
- Floating pill: existing `FloatingCircleManager.onStopTask`.
- `EmergencyStop.trigger` stops the task, monitoring and the foreground service, and records the event.

## Action log

`ActionLog` (MMKV, max 200, local only) stores `timestamp | task | tool | risk | outcome | detail` via `ActionLogFormatter` which redacts `sk-…`, `token=/password=/secret=`, bearer tokens and card-like digit runs, and truncates. Viewer: `ActionLogActivity` (Settings → Tools → Action log, clearable). Never upload it.

## Add a new guarded action

1. Extend `RiskClassifier` (tool name or text heuristic) + unit test.
2. Add Arabic + English text for any new message.
3. If it must not be retried, keep it out of `RetryPolicy.RETRYABLE_TOOLS`.
4. Add an E2E case in `QA_CHECKLIST.md` §SA.

## Tests

`.\gradlew.bat testDebugUnitTest` — `RiskClassifierTest`, `AppBlocklistTest`, `TaskTimeoutPolicyTest`, `RetryPolicyTest`, `ActionLogFormatterTest`, `SafetyCoordinatorTest`.
