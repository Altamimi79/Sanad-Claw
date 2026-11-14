// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.langchain.http;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * P0 security test for FileLoggingInterceptor. JVM only, no network, no mock library.
 *
 * The interceptor is driven through intercept() with a hand-written Chain. The written file
 * is read back from the cache directory and searched for canary values. Canary values are
 * never printed: every assertion uses a boolean and a fixed message.
 */
public class FileLoggingInterceptorTest {

    private static final String CANARY_KEY = "CANARY_KEY_4d1e";
    private static final String AUTH_VALUE = "Bearer sk-" + CANARY_KEY;
    private static final String CANARY_BODY = "CANARY_BODY_8c2f";
    private static final String CANARY_RESP = "CANARY_RESP_9a0b";
    private static final String CANARY_EXC = "CANARY_EXC_5e7d";
    private static final String COOKIE_VALUE = "session=CANARY_COOKIE_6f8a";
    private static final String SET_COOKIE_VALUE = "sid=CANARY_SETCOOKIE_7b9c; Path=/";
    private static final String XKEY_VALUE = "CANARY_XKEY_0c1d";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File cacheDir;

    @Before
    public void setUp() throws IOException {
        cacheDir = tmp.newFolder("cache");
    }

    /** Hand-written chain: returns a canned response, or throws when configured to. */
    private static final class FakeChain implements Interceptor.Chain {
        private final Request request;
        private final Response response;
        private final IOException toThrow;

        FakeChain(Request request, Response response, IOException toThrow) {
            this.request = request;
            this.response = response;
            this.toThrow = toThrow;
        }

        @Override public Request request() { return request; }

        @Override public Response proceed(Request r) throws IOException {
            if (toThrow != null) throw toThrow;
            return response;
        }

        @Override public okhttp3.Connection connection() { return null; }
        @Override public okhttp3.Call call() { return null; }
        @Override public int connectTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withConnectTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int readTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withReadTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int writeTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withWriteTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
    }

    private static Request buildRequest() {
        return new Request.Builder()
                .url("https://cloud.invalid/v1/chat/completions")
                .header("Authorization", AUTH_VALUE)
                .header("X-Api-Key", XKEY_VALUE)
                .header("Cookie", COOKIE_VALUE)
                .header("User-Agent", "PokeClaw-Test/1.0")
                .post(RequestBody.create(CANARY_BODY, MediaType.parse("application/json")))
                .build();
    }

