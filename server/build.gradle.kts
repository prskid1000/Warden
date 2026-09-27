plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.warden.server"
    compileSdk = (property("warden.compileSdk") as String).toInt()
    defaultConfig { minSdk = (property("warden.minSdk") as String).toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":api"))
    // Compiled against framework stubs; the server runs on-device against the
    // real framework via app_process, so these are provided at runtime.
}
