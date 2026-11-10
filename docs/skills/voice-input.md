# Skill: Arabic Voice Input (Phase 3)

Verified against code at phase 3 (branch `phase-3-voice`).

## Architecture

| Layer | File | Notes |
|---|---|---|
| Pure logic | `voice/VoiceTextNormalizer.kt` | trim, strip bidi/zero-width marks, collapse whitespace, cap length |
| Pure logic | `voice/VoiceTranscriptAssembler.kt` | partials replace each other; final replaces pending partial; finals joined with a space |
| Pure logic | `voice/VoiceDictationPolicy.kt` | `shouldAutoSend(...)` gate |
| Pure logic | `voice/VoiceTimerFormat.kt` | `mm:ss` + elapsed seconds |
| Pure logic | `voice/VoiceError.kt` | error taxonomy with string-resource NAMES (UI maps to R.string) |
| Android | `voice/SpeechRecognizerController.kt` | SpeechRecognizer wrapper (main-thread only) |
| UI | `ui/voice/VoiceRecordingBar.kt` | pulse + timer + partials + cancel |
| UI wiring | `ui/chat/ChatScreen.kt` → `ChatInputBar` | permission, mic tap, transcript → composer, auto-send |

## Recognition behavior

- Language is forced to **ar-SA** (`SpeechRecognizerController.DEFAULT_LANGUAGE`), independent of the UI locale.
- Intent extras: free-form model, partial results, 3 hypotheses, silence tuning (complete 1.5s / possibly 0.9s / minimum 0.8s), calling package.
- Hard cap `DEFAULT_MAX_DURATION_MS = 60_000`. On cap the controller calls `stopListening()`; if the engine then errors without a final, the **last partial transcript is used** instead of discarding speech.
- Mic tap while recording = graceful stop (final result still arrives). Cancel discards.
- `SpeechRecognizer` must be created/started/stopped/destroyed on the main thread; the controller enforces main-thread Handler usage. Recreate per session; `destroy()` in `DisposableEffect`.

## Permission flow

- `RECORD_AUDIO` is declared in `AndroidManifest.xml` plus a `<queries>` intent for `android.speech.RecognitionService` (Android 11+ package visibility).
- Chat requests the permission on the first mic tap; denial shows `voice_permission_denied` (Arabic in `values-ar`). No crash path.
- `SpeechRecognizer.isRecognitionAvailable(context)` is checked before start; unavailable shows `voice_input_unavailable`.

## Transcript handling

1. Final text → `VoiceTextNormalizer.normalize`.
2. Appended to the existing composer text (prefix space if needed) — never replaces what the user already typed.
3. If `KVUtils.isVoiceAutoSendEnabled()` (Settings → Tools → Auto-send voice input, default **off**) and the composer is free (`VoiceDictationPolicy.shouldAutoSend`), send immediately; otherwise focus the field for editing.

## Error → Arabic messages

`VoiceError` keys map to `voice_error_*` strings in both locales: `no_match`, `timeout`, `network`, `permission`, `busy`, `audio`, `server`, `client`, `language`, `unknown`. UI mapping lives in `voiceErrorRes()` in `ChatScreen.kt` — keep the `when` exhaustive when adding errors.

## Replies stay written only

There is **no TextToSpeech** anywhere in the app (grep `TextToSpeech` returns nothing). Voice input only produces text; assistant replies remain written Arabic per `LanguagePolicy`.

## Offline Whisper — why skipped (phase 3)

- Upstream's on-device runtime is **LiteRT-LM for text LLMs**; it has no audio encoder/Whisper graph, so an offline Whisper would need a new JNI runtime (whisper.cpp/ONNX) plus a model.
- Model size: Whisper tiny is ~75 MB with weak Arabic accuracy; a usable Arabic model (medium/large) is 0.5–1.5 GB and needs serious RAM — conflicts with this fork's low-RAM device target.
- No Android device/emulator was available to validate latency/reliability at phase close; shipping an unverified native STT stack would violate the fork's "fix deterministic problems first" rule.
- Android's own `SpeechRecognizer` already performs on-device recognition when the device speech service has the Arabic offline language pack installed; `ar-SA` is requested explicitly. Revisit Whisper in P4/P5 with a device farm and measured WER/latency.

## Tests

`.\gradlew.bat testDebugUnitTest` — `VoiceTextNormalizerTest`, `VoiceTranscriptAssemblerTest`, `VoiceDictationPolicyTest`, `VoiceTimerFormatTest`, `VoiceErrorTest`. Controller needs device coverage: QA_CHECKLIST §VA.
