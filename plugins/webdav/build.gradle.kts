plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.3"

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.lookfirst:sardine:5.13")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
