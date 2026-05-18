plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.8"

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.maxmind.geoip2:geoip2:5.1.0")
    // https://github.com/hstyi/geolite2
    implementation("com.github.hstyi:geolite2:v1.0-202603020109")
}

apply(from = "$rootDir/plugins/common.gradle.kts")

