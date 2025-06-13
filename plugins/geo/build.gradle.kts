plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.1"

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.maxmind.geoip2:geoip2:4.3.1")
}

apply(from = "$rootDir/plugins/common.gradle.kts")

