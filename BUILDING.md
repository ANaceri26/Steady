# Building Steady for Android

Requirements: JDK 17, Android SDK platform 35, and Android SDK Build Tools 35.0.0. The Gradle wrapper pins Gradle 8.11.1 and verifies its distribution checksum.

Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your Android SDK, or specify `sdk.dir` in your local `local.properties` file. Do not commit local paths or signing credentials.

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS or Linux:

```sh
sh gradlew testDebugUnitTest lintDebug assembleDebug
```

The preview APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use the application ID `com.steady.scheduler.preview2.dev` so they can coexist with the original app. Their data is separate.

Release builds use `com.steady.scheduler.preview2`. Updating an existing installation requires the original signing key; a newly generated key cannot replace it. Release signing and Google Play distribution are not configured here.

Tests cover task-list validation, goal start/completion, journal grouping and editing, version 3 database migration, scheduling boundaries, and journal export escaping/path confinement. These checks do not replace physical-device testing of alarms, notifications, keyboard layout, or accessibility.
