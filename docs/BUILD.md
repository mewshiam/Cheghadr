# Build & release

## Toolchain (verified versions)

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 (Temurin 17.0.20 tested) | required; a JRE-only install fails (`JAVA_COMPILER` missing) |
| Gradle | 8.10.2 | via wrapper (`./gradlew`); no local install needed |
| Android Gradle Plugin | 8.7.3 | see `gradle/libs.versions.toml` for the full matrix |
| Android SDK | platform **android-35**, build-tools **34.0.0** | `local.properties` → `sdk.dir` or `ANDROID_HOME` |
| Kotlin | 2.0.21 (KSP 2.0.21-1.0.27) | Compose compiler via the Kotlin plugin |

## Everyday commands

```bash
./gradlew assembleDebug        # debug APK  → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # R8-minified unsigned APK → app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew test                 # all JVM unit tests (142 at the time of writing)
./gradlew lint                 # Android lint
```

First run downloads Gradle and dependencies; on a 2-CPU container the release
build takes ~3–4 minutes.

## Signing a release

Signing material is **never committed**. The release build reads four
environment variables (also honored by the GitHub Actions release workflow):

```bash
export CHEGHADR_KEYSTORE_PATH=/absolute/path/keystore.jks
export CHEGHADR_KEYSTORE_PASSWORD=…
export CHEGHADR_KEY_ALIAS=…
export CHEGHADR_KEY_PASSWORD=…
./gradlew assembleRelease      # → app/build/outputs/apk/release/app-release.apk (signed)
```

Create a keystore once:

```bash
keytool -genkeypair -v -keystore cheghadr-release.jks \
  -alias cheghadr -keyalg RSA -keysize 4096 -validity 10000
```

Without the variables the release APK is built **unsigned** (still useful for
local testing; sideload via `adb install --bypass-low-target-sdk-block` is not
required since targetSdk is 34).

An `.env.example` documents the same variables for CI.

## CI / CD

- `.github/workflows/ci.yml` — on every push/PR: JDK 17 + Android SDK setup,
  `test`, `lint`, `assembleDebug`.
- `.github/workflows/release.yml` — on tag `v*`: full build, then a GitHub
  Release carrying `Cheghadr-debug-<tag>.apk` and the (signed-when-secrets-
  present) release APK. Repository secrets (optional):
  `CHEGHADR_KEYSTORE_B64`, `CHEGHADR_KEYSTORE_PASSWORD`, `CHEGHADR_KEY_ALIAS`,
  `CHEGHADR_KEY_PASSWORD`. Without them the release APK is attached unsigned.

Publish a release:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

## Release checklist

1. [ ] `./gradlew test lint assembleDebug assembleRelease` — all green locally.
2. [ ] Update `versionCode` / `versionName` in `app/build.gradle.kts`.
3. [ ] Review `docs/DATA_SOURCES.md` — re-verify each provider still works and
       its terms have not changed (spot-check the endpoints).
4. [ ] Install the debug APK on a real device; smoke-test: first launch (fa),
       language switch to English, dark theme + dynamic colors, markets tabs,
       favorite/unfavorite persistence across restart, converter math, gold
       calculator, alert creation + quiet hours, add both widgets, airplane
       mode (stale labels), share text.
5. [ ] If publishing on GitHub Releases: tag `vX.Y.Z` and let the workflow
       attach the APKs; verify the APKs install from the release page.
6. [ ] Confirm no secrets (keystores, keys, `.env`) are in the commit.
7. [ ] Decide on an open-source license for the code (currently none — the
       repo owner must choose; the bundled font is OFL-1.1 regardless).
8. [ ] (Play Store path) bump targetSdk to 35 after on-device validation of
       Android 15 edge-to-edge behavior; AAB via `./gradlew bundleRelease`.
