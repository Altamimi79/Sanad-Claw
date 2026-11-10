# Skill: Build the APK (Windows)

Verified 2026-11-10. Prerequisites already installed on this machine — see docs/MEMORY.md "Build Environment".

## Debug build

```powershell
cd E:\Sanad-Agent
.\gradlew.bat assembleDebug
```

- Output: `app\build\outputs\apk\debug\PokeClaw_v<versionName>_<timestamp>.apk` (name set by `androidComponents` block in app/build.gradle.kts).
- Requires: Android SDK with licenses accepted (`%LOCALAPPDATA%\Android\Sdk`, wired via gitignored `local.properties` → `sdk.dir=...`), JDK 21 Gradle toolchain (via user-level `~\.gradle\gradle.properties` → `org.gradle.java.installations.paths=C:/Users/Altamimi/.gradle/jdks-manual/jdk-21.0.7+6`).

## Release build (local signing)

1. Create keystore: `keytool -genkey -v -keystore pokeclaw-release.keystore -alias pokeclaw -keyalg RSA -keysize 2048 -validity 10000` (store OUTSIDE repo or in gitignored path).
2. Add to `local.properties` (gitignored): `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (env vars also accepted — app/build.gradle.kts:34-51).
3. `.\gradlew.bat assembleRelease`. CI alternative: see RELEASING.md + `.github/workflows/release.yml` (signs on tag using Actions secrets).

## Install / smoke test via ADB

```powershell
adb install -r app\build\outputs\apk\debug\<apk>
adb logcat --pid=$(pidof io.agents.pokeclaw)   # XLog structured logging
# Grant core permissions headlessly (see docs/AI_AGENT_PLAYBOOK.md §9.5 for exact commands)
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Wrapper download timeout from `mirrors.cloud.tencent.com` | Already fixed: `gradle/wrapper/gradle-wrapper.properties` → `https://services.gradle.org/distributions/gradle-9.3.1-all.zip`, `networkTimeout=120000` |
| `Unable to download toolchain {languageVersion=21}` / foojay move error | Extract Temurin 21 zip manually to `~\.gradle\jdks-manual\jdk-21.0.7+6`; set `org.gradle.java.installations.paths` + `auto-download=false` in user-level `~\.gradle\gradle.properties` |
| `licences have not been accepted` (build-tools/platform) | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --licenses` and answer `y` (or pipe `"y"` ×N) |
| Missing cmdline-tools in SDK | Download `commandlinetools-win-<build>_latest.zip` from dl.google.com, unzip to `Sdk\cmdline-tools\latest` (folder must be named `latest`) |
| "SDK XML versions up to 3 … version 4" warning | Harmless mismatch between AGP and cmdline-tools generations; build proceeds |
