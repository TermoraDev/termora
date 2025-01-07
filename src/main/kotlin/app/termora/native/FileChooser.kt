package app.termora.native

import app.termora.native.osx.DispatchNative
import com.formdev.flatlaf.util.SystemInfo
import de.jangassen.jfa.foundation.Foundation
import jnafilechooser.api.JnaFileChooser
import org.apache.commons.lang3.StringUtils
import java.awt.Window
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.JFileChooser

class FileChooser {
    var fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    var allowsMultiSelection = true
    var title: String = String()
    var allowsOtherFileTypes = true
    var canCreateDirectories = true
    var win32Filters = mutableListOf<Pair<String, List<String>>>()
    var osxAllowedFileTypes = emptyList<String>()

    /**
     * 默认的打开目录
     */
    var defaultDirectory = StringUtils.EMPTY

    fun showOpenDialog(owner: Window? = null): CompletableFuture<List<File>> {
        val future = CompletableFuture<List<File>>()
        if (SystemInfo.isMacOS) {
            showMacOSOpenDialog(future)
        } else {
            val fileChooser = JnaFileChooser()
            fileChooser.isMultiSelectionEnabled = allowsMultiSelection
            fileChooser.setTitle(title)

            if (defaultDirectory.isNotBlank()) {
                fileChooser.setCurrentDirectory(defaultDirectory)
            }

            if (win32Filters.isNotEmpty()) {
                for ((name, filters) in win32Filters) {
                    fileChooser.addFilter(name, *filters.toTypedArray())
                }
            }

            if (fileChooser.showOpenDialog(owner)) {
                future.complete(fileChooser.selectedFiles.toList())
            } else {
                future.complete(emptyList())
            }
        }
        return future
    }

    fun showSaveDialog(
        owner: Window? = null,
        filename: String
    ): CompletableFuture<File?> {
        val future = CompletableFuture<File?>()
        if (SystemInfo.isMacOS) {
            showMacOSSaveDialog(filename, future)
        } else {
            val fileChooser = JnaFileChooser()
            fileChooser.isMultiSelectionEnabled = allowsMultiSelection
            fileChooser.setTitle(title)
            when (fileSelectionMode) {
                JFileChooser.DIRECTORIES_ONLY -> fileChooser.mode = JnaFileChooser.Mode.Directories
                JFileChooser.FILES_ONLY -> fileChooser.mode = JnaFileChooser.Mode.Files
                else -> fileChooser.mode = JnaFileChooser.Mode.FilesAndDirectories
            }
            fileChooser.setDefaultFileName(filename)
            if (SystemInfo.isWindows) {
                for ((name, filters) in win32Filters) {
                    fileChooser.addFilter(name, *filters.toTypedArray())
                }
            }
            if (fileChooser.showSaveDialog(owner)) {
                future.complete(fileChooser.selectedFile)
            } else {
                future.complete(null)
            }
        }
        return future
    }


    private fun showMacOSOpenDialog(future: CompletableFuture<List<File>>) {
        DispatchNative.instance.dispatch_async(object : Runnable {
            override fun run() {
                val pool = Foundation.NSAutoreleasePool()
                try {
                    val openPanelInstance = Foundation.invoke("NSOpenPanel", "openPanel")

                    // 是否允许选择文件
                    Foundation.invoke(
                        openPanelInstance,
                        "setCanChooseFiles:",
                        fileSelectionMode == JFileChooser.FILES_ONLY || fileSelectionMode == JFileChooser.FILES_AND_DIRECTORIES
                    )

                    // 是否允许选择文件夹
                    Foundation.invoke(
                        openPanelInstance,
                        "setCanChooseDirectories:",
                        fileSelectionMode == JFileChooser.DIRECTORIES_ONLY || fileSelectionMode == JFileChooser.FILES_AND_DIRECTORIES
                    )

                    // 是否允许多选
                    Foundation.invoke(openPanelInstance, "setAllowsMultipleSelection:", allowsMultiSelection)

                    // 限制文件类型
                    if (osxAllowedFileTypes.isNotEmpty()) {
                        Foundation.invoke(
                            openPanelInstance,
                            "setAllowedFileTypes:",
                            Foundation.fillArray(osxAllowedFileTypes.toTypedArray())
                        )
                    }

                    if (defaultDirectory.isNotBlank()) {
                        Foundation.invoke(
                            openPanelInstance,
                            "setDirectoryURL:",
                            Foundation.invoke(
                                "NSURL",
                                "fileURLWithPath:",
                                Foundation.nsString(defaultDirectory)
                            )
                        )
                    }

                    // 标题
                    if (title.isNotBlank()) {
                        Foundation.invoke(openPanelInstance, "setTitle:", Foundation.nsString(title))
                    }

                    val response = Foundation.invoke(openPanelInstance, "runModal")
                    if (response == null || response.toInt() != 1) {
                        future.complete(emptyList())
                        return
                    }

                    val files = mutableListOf<File>()
                    val urls = Foundation.NSArray(Foundation.invoke(openPanelInstance, "URLs"))
                    for (i in 0 until urls.count()) {
                        val url = Foundation.invoke(urls.at(i), "path")
                        if (url != null) {
                            files.add(File(Foundation.toStringViaUTF8(url)))
                        }
                    }

                    future.complete(files)

                } finally {
                    pool.drain()
                }

            }
        })

    }

    private fun showMacOSSaveDialog(filename: String, future: CompletableFuture<File?>) {
        DispatchNative.instance.dispatch_async(object : Runnable {
            override fun run() {
                val pool = Foundation.NSAutoreleasePool()
                try {
                    val savePanelInstance = Foundation.invoke("NSSavePanel", "savePanel")
                    // 默认文件名
                    Foundation.invoke(savePanelInstance, "setNameFieldStringValue:", Foundation.nsString(filename))
                    // 是否允许设置为其他类型
                    Foundation.invoke(savePanelInstance, "setAllowsOtherFileTypes:", allowsOtherFileTypes)
                    // 是否允许创建文件夹
                    Foundation.invoke(savePanelInstance, "setCanCreateDirectories:", canCreateDirectories)

                    if (title.isNotBlank()) {
                        Foundation.invoke(savePanelInstance, "setTitle:", Foundation.nsString(title))
                    }

                    val response = Foundation.invoke(savePanelInstance, "runModal")
                    if (response == null || response.toInt() != 1) {
                        future.complete(null)
                        return
                    }

                    val path = Foundation.toStringViaUTF8(
                        Foundation.invoke(
                            Foundation.invoke(savePanelInstance, "URL"),
                            "path"
                        )
                    )
                    future.complete(File(path))

                } finally {
                    pool.drain()
                }

            }
        })
    }
}