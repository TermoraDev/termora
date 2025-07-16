import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.org.apache.commons.io.FileUtils
import org.jetbrains.kotlin.org.apache.commons.io.filefilter.FileFilterUtils
import org.jetbrains.kotlin.org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.org.apache.commons.lang3.time.DateFormatUtils
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future

plugins {
    java
    idea
    application
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}


group = "app.termora"
version = rootProject.projectDir.resolve("VERSION").readText().trim()

val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
val arch: ArchitectureInternal = DefaultNativePlatform.getCurrentArchitecture()
val appVersion = project.version.toString().split("-")[0]
val makeAppx = if (os.isWindows) StringUtils.defaultString(System.getenv("MAKEAPPX_PATH")) else StringUtils.EMPTY
val isDeb = os.isLinux && System.getenv("TERMORA_TYPE") == "deb"
val isAppx = os.isWindows && makeAppx.isNotBlank() && System.getenv("TERMORA_TYPE") == "appx"

// macOS 签名信息
val macOSSignUsername = System.getenv("TERMORA_MAC_SIGN_USER_NAME") ?: StringUtils.EMPTY
val macOSSign = os.isMacOsX && macOSSignUsername.isNotBlank()
        && System.getenv("TERMORA_MAC_SIGN").toBoolean()

// macOS 公证信息
val macOSNotaryKeychainProfile = System.getenv("TERMORA_MAC_NOTARY_KEYCHAIN_PROFILE") ?: StringUtils.EMPTY
val macOSNotary = macOSSign && macOSNotaryKeychainProfile.isNotBlank()
        && System.getenv("TERMORA_MAC_NOTARY").toBoolean()

allprojects {
    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven("https://www.jitpack.io")
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

dependencies {

    testImplementation(kotlin("test"))
    testImplementation(libs.hutool)
    testImplementation(libs.sshj)
    testImplementation(libs.jsch)
    testImplementation(libs.rhino)
    testImplementation(libs.delight.rhino.sandbox)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)
    testImplementation(libs.h2)
    testImplementation(libs.exposed.migration)

    api(kotlin("reflect"))
    api(libs.slf4j.api)
    api(libs.pty4j)
    api(libs.slf4j.tinylog)
    api(libs.tinylog.impl)
    api(libs.commons.codec)
    api(libs.commons.io)
    api(libs.commons.lang3)
    api(libs.commons.csv)
    api(libs.commons.net)
    api(libs.commons.text)
    api(libs.kotlinx.coroutines.swing)
    api(libs.kotlinx.coroutines.core)

    api(libs.flatlaf)
    api(libs.flatlafextras)
    api(libs.flatlafswingx)

    api(libs.kotlinx.serialization.json)
    api(libs.swingx)
    api(libs.jgoodies.forms)
    api(libs.jna)
    api(libs.jna.platform)
    api(libs.versioncompare)
    api(libs.oshi.core)
    api(libs.jSystemThemeDetector) { exclude(group = "*", module = "*") }
    api(libs.jfa) { exclude(group = "*", module = "*") }
    api(libs.jbr.api)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.sshd.core)
    api(libs.commonmark)
    api(libs.jgit)
    api(libs.jgit.sshd) { exclude(group = "*", module = "sshd-osgi") }
    api(libs.jgit.agent) { exclude(group = "*", module = "sshd-osgi") }
    api(libs.eddsa)
    api(libs.jnafilechooser)

    api(libs.colorpicker)
    api(libs.mixpanel)
    api(libs.ini4j)
    api(libs.restart4j)
    api(libs.exposed.core)
    api(libs.exposed.crypt)
    api(libs.exposed.jdbc)
    api(libs.sqlite)
    api(libs.jug)
    api(libs.semver4j)
    api(libs.jsvg)
    api(libs.dom4j) { exclude(group = "*", module = "*") }
}

