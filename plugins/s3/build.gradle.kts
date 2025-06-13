plugins {
    alias(libs.plugins.kotlin.jvm)
}


project.version = "0.0.1"


dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(project(":"))

    implementation("io.minio:minio:8.5.17")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
