import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.org.apache.commons.io.FileUtils
import org.jetbrains.kotlin.org.apache.commons.io.filefilter.FileFilterUtils
import org.jetbrains.kotlin.org.apache.commons.lang3.StringUtils
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.Future

plugins {
    java
    idea
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}


group = "app.termora"
version = "1.0.9"

val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
val arch: ArchitectureInternal = DefaultNativePlatform.getCurrentArchitecture()

// macOS 签名信息
val macOSSignUsername = System.getenv("TERMORA_MAC_SIGN_USER_NAME") ?: StringUtils.EMPTY
val macOSSign = os.isMacOsX && macOSSignUsername.isNotBlank()
        && System.getenv("TERMORA_MAC_SIGN").toBoolean()

// macOS 公证信息
val macOSNotaryKeychainProfile = System.getenv("TERMORA_MAC_NOTARY_KEYCHAIN_PROFILE") ?: StringUtils.EMPTY
val macOSNotary = macOSSign && macOSNotaryKeychainProfile.isNotBlank()
        && System.getenv("TERMORA_MAC_NOTARY").toBoolean()

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven("https://www.jitpack.io")
}

dependencies {
    // 由于签名和公证，macOS 不携带 natives
    val useNoNativesFlatLaf = os.isMacOsX && System.getenv("ENABLE_BUILD").toBoolean()

    testImplementation(kotlin("test"))
    testImplementation(libs.hutool)
    testImplementation(libs.sshj)
    testImplementation(libs.jsch)
    testImplementation(libs.rhino)
    testImplementation(libs.delight.rhino.sandbox)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)

//    implementation(platform(libs.koin.bom))
//    implementation(libs.koin.core)
    implementation(libs.slf4j.api)
    implementation(libs.pty4j)
    implementation(libs.slf4j.tinylog)
    implementation(libs.tinylog.impl)
    implementation(libs.commons.codec)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.csv)
    implementation(libs.commons.net)
    implementation(libs.commons.text)
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.flatlaf) {
        artifact {
            if (useNoNativesFlatLaf) {
                classifier = "no-natives"
            }
        }
    }
    implementation(libs.flatlaf.extras) {
        if (useNoNativesFlatLaf) {
            exclude(group = "com.formdev", module = "flatlaf")
        }
    }
    implementation(libs.flatlaf.swingx) {
        if (useNoNativesFlatLaf) {
            exclude(group = "com.formdev", module = "flatlaf")
        }
    }

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
    implementation(libs.jgit.sshd) { exclude(group = "*", module = "sshd-osgi") }
    implementation(libs.jnafilechooser)
    implementation(libs.xodus.vfs)
    implementation(libs.xodus.openAPI)
    implementation(libs.xodus.environment)
    implementation(libs.bip39)
    implementation(libs.colorpicker)
    implementation(libs.mixpanel)
    implementation(libs.jSerialComm)
    implementation(libs.ini4j)
    implementation(libs.restart4j)
}

