plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.blue_book.feature_scan"
    compileSdk = 34

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    kapt("cn.therouter:apt:1.3.0")
    implementation(project(":lib-base"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // 相机：预览 + 帧分析。本步（3a）只走相册路径，相机在 3b 接入；
    // 依赖先声明齐，避免同一个功能分两次动构建文件。
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // 条码识别。★ MlKitBarcodeScanner 是全项目唯一 import ML Kit 的地方——
    // 换 ZXing 只改那一个文件（BarcodeScanner 是接口，见 ui/scan/BarcodeScanner.kt）
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    testImplementation("junit:junit:4.13.2")
}
