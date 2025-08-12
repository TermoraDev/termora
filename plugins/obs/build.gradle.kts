plugins {
    alias(libs.plugins.kotlin.jvm)
}


project.version = "0.0.2"


dependencies {
    testImplementation(kotlin("test"))
    implementation("com.huaweicloud:esdk-obs-java-bundle:3.25.7")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