application {
    val args = mutableListOf(
        "--add-exports java.base/sun.nio.ch=ALL-UNNAMED",
        "-Xmx2g",
        "-XX:+UseZGC",
        "-XX:+ZUncommit",
        "-XX:+ZGenerational",
        "-XX:ZUncommitDelay=60",
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
    val dir = layout.buildDirectory.dir("libs")
    from(configurations.runtimeClasspath).into(dir)
    val jna = libs.jna.asProvider().get()
    val pty4j = libs.pty4j.get()
    val jSerialComm = libs.jSerialComm.get()
    val restart4j = libs.restart4j.get()

    // 对 JNA 和 PTY4J 的本地库提取
    // 提取出来是为了单独签名，不然无法通过公证
    if (os.isMacOsX && macOSSign) {
        doLast {
            val archName = if (arch.isArm) "aarch64" else "x86_64"
            val dylib = dir.get().dir("dylib").asFile
            for (file in dir.get().asFile.listFiles() ?: emptyArray()) {
                if ("${jna.name}-${jna.version}" == file.nameWithoutExtension) {
                    val targetDir = File(dylib, jna.name)
                    FileUtils.forceMkdir(targetDir)
                    // @formatter:off
                    exec { commandLine("unzip","-j","-o", file.absolutePath, "com/sun/jna/darwin-${arch.name}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                    // 删除所有二进制类库
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/darwin-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/win32-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/sunos-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/openbsd-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/linux-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/freebsd-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/dragonflybsd-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/aix-*") }
                } else if ("${pty4j.name}-${pty4j.version}" == file.nameWithoutExtension) {
                    val targetDir = FileUtils.getFile(dylib, pty4j.name, "darwin")
                    FileUtils.forceMkdir(targetDir)
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "resources/com/pty4j/native/darwin*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                    // 删除所有二进制类库
                    exec { commandLine("zip", "-d", file.absolutePath, "resources/*") }
                } else if ("${jSerialComm.name}-${jSerialComm.version}" == file.nameWithoutExtension) {
                    val targetDir = FileUtils.getFile(dylib, jSerialComm.name, "OSX", archName)
                    FileUtils.forceMkdir(targetDir)
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "OSX/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                    // 删除所有二进制类库
                    exec { commandLine("zip", "-d", file.absolutePath, "Android/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "FreeBSD/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "Linux/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "OpenBSD/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "OSX/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "Solaris/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "Windows/*") }
                } else if ("${restart4j.name}-${restart4j.version}" == file.nameWithoutExtension) {
                    val targetDir = FileUtils.getFile(dylib, restart4j.name)
                    FileUtils.forceMkdir(targetDir)
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "darwin/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                    // 删除所有二进制类库
                    exec { commandLine("zip", "-d", file.absolutePath, "win32/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "darwin/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "linux/*") }
                    // 设置可执行权限
                    for (e in FileUtils.listFiles(
                        targetDir,
                        FileFilterUtils.trueFileFilter(),
                        FileFilterUtils.falseFileFilter()
                    )) {
                        e.setExecutable(true)
                    }
                }
            }

            // 对二进制签名
            Files.walk(dylib.toPath()).use { paths ->
                for (path in paths) {
                    if (Files.isRegularFile(path)) {
                        signMacOSLocalFile(path.toFile())
                    }
                }
            }
        }
    } else if (os.isLinux || os.isWindows) { // 缩减安装包
        doLast {
            for (file in dir.get().asFile.listFiles() ?: emptyArray()) {
                if ("${jna.name}-${jna.version}" == file.nameWithoutExtension) {
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/darwin-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/sunos-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/openbsd-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/freebsd-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/dragonflybsd-*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/aix-*") }
                    if (os.isWindows) {
                        exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/linux-*") }
                        if (arch.isArm) {
                            exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/win32-x86*") }
                        } else {
                            exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/win32-aarch64/*") }
                            exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/win32-x86/*") }
                        }
                    } else if (os.isLinux) {
                        exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/win32-*") }
                    }
                } else if ("${pty4j.name}-${pty4j.version}" == file.nameWithoutExtension) {
                    exec { commandLine("zip", "-d", file.absolutePath, "resources/*darwin*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "resources/*freebsd*") }
                    if (os.isWindows) {
                        exec { commandLine("zip", "-d", file.absolutePath, "resources/*linux*") }
                    } else if (os.isLinux) {
                        exec { commandLine("zip", "-d", file.absolutePath, "resources/*win*") }
                    }
                } else if ("${jSerialComm.name}-${jSerialComm.version}" == file.nameWithoutExtension) {
                    exec { commandLine("zip", "-d", file.absolutePath, "Android/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "FreeBSD/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "OpenBSD/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "OSX/*") }
                    exec { commandLine("zip", "-d", file.absolutePath, "Solaris/*") }
                    if (os.isWindows) {
                        exec { commandLine("zip", "-d", file.absolutePath, "Linux/*") }
                    } else if (os.isLinux) {
                        exec { commandLine("zip", "-d", file.absolutePath, "Windows/*") }
                    }
                } else if ("${restart4j.name}-${restart4j.version}" == file.nameWithoutExtension) {
                    exec { commandLine("zip", "-d", file.absolutePath, "darwin/*") }
                    if (os.isWindows) {
                        exec { commandLine("zip", "-d", file.absolutePath, "linux/*") }
                        if (arch.isArm) {
                            exec { commandLine("zip", "-d", file.absolutePath, "win32/x86_64/*") }
                        } else {
                            exec { commandLine("zip", "-d", file.absolutePath, "win32/aarch64/*") }
                        }
                    } else if (os.isLinux) {
                        exec { commandLine("zip", "-d", file.absolutePath, "win32/*") }
                        if (arch.isArm) {
                            exec { commandLine("zip", "-d", file.absolutePath, "linux/x86_64/*") }
                        } else {
                            exec { commandLine("zip", "-d", file.absolutePath, "linux/aarch64/*") }
                        }
                    }
                }
            }
        }
    }
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
        "-XX:+UseZGC",
        "-XX:+ZUncommit",
        "-XX:+ZGenerational",
        "-XX:ZUncommitDelay=60",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-Dlogger.console.level=off",
        "-Dkotlinx.coroutines.debug=off",
        "-Dapp-version=${project.version}",
    )

    options.add("-Dsun.java2d.metal=true")

    if (os.isMacOsX) {
        options.add("-Dapple.awt.application.appearance=system")
        options.add("--add-opens java.desktop/sun.lwawt.macosx.concurrent=ALL-UNNAMED")
    }

    if (os.isLinux) {
        options.add("-Dsun.java2d.opengl=true")
    }

    val arguments = mutableListOf("${Jvm.current().javaHome}/bin/jpackage")
    arguments.addAll(listOf("--runtime-image", "${buildDir}/jlink"))
    arguments.addAll(listOf("--name", project.name.uppercaseFirstChar()))
    arguments.addAll(listOf("--app-version", "${project.version}"))
    arguments.addAll(listOf("--main-jar", tasks.jar.get().archiveFileName.get()))
    arguments.addAll(listOf("--main-class", application.mainClass.get()))
    arguments.addAll(listOf("--input", "$buildDir/libs"))
    arguments.addAll(listOf("--temp", "$buildDir/jpackage"))
    arguments.addAll(listOf("--dest", "$buildDir/distributions"))
    arguments.addAll(listOf("--java-options", options.joinToString(StringUtils.SPACE)))
    arguments.addAll(listOf("--vendor", "TermoraDev"))
    arguments.addAll(listOf("--copyright", "TermoraDev"))

    if (os.isWindows) {
        arguments.addAll(
            listOf(
                "--description",
                "${project.name.uppercaseFirstChar()}: A terminal emulator and SSH client"
            )
        )
    } else {
        arguments.addAll(listOf("--description", "A terminal emulator and SSH client."))
    }


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

    if (os.isLinux) {
        arguments.addAll(listOf("--icon", "${projectDir.absolutePath}/src/main/resources/icons/termora.png"))
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

    if (os.isMacOsX && macOSSign) {
        arguments.add("--mac-sign")
        arguments.add("--mac-signing-key-user-name")
        arguments.add(macOSSignUsername)
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

        val gradlew = File(projectDir, if (os.isWindows) "gradlew.bat" else "gradlew").absolutePath


        // 清空目录
        exec { commandLine(gradlew, "clean") }

        // 打包并复制依赖
        exec {
            commandLine(gradlew, "jar", "copy-dependencies")
            environment("ENABLE_BUILD" to true)
        }

        // 检查依赖的开源协议
        exec { commandLine(gradlew, "check-license") }

        // jlink
        exec { commandLine(gradlew, "jlink") }

        // 打包
        exec { commandLine(gradlew, "jpackage") }

        // 根据不同的系统构建不同的二进制包
        pack()
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

/**
 * 构建包
 */
fun pack() {
    val osName = if (os.isMacOsX) "osx" else if (os.isWindows) "windows" else "linux"
    val distributionDir = layout.buildDirectory.dir("distributions").get()
    val finalFilenameWithoutExtension = "${project.name}-${project.version}-${osName}-${arch.name}"
    val projectName = project.name.uppercaseFirstChar()

    if (os.isWindows) {
        packOnWindows(distributionDir, finalFilenameWithoutExtension, projectName)
    } else if (os.isLinux) {
        packOnLinux(distributionDir, finalFilenameWithoutExtension, projectName)
    } else if (os.isMacOsX) {
        packOnMac(distributionDir, finalFilenameWithoutExtension, projectName)
    } else {
        throw GradleException("${os.name} is not supported")
    }

}

/**
 * 创建 zip、7z、msi
 */
fun packOnWindows(distributionDir: Directory, finalFilenameWithoutExtension: String, projectName: String) {
    // zip
    exec {
        commandLine(
            "tar", "-vacf",
            distributionDir.file("${finalFilenameWithoutExtension}.zip").asFile.absolutePath,
            projectName
        )
        workingDir = layout.buildDirectory.dir("jpackage/images/win-msi.image/").get().asFile
    }

    // exe
    exec {
        commandLine(
            "iscc",
            "/DMyAppId=${projectName}",
            "/DMyAppName=${projectName}",
            "/DMyAppVersion=${project.version}",
            "/DMyOutputDir=${distributionDir.asFile.absolutePath}",
            "/DMySetupIconFile=${FileUtils.getFile(projectDir, "src", "main", "resources", "icons", "termora.ico")}",
            "/DMySourceDir=${layout.buildDirectory.dir("jpackage/images/win-msi.image/${projectName}").get().asFile}",
            "/F${finalFilenameWithoutExtension}",
            FileUtils.getFile(projectDir, "src", "main", "resources", "termora.iss")
        )
    }

    // msi
    exec {
        commandLine(
            "cmd", "/c", "move",
            "${projectName}-${project.version}.msi",
            "${finalFilenameWithoutExtension}.msi"
        )
        workingDir = distributionDir.asFile
    }
}

/**
 * 对于 macOS 先对 jpackage 构建的 dmg 重命名 -> 签名 -> 公证，另外还会创建一个 zip 包
 */
fun packOnMac(distributionDir: Directory, finalFilenameWithoutExtension: String, projectName: String) {
    val dmgFile = distributionDir.file("${finalFilenameWithoutExtension}.dmg").asFile
    val zipFile = distributionDir.file("${finalFilenameWithoutExtension}.zip").asFile

    // rename
    // @formatter:off
    exec { commandLine("mv", distributionDir.file("${projectName}-${project.version}.dmg").asFile.absolutePath, dmgFile.absolutePath,) }
    // @formatter:on

    // sign dmg
    if (macOSSign) signMacOSLocalFile(dmgFile)

    // 找到 .app
    val imageFile = layout.buildDirectory.dir("jpackage/images/").get().asFile
    val appFile = imageFile.listFiles()?.firstOrNull()?.listFiles()?.firstOrNull()
        ?: throw FileNotFoundException("${projectName}.app")

    // zip
    // @formatter:off
    exec { commandLine("ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", appFile.absolutePath, zipFile.absolutePath) }
    // @formatter:on

    // sign zip
    if (macOSSign) signMacOSLocalFile(zipFile)

    // 公证
    if (macOSNotary) {
        val pool = Executors.newCachedThreadPool()
        val jobs = mutableListOf<Future<*>>()

        // zip
        pool.submit {
            // 对 zip 公证
            notaryMacOSLocalFile(zipFile)
            // 对 .app 盖章
            stapleMacOSLocalFile(appFile)
            // 删除旧的 zip ，旧的 zip 仅仅是为了公证
            FileUtils.deleteQuietly(zipFile)
            // 再对盖完章的 app 打成 zip 包
            // @formatter:off
            exec { commandLine("ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", appFile.absolutePath, zipFile.absolutePath) }
            // @formatter:on
            // 再对 zip 签名
            signMacOSLocalFile(zipFile)
        }.apply { jobs.add(this) }

        // dmg
        pool.submit {
            // 公证
            notaryMacOSLocalFile(dmgFile)
            // 盖章
            stapleMacOSLocalFile(dmgFile)
        }.apply { jobs.add(this) }

        // join ...
        jobs.forEach { it.get() }

        // shutdown
        pool.shutdown()
    }

}

/**
 * 创建 tar.gz 和 AppImage
 */
fun packOnLinux(distributionDir: Directory, finalFilenameWithoutExtension: String, projectName: String) {
    // tar.gz
    exec {
        commandLine(
            "tar", "-czvf",
            distributionDir.file("${finalFilenameWithoutExtension}.tar.gz").asFile.absolutePath,
            projectName
        )
        workingDir = distributionDir.asFile
    }


    // AppImage
    // Download AppImageKit
    val appimagetool = FileUtils.getFile(projectDir, ".gradle", "appimagetool")
    if (!appimagetool.exists()) {
        exec {
            commandLine(
                "wget",
                "-O", appimagetool.absolutePath,
                "https://github.com/AppImage/AppImageKit/releases/download/13/appimagetool-${if (arch.isArm) "aarch64" else "x86_64"}.AppImage"
            )
            workingDir = distributionDir.asFile
        }

        // AppImageKit chmod
        exec { commandLine("chmod", "+x", appimagetool.absolutePath) }
    }


    // Desktop file
    val termoraName = project.name.uppercaseFirstChar()
    val desktopFile = distributionDir.file(termoraName + File.separator + termoraName + ".desktop").asFile
    desktopFile.writeText(
        """[Desktop Entry]
Type=Application
Name=${termoraName}
Comment=Terminal emulator and SSH client
Icon=/lib/${termoraName}
Categories=Development;
Terminal=false
""".trimIndent()
    )

    // AppRun file
    val appRun = File(desktopFile.parentFile, "AppRun")
    val sb = StringBuilder()
    sb.append("#!/bin/sh").appendLine()
    sb.append("SELF=$(readlink -f \"$0\")").appendLine()
    sb.append("HERE=\${SELF%/*}").appendLine()
    sb.append("export LinuxAppImage=true").appendLine()
    sb.append("exec \"\${HERE}/bin/${termoraName}\" \"$@\"")
    appRun.writeText(sb.toString())
    appRun.setExecutable(true)

    // AppImage
    exec {
        commandLine(appimagetool.absolutePath, termoraName, "${finalFilenameWithoutExtension}.AppImage")
        workingDir = distributionDir.asFile
    }
}

/**
 * macOS 对本地文件进行签名
 */
fun signMacOSLocalFile(file: File) {
    if (os.isMacOsX && macOSSign) {
        if (file.exists() && file.isFile) {
            exec {
                commandLine(
                    "/usr/bin/codesign",
                    "-s", macOSSignUsername,
                    "--timestamp", "--force",
                    "-vvvv", "--options", "runtime",
                    file.absolutePath,
                )
            }
        }
    }
}

/**
 * macOS 对本地文件进行公证
 */
fun notaryMacOSLocalFile(file: File) {
    if (os.isMacOsX && macOSNotary) {
        if (file.exists()) {
            exec {
                commandLine(
                    "/usr/bin/xcrun", "notarytool",
                    "submit", file,
                    "--keychain-profile", macOSNotaryKeychainProfile,
                    "--wait",
                )
            }
        }
    }
}

/**
 * 盖章
 */
fun stapleMacOSLocalFile(file: File) {
    if (os.isMacOsX && macOSNotary) {
        if (file.exists()) {
            exec {
                commandLine(
                    "/usr/bin/xcrun",
                    "stapler", "staple", file,
                )
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

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}