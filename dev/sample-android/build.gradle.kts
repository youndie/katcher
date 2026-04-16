plugins {
    id("com.android.application")
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

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
