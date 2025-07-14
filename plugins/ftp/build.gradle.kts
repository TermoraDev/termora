plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.2"

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("org.apache.commons:commons-pool2:2.12.1")
    testImplementation(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
