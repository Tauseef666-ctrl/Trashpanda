// platform — Windows-specific IO. Windows API bridges (JNA recycle, file probes);
// never depends on rules/engine/app (DESIGN §5.3, §5.9).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.jna)
    implementation(libs.jna.platform)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}
