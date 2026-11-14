// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.langchain.http;

import io.agents.pokeclaw.utils.XLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * OkHttp interceptor: writes request metadata, a redacted curl command, and response metadata
 * to individual files in the sandbox cache directory.
 * <p>
 * Security rules (P0, phase 2f):
 * <ul>
 *   <li>Sensitive header values (Authorization, Cookie, API keys, any name containing
 *       "token" or "key") are replaced by {@value #REDACTED}. No prefix or suffix is kept.</li>
 *   <li>Request and response BODIES are never written. Only the byte count and content type
 *       are recorded, as {@code [body omitted: N bytes, content-type X]}.</li>
 *   <li>The curl line keeps every header in redacted form and omits the body.</li>
 *   <li>Exception text is not written; only the exception class name is.</li>
 * </ul>
 * <p>
 * File path: {cacheDir}/http_logs/yyyyMMdd_HHmmssSSS_{method}.txt
 */
public class FileLoggingInterceptor implements Interceptor {

    private static final String TAG = "FileLoggingInterceptor";

    /** Replacement for every sensitive header value. */
    static final String REDACTED = "***";

    private final File logDir;

    public FileLoggingInterceptor(File cacheDir) {
        this.logDir = new File(cacheDir, "http_logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // Measure the request body without writing it anywhere.
        long requestBodyBytes = 0;
        String requestContentType = "none";
        if (request.body() != null) {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            requestBodyBytes = buffer.size();
            MediaType type = request.body().contentType();
            requestContentType = type != null ? type.toString() : "unknown";
        }

        String curl = buildCurl(request);

        // Execute request
        long startMs = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            // Log even on request failure: class name only, never the message text.
            writeFailure(request, requestBodyBytes, requestContentType, curl, e);
            throw e;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startMs);

        // Read response body so its size is known; re-wrap so the caller can still read it.
        long responseBodyBytes = 0;
        String responseContentType = "none";
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
            MediaType contentType = responseBody.contentType();
            responseContentType = contentType != null ? contentType.toString() : "unknown";
            String responseBodyStr = responseBody.string();
            responseBodyBytes = responseBodyStr.getBytes(StandardCharsets.UTF_8).length;
            // Re-wrap ResponseBody because string() can only be consumed once
            response = response.newBuilder()
                    .body(ResponseBody.create(contentType, responseBodyStr))
                    .build();
        }

        writeToFile(request, requestBodyBytes, requestContentType, curl,
                response, response.code(), responseBodyBytes, responseContentType, durationMs);

        return response;
    }

    /**
     * Returns the value to write for a header. Sensitive headers become {@value #REDACTED};
     * no part of the original value is kept.
     */
    static String headerValueForLog(String name, String value) {
        return isSensitiveHeader(name) ? REDACTED : value;
    }

    /**
     * Sensitive when the name is Authorization, Proxy-Authorization, Cookie, Set-Cookie,
     * X-Api-Key, api-key (any case), or contains "token" or "key" (any case).
     */
    static boolean isSensitiveHeader(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        switch (n) {
            case "authorization":
            case "proxy-authorization":
            case "cookie":
            case "set-cookie":
            case "x-api-key":
            case "api-key":
                return true;
            default:
                return n.contains("token") || n.contains("key");
        }
    }

    /**
     * True when a query parameter name is sensitive: key, token, api_key, apikey,
     * access_token, or any name containing "key" or "token" (case-insensitive).
     */
    static boolean isSensitiveQueryParam(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        switch (n) {
            case "key":
            case "token":
            case "api_key":
            case "apikey":
            case "access_token":
                return true;
            default:
                return n.contains("key") || n.contains("token");
        }
    }

    /**
     * Returns the URL as text with every sensitive query parameter value replaced by "***".
     * All other parts of the URL, and non-sensitive parameters, are kept unchanged.
     */
    static String redactedUrl(okhttp3.HttpUrl url) {
        if (url.querySize() == 0) return url.toString();
        okhttp3.HttpUrl.Builder b = url.newBuilder();
        for (int i = 0; i < url.querySize(); i++) {
            String name = url.queryParameterName(i);
            if (isSensitiveQueryParam(name)) {
                b.setQueryParameter(name, REDACTED);
            }
        }
        return b.build().toString();
    }

    static String bodyOmittedLine(long bytes, String contentType) {
        return "[body omitted: " + bytes + " bytes, content-type " + contentType + "]";
    }

    String buildCurl(Request request) {
        StringBuilder sb = new StringBuilder("curl -X ").append(request.method());

        // Headers, redacted by the same rule as the header section
        for (int i = 0; i < request.headers().size(); i++) {
            String name = request.headers().name(i);
            String value = headerValueForLog(name, request.headers().value(i));
            sb.append(" \\\n  -H '").append(name).append(": ").append(value.replace("'", "'\\''")).append("'");
        }

        // Body: never included in the curl line
        if (request.body() != null) {
            sb.append(" \\\n  -d '[body omitted]'");
        }

        sb.append(" \\\n  '").append(redactedUrl(request.url())).append("'");
        return sb.toString();
    }

    private void writeFailure(Request request, long requestBytes, String requestContentType,
                              String curl, IOException e) {
        writeToFile(request, requestBytes, requestContentType, curl, null, -1, 0, "none", 0,
                "exception: " + e.getClass().getName());
    }

    private void writeToFile(Request request, long requestBytes, String requestContentType, String curl,
                             Response response, int statusCode, long responseBytes,
                             String responseContentType, long durationMs) {
        writeToFile(request, requestBytes, requestContentType, curl, response, statusCode,
                responseBytes, responseContentType, durationMs, null);
    }

    private void writeToFile(Request request, long requestBytes, String requestContentType, String curl,
                             Response response, int statusCode, long responseBytes,
                             String responseContentType, long durationMs, String failureNote) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            String fileName = timestamp + "_" + request.method() + ".txt";
            File file = new File(logDir, fileName);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write("==================== REQUEST ====================\n");
                writer.write("URL: " + redactedUrl(request.url()) + "\n");
                writer.write("Method: " + request.method() + "\n");
                writer.write("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "\n");
                writer.write("\n--- Headers ---\n");
                for (int i = 0; i < request.headers().size(); i++) {
                    String name = request.headers().name(i);
                    writer.write(name + ": " + headerValueForLog(name, request.headers().value(i)) + "\n");
                }
                writer.write("\n--- Body ---\n");
                writer.write(bodyOmittedLine(requestBytes, requestContentType) + "\n");

                writer.write("\n==================== CURL ====================\n");
                writer.write(curl + "\n");

                writer.write("\n==================== RESPONSE ====================\n");
                writer.write("Status: " + statusCode + "\n");
                writer.write("Duration: " + durationMs + "ms\n");
                if (failureNote != null) {
                    writer.write(failureNote + "\n");
                }
                if (response != null) {
                    writer.write("\n--- Headers ---\n");
                    for (int i = 0; i < response.headers().size(); i++) {
                        String name = response.headers().name(i);
                        writer.write(name + ": " + headerValueForLog(name, response.headers().value(i)) + "\n");
                    }
                }
                writer.write("\n--- Body ---\n");
                writer.write(bodyOmittedLine(responseBytes, responseContentType) + "\n");
            }

            XLog.d(TAG, "Log written to: " + file.getAbsolutePath());
        } catch (Exception e) {
            XLog.e(TAG, "Failed to write log file", e);
        }
    }
}
