# Source and build correspondence

- Application: 方迹 CubeTrace
- Application ID: `com.cubetrace.app`
- Version: `0.4.1` / versionCode `13`
- License baseline: GPL-3.0-only
- Build: Java 17 + Android SDK 35 / Build Tools 34.0.0 + pinned Gradle 9.4.1 Wrapper, `./gradlew :app:assembleRelease`
- Network permission: none

For offline sharing, provide this source tree together with the same-version APK. Do not place signing keys, `local.properties`, device MAC addresses, diagnostic logs, or user backups in the source archive.

The exact Git commit and APK SHA-256 are recorded in each GitHub release and its checksum attachment. No signing key is stored in this repository.
## Signing continuity

The actual published v0.3.1 APK (SHA-256 `c4229c3ae9fc1d988b225b994e122cb539cf9fc5f5d90410d9efbab2eab0ddbd`) uses the stable Fangji certificate for both v2 and v3 and contains no rotation lineage. This differs from the earlier intended workflow. Future releases must preserve this actual installed base.

- `release.apk`: stable certificate on all supported Android versions; normal update path from v0.3.1.
- `legacy-upgrade.apk`: the same application code, signed with the historical debug key for API 26–27 and the stable key plus existing proof-of-rotation for API 28+. Use only when migrating an old debug-signed installation. An Android 8 installation already using the stable certificate must use `release.apk`.
- Stable certificate SHA-256: `63083b7ee0d2ca84565a1bd04cb4d429960c2903bb7d7391f8e487c9055b819b`.
- Historical debug certificate SHA-256: `cd763632526fc6403bd093328c12df8171e6961e231e4dff9625b8101a26d3ad`.

`scripts/sign-release.ps1` creates and verifies both artifacts from one unsigned release APK using externally supplied credentials and lineage. It never overwrites existing signed artifacts or prints passwords.

The keystores, passwords and binary lineage remain outside this repository with an independent backup. Before publishing, verify the APK separately for API 26–27 and API 28+, then test an adb install -r upgrade without uninstalling or clearing app data.

## Required update policy

Future releases must remain in-place updates: keep `com.cubetrace.app`, use the stable certificate above for the normal release APK, and increase `versionCode` beyond the currently published and installed version. Do not replace the release key, use a debug APK as an update, or uninstall/clear app data to bypass an installation failure. Preserve database migration paths and verify an upgrade before publication.

Local signing credentials and lineage are maintained in `C:/Users/1192420574/Documents/CubeTraceSigning/`, with key and lineage copies in its `backup/` directory. The historical debug keystore remains in `C:/Users/1192420574/.android/debug.keystore` for legacy migration only. These locations are outside project cleanup scope; never include their contents in Git or release attachments.

Project cleanup preserves `scratch/releases/v0.4.0-final/`, upgrade data backups, the previous installed APK for recovery analysis, local extracted toolchains, and `design/` originals. Build outputs, Gradle caches, downloaded tool archives and temporary device captures can be regenerated and removed. These local preservation paths remain ignored by Git.
