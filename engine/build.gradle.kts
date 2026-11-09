// engine — scan + plan + report pipeline. Pure Kotlin, NO UI imports (DESIGN §5.1).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":rules"))
    implementation(project(":platform"))
    implementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test)
}

application {
    mainClass = "trashpanda.engine.ConsoleMainKt"
}

kotlin {
    jvmToolchain(21)
}
