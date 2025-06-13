plugins {
    alias(libs.plugins.kotlin.jvm)
}


project.version = "0.0.1"


dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))

    implementation(libs.xodus.vfs)
    implementation(libs.xodus.openAPI)
    implementation(libs.xodus.environment)
    implementation(libs.bip39)
    implementation(libs.commons.compress)
}


ext.set("Termora-Plugin-Entry", "app.termora.plugins.migration.MigrationPlugin")
apply(from = "$rootDir/plugins/common.gradle.kts")
