// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.agents.pokeclaw.utils.XLog

/**
 * In-app Arabic dictation on top of Android's [SpeechRecognizer] (phase 3).
 *
 * - Language is forced to ar-SA (Saudi Arabic) regardless of UI locale.
 * - Partials are surfaced for live feedback; the final hypothesis replaces them.
 * - Silences: complete 3.0s / possibly-complete 2.0s / minimum 1.5s (patient enough for
 *   slow or paused speech; the user can always tap the mic to stop early).
 * - Hard cap [maxDurationMs]; on timeout the controller stops gracefully and, if the
 *   engine reports no final result, falls back to the last partial transcript so the
 *   user does not lose what they said.
 * - All SpeechRecognizer calls happen on the main thread (Android requirement).
 *
 * Binding: create/destroy via the Compose owner; call [start], [stop], [cancel], [destroy].
 */
class SpeechRecognizerController(
    private val context: Context,
    private val listener: Listener,
    private val languageTag: String = DEFAULT_LANGUAGE,
    private val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
) {

    interface Listener {
        fun onListening()
        fun onProcessing()
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        /**
         * Hard-cap fallback: the engine reported an error after the max-duration stop and
         * the last partial is used. Never a recognized final result, so it must not be
         * routed to command handling. Dictation may insert it as text.
         */
        fun onTimeoutPartial(text: String) {}
        fun onError(error: VoiceError)
        fun onRmsChanged(rmsDb: Float)
    }

    companion object {
        private const val TAG = "VoiceInput"
        const val DEFAULT_LANGUAGE = "ar-SA"
        const val DEFAULT_MAX_DURATION_MS = 120_000L
        private const val RESTART_DELAY_MS = 150L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val assembler = VoiceTranscriptAssembler()
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var finalDelivered = false
    private var timedOut = false
    private var stoppedByUser = false
    private var segments = 0
    private var silentRetries = 0
    private var startedAt = 0L

    private val timeoutRunnable = Runnable {
        if (listening && !finalDelivered) {
            timedOut = true
            XLog.w(TAG, "max duration reached (${maxDurationMs}ms), stopping gracefully")
            runCatching { recognizer?.stopListening() }
        }
    }

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (e: Exception) {
        XLog.w(TAG, "isRecognitionAvailable failed: ${e.message}")
        false
    }

    fun start(): Boolean {
        if (!isAvailable()) {
            XLog.w(TAG, "start: recognition unavailable")
            return false
        }
        if (listening) {
            XLog.w(TAG, "start: already listening")
            return false
        }
        release()
        assembler.reset()
        finalDelivered = false
        timedOut = false
        stoppedByUser = false
        segments = 0
        silentRetries = 0
        startedAt = SystemClock.elapsedRealtime()

        if (!openEngine()) return false
        listener.onListening()
        mainHandler.postDelayed(timeoutRunnable, maxDurationMs)
        XLog.i(TAG, "start: listening language=$languageTag (continuous dictation)")
        return true
    }

    /**
     * Opens a fresh engine for one segment. A new instance per segment avoids the
     * ERROR_CLIENT callback that reusing one after cancel() would deliver.
     */
    private fun openEngine(): Boolean {
        releaseRecognizer()
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            XLog.e(TAG, "createSpeechRecognizer failed", e)
            null
        }
        if (created == null) return false
        recognizer = created
        created.setRecognitionListener(recognitionListener)
        return try {
            created.startListening(buildIntent())
            listening = true
            true
        } catch (e: Exception) {
            XLog.e(TAG, "startListening failed", e)
            releaseRecognizer()
            false
        }
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    /** Graceful stop — the engine may still deliver a final result. */
    fun stop() {
        if (!listening || finalDelivered) return
        stoppedByUser = true
        XLog.d(TAG, "stop requested")
        runCatching { recognizer?.stopListening() }
    }

    /** Cancel and discard everything. */
    fun cancel() {
        XLog.d(TAG, "cancel requested")
        finalDelivered = true
        release()
    }

    fun destroy() {
        finalDelivered = true
        release()
    }

    private fun release() {
        mainHandler.removeCallbacks(timeoutRunnable)
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        listening = false
        val current = recognizer
        recognizer = null
        if (current != null) {
            runCatching { current.setRecognitionListener(null) }
            runCatching { current.destroy() }
        }
    }

    private fun mapError(code: Int): VoiceError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.TIMEOUT
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> VoiceError.NETWORK
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
        SpeechRecognizer.ERROR_AUDIO -> VoiceError.AUDIO
        SpeechRecognizer.ERROR_SERVER -> VoiceError.SERVER
        SpeechRecognizer.ERROR_CLIENT -> VoiceError.CLIENT
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> VoiceError.LANGUAGE
        else -> VoiceError.UNKNOWN
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            XLog.d(TAG, "onReadyForSpeech")
            listener.onListening()
        }

        override fun onBeginningOfSpeech() {
            XLog.d(TAG, "onBeginningOfSpeech")
            listener.onListening()
        }

        override fun onRmsChanged(rmsdB: Float) {
            listener.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            XLog.d(TAG, "onEndOfSpeech")
            listener.onProcessing()
        }

        override fun onError(error: Int) {
            if (finalDelivered) return
            val fallback = assembler.current()
            if (timedOut && fallback.isNotEmpty()) {
                finalDelivered = true
                XLog.i(TAG, "timeout but partial transcript available (${fallback.length} chars) — using it")
                listener.onTimeoutPartial(fallback)
                release()
                return
            }
            val mapped = mapError(error)
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            when (VoiceSessionPolicy.decideOnError(mapped, fallback.isNotEmpty(), silentRetries, elapsed)) {
                VoiceSessionPolicy.OnError.DELIVER -> {
                    finalDelivered = true
                    XLog.i(TAG, "engine ended with $mapped after ${fallback.length} chars — delivering transcript")
                    listener.onFinalText(fallback)
                    release()
                }
                VoiceSessionPolicy.OnError.RETRY -> {
                    silentRetries++
                    XLog.d(TAG, "transient $mapped before speech — silent retry $silentRetries")
                    mainHandler.postDelayed({
                        if (!finalDelivered) {
                            if (openEngine()) {
                                listener.onListening()
                            } else {
                                finalDelivered = true
                                listener.onError(mapped)
                                release()
                            }
                        }
                    }, RESTART_DELAY_MS)
                }
                VoiceSessionPolicy.OnError.FAIL -> {
                    finalDelivered = true
                    XLog.w(TAG, "onError code=$error mapped=$mapped partialLen=${fallback.length}")
                    listener.onError(mapped)
                    release()
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (finalDelivered) return
            val best = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (best.isNullOrBlank()) return
            assembler.onPartial(best)
            listener.onPartialText(assembler.current())
        }

        override fun onResults(results: Bundle?) {
            if (finalDelivered) return
            mainHandler.removeCallbacks(timeoutRunnable)
            segments++
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            assembler.onFinal(best)
            val accumulated = assembler.current()
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (accumulated.isBlank()) {
                val retry = VoiceSessionPolicy.decideOnError(VoiceError.NO_MATCH, false, silentRetries, elapsed) ==
                    VoiceSessionPolicy.OnError.RETRY
                if (retry) {
                    silentRetries++
                    XLog.d(TAG, "empty result — silent retry $silentRetries")
                    mainHandler.postDelayed({
                        if (!finalDelivered) {
                            if (openEngine()) {
                                listener.onListening()
                            } else {
                                finalDelivered = true
                                listener.onError(VoiceError.NO_MATCH)
                                release()
                            }
                        }
                    }, RESTART_DELAY_MS)
                    return
                }
                finalDelivered = true
                XLog.w(TAG, "onResults but empty transcript")
                listener.onError(VoiceError.NO_MATCH)
                release()
                return
            }
            val keepListening = VoiceSessionPolicy.decideOnResult(stoppedByUser, segments, elapsed, maxDurationMs) ==
                VoiceSessionPolicy.OnResult.CONTINUE
            if (keepListening) {
                XLog.i(TAG, "segment $segments ended; still listening (${accumulated.length} chars)")
                listener.onPartialText(accumulated)
                mainHandler.postDelayed({
                    if (!finalDelivered) {
                        if (openEngine()) {
                            listener.onListening()
                        } else {
                            finalDelivered = true
                            listener.onFinalText(accumulated)
                            release()
                        }
                    }
                }, RESTART_DELAY_MS)
                return
            }
            finalDelivered = true
            listening = false
            XLog.i(TAG, "final transcript=${accumulated.length} chars segments=$segments")
            listener.onFinalText(accumulated)
            release()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
