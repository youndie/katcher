### Katcher Android Client

Katcher is a lightweight crash reporting client for Android. This module (`dev/client-android`) collects uncaught exceptions, persists them on disk, and uploads reports to your Katcher server.

This document explains how to add and configure the Android client in your app.

#### What it does
- Installs a global `UncaughtExceptionHandler` on startup
- Captures crash message and full stacktrace
- Enriches with device/app context (Android version, SDK, device, brand, `build_uuid`, app version)
- Persists reports to `filesDir/katcher_crashes/` when offline and flushes them on next start
- Sends JSON reports to `POST {apiUrl}/api/reports`

---

### 1) Add dependency

If you publish the artifact to your repository, use:

```kotlin
dependencies {
    implementation("ru.workinprogress.katcher:client-android:<version>")
}
```

If you use it as a project module, add the project dependency instead:

```kotlin
dependencies {
    implementation(project(":dev:client-android"))
}
```

---

### 2) Provide BuildConfig fields

The client auto-configures itself by reading `BuildConfig` fields via reflection. You have two options to provide them:

Option A — Use the Katcher Android Gradle Plugin (recommended)
- The plugin generates the required fields per variant at build time:
  - `KATCHER_BUILD_UUID` — a fresh UUID per build
  - `KATCHER_SERVER_URL` — from `katcher.serverUrl`
  - `KATCHER_APP_KEY` — from `katcher.appKey`
- See `dev/android-gradle-plugin/README.md` for installation and configuration.

Quick example (in your app module `build.gradle.kts`):

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("ru.workinprogress.katcher.gradle.plugin")
}

katcher {
    serverUrl = "https://your.katcher.server"
    appKey = "<your-app-key>"
    enabled = true
}
```

Option B — Set the fields manually in `build.gradle(.kts)`

```kotlin
android {
    buildTypes {
        release {
            buildConfigField("String", "KATCHER_BUILD_UUID", "\"<your-build-uuid>\"")
            buildConfigField("String", "KATCHER_SERVER_URL", "\"https://your.katcher.server\"")
            buildConfigField("String", "KATCHER_APP_KEY", "\"<your-app-key>\"")
        }

        debug {
            buildConfigField("String", "KATCHER_BUILD_UUID", "\"debug-build\"")
            buildConfigField("String", "KATCHER_SERVER_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "KATCHER_APP_KEY", "\"debug-app-key\"")
        }
    }
}
```

Field names expected by the client (for both options):
- `KATCHER_BUILD_UUID`
- `KATCHER_SERVER_URL` (no trailing slash required; it will be normalized)
- `KATCHER_APP_KEY`

---

### 3) Initialize in your Application

Call `Katcher.start(context)` as early as possible, typically from `Application.onCreate()`.
When using the Gradle plugin (Option A above), no extra wiring is needed beyond applying the plugin and configuring `katcher { ... }`.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Auto-config from BuildConfig fields set above
        Katcher.start(this)
    }
}
```

Alternatively, you can configure explicitly without `BuildConfig` fields:

```kotlin
Katcher.start(this, buildUuid = "123e4567-e89b-12d3-a456-426614174000") {
    apiUrl = "https://your.katcher.server"
    appKey = "your-app-key"
    environment = "production" // optional, default is "production"
    isDebug = BuildConfig.DEBUG  // optional: enables extra logs
}
```

What is sent:
- `message`, `stacktrace`
- `release` = your app version (resolved via `PackageManager`)
- `environment` (default `production`)
- `context` JSON including OS/device fields and the provided `build_uuid`

---

### 4) Testing your setup

Force a crash to verify delivery and offline persistence:

```kotlin
findViewById<View>(R.id.btnCrash).setOnClickListener {
    throw RuntimeException("Test crash from Katcher")
}
```

Behavior to expect:
- On crash, a JSON file appears under `filesDir/katcher_crashes/`.
- The client attempts an immediate upload to `POST {apiUrl}/api/reports`.
- If offline, the file remains and will be retried on next app start.

Enable debug logs by setting `isDebug = true` in `start { ... }` to see detailed logs like:
`Initialized`, `Generated Report JSON`, `Crash saved to disk`, `Found N unsent crashes`, `Crash upload result`.

---

### 5) Network endpoint

The client posts JSON to:
```
{apiUrl}/api/reports
```
with header:
```
Content-Type: application/json; charset=UTF-8
```

Ensure your Katcher server is reachable from the device/emulator, and that it accepts these reports with your `appKey`.

---

### 6) Notes and limitations

- Minimum SDK: 24 (as configured in this module)
- Uses a single-thread executor to flush stored crashes on startup
- Upload timeouts: connect/read 5s each
- The client only handles uncaught exceptions; for handled exceptions, you may add your own capture/forwarding if needed
