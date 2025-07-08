plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.3"

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.hierynomus:smbj:0.14.0")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
