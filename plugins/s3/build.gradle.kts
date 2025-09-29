plugins {
    alias(libs.plugins.kotlin.jvm)
}


project.version = "0.0.6"


dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(project(":"))

    implementation("io.minio:minio:8.6.0")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
