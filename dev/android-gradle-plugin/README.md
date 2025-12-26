### Katcher Android Gradle Plugin

This Gradle plugin automates two things for Android application variants:
- Generates `BuildConfig` fields used by the Katcher Android client at runtime.
- Uploads your ProGuard/R8 `mapping.txt` to the Katcher server after a successful build.

It targets `com.android.application` projects and wires itself per build variant.

---

### What it does

- Adds `BuildConfig` fields to each enabled variant at build time:
  - `KATCHER_BUILD_UUID` — a fresh UUID per build
  - `KATCHER_SERVER_URL` — your server base URL
  - `KATCHER_APP_KEY` — your application key
- If `minifyEnabled` is true for the variant, registers `uploadKatcherMapping<Variant>` which:
  - Finds the variant’s `mapping.txt`
  - Uploads it to `POST {serverUrl}/api/mappings/upload` as multipart form-data with fields:
    - `appKey`
    - `buildUuid`
    - `type=ANDROID_PROGUARD`
    - `mappingFile` (file part)
- Automatically runs the upload task after `assemble<Variant>` and `bundle<Variant>` complete.

---

### Installation

Pick one of the approaches below depending on how you consume the plugin.

1) Published artifact (recommended)

If you publish this plugin to your Maven/Gradle Plugin Portal, apply it in your Android app module:

```kotlin
plugins {
    id("ru.workinprogress.katcher.gradle.plugin") version "<version>"
}
```

2) Composite build (local develop)

Add the plugin project as an included build in `settings.gradle.kts` at the repository root:

```kotlin
includeBuild("dev/android-gradle-plugin")
```

Then in your app module `build.gradle.kts`:

```kotlin
plugins {
    id("ru.workinprogress.katcher.gradle.plugin")
}
```

Notes:
- The exact plugin id depends on your plugin publishing setup. The examples use `ru.workinprogress.katcher.gradle`.

---

### Configure

In your Android app module’s `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("ru.workinprogress.katcher.gradle.plugin")
}

android {
    buildTypes {
        release {
            isMinifyEnabled = true // required to produce mapping.txt
            // proguardFiles(...) or default R8 rules
        }
    }
}

katcher {
    enabled = true
    serverUrl = "https://your.katcher.server" // base URL, no trailing slash required
    appKey = "<your-app-key>"
}
```

What the plugin injects into `BuildConfig` per variant when `enabled=true`:
- `String KATCHER_BUILD_UUID` — `UUID.randomUUID()` per build
- `String KATCHER_SERVER_URL` — from `katcher.serverUrl`
- `String KATCHER_APP_KEY` — from `katcher.appKey`

These fields are consumed by the Katcher Android client (see `dev/client-android/README.md`).

---

### Tasks and build wiring

- `uploadKatcherMapping<Variant>` — uploads the variant’s mapping file after build.
  - Only created for variants where `minifyEnabled == true`.
  - Automatically `finalizedBy` both `assemble<Variant>` and `bundle<Variant>`.
  - Can be executed manually, e.g.: `./gradlew uploadKatcherMappingRelease`.

Example build flows:
- `./gradlew assembleRelease` → builds APK/AAB → uploads mapping.
- `./gradlew bundleRelease` → builds AAB → uploads mapping.

Logging: during upload, you’ll see messages like:
- `Uploading mapping file to Katcher...`
- `✅ Mapping uploaded successfully!` or `❌ Failed to upload mapping: ...`

---

### Server endpoint contract

The upload task sends a `multipart/form-data` POST to:

```
{serverUrl}/api/mappings/upload
```

Fields:
- `appKey` (text)
- `buildUuid` (text)
- `type=ANDROID_PROGUARD` (text)
- `mappingFile` (file)

Expected HTTP 2xx on success. Non-2xx responses are surfaced as build errors.

---

### CI usage example (GitHub Actions)

```yaml
name: Android Release
on:
  workflow_dispatch:
  push:
    tags: [ 'v*' ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: gradle/actions/setup-gradle@v4
      - name: Build and upload mapping
        run: ./gradlew :app:assembleRelease
        env:
          KATCHER_SERVER_URL: https://your.katcher.server
          KATCHER_APP_KEY: ${{ secrets.KATCHER_APP_KEY }}
```

You can wire `katcher.serverUrl`/`katcher.appKey` from environment variables in Gradle if preferred.

---

### Requirements & compatibility

- Android Gradle Plugin: 8.0+ (app plugin `com.android.application`)
- Gradle: 8.x
- Mapping upload requires `minifyEnabled=true` to generate `mapping.txt`.

---

### Troubleshooting

- No `uploadKatcherMapping<Variant>` task: ensure the variant has `minifyEnabled = true` and the plugin is applied to the app module.
- Upload fails with non-2xx:
  - Check `serverUrl` correctness and server availability.
  - Verify `appKey` is valid on the server.
  - Inspect server logs for details.
- Android client cannot find `BuildConfig` fields:
  - Ensure the app also includes the Katcher client and its ProGuard/R8 keep rules (see `dev/client-android/README.md`).

---

### Security note

`appKey` is used to authenticate mapping and report uploads. Store it securely (e.g., via CI secrets or Gradle local properties) and avoid committing secrets.
