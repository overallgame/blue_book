// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("cn.therouter") version "1.3.0" apply false
}

subprojects {
    configurations.all {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "support-media-compat")
        exclude(group = "com.android.support", module = "support-annotations")
        exclude(group = "com.android.support", module = "support-core-utils")
        exclude(group = "com.android.support", module = "support-core-ui")
        exclude(group = "com.android.support", module = "support-fragment")
        exclude(group = "com.android.support", module = "appcompat-v7")
    }
}
