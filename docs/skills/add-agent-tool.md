# Skill: Add an Agent Tool

Verified against code at phase 1 (v0.7.1). Tools are classes extending `tool/BaseTool` and live in `tool/impl/`.

## 1. Implement

1. Create `tool/impl/<Name>Tool.java` (existing tools are Java) or `.kt`; match the file style of neighboring tools (e.g. `TapTool.java`, `OpenAppTool.java`). Keep the Apache-2.0 header.
2. Implement the three abstract members from `BaseTool.kt`:
   - `getName()` — snake_case identifier the model will call (keep it generic, not app-specific).
   - `getParameters()` — list of `ToolParameter(name, type, description, required)`. Descriptions are what the LLM reads; write them for a small local model too (short, imperative). Note `BaseTool.useChineseDescription` toggles CN/EN descriptions — if adding Arabic later, extend this switch rather than hardcoding language.
   - `execute(params: Map<String, Any>) -> ToolResult` — success: `ToolResult` with data; failure: `result.error` set (never throw silently; errors surface to model + UI). Use `XLog.d/e` for every call and error path.
3. Prefer the shared wait parameter: build params via `getParametersWithWaitAfter()` so `wait_after` is appended automatically (see BaseTool.WAIT_AFTER_PARAM). Accessibility-backed tools get the node tree via `ClawAccessibilityService`.

## 2. Register

4. Add `register(<Name>Tool())` in `tool/ToolRegistry.kt`: `registerCommonTools()` for both device types, or `registerMobileTools()` if phone-only (`registerAllTools(DeviceType)` at ToolRegistry.kt:22-28).
5. The registry feeds both bridges automatically (LangChain4j bridge for cloud, OpenApiTool schema for LiteRT-LM) — no prompt edits required for basic exposure, but check `agent/PromptUtils.kt` tool rules if the tool needs usage constraints.
6. If skills should route to it, add the identifier to the valid-tool list in `docs/skill-spec.md` (§Valid Tool Identifiers).

## 3. QA (mandatory)

- Add a case under the relevant `QA_CHECKLIST.md` section: trigger a task through chat that forces the new tool, verify via logcat (`onToolCall: <name>(...)`) and UI result; cover missing-permission and failure paths.
- Debug builds: quick headless check with `adb shell am broadcast -a io.agents.pokeclaw.DEBUG_TASK --es task "<direct-tool-call>"` pattern documented in `debug/DebugTaskReceiver.kt` header.
