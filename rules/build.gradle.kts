// rules — rule database: built-in TOML + loader + validation. Depends on platform only.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":platform"))
    implementation(libs.ktoml)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}
