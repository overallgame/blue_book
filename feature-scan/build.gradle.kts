plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
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

// 第 1 步只接通模块与路由，所以依赖只留真正用到的：
// 尚无 Hilt（第 3 步有 ViewModel 时再加）、尚无 core-network（第 4 步接校验接口时再加）。
// 相机/条码库（ML Kit）同样等到第 3 步。
dependencies {
    kapt("cn.therouter:apt:1.3.0")
    implementation(project(":lib-base"))
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
