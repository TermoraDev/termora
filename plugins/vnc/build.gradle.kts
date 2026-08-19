plugins {
    alias(libs.plugins.kotlin.jvm)
}


project.version = "0.0.2"


dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":"))
    implementation(files("${project.projectDir}/libs/trilead-ssh2-build217-jenkins-8.jar"))
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