application {
    val args = mutableListOf(
        "-Xmx2048m",
        "-Drelease-date=${DateFormatUtils.format(Date(), "yyyy-MM-dd")}",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    )

    if (os.isMacOsX) {
        // macOS NSWindow
        args.add("--add-opens java.desktop/java.awt=ALL-UNNAMED")
        args.add("--add-opens java.desktop/sun.lwawt=ALL-UNNAMED")
        args.add("--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        args.add("--add-exports java.desktop/com.apple.eawt=ALL-UNNAMED")
        args.add("-Dsun.java2d.metal=true")
        args.add("-Dapple.awt.application.appearance=system")
    }

    args.add("-DTERMORA_PLUGIN_DIRECTORY=${layout.buildDirectory.get().asFile.absolutePath}${File.separator}plugins")

    applicationDefaultJvmArgs = args
    mainClass = "app.termora.MainKt"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = project.name
                description = "Termora is a terminal emulator and SSH client for Windows, macOS and Linux"
                url = "https://github.com/TermoraDev/termora"

                licenses {
                    license {
                        name = "AGPL-3.0"
                        url = "https://opensource.org/license/agpl-v3"
                    }
                }

                developers {
                    developer {
                        name = "hstyi"
                        url = "https://github.com/hstyi"
                    }
                }

                scm {
                    url = "https://github.com/TermoraDev/termora"
                }
            }
        }
    }
}

tasks.processResources {
    filesMatching("**/AppxManifest.xml") {
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "version" to appVersion,
                "architecture" to if (arch.isArm64) "arm64" else "x64",
                "projectDir" to project.projectDir.absolutePath,
            )
        )
    }
}


tasks.test {
    useJUnitPlatform()
}

