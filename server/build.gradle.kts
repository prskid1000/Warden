plugins {
    id("com.android.library")
}

android {
    namespace = "app.warden.server"
    compileSdk = (property("warden.compileSdk") as String).toInt()
    defaultConfig { minSdk = (property("warden.minSdk") as String).toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":api"))
    // Compiled against framework stubs; the server runs on-device against the
    // real framework via app_process, so these are provided at runtime.
}
