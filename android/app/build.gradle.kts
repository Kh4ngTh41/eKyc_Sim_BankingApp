plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.example.ekycsimulate"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.ekycsimulate"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Các dependency mặc định
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")
    implementation("org.bouncycastle:bcpg-jdk15on:1.68")
    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-extensions:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-client-logging:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    // ML Kit - Text Recognition (Bao gồm cả Tiếng Việt)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.mlkit:face-detection:16.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // Các thư viện UI phụ trợ
    implementation("androidx.compose.material:material-icons-extended-android:1.6.7")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // OpenCV: The coordinate org.opencv:opencv-android is not available on Google/MavenCentral/JitPack.
    // Remove for now to fix sync. If you need OpenCV, import the OpenCV Android SDK as a module
    // or use a community AAR published to Maven. See notes in the PR/Chat.
    // kiểm tra phiên bản mới nhất
    // Jetpack Compose + material3 (nếu chưa có)
    implementation("androidx.compose.material3:material3:1.1.1")
    // dùng phiên bản project của bạn
    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.pytorch:pytorch_android_lite:2.1.0")
    implementation("org.pytorch:pytorch_android_torchvision_lite:2.1.0")
}