@Suppress("CascadeIf")
tasks.register<Copy>("copy-dependencies") {
    val dir = layout.buildDirectory.dir("libs")
    from(configurations.runtimeClasspath).into(dir)
    val jna = libs.jna.asProvider().get()
    val pty4j = libs.pty4j.get()
    val flatlaf = libs.flatlaf.get()
    val restart4j = libs.restart4j.get()
    val sqlite = libs.sqlite.get()
    val archName = if (arch.isArm) "aarch64" else "x86_64"
    val dylib = dir.get().dir("dylib").asFile

    doLast {
        for (file in dir.get().asFile.listFiles() ?: emptyArray()) {
            if ("${jna.name}-${jna.version}" == file.nameWithoutExtension) {
                val targetDir = File(dylib, jna.name)
                FileUtils.forceMkdir(targetDir)
                if (os.isWindows) {
                    // @formatter:off
                    exec { commandLine("unzip","-j","-o", file.absolutePath, "com/sun/jna/win32-${arch.name}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isLinux) {
                    // @formatter:off
                    exec { commandLine("unzip","-j","-o", file.absolutePath, "com/sun/jna/linux-${arch.name}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isMacOsX) {
                    // @formatter:off
                    exec { commandLine("unzip","-j","-o", file.absolutePath, "com/sun/jna/darwin-${arch.name}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                }

                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/win32-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/linux-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/darwin-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/sunos-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/openbsd-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/freebsd-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/dragonflybsd-*") }
                exec { commandLine("zip", "-d", file.absolutePath, "com/sun/jna/aix-*") }
            } else if ("${pty4j.name}-${pty4j.version}" == file.nameWithoutExtension) {
                val targetDir = FileUtils.getFile(dylib, pty4j.name, if (os.isWindows) "win32" else "linux")
                FileUtils.forceMkdir(targetDir)
                val myArchName = if (arch.isArm) "aarch64" else "x86-64"
                if (os.isWindows) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "resources/*win/${myArchName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isLinux) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "resources/*linux/${myArchName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isMacOsX) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "resources/com/pty4j/native/darwin*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                }
                exec { commandLine("zip", "-d", file.absolutePath, "resources/*") }
            } else if ("${restart4j.name}-${restart4j.version}" == file.nameWithoutExtension) {
                val targetDir = FileUtils.getFile(dylib, restart4j.name)
                FileUtils.forceMkdir(targetDir)
                if (os.isWindows) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "win32/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isLinux) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "linux/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isMacOsX) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "darwin/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                }
                // 设置可执行权限
                for (e in FileUtils.listFiles(
                    targetDir,
                    FileFilterUtils.trueFileFilter(),
                    FileFilterUtils.falseFileFilter()
                )) e.setExecutable(true)
                exec { commandLine("zip", "-d", file.absolutePath, "win32/*") }
                exec { commandLine("zip", "-d", file.absolutePath, "darwin/*") }
                exec { commandLine("zip", "-d", file.absolutePath, "linux/*") }
            } else if ("${sqlite.name}-${sqlite.version}" == file.nameWithoutExtension) {
                val targetDir = FileUtils.getFile(dylib, sqlite.name)
                FileUtils.forceMkdir(targetDir)
                if (os.isWindows) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "org/sqlite/native/Windows/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isLinux) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "org/sqlite/native/Linux/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isMacOsX) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "org/sqlite/native/Mac/${archName}/*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                }
                exec { commandLine("zip", "-d", file.absolutePath, "org/sqlite/native/*") }
            } else if ("${flatlaf.name}-${flatlaf.version}" == file.nameWithoutExtension) {
                val targetDir = FileUtils.getFile(dylib, flatlaf.name)
                FileUtils.forceMkdir(targetDir)
                val isArm = arch.isArm
                if (os.isWindows) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "com/formdev/flatlaf/natives/*windows*${if (isArm) "arm64" else "x86_64"}*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isLinux) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "com/formdev/flatlaf/natives/*linux*${if (isArm) "arm64" else "x86_64"}*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                } else if (os.isMacOsX) {
                    // @formatter:off
                    exec { commandLine("unzip", "-j" , "-o", file.absolutePath, "com/formdev/flatlaf/natives/*macos*${if (isArm) "arm" else "x86"}*", "-d", targetDir.absolutePath) }
                    // @formatter:on
                }
                exec { commandLine("zip", "-d", file.absolutePath, "com/formdev/flatlaf/natives/*") }
            }
        }

        // 对二进制签名
        if (os.isMacOsX) {
            Files.walk(dylib.toPath()).use { paths ->
                for (path in paths) {
                    if (Files.isRegularFile(path)) {
                        signMacOSLocalFile(path.toFile())
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
        "java.sql",
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
        "-Xmx2048m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-Dlogger.console.level=off",
        "-Dkotlinx.coroutines.debug=off",
        "-Dapp-version=${project.version}",
        "-Drelease-date=${DateFormatUtils.format(Date(), "yyyy-MM-dd")}",
        "--add-exports java.base/sun.nio.ch=ALL-UNNAMED",
    )

    options.add("-Dsun.java2d.metal=true")

    if (os.isMacOsX) {
        // NSWindow
        options.add("-Dapple.awt.application.appearance=system")
        options.add("--add-opens java.desktop/java.awt=ALL-UNNAMED")
        options.add("--add-opens java.desktop/sun.font=ALL-UNNAMED")
        options.add("--add-opens java.desktop/sun.lwawt=ALL-UNNAMED")
        options.add("--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        options.add("--add-opens java.desktop/sun.lwawt.macosx.concurrent=ALL-UNNAMED")
        options.add("--add-exports java.desktop/com.apple.eawt=ALL-UNNAMED")
    }

    if (os.isLinux) {
        if (isDeb) {
            options.add("-Djpackage.app-layout=deb")
        }
    }

    val arguments = mutableListOf("${Jvm.current().javaHome}/bin/jpackage")
    arguments.addAll(listOf("--runtime-image", "${buildDir}/jlink"))
    arguments.addAll(listOf("--name", project.name.uppercaseFirstChar()))
    arguments.addAll(listOf("--app-version", appVersion))
    arguments.addAll(listOf("--main-jar", tasks.jar.get().archiveFileName.get()))
    arguments.addAll(listOf("--main-class", application.mainClass.get()))
    arguments.addAll(listOf("--input", "$buildDir/libs"))
    arguments.addAll(listOf("--temp", "$buildDir/jpackage"))
    arguments.addAll(listOf("--dest", "$buildDir/distributions"))
    arguments.addAll(listOf("--java-options", options.joinToString(StringUtils.SPACE)))
    arguments.addAll(listOf("--vendor", "TermoraDev"))
    arguments.addAll(listOf("--copyright", "TermoraDev"))
    arguments.addAll(listOf("--app-content", "$buildDir/plugins"))

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
        arguments.addAll(listOf("--icon", "${projectDir.absolutePath}/src/main/resources/icons/termora.ico"))
    }

    if (os.isLinux) {
        arguments.addAll(listOf("--icon", "${projectDir.absolutePath}/src/main/resources/icons/termora.png"))
    }


    arguments.add("--type")
    if (os.isMacOsX) {
        arguments.add("dmg")
    } else if (os.isWindows) {
        arguments.add("app-image")
    } else if (os.isLinux) {
        arguments.add(if (isDeb) "deb" else "app-image")
        if (isDeb) {
            arguments.add("--linux-deb-maintainer")
            arguments.add("support@termora.app")
        }
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
}

tasks.register("check-license") {
    doLast {
        val iterator = File(projectDir, "THIRDPARTY").readLines().iterator()
        val thirdPartyNames = mutableSetOf<String>()

        while (iterator.hasNext()) {
            val name = iterator.next()
            if (name.isBlank()) {
                continue
            }

            // ignore license name
            iterator.next()
            // ignore license url
            iterator.next()

            thirdPartyNames.add(name)
        }

        for (dependency in configurations.runtimeClasspath.get().allDependencies) {
            if (!thirdPartyNames.contains(dependency.name)) {
                throw GradleException("${dependency.name} No license found")
            }
        }
    }
}


/**
 * 创建 zip、msi
 */
fun packOnWindows(distributionDir: Directory, finalFilenameWithoutExtension: String, projectName: String) {
    val dir = layout.buildDirectory.dir("distributions").get().asFile
    val cfg = FileUtils.getFile(dir, projectName, "app", "${projectName}.cfg")
    val configText = cfg.readText()

    // appx
    if (isAppx) {
        cfg.writeText(StringBuilder(configText).appendLine("java-options=-Djpackage.app-layout=appx").toString())
        val appxManifest = FileUtils.getFile(dir, projectName, "AppxManifest.xml")
        layout.buildDirectory.file("resources/main/AppxManifest.xml").get().asFile
            .renameTo(appxManifest)
        val icons = setOf("termora.png", "termora_44x44.png", "termora_150x150.png")
        for (file in projectDir.resolve("src/main/resources/icons/").listFiles()) {
            if (icons.contains(file.name)) {
                val p = appxManifest.parentFile.resolve("icons/${file.name}")
                FileUtils.forceMkdirParent(p)
                file.copyTo(p, true)
            }
        }
        exec {
            commandLine(makeAppx, "pack", "/d", projectName, "/p", "${finalFilenameWithoutExtension}.msix")
            workingDir = dir
        }
        return
    }

    // zip
    cfg.writeText(StringBuilder(configText).appendLine("java-options=-Djpackage.app-layout=zip").toString())
    exec {
        commandLine(
            "tar", "-vacf",
            distributionDir.file("${finalFilenameWithoutExtension}.zip").asFile.absolutePath,
            projectName
        )
        workingDir = dir
    }

    // exe
    cfg.writeText(StringBuilder(configText).appendLine("java-options=-Djpackage.app-layout=exe").toString())
    exec {
        commandLine(
            "iscc",
            "/DMyAppId=${projectName}",
            "/DMyAppName=${projectName}",
            "/DMyAppVersion=${appVersion}",
            "/DMyOutputDir=${distributionDir.asFile.absolutePath}",
            "/DMySetupIconFile=${FileUtils.getFile(projectDir, "src", "main", "resources", "icons", "termora.ico")}",
            "/DMySourceDir=${FileUtils.getFile(dir, projectName).absolutePath}",
            "/F${finalFilenameWithoutExtension}",
            FileUtils.getFile(projectDir, "src", "main", "resources", "termora.iss")
        )
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
    exec { commandLine("mv", distributionDir.file("${projectName}-${appVersion}.dmg").asFile.absolutePath, dmgFile.absolutePath,) }
    // @formatter:on

    // sign dmg
    signMacOSLocalFile(dmgFile)

    // 找到 .app
    val imageFile = layout.buildDirectory.dir("jpackage/images/").get().asFile
    val appFile = imageFile.listFiles()?.firstOrNull()?.listFiles()?.firstOrNull()
        ?: throw FileNotFoundException("${projectName}.app")

    // zip
    // @formatter:off
    exec { commandLine("ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", appFile.absolutePath, zipFile.absolutePath) }
    // @formatter:on

    // sign zip
    signMacOSLocalFile(zipFile)

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

    if (isDeb) {
        val arch = if (arch.isArm) "arm" else "amd"
        distributionDir.file("${project.name}_${appVersion}_${arch}64.deb").asFile
            .renameTo(distributionDir.file("${finalFilenameWithoutExtension}.deb").asFile)
        return
    }

    val cfg = FileUtils.getFile(distributionDir.asFile, projectName, "lib", "app", "${projectName}.cfg")
    val configText = cfg.readText()

    // tar.gz
    cfg.writeText(StringBuilder(configText).appendLine("java-options=-Djpackage.app-layout=tar.gz").toString())
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
    cfg.writeText(StringBuilder(configText).appendLine("java-options=-Djpackage.app-layout=AppImage").toString())
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
    }
}

java {
    withSourcesJar()
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}