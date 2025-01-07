import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.jetbrains.kotlin.org.apache.commons.lang3.StringUtils

plugins {
    java
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}


group = "app.termora"
version = "1.0.0"

val os: DefaultOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
var arch: ArchitectureInternal = DefaultNativePlatform.getCurrentArchitecture()


repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven("https://www.jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.hutool)
    testImplementation(libs.sshj)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.core)
    testImplementation(libs.jsch)
    testImplementation(libs.rhino)
    testImplementation(libs.delight.rhino.sandbox)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)

    implementation(libs.slf4j.api)
    implementation(libs.pty4j)
    implementation(libs.slf4j.tinylog)
    implementation(libs.tinylog.impl)
    implementation(libs.commons.codec)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.net)
    implementation(libs.commons.text)
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.flatlaf.swingx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.swingx)
    implementation(libs.jgoodies.forms)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.versioncompare)
    implementation(libs.oshi.core)
    implementation(libs.jSystemThemeDetector) { exclude(group = "*", module = "*") }
    implementation(libs.jfa) { exclude(group = "*", module = "*") }
    implementation(libs.jbr.api)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.sshd.core)
    implementation(libs.commonmark)
    implementation(libs.jgit)
    implementation(libs.jgit.sshd)
    implementation(libs.jnafilechooser)
    implementation(libs.xodus.vfs)
    implementation(libs.xodus.openAPI)
    implementation(libs.xodus.environment)
    implementation(libs.bip39)
    implementation(libs.colorpicker)
}

