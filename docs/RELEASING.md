# Releasing (fork variant)

Fork-specific release notes. Upstream release process lives in the upstream README (now `docs/README.upstream.md`).

## Versioning

- Current fork line: `versionName = 1.0.3-dev-3`, `versionCode = 104` (defaults in `app/build.gradle.kts`).
- Override without editing the build file: `POKECLAW_VERSION_NAME` / `POKECLAW_VERSION_CODE` via `local.properties` or environment.
- Package id stays `io.agents.pokeclaw`; changing it would break accessibility settings and stored data.
- APK file name: `SanadClaw_v<versionName>_<timestamp>.apk`.
- Git tags: `v<versionName>` (e.g. `v1.0.3-dev-3`).
- `origin` = https://github.com/Altamimi79/Sanad-Claw ; `upstream` = agents-io/PokeClaw.

## Artefacts

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
# -> app\build\outputs\apk\debug\SanadClaw_v<version>_<timestamp>.apk
```

The debug APK is signed with the local debug keystore. It is installable and upgradeable over other debug-signed builds only. Installing over a build signed with a different key requires uninstalling first (app data is cleared).

## Signed release (not produced yet)

`assembleRelease` needs a keystore that is intentionally not committed (see the `release-signing.env` setup outside the repo). Until a fork signing key exists, publish debug-signed APKs as pre-releases only.

## GitHub pre-release

Requires the `gh` CLI, authenticated (`gh auth login`).

```powershell
git tag -a v1.0.3-dev-3 -m "Sanad Claw 1.0.3-dev-3" ; git push origin v1.0.3-dev-3
gh release create v1.0.3-dev-3 "app\build\outputs\apk\debug\SanadClaw_v1.0.3-dev-3_<timestamp>.apk" --prerelease --title "Sanad Claw 1.0.3-dev-3" --notes-file docs\DEVICE_QA_SESSION.md
```

Do not mark a pre-release as Latest until device QA in `docs/DEVICE_QA_SESSION.md` is complete.

## Pre-release gate (mandatory)

Run the relevant `QA_CHECKLIST.md` sections and record results in `docs/DEVICE_QA_SESSION.md`. Unit tests must be green (271 at this version).
