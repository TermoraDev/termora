plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.1"



dependencies {
    testImplementation(kotlin("test"))
    implementation("com.qcloud:cos_api:5.6.247")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