application {
    val args = mutableListOf(
        "--add-exports java.base/sun.nio.ch=ALL-UNNAMED",
    )

    if (os.isMacOsX) {
        args.add("--add-opens java.desktop/sun.lwawt.macosx.concurrent=ALL-UNNAMED")
        args.add("-Dsun.java2d.metal=true")
        args.add("-Dapple.awt.application.appearance=system")
    }

    args.add("-Dapp-version=${project.version}")

    if (os.isLinux) {
        args.add("-Dsun.java2d.opengl=true")
    }

    applicationDefaultJvmArgs = args
    mainClass = "app.termora.MainKt"
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("copy-dependencies") {
    from(configurations.runtimeClasspath)
        .into("${layout.buildDirectory.get()}/libs")
}

tasks.register<Exec>("jlink") {
    val modules = listOf(
        "java.base",
        "java.desktop",
        "java.logging",
        "java.management",
        "java.rmi",
        "java.security.jgss",
        "jdk.crypto.ec",
        "jdk.unsupported",
    )

    commandLine(
        "${Jvm.current().javaHome}/bin/jlink",
        "--verbose",
        "--strip-java-debug-attributes",
        "--strip-native-commands",
        "--strip-debug",
        "--compress=zip-9",
        "--no-header-files",
        "--no-man-pages",
        "--add-modules",
        modules.joinToString(","),
        "--output",
        "${layout.buildDirectory.get()}/jlink"
    )
}

tasks.register<Exec>("jpackage") {
    val buildDir = layout.buildDirectory.get()
    val options = mutableListOf(
        "--add-exports java.base/sun.nio.ch=ALL-UNNAMED",
        "-Xmx2g",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-Dlogger.console.level=off",
        "-Dkotlinx.coroutines.debug=off",
        "-Dapp-version=${project.version}",
    )

    if (os.isMacOsX) {
        options.add("-Dsun.java2d.metal=true")
        options.add("-Dapple.awt.application.appearance=system")
        options.add("--add-opens java.desktop/sun.lwawt.macosx.concurrent=ALL-UNNAMED")
    } else {
        options.add("-Dsun.java2d.opengl=true")
    }

    val arguments = mutableListOf("${Jvm.current().javaHome}/bin/jpackage", "--verbose")
    arguments.addAll(listOf("--runtime-image", "${buildDir}/jlink"))
    arguments.addAll(listOf("--name", project.name.uppercaseFirstChar()))
    arguments.addAll(listOf("--app-version", "${project.version}"))
    arguments.addAll(listOf("--main-jar", tasks.jar.get().archiveFileName.get()))
    arguments.addAll(listOf("--main-class", application.mainClass.get()))
    arguments.addAll(listOf("--input", "$buildDir/libs"))
    arguments.addAll(listOf("--temp", "$buildDir/jpackage"))
    arguments.addAll(listOf("--dest", "$buildDir/distributions"))
    arguments.addAll(listOf("--java-options", options.joinToString(StringUtils.SPACE)))


    if (os.isMacOsX) {
        arguments.addAll(listOf("--mac-package-name", project.name.uppercaseFirstChar()))
        arguments.addAll(listOf("--mac-app-category", "developer-tools"))
        arguments.addAll(listOf("--mac-package-identifier", "${project.group}"))
        arguments.addAll(listOf("--icon", "${projectDir.absolutePath}/src/main/resources/icons/termora.icns"))
    }

    if (os.isWindows) {
        arguments.add("--win-dir-chooser")
        arguments.add("--win-shortcut")
        arguments.add("--win-shortcut-prompt")
        arguments.addAll(listOf("--win-upgrade-uuid", "E1D93CAD-5BF8-442E-93BA-6E90DE601E4C"))
        arguments.addAll(listOf("--icon", "${projectDir.absolutePath}/src/main/resources/icons/termora.ico"))
    }


    arguments.add("--type")
    if (os.isMacOsX) {
        arguments.add("dmg")
    } else if (os.isWindows) {
        arguments.add("msi")
    } else if (os.isLinux) {
        arguments.add("app-image")
    } else {
        throw UnsupportedOperationException()
    }

    commandLine(arguments)

}

tasks.register("dist") {
    doLast {
        val vendor = Jvm.current().vendor ?: StringUtils.EMPTY
        @Suppress("UnstableApiUsage")
        if (!JvmVendorSpec.JETBRAINS.matches(vendor)) {
            throw GradleException("JVM: $vendor is not supported")
        }

        val distributionDir = layout.buildDirectory.dir("distributions").get()
        val gradlew = File(projectDir, if (os.isWindows) "gradlew.bat" else "gradlew").absolutePath

        // 清空目录
        exec { commandLine(gradlew, "clean") }

        // 打包并复制依赖
        exec { commandLine(gradlew, "jar", "copy-dependencies") }

        // 检查依赖的开源协议
        exec { commandLine(gradlew, "check-license") }

        // jlink
        exec { commandLine(gradlew, "jlink") }

        // 打包
        exec { commandLine(gradlew, "jpackage") }

        // pack
        exec {
            if (os.isWindows) { // zip
                commandLine(
                    "tar", "-vacf",
                    distributionDir.file("${project.name}-${project.version}-windows-${arch.name}.zip").asFile.absolutePath,
                    project.name.uppercaseFirstChar()
                )
                workingDir = layout.buildDirectory.dir("jpackage/images/win-msi.image/").get().asFile
            } else if (os.isLinux) { // tar.gz
                commandLine(
                    "tar", "-czvf",
                    distributionDir.file("${project.name}-${project.version}-linux-${arch.name}.tar.gz").asFile.absolutePath,
                    project.name.uppercaseFirstChar()
                )
                workingDir = distributionDir.asFile
            } else if (os.isMacOsX) { // rename
                commandLine(
                    "mv",
                    distributionDir.file("${project.name.uppercaseFirstChar()}-${project.version}.dmg").asFile.absolutePath,
                    distributionDir.file("${project.name}-${project.version}-osx-${arch.name}.dmg").asFile.absolutePath,
                )
            } else {
                throw GradleException("${os.name} is not supported")
            }
        }
    }
}

tasks.register("check-license") {
    doLast {
        val thirdParty = mutableMapOf<String, String>()
        val iterator = File(projectDir, "THIRDPARTY").readLines().iterator()
        val thirdPartyNames = mutableSetOf<String>()

        while (iterator.hasNext()) {
            val nameWithVersion = iterator.next()
            if (nameWithVersion.isBlank()) {
                continue
            }

            // ignore license name
            iterator.next()

            val license = iterator.next()
            thirdParty[nameWithVersion.replace(StringUtils.SPACE, "-")] = license
            thirdPartyNames.add(nameWithVersion.split(StringUtils.SPACE).first())
        }

        for (file in configurations.runtimeClasspath.get()) {
            val name = file.nameWithoutExtension
            if (!thirdParty.containsKey(name)) {
                if (logger.isWarnEnabled) {
                    logger.warn("$name does not exist in third-party")
                }
                if (!thirdPartyNames.contains(name)) {
                    throw GradleException("$name No license found")
                }
            }
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }
}