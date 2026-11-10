# Skill: Arabic Localization (Sanad fork)

Verified against code at phase 2 (branch `phase-2-arabic-chat`).

## Locale plumbing

- `utils/AppLocale.kt` is the single source of truth. Default language is **Arabic**; English only when the user explicitly picked it. Persisted in plain SharedPreferences file `pokeclaw_locale`, key `KEY_APP_LANGUAGE` (values `ar` / `en`). Not MMKV — locale must be readable before MMKV init during `attachBaseContext`.
- Every entry point wraps the base context: `BaseActivity.attachBaseContext`, `ComposeChatActivity.attachBaseContext`, `ClawApplication.attachBaseContext` call `AppLocale.wrap(base)`. New Activities extending something other than BaseActivity must do the same or they render in system locale.
- Strings: English lives in `res/values/strings.xml` (fallback), Arabic in `res/values-ar/strings.xml`. Every new key must be added to BOTH files; duplicate keys across the two files within the same file are a resource merger build error — grep before adding (`chat_error_prefix` collision was hit once).
- Arabic strings with format args use positional form (`%1$s`) when word order differs from English.

## Font + Compose typography

- Bundled font: `res/font/noto_naskh_arabic_variable.ttf` + alias `res/font/noto_naskh_arabic.xml`. License OFL at `docs/licenses/NotoNaskhArabic-OFL.txt` — keep it.
- Views pick it up via `android:fontFamily` in `res/values/themes.xml`.
- Compose uses `ui/chat/PokeclawTypography.kt`: material3 1.3 (compose-bom 2025.05) REMOVED `Typography(defaultFontFamily=…)` — the font is applied with `.copy()` on all 15 text styles. If the BOM updates again, re-check that constructor before "fixing" a compile error.
- `ComposeChatActivity` wraps content in `MaterialTheme(typography = PokeclawTypography)`.

## RTL rules

- Manifest already has `supportsRtl=true`; Arabic locale flips layouts automatically for Views and Compose (`start`/`end` paddings mirror).
- Directional icons must use `Icons.AutoMirrored.*` (Send, Chat, ArrowBack…). Upstream still uses several non-mirrored icons (deprecation warnings in build) — swap when touching those files; do not mass-refactor just for this.
- Never hardcode LTR strings like `"● Ready"` differently per locale — the bullet glyph is direction-neutral; keep status glyphs identical in both files.

## Adding new UI text (mandatory flow)

1. Add key to `values/strings.xml` AND `values-ar/strings.xml` (same key names).
2. Reference via `getString(R.string.…)` (Views) or `stringResource` (Compose). No hardcoded literals — phase 2 already removed the known ones from ChatScreen/ChatSessionController/LlmConfigActivity.
3. Run `.\gradlew.bat testDebugUnitTest assembleDebug`; resource merger catches missing/duplicate keys.

## QA hooks

QA_CHECKLIST.md §FA1–FA3 cover default-Arabic launch, language switch persistence, and font rendering.
