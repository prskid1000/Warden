pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Warden"
// :module is a native Zygisk/Magisk module built with ndk-build (see module/),
// not a Gradle subproject.
include(":api", ":server", ":app")
