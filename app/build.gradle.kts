plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.relay.ctrl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.relay.ctrl"
        minSdk = 28
        targetSdk = 34
        // CI sets these from the run number; local builds fall back to 1 / 1.0
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        // Used by CI (see .github/workflows/android-release.yml). Same key every release = in-app updates keep working.
        create("ci") {
            System.getenv("KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // CI signs with the keystore secret; local builds fall back to the debug key.
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) signingConfigs.getByName("ci") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    // Lint crashes when Gradle runs on a JDK newer than the toolchain supports; CI runs lint-free release builds.
    lint { checkReleaseBuilds = false }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // App Actions: push dynamic shortcuts to Google Assistant / Gemini
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-google-shortcuts:1.0.1")

    // QR: ZXing draws the share code, Google's code scanner reads it (no camera permission needed)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
