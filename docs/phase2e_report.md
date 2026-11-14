# Phase 2e — Row 14 closure and FILE_LOGGING_ENABLED registration

## Step 0: baseline

`.\gradlew.bat testDebugUnitTest` → **204 tests, 39 classes, 0 failures.** Matches expected.

## Step 1: direct `android.util.Log` calls on the pause path

Strict pattern `(?<![A-Za-z_])Log\.[diwev]\(` over the four files: **0 hits.** No aliased `Log` import, no `println`/`System.out`.

| File | Line | Call | Covered by Sink |
|---|---|---|---|
| DefaultAgentService.kt | — | none | n/a |
| PausedTaskState.kt | — | none | n/a |
| TaskFlowController.kt | — | none (all calls are `XLog.*`) | yes |
| ChatSessionController.kt | — | none (all calls are `XLog.*`) | yes |

Note: a loose grep (`Log\.[diwev]\(`) matches `XLog.` and returned 90 hits. All are `XLog` calls; none is a direct `android.util.Log`.

Result: **zero direct Log calls on the pause path.**

## Step 2: XLog diff

Approved edit: Sink interface, `testSink` field, `setTestSinkForTests`, and one dispatch line per log method.

**Count correction:** the brief said 16 log methods. The source has **15 log methods** plus `setDEBUG`, which is not a log method and was correctly left untouched. The dispatch-line grep returns **15**, and each of the 15 methods has the Sink line as its first statement (verified per method).

`git diff --stat`: `XLog.java | 27 insertions(+)`, **0 deletions**.

Full before/after: before = `git show HEAD:app/src/main/java/io/agents/pokeclaw/utils/XLog.java` (89 lines). After = HEAD plus the diff below.

```diff
 public class XLog {
     private static boolean DEBUG = true;
 
+    /** Test-only observer. Null in production; never set outside unit tests. */
+    public interface Sink {
+        void onLog(String level, String tag, String msg, Throwable tr);
+    }
+
+    private static volatile Sink testSink = null;
+
+    /** Test-only: install or clear the observer. Production never calls this. */
+    public static void setTestSinkForTests(Sink sink) {
+        testSink = sink;
+    }
+
     public static void setDEBUG(boolean debug) {
         DEBUG = debug;
     }
 
     public static void i(String tag, String msg) {
+        Sink s = testSink; if (s != null) s.onLog("I", tag, msg, null);
         AppLogStore.log("I", tag, msg, null);
 ...
```

The 15 added dispatch lines carry levels `I` (×2), `D` (×2), `E` (×3), `W` (×3), `V` (×2), `WTF` (×3). The `e(String,Throwable)`, `w(String,Throwable)`, `wtf(String,Throwable)` overloads pass `""` as the message, matching the existing call.

Production behavior: `testSink` is `null` and only `setTestSinkForTests` assigns it. With null, the added line is a no-op.

## Step 3: capture test

`PausedTaskLogCaptureTest` (new, 4 tests). `@Before`: `XLog.setDEBUG(false)` and install a capturing Sink. `@After`: `setTestSinkForTests(null)` and `setDEBUG(true)`.

| Test | Flow | Captured lines | Canary assertions | Result |
|---|---|---|---|---|
| `pauseResumeSubstituteClearFlowNeverLogsCanaryValue` | pause → next message → substitute → tool executed → clear | 4 | `CANARY_VALUE_7f3a` absent (boolean + count = 0) | PASS |
| `ibanShapedSubstitutionNeverLogsCanaryIban` | pause → IBAN-shaped value → substitute → tool executed → clear | 4 | `CANARY_IBAN_9b2c` absent (boolean + count = 0) | PASS |
| `timeoutCancellationNeverLogsCanaryValue` | pause → clock past 45 s → late message rejected → timeout log → cancel | 2 | `CANARY_VALUE_7f3a` absent (count = 0) | PASS |
| `captureIsActiveAndRecordsLevelsAndTags` | direct probe: `XLog.i` + `XLog.w` with Throwable | 2 | level and Throwable recorded | PASS |

Captured line counts are **asserted greater than zero** inside each flow test (`records.isNotEmpty()`), so zero-occurrence cannot pass on an inactive sink. Canary values are never printed: assertion messages contain no canary text, only booleans and counts.

**Scope limitation (stated plainly):** the flow tests drive `PausedTaskState` and emit the same XLog statements the production hooks emit (`DefaultAgentService` pause/resume/timeout lines, and the tool-executed and cleared lines). `DefaultAgentService` requires Android and cannot run in a JVM test, so the production statements themselves are not invoked. Equivalence is established by reading the hook code (`DefaultAgentService.kt` lines ~800–825), not by executing it.

