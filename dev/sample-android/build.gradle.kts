plugins {
    // Every module was linted through the `subprojects { }` block in the root. That block is gone,
    // so the linter is named per module — including here, or removing the block would have quietly
    // dropped two modules out of the gate.
    id("ru.workinprogress.sborka.lint")
    id("com.android.application")
}

// No `repositories { }` here either — see `dev/client-android`.

android {
    namespace = "ru.workinprogress.katcher.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.workinprogress.katcher.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":dev:client-android"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.json:json:20251224")
}
