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

    configurations.all {
        exclude(group = "androidx.input", module = "input-motionevents")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Skip lint for transitive alpha dependencies
            lintOptions {
                isAbortOnError = false
                disable("MissingDimensionActivityCreator", "MissingDimensionBroadcastReceiver")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkDependencies = false
        disable.add("MissingDimensionActivityCreator")
        disable.add("MissingDimensionBroadcastReceiver")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        dataBinding = false
        viewBinding = false
    }
    // With Kotlin 1.9.x the Compose compiler extension version is set here.
    // composeCompiler = "1.5.14" aligns with Kotlin 1.9.24 and Compose BOM 2024.06.00.
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

// ── Dependency Resolution Strategy ────────────────────────────────────────
configurations.all {
    resolutionStrategy {
        force("androidx.input:input-motionevents:1.0.0-alpha01")
        eachDependency { details ->
            if (details.requested.group == "androidx.input" && details.requested.name == "input-motionevents") {
                details.useVersion("1.0.0-alpha01")
                details.because("Forcing input-motionevents from androidx.dev snapshots")
            }
        }
    }
}

dependencies {
    constraints {
        implementation("androidx.input:input-motionevents:1.0.0-alpha01") {
            because("Force exclude alpha dependency")
            version {
                reject("1.0.0-alpha01")
            }
        }
    }

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
    implementation(libs.androidx.navigation.compose) {
        exclude(group = "androidx.input", module = "input-motionevents")
    }

    // ── Drawing Engine ───────────────────────────────────────────────────────
    // Jetpack Graphics — Apache 2.0 (Temporarily disabled - depends on unstable versions)
    // Provides CanvasBufferedRenderer for ultra-low-latency, hardware-accelerated
    // stroke rendering via the Skia 2D graphics engine.
    // implementation(libs.androidx.graphics.core)

    // androidx.ink — Apache 2.0 (Temporarily disabled due to alpha transitive dependency)
    // Google's open-source Ink API purpose-built for pen-to-screen applications.
    // ink-authoring : captures raw MotionEvent input with stroke smoothing & prediction
    // ink-rendering : renders in-progress and committed strokes via SurfaceView/Canvas
    // ink-geometry  : bezier/path math utilities for stroke manipulation
    // ink-brush     : configurable brush model (size, opacity, tip shape)
    // implementation(libs.ink.authoring)
    // implementation(libs.ink.rendering)
    // implementation(libs.ink.geometry)
    // implementation(libs.ink.brush)

    // ── Persistence ──────────────────────────────────────────────────────────
    // Room — Apache 2.0, project metadata storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Dependency Injection ─────────────────────────────────────────────
    // Hilt — Apache 2.0 (Temporarily disabled to resolve alpha dependency)
    // implementation(libs.hilt.android) {
    //     exclude(group = "androidx.input", module = "input-motionevents")
    // }
    // ksp(libs.hilt.compiler)
    // implementation(libs.hilt.navigation.compose) {
    //     exclude(group = "androidx.input", module = "input-motionevents")
    // }

    // ── Async ────────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android) {
        exclude(group = "androidx.input", module = "input-motionevents")
    }

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test) {
        exclude(group = "androidx.input", module = "input-motionevents")
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core) {
        exclude(group = "androidx.input", module = "input-motionevents")
    }
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
