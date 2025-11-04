plugins {
    alias(libs.plugins.kotlin.jvm)
}



project.version = "0.0.5"


dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.fazecast:jSerialComm:2.11.4")
}

apply(from = "$rootDir/plugins/common.gradle.kts")

