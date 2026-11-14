# Phase 2f-finish — FileLoggingInterceptor P0 at unit level

## Step 0: baseline before any change

Corrected baseline confirmed: **217 tests, 41 classes, 7 failures**, all in `FileLoggingInterceptorTest`, all `android.util.Log not mocked`.

Per-test before-state saved to `docs/phase2f_before.txt`.

## Step 1: build.gradle.kts change

Diff (only change to this file):

```diff
@@ -85,6 +85,8 @@ android {
 
     }
 
+    testOptions { unitTests.isReturnDefaultValues = true }
+
     compileOptions {
         sourceCompatibility = JavaVersion.VERSION_17
         targetCompatibility = JavaVersion.VERSION_17
```

`git diff --stat`: `app/build.gradle.kts | 2 ++` (2 insertions, 0 deletions).

Full suite after this change, compared per test against `docs/phase2f_before.txt`:

| Class | Test | Before | After |
|---|---|---|---|
| FileLoggingInterceptorTest | authorizationValueNeverWrittenAndMasked | FAIL | PASS |
| FileLoggingInterceptorTest | requestBodyOmittedWithByteCountAndContentType | FAIL | PASS |
| FileLoggingInterceptorTest | curlLineHasNoAuthorizationValueOrPrefixOrBody | FAIL | PASS |
| FileLoggingInterceptorTest | apiKeyCookieAndSetCookieMasked | FAIL | PASS |
| FileLoggingInterceptorTest | responseBodyNeverWritten | FAIL | PASS |
| FileLoggingInterceptorTest | exceptionMessageNotWrittenButClassNameIs | FAIL | PASS |
| FileLoggingInterceptorTest | nonSensitiveHeaderWrittenInFull | FAIL | PASS |

- Pass → fail: **0**
- Fail → pass: **7** (the expected set)
- Other changes: **0**
- Stop conditions: not triggered.

## Step 2: FileLoggingInterceptorTest, first run after build change

All 9 approved tests pass. No assertion was changed.

| # | Test | Result |
|---|---|---|
| 1 | `authorizationValueNeverWrittenAndMasked` | PASS |
| 2 | `apiKeyCookieAndSetCookieMasked` | PASS |
| 3 | `requestBodyOmittedWithByteCountAndContentType` | PASS |
| 4 | `responseBodyNeverWritten` | PASS |
| 5 | `curlLineHasNoAuthorizationValueOrPrefixOrBody` | PASS |
| 6 | `nonSensitiveHeaderWrittenInFull` | PASS |
| 7 | `exceptionMessageNotWrittenButClassNameIs` | PASS |
| 8 | `headerRuleCoversRequiredNamesAndNotOthers` | PASS |
| 9 | `maskedValueIsExactlyPlaceholder` | PASS |

## Step 3: URL query redaction

Diff (`FileLoggingInterceptor.java`):

- New helpers `isSensitiveQueryParam(String)` and `redactedUrl(okhttp3.HttpUrl)`. A parameter is sensitive when its name is `key`, `token`, `api_key`, `apikey`, `access_token`, or contains `key` or `token` (case-insensitive). The value is replaced with `***`.
- curl builder: `request.url()` → `redactedUrl(request.url())`.
- URL header line: `request.url()` → `redactedUrl(request.url())`.

New test `sensitiveQueryParamsRedactedAndNonSensitiveKept` (added, not weakening anything): URL `…?key=CANARY_QUERY_3c7a&token=CANARY_QUERY_5b1d&page=2`. Asserts both canaries absent, `page=2` present, and both the URL line and the curl line present.

Measured redacted output (from a probe, since removed):
`https://cloud.invalid/v1/items?page=2&key=***&token=***`

OkHttp reorders parameters (`page=2` first). That is cosmetic; the values are not percent-encoded.

`FileLoggingInterceptorTest`: **10 tests, 0 failures** (9 approved + the new query test).

## Step 4: full suite after URL redaction

| Metric | Value |
|---|---|
| Classes | 41 |
| Tests | 218 |
| Failures | 0 |

## Step 5: grep of every URL write

| Line | Code | Redacted? |
|---|---|---|
| 161 | `static String redactedUrl(okhttp3.HttpUrl url)` | helper definition |
| 192 | `sb.append(" \\\n  '").append(redactedUrl(request.url())).append("'");` | yes (curl line) |
| 219 | `writer.write("URL: " + redactedUrl(request.url()) + "\n");` | yes (URL line) |

Raw-URL write check (`append(request.url())` or `"URL: " + request.url()`): **0 matches**.

## Step 6: stability re-run

Full suite run twice after the URL redaction: 41 classes, 218 tests, 0 failures in both runs.

## Header, body, and exception behavior (unchanged from step 2f)

- Header values for Authorization, Proxy-Authorization, Cookie, Set-Cookie, X-Api-Key, api-key, and any name containing `token` or `key` are written as `***` (request headers, response headers, curl `-H`).
- Request and response bodies are never written; only `[body omitted: N bytes, content-type X]` and `[body omitted]` in curl.
- Exception text is not written; only the exception class name is.

## Files changed in this phase

| File | Change | Lines |
|---|---|---|
| app/build.gradle.kts | `testOptions { unitTests.isReturnDefaultValues = true }` | +2 |
| app/src/main/java/.../FileLoggingInterceptor.java | URL redaction helpers and two call sites | (phase 2f + this round) |
| app/src/test/java/.../FileLoggingInterceptorTest.java | new query-redaction test | +1 test |
| QA_CHECKLIST.md | FL3 added, NOT EXECUTED | docs |
| docs/MEMORY.md | one line | docs |
| plan file | P0 status | docs |
| docs/phase2f_before.txt | per-test before-state | new |

## Not covered

- Device run of FL3 (NOT EXECUTED).
- Other query-string secrets that do not match the name rule (for example a secret under a non-matching name). The rule is name-based by design.
- Redaction only covers the URL as OkHttp parses it. A malformed URL is passed through `HttpUrl.parse` by OkHttp before the interceptor sees it.
