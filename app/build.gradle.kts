plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Build number from CI (falls back to 1 for local builds)
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

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
}
