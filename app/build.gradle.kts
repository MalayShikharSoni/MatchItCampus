plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Generates serializers for the @Serializable navigation routes (type-safe navigation).
    alias(libs.plugins.kotlin.serialization)
    // KSP runs annotation processors (Room from Phase 2). Used instead of kapt, which
    // is slower and not supported by AGP 9's built-in Kotlin.
    alias(libs.plugins.ksp)
    // Reads app/google-services.json and turns it into resources Firebase reads at runtime.
    alias(libs.plugins.google.services)
    // Room Gradle plugin: exports the schema JSON on every build (see the `room` block below).
    alias(libs.plugins.room)
}

android {
    namespace = "com.matchitcampus.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.matchitcampus.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Native code (MediaPipe, MapLibre) is packaged only for these CPU types. 32-bit x86 is
        // left out: only old emulators use it, and it was the largest set (~43 MB of the APK).
        // armeabi-v7a stays because many budget phones still run 32-bit Android.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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
        // With built-in Kotlin, the Kotlin JVM target follows this value automatically.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG hides the Debug tools screen in release builds.
        buildConfig = true
    }
}

// Compose compiler reports (Phase 7 performance pass): which classes Compose treats as stable and
// which composables can skip recomposition. Off by default; run
// `./gradlew assembleDebug -PcomposeReports=true` and read app/build/compose_reports/.
if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

room {
    // Each database version's schema is exported here as JSON and committed to Git.
    // That gives a reviewable history of the schema, and MigrationTestHelper uses
    // these files to build old versions of the database in the Phase 7 migration test.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    // withTransaction { } for suspend transactions that span several DAOs.
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)

    // On-device text embeddings for semantic matching. Only ml/TextEmbedderHelper uses it.
    implementation(libs.mediapipe.tasks.text)

    // Shows item photos (AsyncImage) from files in filesDir/photos.
    implementation(libs.coil.compose)

    // Phase 6: fused location (LocationClient) and the map (MapLibre Compose + OpenFreeMap tiles).
    implementation(libs.play.services.location)
    implementation(libs.maplibre.compose)
    runtimeOnly(libs.maplibre.compose.runtime.opengl)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Turbine: readable assertions on Flow emissions (awaitItem()) in DAO tests.
    androidTestImplementation(libs.turbine)
}
