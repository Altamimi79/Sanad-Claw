// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.http.client.HttpClient
import io.agents.pokeclaw.agent.langchain.http.OkHttpClientAdapter
import io.agents.pokeclaw.agent.langchain.http.OkHttpClientBuilderAdapter
import io.agents.pokeclaw.agent.llm.OpenAiLlmClient
import io.agents.pokeclaw.safety.ScreenContentGuard
import io.agents.pokeclaw.safety.SensitiveDataRedactor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloud transport check (phase 2d step 2). No mock library.
 *
 * Production request path exercised:
 *   OpenAiLlmClient.chat
 *     -> langchain4j OpenAI request builder (request body serialization)
 *     -> HttpClient built by OkHttpClientBuilderAdapter.build (overridden in this test to
 *        return the PRODUCTION OkHttpClientAdapter around an OkHttp client that has the
 *        capturing interceptor)
 *     -> OkHttpClientAdapter.execute -> OkHttp Call -> CapturingInterceptor (no socket).
 *
 * Only the network hop is replaced. The body captured is exactly what production sends.
 */
class CloudTransportPayloadTest {

    /** Captures the outgoing request body and URL, then returns a canned chat completion. */
    private class CapturingInterceptor : Interceptor {
        val bodies = mutableListOf<String>()
        val urls = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            urls += req.url.toString()
            val buf = Buffer()
            req.body?.writeTo(buf)
            bodies += buf.readUtf8()
            val json = """{"id":"x","object":"chat.completion","created":0,"model":"m",""" +
                """"choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],""" +
                """"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    /**
     * Subclass of the production builder. Only build() is overridden: it returns the
     * production OkHttpClientAdapter wrapped around a client with the capturing interceptor.
     */
    private class CapturingBuilder(private val interceptor: Interceptor) : OkHttpClientBuilderAdapter() {
        override fun build(): HttpClient = OkHttpClientAdapter(
            OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )
    }

    private val card = "4111 1111 1111 1111"
    private val cardCompact = "4111111111111111"
    private val iban = "GB82WEST12345698765432"
    private val nationalId = "1098765432"
    private val otp = "483921"

    /** Same two steps DefaultAgentService applies to each tool result: wrap, then redact. */
    private fun toolResultFromScreen(raw: String): String {
        val wrapped = ScreenContentGuard().wrap("tool_get_screen_info", raw).text
        return SensitiveDataRedactor.redact(wrapped).text
    }

    private fun clientWith(capture: CapturingInterceptor): OpenAiLlmClient {
        val config = AgentConfig(
            apiKey = "test-key-not-real",
            baseUrl = "https://cloud.invalid/v1",
            modelName = "test-model",
            provider = LlmProvider.OPENAI,
        )
        return OpenAiLlmClient(config, CapturingBuilder(capture))
    }

    @Test
    fun capturedCloudRequestBodyContainsNoSensitiveValuesAndHasPlaceholders() {
        val capture = CapturingInterceptor()
        val screen = "Card $card, IBAN $iban, id $nationalId, verification code is $otp, card2 $cardCompact"
        val messages = listOf(
            SystemMessage.from("system rules"),
            UserMessage.from("read the screen and summarize"),
            ToolExecutionResultMessage.from("call-1", "get_screen_info", toolResultFromScreen(screen)),
        )

        clientWith(capture).chat(messages, emptyList())

        assertTrue("exactly one HTTP request must be captured", capture.bodies.size == 1)
        val body = capture.bodies.single()

        // Sensitive values must NOT appear in the request body.
        assertFalse("card number leaked", body.contains("4111"))
        assertFalse("compact card leaked", body.contains(cardCompact))
        assertFalse("IBAN leaked", body.contains("GB82WEST"))
        assertFalse("national ID leaked", body.contains(nationalId))
        assertFalse("OTP leaked", body.contains(otp))

        // Placeholders MUST appear.
        assertTrue("card placeholder missing", body.contains("[REDACTED:card]"))
        assertTrue("IBAN placeholder missing", body.contains("[REDACTED:iban]"))
        assertTrue("national-id placeholder missing", body.contains("[REDACTED:national_id]"))
        assertTrue("otp placeholder missing", body.contains("[REDACTED:otp]"))

        // The untrusted delimiter survives into the request.
        assertTrue("untrusted delimiter missing", body.contains("untrusted_content"))
    }

    @Test
    fun requestPathIsTheOpenAiChatCompletionsEndpoint() {
        val capture = CapturingInterceptor()
        clientWith(capture).chat(listOf(UserMessage.from("hi")), emptyList())
        assertTrue(capture.urls.single().endsWith("/chat/completions"))
    }
}
