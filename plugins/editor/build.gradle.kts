plugins {
    alias(libs.plugins.kotlin.jvm)
}



project.version = "0.0.3"


dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    implementation("com.fifesoft:languagesupport:3.3.0")
    implementation("com.fifesoft:autocomplete:3.3.2")
}

apply(from = "$rootDir/plugins/common.gradle.kts")

