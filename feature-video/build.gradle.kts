plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.blue_book.feature_video"
    compileSdk = 34
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    kapt("cn.therouter:apt:1.3.0")
    implementation(project(":lib-base"))
    implementation(project(":core-network"))
    implementation(project(":core-player"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("javax.inject:javax.inject:1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.5.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.bumptech.glide:glide:4.13.2")
    kapt("com.github.bumptech.glide:compiler:4.13.2")
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    // 本模块第一个测试源集：上传编排（并发/续传/进度/重试策略）在纯 JVM 上测——
    // 内容源换成字节数组，HTTP 用 MockWebServer。相机、文件 provider 这些不可测的部分
    // 已经被隔到 UriUploadSource 一个文件里
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    // 发布页的 ViewModel 测试要驱动 viewModelScope，用 TestDispatcher 换掉主线程调度器
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

