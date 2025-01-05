# Termora

**Termora** 是一个终端模拟器和 SSH 客户端，支持 Windows，macOS 和 Linux。

<div align="center">
  <img src="./docs/readme.png" alt="termora" />
</div>

**Termora** 采用 [Kotlin/JVM](https://kotlinlang.org/) 开发并实现了 [XTerm](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html) 协议（尚未完全实现），它的最终目标是通过 [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) 实现全平台（含 Android、iOS、iPadOS 等）。

## 功能特性

- 支持 SSH 和本地终端
- 支持 [SFTP](./docs/sftp-zh_CN.png) 文件传输
- 支持 Windows、macOS、Linux 平台
- 支持 Zmodem 协议
- 支持 SSH 端口转发
- 支持配置同步到 [Gist](https://gist.github.com)
- 支持宏（录制脚本并回放）
- 支持关键词高亮
- 支持密钥管理器
- 支持将命令发送到多个会话
- 支持 [Find Everywhere](./docs/findeverywhere.png) 快速跳转
- 支持数据加密
- ...

## 下载

- [releases](https://github.com/TermoraDev/termora/releases/latest)

### macOS

由于苹果开发者证书正在申请中，所以 macOS 用户需要执行 `sudo xattr -r -d com.apple.quarantine /Applications/Termora.app` 后才可以运行程序。

## 开发

建议使用 [JetBrainsRuntime](https://github.com/JetBrains/JetBrainsRuntime) 的 JDK 版本，通过 `./gradlew :run` 即可运行程序。

通过 `./gradlew dist` 可以自动构建适用于本机的版本。在 macOS 上是：`dmg`，在 Windows 上是：`zip`，在 Linux 上是：`tar.gz`。

## 协议

本软件采用双重许可模式，您可以选择以下任意一种许可方式：

- AGPL-3.0：根据 [AGPL-3.0](https://opensource.org/license/agpl-v3) 的条款，您可以自由使用、分发和修改本软件。
- 专有许可：如果希望在闭源或专有环境中使用，请联系作者获取许可。
