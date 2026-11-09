// tools — future CLI / dev scripts (v0.x stub only, PLAN.txt P0).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass = "trashpanda.tools.CliMainKt"
}

kotlin {
    jvmToolchain(21)
}
