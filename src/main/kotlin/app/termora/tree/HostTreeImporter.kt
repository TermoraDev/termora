package app.termora.tree

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.account.TeamRole
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.OpenHostAction
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import app.termora.plugin.ExtensionManager
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.protocol.TransferProtocolProvider
import app.termora.tag.TagDialog
import app.termora.tag.TagManager
import app.termora.tag.TagSimpleTreeCellRendererExtension
import app.termora.transfer.TransferActionEvent
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.ini4j.Ini
import org.ini4j.Reg
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.io.*
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal class HostTreeImporter(private val tree: NewHostTree) {
    private val owner get() = SwingUtilities.getWindowAncestor(tree)
    private val properties get() = DatabaseManager.getInstance().properties
    companion object {
        private val log = LoggerFactory.getLogger(HostTreeImporter::class.java)
        private val CSV_HEADERS = arrayOf("Folders", "Label", "Protocol", "Hostname", "Port", "Username", "Password")
        private fun isUtf8(file: File): Boolean {
            val bytes = file.readBytes()

            // check UTF-8 BOM (EF BB BF)
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                return true
            }

            return try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * using Windows API convert ANSI -> UTF-16 -> String
         */
        private fun ansiToUtf8(bytes: ByteArray): String {
            val kernel32 = MyKernel32.INSTANCE

            val wideCharCount = kernel32.MultiByteToWideChar(
                MyKernel32.CP_ACP,
                0,  // MB_PRECOMPOSED
                bytes,
                bytes.size,
                null,
                0
            )
            if (wideCharCount == 0) {
                throw RuntimeException("MultiByteToWideChar failed to calculate buffer size")
            }

            // ANSI -> UTF-16
            val wideChars = CharArray(wideCharCount)
            val result = kernel32.MultiByteToWideChar(
                MyKernel32.CP_ACP,
                0,
                bytes,
                bytes.size,
                wideChars,
                wideCharCount
            )

            if (result == 0) {
                throw RuntimeException("MultiByteToWideChar conversion failed")
            }

            // UTF-16 to Java String
            return String(wideChars)
        }
    }
    fun importHosts(folder: HostTreeNode, type: ImportType) {
        try {
            doImportHosts(folder, type)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            OptionPane.showMessageDialog(owner, ExceptionUtils.getMessage(e), messageType = JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun doImportHosts(folder: HostTreeNode, type: ImportType) {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        chooser.isMultiSelectionEnabled = false

        when (type) {
            ImportType.WindTerm -> chooser.fileFilter = FileNameExtensionFilter("WindTerm (*.sessions)", "sessions")
            ImportType.SSH -> chooser.fileFilter = FileNameExtensionFilter("SSH (config)", "config")
            ImportType.CSV -> chooser.fileFilter = FileNameExtensionFilter("CSV (*.csv)", "csv")
            ImportType.SecureCRT -> chooser.fileFilter = FileNameExtensionFilter("SecureCRT (*.xml)", "xml")
            ImportType.electerm -> chooser.fileFilter = FileNameExtensionFilter("electerm (*.json)", "json")
            ImportType.PuTTY -> chooser.fileFilter = FileNameExtensionFilter("PuTTY (*.reg)", "reg")
            ImportType.MobaXterm -> chooser.fileFilter =
                FileNameExtensionFilter("MobaXterm (*.mobaconf,*.ini)", "ini", "mobaconf")

            ImportType.Xshell -> {
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Xshell Sessions"
                chooser.isAcceptAllFileFilterUsed = true
            }

            ImportType.FinalShell -> {
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isAcceptAllFileFilterUsed = true
            }
        }

        val dir = properties.getString("NewHostTree.ImportHosts.defaultDir", StringUtils.EMPTY)
        if (dir.isNotBlank()) {
            val file = FileUtils.getFile(dir)
            if (file.exists()) {
                chooser.currentDirectory = file
            }
        }

        // csv template
        if (type == ImportType.CSV) {
            val code = OptionPane.showConfirmDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.csv.download-template"),
                optionType = JOptionPane.YES_NO_OPTION,
                messageType = JOptionPane.QUESTION_MESSAGE,
                options = arrayOf(
                    I18n.getString("termora.welcome.contextmenu.import"),
                    I18n.getString("termora.welcome.contextmenu.download")
                ),
                initialValue = I18n.getString("termora.welcome.contextmenu.import")
            )
            if (code == JOptionPane.DEFAULT_OPTION) {
                return
            } else if (code != JOptionPane.YES_OPTION) {
                chooser.setSelectedFile(File("termora_import.csv"))
                if (chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                    CSVPrinter(
                        FileWriter(chooser.selectedFile, Charsets.UTF_8),
                        CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()
                    ).use { printer ->
                        printer.printRecord(
                            "Projects/Dev",
                            "Web Server",
                            SSHProtocolProvider.PROTOCOL,
                            "192.168.1.1",
                            "22",
                            "root",
                            StringUtils.EMPTY,
                        )
                        printer.printRecord(
                            "Projects/Prod",
                            "Web Server",
                            SSHProtocolProvider.PROTOCOL,
                            "serverhost.com",
                            "2222",
                            "root",
                            StringUtils.EMPTY,
                        )
                        printer.printRecord(
                            StringUtils.EMPTY,
                            "Web Server",
                            SSHProtocolProvider.PROTOCOL,
                            "serverhost.com",
                            "2222",
                            "user",
                            StringUtils.EMPTY,
                        )
                    }
                    OptionPane.openFileInFolder(
                        owner,
                        chooser.selectedFile,
                        I18n.getString("termora.welcome.contextmenu.import.csv.download-template-done-open-folder"),
                        I18n.getString("termora.welcome.contextmenu.import.csv.download-template-done")
                    )
                }
                return
            }
        }

        // 选择文件
        if (type != ImportType.SSH) {
            val code = chooser.showOpenDialog(owner)
            if (code != JFileChooser.APPROVE_OPTION) {
                return
            }
        }

        val file = chooser.selectedFile
        if (file != null && file.parentFile != null) {
            properties.putString(
                "NewHostTree.ImportHosts.defaultDir",
                (if (FileUtils.isDirectory(file)) file else file.parentFile).absolutePath
            )
        }

        val nodes = when (type) {
            ImportType.SSH -> parseFromSSH(folder)
            ImportType.WindTerm -> parseFromWindTerm(folder, file)
            ImportType.SecureCRT -> parseFromSecureCRT(folder, file)
            ImportType.MobaXterm -> parseFromMobaXterm(folder, file)
            ImportType.PuTTY -> parseFromPuTTY(folder, file)
            ImportType.Xshell -> parseFromXshell(folder, file)
            ImportType.FinalShell -> parseFromFinalShell(folder, file)
            ImportType.electerm -> parseFromElecterm(folder, file)
            ImportType.CSV -> file.bufferedReader().use { parseFromCSV(folder, it) }
        }

        if (nodes.isEmpty()) return

        val hostManager = HostManager.getInstance()
        for (node in nodes) {
            hostManager.addHost(
                node.host.copy(
                    ownerType = folder.host.ownerType,
                    ownerId = folder.host.ownerId,
                ),
                DatabaseChangedExtension.Source.User
            )
            for (host in node.getAllChildren().map { it.host }) {
                hostManager.addHost(
                    host.copy(ownerType = folder.host.ownerType, ownerId = folder.host.ownerId),
                    DatabaseChangedExtension.Source.User
                )
            }
        }

        // 重新加载
        tree.simpleTreeModel.reload(folder)

        // expand root
        tree.expandPath(TreePath(tree.simpleTreeModel.getPathToRoot(folder)))
    }

    private fun parseFromWindTerm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val sessions = ohMyJson.runCatching { ohMyJson.parseToJsonElement(file.readText()).jsonArray }
            .onFailure { OptionPane.showMessageDialog(owner, ExceptionUtils.getMessage(it)) }
            .getOrNull() ?: return emptyList()

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until sessions.size) {
                val json = sessions[i].jsonObject
                val protocol = json["session.protocol"]?.jsonPrimitive?.content ?: SSHProtocolProvider.PROTOCOL
                if (!StringUtils.equalsIgnoreCase(SSHProtocolProvider.PROTOCOL, protocol)) continue
                val label = json["session.label"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val target = json["session.target"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val port = json["session.port"]?.jsonPrimitive?.intOrNull ?: 22
                val group = json["session.group"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val groups = group.split(">")
                printer.printRecord(
                    groups.joinToString("/"),
                    label,
                    SSHProtocolProvider.PROTOCOL,
                    target,
                    port,
                    StringUtils.EMPTY,
                    StringUtils.EMPTY,
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromSSH(folder: HostTreeNode): List<HostTreeNode> {
        val entries = HostConfigEntry.readHostConfigEntries(HostConfigEntry.getDefaultHostConfigFile())

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (entry in entries) {
                printer.printRecord(
                    StringUtils.EMPTY,
                    StringUtils.defaultString(entry.host),
                    SSHProtocolProvider.PROTOCOL,
                    StringUtils.defaultString(entry.hostName),
                    if (entry.port == 0) 22 else entry.port,
                    StringUtils.defaultString(entry.username),
                    StringUtils.EMPTY,
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromSecureCRT(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val xPath = XPathFactory.newInstance().newXPath()
        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = db.parse(file)
        val sessionElement = xPath.compile("/VanDyke/key[@name='Sessions']")
            .evaluate(doc, XPathConstants.NODE) as Element? ?: return emptyList()
        val nodeList = xPath.compile(".//key[not(key)]").evaluate(sessionElement, XPathConstants.NODESET) as NodeList
        if (nodeList.length == 0) return emptyList()

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until nodeList.length) {
                val ele = nodeList.item(i) as Element
                val protocol = xPath.compile("./string[@name='Protocol Name']/text()").evaluate(ele)
                if (!StringUtils.equalsIgnoreCase(protocol, "SSH2")) continue
                val label = ele.getAttribute("name")
                if (StringUtils.isBlank(label)) continue
                val hostname = xPath.compile("./string[@name='Hostname']/text()").evaluate(ele)
                if (StringUtils.isBlank(hostname)) continue
                val username = xPath.compile("./string[@name='Username']/text()").evaluate(ele)
                val port = xPath.compile("./dword[@name='[SSH2] Port']/text()").evaluate(ele)?.toIntOrNull() ?: 22


                val folders = mutableListOf<String>()
                var p = ele.parentNode as Element
                while (p != sessionElement) {
                    folders.addFirst(p.getAttribute("name"))
                    p = p.parentNode as Element
                }
                printer.printRecord(
                    folders.joinToString("/"),
                    label,
                    SSHProtocolProvider.PROTOCOL,
                    hostname,
                    port.toString(),
                    username,
                    StringUtils.EMPTY,
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromPuTTY(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val reg = Reg(file)
        val prefix = "HKEY_CURRENT_USER\\Software\\SimonTatham\\PuTTY\\Sessions\\"


        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (key in reg.keys) {
                if (!key.startsWith(prefix)) {
                    continue
                }
                val properties = reg[key]?.toProperties() ?: continue
                val label = StringUtils.removeStart(key, prefix)
                val hostname = properties.getProperty("HostName")
                val username = properties.getProperty("UserName")
                val port = properties.getProperty("PortNumber")
                printer.printRecord(
                    StringUtils.EMPTY,
                    label,
                    SSHProtocolProvider.PROTOCOL,
                    hostname,
                    port.toString(),
                    username,
                    StringUtils.EMPTY,
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromMobaXterm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val ini = Ini()
        ini.config.isEscapeKeyOnly = true

        if (isUtf8(file)) {
            ini.load(file)
        } else {
            if (!SystemInfo.isWindows) {
                OptionPane.showMessageDialog(
                    owner,
                    "Non-UTF-8 encoded MobaXterm config files are only supported on Windows. Please convert the file to UTF-8 encoding first."
                )
                return emptyList()
            }
            val bytes = file.readBytes()
            val utf8String = ansiToUtf8(bytes)
            StringReader(utf8String).use { reader ->
                ini.load(reader)
            }
        }

        val bookmarks = mutableListOf<String>()
        for (key in ini.keys) {
            if (key.startsWith("Bookmarks")) {
                bookmarks.add(key)
            }
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->

            for (bookmark in bookmarks) {
                val properties = (ini[bookmark] ?: continue).toProperties()
                // 删除不必要元素
                properties.remove("ImgNum")
                val folders = FilenameUtils.separatorsToUnix(
                    (properties.remove("SubRep")
                        ?: StringUtils.EMPTY).toString()
                )

                for (key in properties.stringPropertyNames()) {
                    val segments = properties.getProperty(key).split("%")
                    if (segments.isEmpty()) continue
                    // ssh: #109#0
                    // telnet: #98#1
                    if (segments.first() != "#109#0") continue
                    val hostname = segments.getOrNull(1) ?: StringUtils.EMPTY
                    val port = segments.getOrNull(2) ?: 22
                    printer.printRecord(
                        folders,
                        key,
                        SSHProtocolProvider.PROTOCOL,
                        hostname,
                        port,
                        StringUtils.EMPTY,
                        StringUtils.EMPTY
                    )
                }
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromXshell(folder: HostTreeNode, dir: File): List<HostTreeNode> {
        val files = FileUtils.listFiles(dir, arrayOf("xsh"), true)
        if (files.isEmpty()) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.xshell-folder-empty")
            )
            return emptyList()
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (file in files) {
                val ini = Ini(file)
                val protocol = ini.get("CONNECTION", "Protocol") ?: SSHProtocolProvider.PROTOCOL
                if (!StringUtils.equalsIgnoreCase(SSHProtocolProvider.PROTOCOL, protocol)) continue
                val folders = FilenameUtils.separatorsToUnix(file.parentFile.relativeTo(dir).toString())
                val hostname = ini.get("CONNECTION", "Host") ?: StringUtils.EMPTY
                val label = file.nameWithoutExtension
                val port = ini.get("CONNECTION", "Port")?.toIntOrNull() ?: 22
                val username = ini.get("CONNECTION:AUTHENTICATION", "UserName") ?: StringUtils.EMPTY
                printer.printRecord(
                    folders,
                    label,
                    SSHProtocolProvider.PROTOCOL,
                    hostname,
                    port,
                    username,
                    StringUtils.EMPTY
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromFinalShell(folder: HostTreeNode, dir: File): List<HostTreeNode> {
        val files = FileUtils.listFiles(
            dir,
            FileFilterUtils.suffixFileFilter("_connect_config.json"),
            FileFilterUtils.trueFileFilter()
        )

        if (files.isEmpty()) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.finalshell-folder-empty")
            )
            return emptyList()
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (file in files) {
                try {
                    val json = ohMyJson.runCatching { ohMyJson.parseToJsonElement(file.readText()) }
                        .getOrNull()?.jsonObject ?: continue
                    val username = json["user_name"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                    val label = json["name"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                    val host = json["host"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                    val port = json["port"]?.jsonPrimitive?.intOrNull ?: 22
                    if (StringUtils.isAllBlank(host, label)) continue
                    val folders = FilenameUtils.separatorsToUnix(file.parentFile.relativeTo(dir).toString())
                    printer.printRecord(
                        folders,
                        StringUtils.defaultIfBlank(label, host),
                        SSHProtocolProvider.PROTOCOL,
                        host,
                        port,
                        username,
                        StringUtils.EMPTY,
                    )
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(file.absolutePath, e)
                    }
                }
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    @Serializable
    private data class ElectermGroup(
        val id: String = StringUtils.EMPTY,
        val title: String = StringUtils.EMPTY,
        val bookmarkIds: Set<String> = emptySet(),
        val bookmarkGroupIds: Set<String> = emptySet(),
    )

    private fun parseFromElecterm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val json = ohMyJson.parseToJsonElement(file.readText()).jsonObject
        val bookmarks = json["bookmarks"]?.jsonArray ?: return emptyList()
        val bookmarkGroups = ohMyJson.decodeFromJsonElement<List<ElectermGroup>>(
            json["bookmarkGroups"]?.jsonArray ?: JsonArray(emptyList())
        )


        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until bookmarks.size) {
                val host = bookmarks[i].jsonObject
                val type = host["type"]?.jsonPrimitive?.content ?: SSHProtocolProvider.PROTOCOL
                if (!StringUtils.equalsIgnoreCase(type, SSHProtocolProvider.PROTOCOL)) continue
                val hostname = host["host"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val id = host["id"]?.jsonPrimitive?.content ?: continue
                val title = host["title"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                if (StringUtils.isAllBlank(title, hostname)) continue
                val username = host["username"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val port = host["port"]?.jsonPrimitive?.intOrNull ?: 22

                val folderNames = mutableListOf<String>()
                var group = bookmarkGroups.find { it.bookmarkIds.contains(id) }
                while (group != null && group.id != "default") {
                    folderNames.addFirst(group.title)
                    group = bookmarkGroups.find { it.bookmarkGroupIds.contains(group?.id ?: StringUtils.EMPTY) }
                }

                printer.printRecord(
                    folderNames.joinToString("/"),
                    StringUtils.defaultIfBlank(title, hostname),
                    SSHProtocolProvider.PROTOCOL,
                    hostname,
                    port,
                    username,
                    StringUtils.EMPTY,
                )
            }

        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromCSV(folderNode: HostTreeNode, sr: Reader): List<HostTreeNode> {
        val records = CSVParser.builder()
            .setFormat(CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).setSkipHeaderRecord(true).get())
            .setCharset(Charsets.UTF_8)
            .setReader(sr)
            .get()
            .use { it.records }
        // 把现有目录提取出来，避免重复创建
        val nodes = folderNode.clone(setOf("Folder"))
            .childrenNode().filter { it.isFolder }
            .toMutableList()

        for (record in records) {
            val map = mutableMapOf<String, String>()
            for (e in record.parser.headerMap.keys) {
                map[e] = record.get(e)
            }

            val folder = map["Folders"] ?: StringUtils.EMPTY
            val label = map["Label"] ?: StringUtils.EMPTY
            val hostname = map["Hostname"] ?: StringUtils.EMPTY
            val port = map["Port"]?.toIntOrNull() ?: 22
            val username = map["Username"] ?: StringUtils.EMPTY
            val password = map["Password"] ?: StringUtils.EMPTY
            val protocol = map["Protocol"] ?: "SSH"
            // 仅支持 SSH、RDP 协议
            if (StringUtils.equalsAnyIgnoreCase(protocol, "SSH", "RDP").not()) continue
            if (StringUtils.isAllBlank(hostname, label)) continue

            var p: HostTreeNode? = null
            if (folder.isNotBlank()) {
                for ((j, name) in folder.split("/").withIndex()) {
                    val folders = if (j == 0 || p == null) nodes
                    else p.children().toList().filterIsInstance<HostTreeNode>()
                    val n = HostTreeNode(
                        Host(
                            name = name, protocol = "Folder",
                            parentId = p?.host?.id ?: folderNode.id
                        )
                    )
                    val cp = folders.find { it.isFolder && it.host.name == name }
                    if (cp != null) {
                        p = cp
                        continue
                    }
                    if (p == null) {
                        p = n
                        nodes.add(n)
                    } else {
                        p.add(n)
                        p = n
                    }
                }
            }

            val n = HostTreeNode(
                Host(
                    name = StringUtils.defaultIfBlank(label, hostname),
                    host = hostname,
                    port = port,
                    username = username,
                    protocol = protocol,
                    parentId = p?.host?.id ?: folderNode.id,
                )
            )

            if (password.isNotBlank()) {
                n.host = n.host.copy(
                    authentication = Authentication.No.copy(
                        type = AuthenticationType.Password,
                        password = password,
                    )
                )
            }

            if (p == null) {
                nodes.add(n)
            } else {
                p.add(n)
            }
        }

        return nodes
    }


    enum class ImportType {
        WindTerm,
        CSV,
        Xshell,
        PuTTY,
        SecureCRT,
        MobaXterm,
        SSH,
        FinalShell,
        electerm,
    }

}
