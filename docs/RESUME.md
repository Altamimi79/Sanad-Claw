# Resume here (handoff to another machine)

Last updated: 2026-11-13

## Exact state
- Branch: `sanad/dev-1.0.3`. HEAD and origin are both `14b719c1ceba07d2a141e6cd7c2246e88c7f256c`.
- Tags on origin: `v1.0.3-dev` (067aa33), `v1.0.3-dev-2` (14b719c).
- Pre-releases on GitHub: `v1.0.3-dev-2` (current build), `v1.0.3-dev`. Latest release is still `v1.0.2`.
- Latest APK: `PokeClaw_v1.0.3-dev-2_20261113_080229.apk`, SHA-256 `D49D6D047AC4F966A816F0B1E311585683DB2E208EF0E974DA118A39A9B06DE8`.
- Unit tests: 271 tests, 51 classes, 0 failures.

## Not committed on purpose
- `.kilo/plans/` (local plan file). Not part of the code.

## Open decisions
1. APK naming: keep `PokeClaw_...` or change to `SanadClaw` (edit line 201 of `app/build.gradle.kts`).
2. Whether to publish any build as Latest (currently none).

## Not done (blocking for any usage announcement)
- Device QA: NOT EXECUTED. No device was connected. Checklist: `docs/DEVICE_QA_SESSION.md` (IJ, SD, FL3, QP, QL, QV).

## Next steps on the other machine
1. `git fetch origin --tags` then `git checkout sanad/dev-1.0.3`.
2. Confirm HEAD equals `14b719c1ceba07d2a141e6cd7c2246e88c7f256c`.
3. Build: `.\gradlew.bat assembleDebug` (needs JDK 21 per AGENTS.md).
4. Run device checklist when a device is connected.