// engine — scan + plan + report + execute + undo pipeline. Pure Kotlin, NO UI imports (DESIGN §5.1).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":rules"))
    implementation(project(":platform"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
}

application {
    mainClass = "trashpanda.engine.ConsoleMainKt"
}

kotlin {
    jvmToolchain(21)
}
