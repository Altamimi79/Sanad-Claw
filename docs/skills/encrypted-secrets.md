# Skill: Encrypted Secrets Storage (SecureStore)

Verified against code at phase 2 (branch `phase-2-arabic-chat`). This replaced plaintext MMKV for all secret values.

## What counts as a secret

`KEY_LLM_API_KEY`, `KEY_LLM_API_KEY_<PROVIDER>`, `DEFAULT_DISCORD_BOT_TOKEN`, `DEFAULT_TELEGRAM_BOT_TOKEN`, `DEFAULT_WECHAT_BOT_TOKEN`. Anything else (URLs, model ids, flags, prompts) stays in MMKV via `KVUtils` plain accessors.

## How it works

- `utils/SecureStore.kt`: EncryptedSharedPreferences `pokeclaw_secure` — AES256-GCM values + AES256-SIV key names, master key from Android Keystore (`MasterKey.Builder(...).setKeyScheme(AES256_GCM)`).
- Fallback: if keystore init throws, a private-mode file `pokeclaw_secure_fallback` is used and a warning is logged WITHOUT secret values. Availability beats silent failure (project rule), but log the fallback loudly.
- Init order: `KVUtils.init(context)` calls `SecureStore.init(context)`; migration copies any still-plaintext secrets out of MMKV (`isSecretKey()` filter) into SecureStore, then removes the MMKV copies. Migration skips keys already present in the secure store.
- `androidx.security:security-crypto` is deprecated upstream (1.1.0 is final). It works today; P4 should evaluate alternatives (e.g. Tink direct or Keystore-backed file) — flag before building more on it.

## Adding a new secret (mandatory flow)

1. Add the key constant in `KVUtils` with the other config keys.
2. Accessors MUST delegate to SecureStore, mirroring existing pairs:
   `fun getX(): String = SecureStore.getString(KEY_X, "")` / `fun setX(v: String) = SecureStore.putString(KEY_X, v)`.
3. If the key can pre-exist in MMKV from older builds, add its pattern to `SecureStore.isSecretKey()` so migration picks it up.
4. Never log the value — key name only (`XLog.w(TAG, "... key=$key")` style). Never put values in Toasts or user-visible errors.

## ConfigServer masking

- GET responses pass secret values through `utils/SecretsMasker.mask(...)` → value replaced with asterisks, key NAMES preserved (clients need to know which keys are set).
- POST handling detects masked values (contains `*`) and skips them, so a client round-tripping a GET payload never overwrites real keys. Keep this guard when adding new config endpoints.

## Tests / QA

- Unit: `app/src/test/.../SecretsMaskerTest.kt` (`.\gradlew.bat testDebugUnitTest`).
- Device `[DEBUG-ONLY]`: `adb shell run-as io.agents.pokeclaw cat shared_prefs/pokeclaw_secure.xml` → values must be ciphertext; MMKV files must contain no plaintext secret copies (QA_CHECKLIST §FA11).
