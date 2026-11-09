// platform — Windows-specific IO. Zero main dependencies, ever (DESIGN §5.3).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}
