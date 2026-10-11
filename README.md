# AeonTV (Android)

AeonTV is an Android app for the **AeonTV** media server — a rebrand of the
official [Jellyfin Android](https://github.com/jellyfin/jellyfin-android) client
(v2.6.2, commit `8908d18`).

## What's rebranded

- Package: `com.aeontv.app` (debug builds: `com.aeontv.app.debug`)
- App name: **AeonTV**, with AeonTV launcher icons (all densities + adaptive icons)
- Accent color → AeonTV cyan `#00c8f0`, launcher background `#170a2e`
- All user-visible strings: Jellyfin → AeonTV (every bundled locale)
- Internal `org.jellyfin.mobile` Kotlin namespaces are kept unchanged, just like
  the AeonTV server keeps its `Jellyfin.*` namespaces — so client/server
  compatibility is unaffected.

## Build

Requirements: JDK 17, Android SDK 34, Gradle 8.7.

```bash
export ANDROID_HOME=/path/to/android-sdk
export JELLYFIN_VERSION=2.6.2
gradle :app:assembleLibreDebug --no-daemon
```

The `libre` flavor is used (no proprietary Google Cast dependencies).
Output: `app/build/outputs/apk/libre/debug/aeontv-android-v2.6.2-libre-debug.apk`

## Versioning

The version comes from the `JELLYFIN_VERSION` environment variable (or the
`jellyfin.version` Gradle property). It tracks the upstream Jellyfin Android
release this rebrand is based on.

## License

GPL-2.0, same as upstream Jellyfin Android. See `LICENSE.md`.
