package app.termora.terminal.panel.vw

import app.termora.Disposer
import app.termora.I18n
import app.termora.Icons
import app.termora.SshClients
import app.termora.plugin.internal.ssh.SSHTerminalTab
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXBusyLabel
import org.slf4j.LoggerFactory
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.io.StringReader
import javax.swing.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

class NvidiaSMIVisualWindow(tab: SSHTerminalTab, visualWindowManager: VisualWindowManager) :
    SSHVisualWindow(tab, "NVIDIA-SMI", visualWindowManager) {

    companion object {
        private val log = LoggerFactory.getLogger(NvidiaSMIVisualWindow::class.java)
    }

    private val nvidiaSMIPanel by lazy { NvidiaSMIPanel() }
    private val busyLabel = JXBusyLabel()
    private val errorPanel = FormBuilder.create().layout(FormLayout("pref:grow", "20dlu, pref, 5dlu, pref"))
        .add(JLabel(FlatSVGIcon(Icons.warningDialog.name, 60, 60))).xy(1, 2, "center, fill")
        .add(JLabel("Not supported")).xy(1, 4, "center, fill")
        .build()
    private val loadingPanel = FormBuilder.create().layout(FormLayout("pref:grow", "20dlu, pref"))
        .add(busyLabel).xy(1, 2, "center, fill")
        .build()
    private val cardLayout = CardLayout()
    private val rootPanel = JPanel(cardLayout)
    private var isPercentage
        get() = properties.getString("VisualWindow.${id}.isPercentage", "false").toBoolean()
        set(value) = properties.putString("VisualWindow.${id}.isPercentage", value.toString())

    private val percentageBtn by lazy { JButton(if (isPercentage) Icons.text else Icons.percentage) }

    init {
        Disposer.register(tab, this)
        initViews()
        initEvents()
        initVisualWindowPanel()
    }

    override fun toolbarButtons(): List<JButton> {
        return listOf(percentageBtn)
    }

    private fun initViews() {
        title = I18n.getString("termora.visual-window.nvidia-smi")
        busyLabel.isBusy = true

        rootPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        rootPanel.add(errorPanel, "ErrorPanel")
        rootPanel.add(loadingPanel, "LoadingPanel")
        rootPanel.add(nvidiaSMIPanel, "NvidiaSMIPanel")

        add(rootPanel, BorderLayout.CENTER)

        cardLayout.show(rootPanel, "LoadingPanel")
    }

    private fun initEvents() {
        percentageBtn.addActionListener {
            isPercentage = !isPercentage
            percentageBtn.icon = if (isPercentage) Icons.text else Icons.percentage
            nvidiaSMIPanel.refreshPanel()
        }

        Disposer.register(this, nvidiaSMIPanel)
    }

    private data class GPU(
        /**
         * 名称 product_name
         */
        val productName: String = StringUtils.EMPTY,

        /**
         * 序号 minor_number
         */
        val minorNumber: Int = 0,

        /**
         * 温度 temperature.gpu_temp
         *
         * 单位：C
         */
        var temp: Double = 0.0,
        var tempText: String = StringUtils.EMPTY,

        /**
         * 使用的功率 gpu_power_readings.power_draw
         *
         * 单位：W
         */
        var powerUsage: Double = 0.0,
        var powerUsageText: String = StringUtils.EMPTY,

        /**
         * 功率大小 gpu_power_readings.max_power_limit
         *
         * 单位：W
         */
        var powerCap: Double = 0.0,
        var powerCapText: String = StringUtils.EMPTY,

        /**
         * 使用的显存 fb_memory_usage.used
         *
         * 单位：Mib
         */
        var memoryUsage: Double = 0.0,
        var memoryUsageText: String = StringUtils.EMPTY,

        /**
         * 显存大小 fb_memory_usage.total
         *
         * 单位：Mib
         */
        var memoryCap: Double = 0.0,
        var memoryCapText: String = StringUtils.EMPTY,


        /**
         * GPU 利用率 utilization.gpu_util
         *
         * 单位：%
         */
        var gpu: Double = 0.0
    )

    private class NvidiaSMI(
        val driverVersion: String = StringUtils.EMPTY,
        val cudaVersion: String = StringUtils.EMPTY,
        val gpus: MutableList<GPU> = mutableListOf<GPU>(),
    )

    private class GPUPanel(val minorNumber: Int, title: String) : JPanel(BorderLayout()) {
        val gpuProgressBar = SmartProgressBar()
        val tempProgressBar = SmartProgressBar()
        val memProgressBar = SmartProgressBar()
        val powerProgressBar = SmartProgressBar()

        init {
            val formMargin = "4dlu"
            var rows = 1
            val step = 2
            val p = FormBuilder.create().debug(false)
                .layout(
                    FormLayout(
                        "left:pref, $formMargin, default:grow",
                        "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
                    )
                )
                .border(
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createTitledBorder(title),
                        BorderFactory.createEmptyBorder(4, 4, 4, 4),
                    )
                )
                .add("GPU: ").xy(1, rows)
                .add(gpuProgressBar).xy(3, rows).apply { rows += step }
                .add("Temp: ").xy(1, rows)
                .add(tempProgressBar).xy(3, rows).apply { rows += step }
                .add("Mem: ").xy(1, rows)
                .add(memProgressBar).xy(3, rows).apply { rows += step }
                .add("Power: ").xy(1, rows)
                .add(powerProgressBar).xy(3, rows).apply { rows += step }
                .build()
            add(p, BorderLayout.CENTER)
        }
    }

    private inner class NvidiaSMIPanel : AutoRefreshPanel() {

        private val xPath by lazy { XPathFactory.newInstance().newXPath() }
        private val db by lazy {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isValidating = false
            factory.isXIncludeAware = false
            factory.isNamespaceAware = false
            val db = factory.newDocumentBuilder()
            db.setEntityResolver(object : EntityResolver {
                override fun resolveEntity(
                    publicId: String?,
                    systemId: String?
                ): InputSource? {
                    return if (StringUtils.contains(systemId, ".dtd")) {
                        InputSource(StringReader(StringUtils.EMPTY))
                    } else {
                        null
                    }
                }

            })
            db
        }

        private var nvidiaSMI = NvidiaSMI()
        private val gpuRootPanel = JPanel()
        private val driverVersionLabel = JLabel()
        private val cudaVersionLabel = JLabel()
        private val gpusLabel = JLabel()


        init {
            initViews()
            initEvents()
        }


        private fun initViews() {

            layout = BorderLayout()

            add(
                FormBuilder.create().debug(false)
                    .layout(
                        FormLayout(
                            "default:grow, pref, default:grow, 4dlu, pref, default:grow, 4dlu, pref, default:grow, default:grow",
                            "pref, 4dlu"
                        )
                    )
                    .add(Box.createHorizontalGlue()).xy(1, 1)
                    .add("Driver: ").xy(2, 1)
                    .add(driverVersionLabel).xy(3, 1)
                    .add("CUDA: ").xy(5, 1)
                    .add(cudaVersionLabel).xy(6, 1)
                    .add("GPUS: ").xy(8, 1)
                    .add(gpusLabel).xy(9, 1)
                    .add(Box.createHorizontalGlue()).xy(10, 1)
                    .build(), BorderLayout.NORTH
            )

            add(JScrollPane(gpuRootPanel).apply {
                verticalScrollBar.maximumSize = Dimension(0, 0)
                verticalScrollBar.preferredSize = Dimension(0, 0)
                verticalScrollBar.minimumSize = Dimension(0, 0)
                border = BorderFactory.createEmptyBorder()
            }, BorderLayout.CENTER)
        }


        private fun initEvents() {

        }


        override suspend fun refresh(isFirst: Boolean) {
            val session = tab.getData(SSHTerminalTab.SSHSession) ?: return

            val doc = try {
                val (code, text) = SshClients.execChannel(session, "nvidia-smi -x -q")
                if (StringUtils.isNotBlank(text)) {
                    db.parse(InputSource(StringReader(text)))
                } else {
                    throw IllegalStateException("exit code: $code")
                }
            } catch (e: Exception) {

                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }

                null
            }

            if (doc == null) {
                if (isFirst) {
                    withContext(Dispatchers.Swing) {
                        cardLayout.show(rootPanel, "ErrorPanel")
                    }
                    // 直接取消轮训
                    SwingUtilities.invokeLater { coroutineScope.cancel() }
                }
                return
            }

            nvidiaSMI = NvidiaSMI(
                driverVersion = xPath.compile("/nvidia_smi_log/driver_version/text()").evaluate(doc),
                cudaVersion = xPath.compile("/nvidia_smi_log/cuda_version/text()").evaluate(doc),
            )
            val attachedGPUs = xPath.compile("/nvidia_smi_log/attached_gpus/text()").evaluate(doc).toIntOrNull() ?: 0

            for (i in 1..attachedGPUs) {
                val gpu = GPU(
                    productName = xPath.compile("/nvidia_smi_log/gpu[${i}]/product_name/text()").evaluate(doc),
                    minorNumber = xPath.compile("/nvidia_smi_log/gpu[${i}]/minor_number/text()").evaluate(doc)
                        .toIntOrNull() ?: 0,
                    tempText = xPath.compile("/nvidia_smi_log/gpu[${i}]/temperature/gpu_temp/text()").evaluate(doc),
                    powerUsageText = xPath.compile("/nvidia_smi_log/gpu[${i}]/gpu_power_readings/power_draw/text()")
                        .evaluate(doc),
                    powerCapText = xPath.compile("/nvidia_smi_log/gpu[${i}]/gpu_power_readings/max_power_limit/text()")
                        .evaluate(doc),
                    memoryUsageText = xPath.compile("/nvidia_smi_log/gpu[${i}]/fb_memory_usage/used/text()")
                        .evaluate(doc),
                    memoryCapText = xPath.compile("/nvidia_smi_log/gpu[${i}]/fb_memory_usage/total/text()")
                        .evaluate(doc),
                    gpu = xPath.compile("/nvidia_smi_log/gpu[${i}]/utilization/gpu_util/text()")
                        .evaluate(doc).split(StringUtils.SPACE).first().toDoubleOrNull() ?: 0.0,
                )

                nvidiaSMI.gpus.add(
                    gpu.copy(
                        temp = gpu.tempText.split(StringUtils.SPACE).first().toDoubleOrNull() ?: 0.0,
                        powerUsage = gpu.powerUsageText.split(StringUtils.SPACE).first().toDoubleOrNull() ?: 0.0,
                        powerCap = gpu.powerCapText.split(StringUtils.SPACE).first().toDoubleOrNull() ?: 0.0,
                        memoryUsage = gpu.memoryUsageText.split(StringUtils.SPACE).first().toDoubleOrNull() ?: 0.0,
                        memoryCap = gpu.memoryCapText.split(StringUtils.SPACE).first().toDoubleOrNull() ?: 0.0,
                    )
                )
            }

            withContext(Dispatchers.Swing) {
                if (isFirst) {
                    initPanel()
                    cardLayout.show(rootPanel, "NvidiaSMIPanel")
                }

                refreshPanel()

            }
        }

        private fun initPanel() {
            gpuRootPanel.layout = GridLayout(
                if (nvidiaSMI.gpus.size % 2 == 0) nvidiaSMI.gpus.size / 2 else nvidiaSMI.gpus.size / 2 + 1,
                2, 4, 4
            )
            for (e in nvidiaSMI.gpus) {
                gpuRootPanel.add(GPUPanel(e.minorNumber, "${e.minorNumber} ${e.productName}"))
            }
        }

        fun refreshPanel() {
            cudaVersionLabel.text = nvidiaSMI.cudaVersion
            driverVersionLabel.text = nvidiaSMI.driverVersion
            gpusLabel.text = nvidiaSMI.gpus.size.toString()

            for (c in gpuRootPanel.components) {
                if (c is GPUPanel) {
                    for (g in nvidiaSMI.gpus) {
                        if (c.minorNumber == g.minorNumber) {
                            refreshGPUPanel(g, c)
                            break
                        }
                    }
                }
            }
        }

        private fun refreshGPUPanel(gpu: GPU, g: GPUPanel) {
            g.gpuProgressBar.value = gpu.gpu.toInt()

            g.tempProgressBar.value = gpu.temp.toInt()
            g.tempProgressBar.string = if (isPercentage) "${g.tempProgressBar.value}%" else gpu.tempText

            g.powerProgressBar.value = (gpu.powerUsage / gpu.powerCap * 100.0).toInt()
            g.powerProgressBar.string = if (isPercentage) "${g.powerProgressBar.value}%"
            else "${gpu.powerUsageText}/${gpu.powerCapText}"

            g.memProgressBar.value = (gpu.memoryUsage / gpu.memoryCap * 100.0).toInt()
            g.memProgressBar.string = if (isPercentage) "${g.memProgressBar.value}%"
            else "${gpu.memoryUsageText}/${gpu.memoryCapText}"

        }
    }


    override fun dispose() {
        busyLabel.isBusy = false
        super.dispose()
    }
}