    private static Response buildResponse(Request request, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Set-Cookie", SET_COOKIE_VALUE)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }

    /** Runs intercept() and returns the single file written, as text. */
    private String runAndReadLog(Request request, Response response, IOException toThrow) throws IOException {
        FileLoggingInterceptor interceptor = new FileLoggingInterceptor(cacheDir);
        try {
            interceptor.intercept(new FakeChain(request, response, toThrow));
        } catch (IOException expected) {
            // failure path: the exception is rethrown by design; the log file is what we check
        }
        File logDir = new File(cacheDir, "http_logs");
        File[] files = logDir.listFiles();
        assertTrue("a log file must be written", files != null && files.length == 1);
        return new String(Files.readAllBytes(files[0].toPath()), StandardCharsets.UTF_8);
    }

    private String runSuccess() throws IOException {
        Request req = buildRequest();
        return runAndReadLog(req, buildResponse(req, CANARY_RESP), null);
    }

    // 1. Authorization value never written; header line shows ***
    @Test
    public void authorizationValueNeverWrittenAndMasked() throws IOException {
        String log = runSuccess();
        assertFalse("Authorization value leaked", log.contains(CANARY_KEY));
        assertFalse("Authorization scheme+value leaked", log.contains(AUTH_VALUE));
        assertTrue("Authorization header line must show ***", log.contains("Authorization: ***"));
    }

    // 2. X-Api-Key, Cookie, Set-Cookie masked
    @Test
    public void apiKeyCookieAndSetCookieMasked() throws IOException {
        String log = runSuccess();
        assertFalse("X-Api-Key value leaked", log.contains(XKEY_VALUE));
        assertFalse("Cookie value leaked", log.contains("CANARY_COOKIE_6f8a"));
        assertFalse("Set-Cookie value leaked", log.contains("CANARY_SETCOOKIE_7b9c"));
        assertTrue("X-Api-Key line must show ***", log.contains("X-Api-Key: ***"));
        assertTrue("Cookie line must show ***", log.contains("Cookie: ***"));
        assertTrue("Set-Cookie line must show ***", log.contains("Set-Cookie: ***"));
    }

    // 3. Request body never written; placeholder with byte count and content type
    @Test
    public void requestBodyOmittedWithByteCountAndContentType() throws IOException {
        String log = runSuccess();
        assertFalse("request body text leaked", log.contains(CANARY_BODY));
        int expectedBytes = CANARY_BODY.getBytes(StandardCharsets.UTF_8).length;
        assertTrue("request body placeholder must show byte count",
                log.contains("[body omitted: " + expectedBytes + " bytes, content-type "));
        assertTrue("request body placeholder must show content type",
                log.contains("content-type application/json"));
    }

    // 4. Response body never written
    @Test
    public void responseBodyNeverWritten() throws IOException {
        String log = runSuccess();
        assertFalse("response body text leaked", log.contains(CANARY_RESP));
        assertTrue("response body placeholder must be present",
                log.contains("[body omitted: " + CANARY_RESP.getBytes(StandardCharsets.UTF_8).length + " bytes"));
    }

    // 5. curl line: no Authorization value, no prefix of it, no body canary
    @Test
    public void curlLineHasNoAuthorizationValueOrPrefixOrBody() throws IOException {
        String log = runSuccess();
        int curlStart = log.indexOf("==================== CURL");
        int curlEnd = log.indexOf("==================== RESPONSE");
        assertTrue("curl section must exist", curlStart >= 0 && curlEnd > curlStart);
        String curl = log.substring(curlStart, curlEnd);

        assertFalse("curl contains Authorization key canary", curl.contains(CANARY_KEY));
        assertFalse("curl contains Authorization full value", curl.contains(AUTH_VALUE));
        // No prefix of the key: the old code kept the first 15 characters of the header value.
        assertFalse("curl contains Authorization prefix 'Bearer sk-CANA'", curl.contains("Bearer sk-CANA"));
        assertFalse("curl contains body canary", curl.contains(CANARY_BODY));
        assertTrue("curl must show redacted Authorization", curl.contains("Authorization: ***"));
        assertTrue("curl must show body omitted", curl.contains("[body omitted]"));
    }

    // 6. Non-sensitive header still written in full: proves the logger still logs
    @Test
    public void nonSensitiveHeaderWrittenInFull() throws IOException {
        String log = runSuccess();
        assertTrue("User-Agent must be written in full",
                log.contains("User-Agent: PokeClaw-Test/1.0"));
        assertTrue("URL must be written", log.contains("URL: https://cloud.invalid/v1/chat/completions"));
    }

    // 7. Exception message not written; class name is
    @Test
    public void exceptionMessageNotWrittenButClassNameIs() throws IOException {
        Request req = buildRequest();
        IOException boom = new IOException("network failed " + CANARY_EXC);
        String log = runAndReadLog(req, null, boom);
        assertFalse("exception message canary leaked", log.contains(CANARY_EXC));
        assertTrue("exception class name must be written",
                log.contains("exception: java.io.IOException"));
    }

    // 8. Sensitive query parameters redacted in URL line and curl line; non-sensitive kept
    @Test
    public void sensitiveQueryParamsRedactedAndNonSensitiveKept() throws IOException {
        Request req = new Request.Builder()
                .url("https://cloud.invalid/v1/items?key=CANARY_QUERY_3c7a&token=CANARY_QUERY_5b1d&page=2")
                .build();
        String log = runAndReadLog(req, buildResponse(req, "ok"), null);
        assertFalse("query key canary leaked", log.contains("CANARY_QUERY_3c7a"));
        assertFalse("query token canary leaked", log.contains("CANARY_QUERY_5b1d"));
        assertTrue("non-sensitive query parameter page=2 must be written", log.contains("page=2"));
        int urlLines = 0;
        for (String line : log.split("\n")) {
            if (line.startsWith("URL: ") || line.contains("'https://cloud.invalid/v1/items")) {
                urlLines++;
            }
        }
        assertTrue("URL line and curl line must both be present", urlLines >= 2);
    }

    /** Headers rule unit check: names that must be masked and names that must not. */
    @Test
    public void headerRuleCoversRequiredNamesAndNotOthers() {
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("Authorization"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("authorization"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("Proxy-Authorization"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("Cookie"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("Set-Cookie"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("X-Api-Key"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("x-api-key"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("api-key"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("X-Auth-Token"));
        assertTrue(FileLoggingInterceptor.isSensitiveHeader("x-session-key"));
        assertFalse(FileLoggingInterceptor.isSensitiveHeader("User-Agent"));
        assertFalse(FileLoggingInterceptor.isSensitiveHeader("Content-Type"));
        assertFalse(FileLoggingInterceptor.isSensitiveHeader("Accept"));
    }

    /** Masked value never reveals a prefix or suffix. */
    @Test
    public void maskedValueIsExactlyPlaceholder() {
        assertTrue(FileLoggingInterceptor.headerValueForLog("Authorization", AUTH_VALUE).equals("***"));
        assertTrue(FileLoggingInterceptor.headerValueForLog("User-Agent", "x").equals("x"));
    }
}
