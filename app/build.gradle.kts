plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Build number from CI (falls back to 1 for local builds)
val buildNumber = (System.getenv("BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.lewisw.lockgestures"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lewisw.lockgestures"
        minSdk = 30
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        // Fixed key committed to the repo so every build can install over the
        // previous one on your phone. Fine for a personal sideloaded app.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
