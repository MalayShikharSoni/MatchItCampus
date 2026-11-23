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
