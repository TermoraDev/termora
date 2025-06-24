plugins {
    alias(libs.plugins.kotlin.jvm)
}


project.version = "0.0.4"



dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
}

apply(from = "$rootDir/plugins/common.gradle.kts")

