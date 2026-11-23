plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.roundly.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Same applicationId (and signing key) as :app, required by the Wearable Data Layer.
        applicationId = "app.roundly"
        minSdk = 30
        targetSdk = 36
        // Wear builds use a distinct versionCode range so both APKs can coexist on Play.
        versionCode = 1_000_001
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // targetSdk follows the Google Play requirement (36), not the newest platform (37).
        disable += "OldTargetApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.wear)

    // Compose for Wear OS (Material 3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // @WearPreviewDevices annotations are referenced from main sources, so this must not be debug-only.
    implementation(libs.androidx.wear.compose.ui.tooling)

    // Tiles + complications
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)

    // Local persistence of the synced schedule (works when the phone is out of range)
    implementation(libs.androidx.datastore.preferences)

    // Async + serialisation
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Wearable Data Layer (Bluetooth only)
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
}
