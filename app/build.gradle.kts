plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Note: kotlin.plugin.compose is a Kotlin 2.0+ concept; with Kotlin 1.9.x the
    // Compose compiler is wired through composeOptions{} below.
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.artisthaven.app"
    // Default to SDK 34 for maximum pen-input device compatibility
    compileSdk = 34

    defaultConfig {
        applicationId = "com.artisthaven.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
    // With Kotlin 1.9.x the Compose compiler extension version is set here.
    // composeCompiler = "1.5.14" aligns with Kotlin 1.9.24 and Compose BOM 2024.06.00.
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // ── Drawing Engine ───────────────────────────────────────────────────────
    // Jetpack Graphics — Apache 2.0
    // Provides CanvasBufferedRenderer for ultra-low-latency, hardware-accelerated
    // stroke rendering via the Skia 2D graphics engine.
    implementation(libs.androidx.graphics.core)

    // androidx.ink — Apache 2.0
    // Google's open-source Ink API purpose-built for pen-to-screen applications.
    // ink-authoring : captures raw MotionEvent input with stroke smoothing & prediction
    // ink-rendering : renders in-progress and committed strokes via SurfaceView/Canvas
    // ink-geometry  : bezier/path math utilities for stroke manipulation
    // ink-brush     : configurable brush model (size, opacity, tip shape)
    implementation(libs.ink.authoring)
    implementation(libs.ink.rendering)
    implementation(libs.ink.geometry)
    implementation(libs.ink.brush)

    // ── Persistence ──────────────────────────────────────────────────────────
    // Room — Apache 2.0, project metadata storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Dependency Injection ─────────────────────────────────────────────────
    // Hilt — Apache 2.0
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Async ────────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
