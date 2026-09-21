plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.blue_book.lib_base"
    compileSdk = 34

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api("cn.therouter:router:1.3.0")
    // host/IMainHost.kt 的 Fragment.mainHost 取用扩展需要 Fragment 类型；
    // 用 api 让各 feature 模块可直接使用该扩展（各模块原本已各自声明同版本依赖）
    api("androidx.fragment:fragment-ktx:1.5.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("javax.inject:javax.inject:1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.bumptech.glide:glide:4.13.2")
    // pre_video_item_view.xml 使用：布局由本模块持有，依赖也要在本模块声明
    // （否则 lint 报 MissingClass，单独构建/预览本模块会失败）
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // scan/ScanCodeFormat.kt 是纯函数（无 Android 依赖），所以只要 junit，不需要 coroutines-test。
    // 它是扫码的安全边界，测试必须穷举畸形与恶意输入——这是本模块第一个测试。
    testImplementation("junit:junit:4.13.2")
}
