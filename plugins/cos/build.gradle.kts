plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.5"



dependencies {
    testImplementation(kotlin("test"))
    implementation("com.qcloud:cos_api:5.6.260.1")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
