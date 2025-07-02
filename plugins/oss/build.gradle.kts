plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.3"

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.3")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("javax.activation:activation:1.1.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
