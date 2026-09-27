plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "app.warden.api"
    compileSdk = (property("warden.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = (property("warden.minSdk") as String).toInt()
    }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
