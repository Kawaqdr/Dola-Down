plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kawaqdr.doladown"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kawaqdr.doladown"
        minSdk = 21
        targetSdk = 33
        versionCode = 2
        versionName = "0.2.0-sandbox"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":virtualapp-engine:lib"))
}
