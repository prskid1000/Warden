import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.warden"
    compileSdk = (property("warden.compileSdk") as String).toInt()

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    defaultConfig {
        // Rename the product: PRODUCT_NAME (gradle.properties) + this id.
        applicationId = "app.warden"
        minSdk = (property("warden.minSdk") as String).toInt()
        targetSdk = (property("warden.targetSdk") as String).toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    // The manager never runs the su shim, so native libs stay uncompressed and
    // 16 KB-aligned in the APK. Only BrokerLiveTest's su checks need them
    // extracted (WardenSu execs from the native lib dir): -Pwarden.suTest
    if (project.hasProperty("warden.suTest")) packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // Same signing key for debug and release, so a server started against
        // either build authenticates the manager (cert pinning in Starter).
        if (keystoreProps.isNotEmpty()) {
            debug { signingConfig = signingConfigs.getByName("release") }
            release {
                isMinifyEnabled = false
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":api"))
    // Bundles the server dex into the APK so start.sh can app_process it.
    implementation(project(":server"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // PC-free server start: an in-app ADB client that pairs with the device's
    // own adbd over Wireless Debugging (Android 11+) and runs the bootstrap.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.7.0")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.86")

    // On-device checks against a live broker (BrokerLiveTest).
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