## Step 4: full suite

| Metric | Value |
|---|---|
| Classes | **40** (39 + `PausedTaskLogCaptureTest`) |
| Tests | **208** (204 + 4) |
| Failures | **0** |

Mandatory criterion (zero sensitive actions without confirmation from screen-sourced text): unchanged. `InjectionCorpusTest` reports `sensitive_checks=350 violations=0`.

## Step 5: row 14 status

**Row 14: PASS**, conditional on the scope stated above.

Reason: (a) `PausedTaskLogCaptureTest` passes; (b) the capture-active assertion (`records.isNotEmpty()`) passes in every flow; (c) Step 1 found zero direct Log calls on the pause path. All XLog calls reachable from the modeled pause, resume, substitution, clear and timeout path route through the Sink.

Residual gap, explicitly not covered: the **production** `DefaultAgentService` hook is not executed under test (needs Android). A future instrumented or Robolectric test would close this. Also not covered: `AppLogStore` file contents and logcat output on a device.

## FILE_LOGGING_ENABLED: write paths (registered, not fixed)

Gate: `ClawApplication.kt:50` `FILE_LOGGING_ENABLED = BuildConfig.DEBUG`. Debug builds: **on**. Release: **off**.

| File | Line | What is written | Includes body / screen text / secrets |
|---|---|---|---|
| agent/langchain/http/FileLoggingInterceptor.java | 116–156 (`writeToFile`) | file `{cacheDir}/http_logs/yyyyMMdd_HHmmssSSS_{METHOD}.txt` | — |
| FileLoggingInterceptor.java | 130–131 | **all request headers, un-masked** | **Yes — secrets** (`Authorization` written in full; only the curl line masks it) |
| FileLoggingInterceptor.java | 134 | full **request body** | **Yes — body** (LLM prompt, tool results incl. screen text, redacted placeholders only if redaction ran) |
| FileLoggingInterceptor.java | 106–109, 112 | curl command with body truncated to 2000 chars and `Authorization` masked to 15 chars | Yes — body (truncated) |
| FileLoggingInterceptor.java | 144–146 | response headers | Possible (depends on server) |
| FileLoggingInterceptor.java | 149 | full **response body** (LLM completion) | **Yes — model output** |
| FileLoggingInterceptor.java | 69 | exception text on request failure, with empty body | No body; error text only |
| agent/langchain/http/OkHttpClientBuilderAdapter.java | 134–135 | wires the interceptor when enabled | gate only |
| agent/llm/LlmClientFactory.kt | 15–16 | turns it on when flag and cache dir set | gate only |
| agent/DefaultAgentService.kt | 109, 111 | flag and cache dir holders | gate only |
| ClawApplication.kt | 50–51 | sets flag to `BuildConfig.DEBUG` | gate only |

Summary: the writer records **request body, response body, un-masked Authorization header**. Registered as **P1 (high)** in the plan file, section "Known defects outside items 1-2". QA cases FL1 and FL2 added as NOT EXECUTED. Behavior not changed.

## Acceptance

| Check | Status | Proof |
|---|---|---|
| Row 14: capture test passes | PASS | `PausedTaskLogCaptureTest` (4/4) |
| Row 14: capture-active assertion | PASS | `records.isNotEmpty()` in all flow tests |
| Row 14: zero direct Log calls on pause path | PASS | Step 1 grep = 0 |
| Mandatory criterion | PASS, 0 violations | `InjectionCorpusTest` (350 checks) |
| FILE_LOGGING_ENABLED registered P1 | PASS | plan file section, MEMORY.md |
| FL1 / FL2 device | NOT EXECUTED | QA_CHECKLIST.md |
| ConfigServer binding 127.0.0.1 | PASS (unchanged) | `ConfigServer.kt:25` |

## Diff summary

| File | Lines changed | Tests | Result |
|---|---|---|---|
| utils/XLog.java | +27, −0 | — | green |
| safety/PausedTaskLogCaptureTest.kt | new | 4 | green |
| QA_CHECKLIST.md | +FL1, FL2 | — | docs |
| docs/MEMORY.md | +2 lines | — | docs |
| plan file | +P1 section | — | docs |
| docs/phase2e_report.md | new | — | docs |

## Verdict

ROW 14 CLOSED at unit level, with the scope limitation above. FILE_LOGGING_ENABLED registered P1, not fixed. Device QA pending.
