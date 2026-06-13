plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.github.triplet.play")
}

play {
    // Service account JSON is written to this path by CI from a repo secret;
    // not present (and not required) for local/manual gradle invocations
    // unless you're running publish tasks yourself.
    serviceAccountCredentials.set(file("${rootProject.projectDir}/play-publisher-key.json"))
    defaultToAppBundles.set(true)
    track.set("internal")
    // Don't let a publish failure (e.g. missing credentials locally) break
    // unrelated tasks like assembleDebug/bundleRelease.
    enabled.set(file("${rootProject.projectDir}/play-publisher-key.json").exists())
}

android {
    namespace = "uk.aprsnet.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.aprsnet.client"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "2.6.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // Map - osmdroid (same OpenStreetMap tiles as the website)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.github.MKergall:osmbonuspack:6.9.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Room (persistence - messages, contacts, stations)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}