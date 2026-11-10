# Releasing (fork variant)

Fork-specific release notes. Upstream release process lives in the upstream README (now `docs/README.upstream.md`) and `RELEASING.md` history.

## Versioning

- Fork releases use `versionName = 1.0.0`, `versionCode = 100` (bumped from upstream 0.7.1/29).
- Override without editing the build file: `POKECLAW_VERSION_NAME` / `POKECLAW_VERSION_CODE` via `local.properties` or environment.
- Package id stays `io.agents.pokeclaw`; changing it would break channel integrations, accessibility settings and stored data.
- Git tags: `v<versionName>` (this fork starts at `v1.0.0`).

## Artefacts

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
# -> app/build/outputs/apk/debug/PokeClaw_v1.0.0_<timestamp>.apk
```

The debug APK is signed with the local debug keystore. It is installable and upgradeable over other debug-signed builds only.

## Signed release (not produced in this environment)

`assembleRelease` requires a keystore that is intentionally not committed:

1. Create `~/.config/sanad/release-signing.env` (outside the repo) with `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
2. Wire it into `app/build.gradle.kts` or `~/.gradle/gradle.properties` (never commit secrets).
3. Run `./gradlew assembleRelease` and verify the artifact in `app/build/outputs/apk/release/`.

Upstream history: the original signing key was lost, so a signed fork build cannot upgrade an upstream-signed install; users must uninstall once. Document this in every release note.

## GitHub release (blocked here)

- `gh` CLI is not installed on this machine.
- `origin` still points to upstream `agents-io/PokeClaw` and pushes are denied (403) until a fork exists under `Altamimi79`.

Once the fork exists:

```powershell
git remote rename origin upstream
git remote add origin https://github.com/Altamimi79/<fork-name>.git
git push -u origin main
git push origin v1.0.0
# after installing gh and authenticating:
gh release create v1.0.0 "app/build/outputs/apk/debug/PokeClaw_v1.0.0_<timestamp>.apk" --title "Sanad-Agent v1.0.0" --notes-file docs/CHANGELOG.md
```

## Pre-release gate (mandatory)

Run the relevant `QA_CHECKLIST.md` sections (FA/VA/SA plus release smoke). This phase did **not** execute device QA (no adb device), so v1.0.0 must be treated as a code-complete but device-unverified build.
