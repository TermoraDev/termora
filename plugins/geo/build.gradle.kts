plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.7"

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.maxmind.geoip2:geoip2:4.3.1")
    // https://github.com/hstyi/geolite2
    implementation("com.github.hstyi:geolite2:v1.0-202507070058")
}

apply(from = "$rootDir/plugins/common.gradle.kts")

