# Building Steady

Requirements: JDK 17, Android SDK platform 35, and Build Tools 35.0.0. The Gradle wrapper pins Gradle 8.11.1 and checks its distribution checksum.

Set JAVA_HOME and ANDROID_HOME, or set sdk.dir in local.properties. Keep local configuration and signing credentials out of Git.

## Demo variants

- fixed: corrected charcoal interface, application ID com.steady.scheduler.preview2.fixed.dev in debug builds.
- studio: selected warm-light design, displayed as Steady, application ID com.steady.scheduler.preview2.studio.dev in debug builds.

They install alongside one another and alongside the existing app. Each has its own data. All task, project, goal, journal, and workout logic is shared.

## Build and test

Windows:

```powershell
.\gradlew.bat testFixedDebugUnitTest testStudioDebugUnitTest lintFixedDebug lintStudioDebug assembleFixedDebug assembleStudioDebug
```

On macOS or Linux, use `sh gradlew` with the same task names.

APKs are under app/build/outputs/apk/fixed/debug and app/build/outputs/apk/studio/debug. These are debug-signed design demos, not Google Play release artifacts.

DemoLayoutTest renders all six pages with empty and sample data at 360 by 800 pixels, plus Today and Journal at 320 by 760 with 130% font scaling. Images are under app/build/previews/fixed and app/build/previews/studio. These are Robolectric native renderings, not physical-device screenshots. Sample data is only inserted by tests and is not bundled into the APK.

Regression tests cover task times, goal start/completion, Signal/Noise inheritance, journal grouping and migration, exports, and task creation controls. Device testing is still required for system UI, notifications, alarms, and animation feel.

## Offline transfer

Settings offers full backup and replacement import using portable JSON and media in a ZIP. See [the backup format](docs/portable-backup.md) for the user workflow and cross-platform contract. Android supports it now; the web and iOS apps are not built yet. Backup tests cover round-trip preservation, journal export after restore, missing media, broken references, invalid schedules, oversized manifests and unsafe paths. UI tests verify picker actions and the replacement confirmation.
