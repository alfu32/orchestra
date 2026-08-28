package com.threadwork.app.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable
import javax.print.PrintService
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.WindowConstants
import javax.swing.event.ChangeListener
import javax.swing.SpinnerNumberModel

internal data class PrintDocumentationAsset(
    val title: String,
    val pages: List<BufferedImage>,
)

internal class PreprintDialog(
    parent: Component,
    private val profiles: ThreadworkPrintProfileStore,
    private val formatChoices: List<String>,
    private val scaleChoices: List<String>,
    private val fallbackSettings: () -> SheetPaginationSettings,
    private val applySettings: (String, SheetPaginationSettings) -> Unit,
    private val planPreview: () -> BufferedImage?,
    private val documentationAssets: () -> List<PrintDocumentationAsset>,
    private val savePdf: (List<PrintDocumentationAsset>) -> Unit,
    private val print: (PrintService, List<PrintDocumentationAsset>) -> Unit,
    private val reportStatus: (String) -> Unit,
) : JDialog() {
    private val printerChoices = JComboBox<PrinterTarget>()
    private val formatChoicesBox = JComboBox(DefaultComboBoxModel(formatChoices.toTypedArray()))
    private val scaleChoicesBox = JComboBox(DefaultComboBoxModel(scaleChoices.toTypedArray()))
    private val multipageBox = JCheckBox("Split fixed sheets across pages")
    private val overlapSpinner = JSpinner(SpinnerNumberModel(5.0, 0.0, 50.0, 0.5))
    private val planPreviewLabel = JLabel("No plan to preview", SwingConstants.CENTER)
    private val documentPreviewPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }
    private val savePdfButton = JButton("Save PDF...")
    private val printButton = JButton("Print...")
    private val removeButton = JButton("Remove Stored Settings")
    private val resetButton = JButton("Reset Settings")
    private var documents = emptyList<PrintDocumentationAsset>()
    private var updating = false

    init {
        title = "Print Preview"
        isModal = true
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        minimumSize = Dimension(1080, 700)
        setSize(1280, 840)
        setLocationRelativeTo(parent)

        printerChoices.renderer = PrinterTargetRenderer()
        refreshPrinterChoices()
        installListeners()
        documents = runCatching(documentationAssets).getOrElse {
            reportStatus("Documentation preview failed: ${it.message}")
            emptyList()
        }
        refreshDocumentPreview()
        refreshPlanPreview()

        contentPane = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(settingsPanel(), BorderLayout.WEST)
            add(previewPanel(), BorderLayout.CENTER)
            add(actionsPanel(), BorderLayout.SOUTH)
        }
    }

    private fun settingsPanel(): JPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        preferredSize = Dimension(300, 600)
        add(JLabel("Printer"))
        add(printerChoices)
        add(verticalGap())
        add(JLabel("Page format"))
        add(formatChoicesBox)
        add(verticalGap())
        add(JLabel("Scale"))
        add(scaleChoicesBox)
        add(verticalGap())
        add(multipageBox)
        add(verticalGap())
        add(JLabel("Page overlap (mm)"))
        add(overlapSpinner)
        add(verticalGap())
        add(JLabel("Pagination settings are saved per printer."))
    }

    private fun previewPanel(): JTabbedPane = JTabbedPane().apply {
        addTab("Plan", JScrollPane(planPreviewLabel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        })
        addTab("Documentation", JScrollPane(documentPreviewPanel).apply {
            border = BorderFactory.createEmptyBorder()
        })
    }

    private fun actionsPanel(): JPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
        add(removeButton)
        add(resetButton)
        add(savePdfButton)
        add(printButton)
        add(JButton("Close").apply { addActionListener { dispose() } })
    }

    private fun installListeners() {
        printerChoices.addActionListener {
            if (!updating) loadSelectedProfile()
        }
        val settingsChanged = ChangeListener {
            if (!updating) saveCurrentProfileAndRefresh()
        }
        formatChoicesBox.addActionListener { if (!updating) saveCurrentProfileAndRefresh() }
        scaleChoicesBox.addActionListener { if (!updating) saveCurrentProfileAndRefresh() }
        multipageBox.addActionListener { if (!updating) saveCurrentProfileAndRefresh() }
        overlapSpinner.addChangeListener(settingsChanged)
        resetButton.addActionListener {
            selectedTarget()?.let { target ->
                profiles.reset(target.key)
                loadSelectedProfile()
                reportStatus("Reset pagination settings for ${target.label}")
            }
        }
        removeButton.addActionListener {
            selectedTarget()?.let { target ->
                if (!target.isPdf) {
                    profiles.remove(target.key)
                    refreshPrinterChoices(target.key)
                    reportStatus("Removed stored settings for ${target.label}")
                }
            }
        }
        savePdfButton.addActionListener { savePdf(documents) }
        printButton.addActionListener {
            selectedTarget()?.service?.let { service -> print(service, documents) }
        }
    }

    private fun refreshPrinterChoices(preferredKey: String? = null) {
        val targets = profiles.targets()
        updating = true
        try {
            printerChoices.model = DefaultComboBoxModel(targets.toTypedArray())
            val selected = targets.firstOrNull { it.key == preferredKey }
                ?: targets.firstOrNull { it.isPdf }
                ?: targets.firstOrNull()
            printerChoices.selectedItem = selected
        } finally {
            updating = false
        }
        loadSelectedProfile()
    }

    private fun loadSelectedProfile() {
        val target = selectedTarget() ?: return
        val settings = profiles.settingsFor(target.key, fallbackSettings())
        updating = true
        try {
            formatChoicesBox.selectedItem = settings.formatChoice
            scaleChoicesBox.selectedItem = settings.scaleChoice
            multipageBox.isSelected = settings.multipage
            overlapSpinner.value = settings.overlapMm
            savePdfButton.isVisible = target.isPdf
            printButton.isVisible = !target.isPdf
            printButton.isEnabled = target.reachable && target.service != null
            removeButton.isEnabled = !target.isPdf
        } finally {
            updating = false
        }
        applySettings(target.key, settings)
        refreshPlanPreview()
    }

    private fun saveCurrentProfileAndRefresh() {
        val target = selectedTarget() ?: return
        val settings = selectedSettings()
        profiles.save(target.key, settings)
        applySettings(target.key, settings)
        refreshPlanPreview()
    }

    private fun selectedSettings(): SheetPaginationSettings = SheetPaginationSettings(
        formatChoice = formatChoicesBox.selectedItem?.toString().orEmpty(),
        scaleChoice = scaleChoicesBox.selectedItem?.toString().orEmpty(),
        multipage = multipageBox.isSelected,
        overlapMm = (overlapSpinner.value as Number).toDouble(),
    )

    private fun selectedTarget(): PrinterTarget? = printerChoices.selectedItem as? PrinterTarget

    private fun refreshPlanPreview() {
        val image = planPreview()
        planPreviewLabel.icon = image?.let { javax.swing.ImageIcon(scalePreview(it, 1000, 680)) }
        planPreviewLabel.text = if (image == null) "No plan to preview" else ""
        planPreviewLabel.revalidate()
        planPreviewLabel.repaint()
    }

    private fun refreshDocumentPreview() {
        documentPreviewPanel.removeAll()
        if (documents.isEmpty()) {
            documentPreviewPanel.add(JLabel("No documentation pages available."))
        } else {
            documents.forEach { asset ->
                documentPreviewPanel.add(JLabel(asset.title).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = BorderFactory.createEmptyBorder(8, 4, 4, 4)
                })
                asset.pages.forEachIndexed { index, image ->
                    documentPreviewPanel.add(JLabel("Page ${index + 1}", javax.swing.ImageIcon(scalePreview(image, 420, 594)), SwingConstants.LEFT).apply {
                        alignmentX = Component.LEFT_ALIGNMENT
                        verticalTextPosition = SwingConstants.TOP
                        horizontalTextPosition = SwingConstants.CENTER
                        border = BorderFactory.createEmptyBorder(4, 4, 12, 4)
                    })
                }
            }
        }
        documentPreviewPanel.revalidate()
        documentPreviewPanel.repaint()
    }

    private fun verticalGap(): Component = javax.swing.Box.createVerticalStrut(8)

    private fun scalePreview(image: BufferedImage, maxWidth: Int, maxHeight: Int): Image {
        val scale = minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height, 1.0)
        return image.getScaledInstance(
            (image.width * scale).toInt().coerceAtLeast(1),
            (image.height * scale).toInt().coerceAtLeast(1),
            Image.SCALE_SMOOTH,
        )
    }

    private class PrinterTargetRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val target = value as? PrinterTarget ?: return label
            label.text = target.label
            label.icon = ReachabilityIcon(if (target.reachable) Color(0x35a854) else Color(0xd64545))
            label.iconTextGap = 7
            label.toolTipText = target.key
            return label
        }
    }

    private class ReachabilityIcon(private val color: Color) : javax.swing.Icon {
        override fun getIconWidth(): Int = 10

        override fun getIconHeight(): Int = 10

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            val previous = graphics.color
            graphics.color = color
            graphics.fillOval(x + 1, y + 1, 8, 8)
            graphics.color = previous
        }
    }
}

/** Renders the same internally generated PDF pages into a physical printer job. */
internal class RasterPagesPrintable(
    private val pages: List<PdfRasterPage>,
) : Printable {
    override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
        val page = pages.getOrNull(pageIndex) ?: return Printable.NO_SUCH_PAGE
        val graphics2d = graphics.create() as Graphics2D
        try {
            val printableWidth = pageFormat.imageableWidth
            val printableHeight = pageFormat.imageableHeight
            val scale = minOf(
                printableWidth / page.image.width,
                printableHeight / page.image.height,
            )
            val width = page.image.width * scale
            val height = page.image.height * scale
            graphics2d.translate(
                pageFormat.imageableX + (printableWidth - width) / 2.0,
                pageFormat.imageableY + (printableHeight - height) / 2.0,
            )
            graphics2d.scale(scale, scale)
            graphics2d.drawImage(page.image, 0, 0, null)
        } finally {
            graphics2d.dispose()
        }
        return Printable.PAGE_EXISTS
    }
}
