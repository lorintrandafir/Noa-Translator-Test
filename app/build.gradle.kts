plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lorin.noatranslator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lorin.noatranslator"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
}
