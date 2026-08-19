# Source and build correspondence

- Application: 方迹 CubeTrace
- Application ID: `com.cubetrace.app`
- Version: `0.1.0` / versionCode `1`
- License baseline: GPL-3.0-only
- Build: Android Studio + Java 17 + Android SDK 35 + Gradle project in this directory
- Network permission: none

For offline sharing, provide this source tree together with the same-version APK. Do not place signing keys, `local.properties`, device MAC addresses, diagnostic logs, or user backups in the source archive.

The current working tree is the source corresponding to the initial development build. A release package must add the exact Git commit and SHA-256 values to this file or to its generated release notes.
