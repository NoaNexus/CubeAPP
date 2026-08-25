# Source and build correspondence

- Application: 方迹 CubeTrace
- Application ID: `com.cubetrace.app`
- Version: `0.2.5` / versionCode `7`
- License baseline: GPL-3.0-only
- Build: Android Studio + Java 17 + Android SDK 35 + Gradle project in this directory
- Network permission: none

For offline sharing, provide this source tree together with the same-version APK. Do not place signing keys, `local.properties`, device MAC addresses, diagnostic logs, or user backups in the source archive.

The `0.2.5` package is a private self-use test release. Its exact Git commit, signing-certificate fingerprint and APK SHA-256 are recorded in the generated release notes; no signing key is stored in this repository.
## Signing continuity

Official APKs use an external signing-certificate lineage from the historical Android debug certificate to the stable Fangji release certificate. Because minSdk is 26, every future official APK must keep the oldest signer for v2 on API 26–27 and use the rotated release signer plus the lineage for v3 on API 28+. Do not publish a new APK signed only with one of those keys: that would strand one part of the installed base.

The keystores, passwords and binary lineage remain outside this repository with an independent backup. Before publishing, verify the APK separately for API 26–27 and API 28+, then test an adb install -r upgrade without uninstalling or clearing app data.
