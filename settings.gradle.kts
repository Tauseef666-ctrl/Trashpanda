pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // androidx.* transitive deps of Compose Multiplatform
    }
}

rootProject.name = "trashpanda"

include("app", "engine", "rules", "platform", "tools")
