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
    // 接 /api/v2/scan/resolve 要 ApiGateway（Retrofit/OkHttp 都在 core-network）。
    // 层次是 feature → core → lib-base；扫码结果要跳的播放页/作者主页**走路由**，
    // 不引入任何 feature 之间的横向编译依赖（设计方案 4.4 的"稳定契约"）
    implementation(project(":core-network"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // 相机：预览 + 帧分析
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // 条码识别。MlKitBarcodeScanner 是全项目唯一 import ML Kit 的地方——
    // 换 ZXing 只改那一个文件（BarcodeScanner 是接口，见 ui/scan/BarcodeScanner.kt）
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    testImplementation("junit:junit:4.13.2")
    // ScanViewModel / ScanMappers / 失败分类的测试要驱动 viewModelScope，用 TestDispatcher 换主线程调度器
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // ScanApiIntegrationTest：真 HTTP 上验证路径、@Query 编码、信封解析与错误映射。
    // 版本跟 core-network 的 okhttp 对齐（4.11.0），避免测试类路径上解析出两份 okhttp。
    // 不能用 JDK 的 com.sun.net.httpserver：Android 单元测试以 android.jar 为基准，com.sun.* 不可见
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}
