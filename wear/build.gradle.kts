plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "uk.aprsnet.client.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.aprsnet.client.wear"
        minSdk        = 30          // Wear OS 3.0 (Pixel Watch 1+)
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // Tiles (shown on watch face long-press)
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.tiles:tiles-material:1.4.0")

    // Phone <-> Watch data layer
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Coroutines + JSON
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.json:json:20240303")
    // Guava ListenableFuture required by TileService
    implementation("com.google.guava:guava:32.0.1-android")
}
