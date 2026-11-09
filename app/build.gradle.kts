// app — Compose Desktop UI, theming, mascot. Depends on engine (and its deps), never the reverse.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.desktop)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":rules"))
    implementation(project(":platform"))
    implementation(libs.coroutines.core)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "trashpanda.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "trashpanda"
            packageVersion = "0.1.0"
            vendor = "trashpanda"
            description = "Eats your digital garbage. Leaves your files alone."

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                iconFile = project.file("icon.ico")
                upgradeUuid = "8e3b7c52-6a1d-4f0e-9c4a-2f5b8d9e7713"
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}
