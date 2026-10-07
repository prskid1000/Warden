plugins {
    id("com.android.library")
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
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.annotation:annotation:1.11.0")
}
