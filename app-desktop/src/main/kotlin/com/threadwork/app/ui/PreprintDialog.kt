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
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
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
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.WindowConstants
import javax.swing.event.ChangeListener
import javax.swing.event.ListSelectionListener
import javax.swing.SpinnerNumberModel

internal data class PrintDocumentationAsset(
    val title: String,
    val markdown: String,
    val pages: List<BufferedImage>,
)

/** Unified per-printer print setup, keeping plan and documentation pagination independent. */
internal class PreprintDialog(
    parent: Component,
    private val profiles: ThreadworkPrintProfileStore,
    private val formatChoices: List<String>,
    private val scaleChoices: List<String>,
    private val planFallback: () -> SheetPaginationSettings,
    private val documentationFallback: () -> DocumentationPrintSettings,
    private val applyPlanSettings: (String, SheetPaginationSettings) -> Unit,
    private val planPreview: () -> BufferedImage?,
    private val documentationAssets: (DocumentationPrintSettings) -> List<PrintDocumentationAsset>,
    private val savePdf: (List<PrintDocumentationAsset>, DocumentationPrintSettings) -> Unit,
    private val print: (PrintService, List<PrintDocumentationAsset>, DocumentationPrintSettings) -> Unit,
    private val reportStatus: (String) -> Unit,
) : JDialog() {
    private val printerModel = DefaultListModel<PrinterTarget>()
    private val printerList = JList(printerModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = PrinterTargetRenderer()
    }
    private val printerDetails = JLabel().apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0x969696)),
            BorderFactory.createEmptyBorder(10, 8, 8, 8),
        )
        verticalAlignment = SwingConstants.TOP
    }
    private val planControls = PaginationControls("Plan", formatChoices, scaleChoices)
    private val documentationControls = DocumentationControls(
        formatChoices.filter { choice -> choice != "Auto" && !choice.contains("-roll") },
    )
    private val planPreviewLabel = JLabel("No plan to preview", SwingConstants.CENTER)
    private val documentPreviewPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(18, 18, 18, 18)
        background = Color(0x6d6d6d)
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

        installListeners()
        refreshPrinterTargets()

        contentPane = JPanel(BorderLayout(10, 10)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(printerPanel(), BorderLayout.WEST)
            add(previewPanel(), BorderLayout.CENTER)
            add(actionsPanel(), BorderLayout.SOUTH)
        }
        setLocationRelativeTo(parent)
    }

    private fun printerPanel(): JPanel = JPanel(BorderLayout(0, 8)).apply {
        preferredSize = Dimension(285, 600)
        add(JLabel("Printers"), BorderLayout.NORTH)
        add(JScrollPane(printerList).apply {
            border = BorderFactory.createEtchedBorder()
            verticalScrollBar.unitIncrement = 28
        }, BorderLayout.CENTER)
        add(printerDetails, BorderLayout.SOUTH)
    }

    private fun previewPanel(): JTabbedPane = JTabbedPane().apply {
        addTab("Plan", previewTab(planControls, JScrollPane(planPreviewLabel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 32
            horizontalScrollBar.unitIncrement = 32
        }))
        addTab("Documentation", previewTab(documentationControls, JScrollPane(documentPreviewPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 32
            horizontalScrollBar.unitIncrement = 32
            viewport.background = documentPreviewPanel.background
        }))
    }

    private fun previewTab(controls: PaginationControls, preview: JScrollPane): JPanel = JPanel(BorderLayout(0, 8)).apply {
        add(controls.panel, BorderLayout.NORTH)
        add(preview, BorderLayout.CENTER)
    }

    private fun previewTab(controls: DocumentationControls, preview: JScrollPane): JPanel = JPanel(BorderLayout(0, 8)).apply {
        add(controls.panel, BorderLayout.NORTH)
        add(preview, BorderLayout.CENTER)
    }

    private fun actionsPanel(): JPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
        add(removeButton)
        add(resetButton)
        add(savePdfButton)
        add(printButton)
        add(JButton("Close").apply { addActionListener { dispose() } })
    }

    private fun installListeners() {
        printerList.addListSelectionListener(ListSelectionListener {
            if (!it.valueIsAdjusting && !updating) loadSelectedProfile()
        })
        planControls.onChange { if (!updating) saveProfileAndRefreshPlan() }
        documentationControls.onChange { if (!updating) saveProfileAndRefreshDocumentation() }
        resetButton.addActionListener {
            selectedTarget()?.let { target ->
                profiles.reset(target.key)
                loadSelectedProfile()
                reportStatus("Reset pagination settings for ${target.label}")
            }
        }
        removeButton.addActionListener {
            selectedTarget()?.takeUnless(PrinterTarget::isPdf)?.let { target ->
                profiles.remove(target.key)
                refreshPrinterTargets(target.key)
                reportStatus("Removed stored settings for ${target.label}")
            }
        }
        savePdfButton.addActionListener { savePdf(documents, documentationControls.settings()) }
        printButton.addActionListener {
            selectedTarget()?.service?.let { service ->
                print(service, documents, documentationControls.settings())
            }
        }
    }

    private fun refreshPrinterTargets(preferredKey: String? = null) {
        val targets = profiles.targets()
        updating = true
        try {
            printerModel.clear()
            targets.forEach(printerModel::addElement)
            val selectedIndex = targets.indexOfFirst { it.key == preferredKey }
                .takeIf { it >= 0 }
                ?: targets.indexOfFirst(PrinterTarget::isPdf).coerceAtLeast(0)
            if (printerModel.size > 0) printerList.selectedIndex = selectedIndex
        } finally {
            updating = false
        }
        loadSelectedProfile()
    }

    private fun loadSelectedProfile() {
        val target = selectedTarget() ?: return
        val profile = profiles.profileFor(target.key, planFallback(), documentationFallback())
        updating = true
        try {
            planControls.setSettings(profile.plan)
            documentationControls.setSettings(profile.documentation)
            printerDetails.text = printerDetailHtml(target)
            savePdfButton.isVisible = target.isPdf
            printButton.isVisible = !target.isPdf
            printButton.isEnabled = target.reachable && target.service != null
            removeButton.isEnabled = !target.isPdf
        } finally {
            updating = false
        }
        applyPlanSettings(target.key, profile.plan)
        refreshPlanPreview()
        refreshDocumentationPreview()
    }

    private fun saveProfileAndRefreshPlan() {
        val target = selectedTarget() ?: return
        val current = profiles.profileFor(target.key, planFallback(), documentationFallback())
        val profile = current.copy(plan = planControls.settings())
        profiles.save(target.key, profile)
        applyPlanSettings(target.key, profile.plan)
        refreshPlanPreview()
    }

    private fun saveProfileAndRefreshDocumentation() {
        val target = selectedTarget() ?: return
        val current = profiles.profileFor(target.key, planFallback(), documentationFallback())
        profiles.save(target.key, current.copy(documentation = documentationControls.settings()))
        refreshDocumentationPreview()
    }

    private fun selectedTarget(): PrinterTarget? = printerList.selectedValue

    private fun refreshPlanPreview() {
        val image = planPreview()
        planPreviewLabel.icon = image?.let { javax.swing.ImageIcon(scalePreview(it, 1000, 680)) }
        planPreviewLabel.text = if (image == null) "No plan to preview" else ""
        planPreviewLabel.revalidate()
        planPreviewLabel.repaint()
    }

    private fun refreshDocumentationPreview() {
        documents = runCatching { documentationAssets(documentationControls.settings()) }.getOrElse {
            reportStatus("Documentation preview failed: ${it.message}")
            emptyList()
        }
        documentPreviewPanel.removeAll()
        if (documents.isEmpty()) {
            documentPreviewPanel.add(JLabel("No documentation pages available.").apply {
                foreground = Color.WHITE
                alignmentX = Component.CENTER_ALIGNMENT
            })
        } else {
            documents.forEach { asset ->
                documentPreviewPanel.add(JLabel(asset.title).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    foreground = Color.WHITE
                    border = BorderFactory.createEmptyBorder(0, 4, 8, 4)
                })
                asset.pages.forEach { image ->
                    documentPreviewPanel.add(JLabel(javax.swing.ImageIcon(scalePreview(image, 620, 840))).apply {
                        alignmentX = Component.CENTER_ALIGNMENT
                        isOpaque = true
                        background = Color.WHITE
                        border = BorderFactory.createLineBorder(Color(0x9a9a9a))
                    })
                    documentPreviewPanel.add(javax.swing.Box.createVerticalStrut(22))
                }
            }
        }
        documentPreviewPanel.revalidate()
        documentPreviewPanel.repaint()
    }

    private fun printerDetailHtml(target: PrinterTarget): String = buildString {
        append("<html><b>").append(escapeHtml(target.label)).append("</b><br>")
        append(if (target.isPdf) "Built-in PDF output" else if (target.reachable) "Available system printer" else "Unavailable stored printer")
        append("<br><span style='font-size:9px'>").append(escapeHtml(target.key)).append("</span></html>")
    }

    private fun scalePreview(image: BufferedImage, maxWidth: Int, maxHeight: Int): Image {
        val scale = minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height, 1.0)
        return image.getScaledInstance(
            (image.width * scale).toInt().coerceAtLeast(1),
            (image.height * scale).toInt().coerceAtLeast(1),
            Image.SCALE_SMOOTH,
        )
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private class PaginationControls(
        label: String,
        formatChoices: List<String>,
        scaleChoices: List<String>,
    ) {
        private val formatBox = JComboBox(formatChoices.toTypedArray())
        private val scaleBox = JComboBox(scaleChoices.toTypedArray())
        private val multipageBox = JCheckBox("Split fixed sheets across pages")
        private val overlapSpinner = JSpinner(SpinnerNumberModel(5.0, 0.0, 50.0, 0.5))
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            border = BorderFactory.createTitledBorder("$label pagination")
            add(JLabel("Format"))
            add(formatBox.apply { preferredSize = Dimension(118, preferredSize.height) })
            add(JLabel("Scale"))
            add(scaleBox.apply { preferredSize = Dimension(76, preferredSize.height) })
            add(multipageBox)
            add(JLabel("Overlap (mm)"))
            add(overlapSpinner.apply { preferredSize = Dimension(76, preferredSize.height) })
        }

        fun onChange(listener: () -> Unit) {
            formatBox.addActionListener { listener() }
            scaleBox.addActionListener { listener() }
            multipageBox.addActionListener { listener() }
            overlapSpinner.addChangeListener(ChangeListener { listener() })
        }

        fun setSettings(settings: SheetPaginationSettings) {
            formatBox.selectedItem = settings.formatChoice
            scaleBox.selectedItem = settings.scaleChoice
            multipageBox.isSelected = settings.multipage
            overlapSpinner.value = settings.overlapMm
        }

        fun settings(): SheetPaginationSettings = SheetPaginationSettings(
            formatChoice = formatBox.selectedItem?.toString().orEmpty(),
            scaleChoice = scaleBox.selectedItem?.toString().orEmpty(),
            multipage = multipageBox.isSelected,
            overlapMm = (overlapSpinner.value as Number).toDouble(),
        )
    }

    private class DocumentationControls(formatChoices: List<String>) {
        private val formatBox = JComboBox(formatChoices.toTypedArray())
        private val headerBox = JCheckBox("Header", true)
        private val footerBox = JCheckBox("Footer", true)
        private val topMargin = marginSpinner()
        private val leftMargin = marginSpinner()
        private val rightMargin = marginSpinner()
        private val bottomMargin = marginSpinner()
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            border = BorderFactory.createTitledBorder("Documentation page")
            add(JLabel("Format"))
            add(formatBox.apply { preferredSize = Dimension(118, preferredSize.height) })
            add(headerBox)
            add(footerBox)
            add(JLabel("Top (mm)"))
            add(topMargin)
            add(JLabel("Left (mm)"))
            add(leftMargin)
            add(JLabel("Right (mm)"))
            add(rightMargin)
            add(JLabel("Bottom (mm)"))
            add(bottomMargin)
        }

        fun onChange(listener: () -> Unit) {
            formatBox.addActionListener { listener() }
            headerBox.addActionListener { listener() }
            footerBox.addActionListener { listener() }
            listOf(topMargin, leftMargin, rightMargin, bottomMargin).forEach { spinner ->
                spinner.addChangeListener(ChangeListener { listener() })
            }
        }

        fun setSettings(settings: DocumentationPrintSettings) {
            formatBox.selectedItem = settings.formatChoice
            headerBox.isSelected = settings.includeHeader
            footerBox.isSelected = settings.includeFooter
            topMargin.value = settings.marginTopMm
            leftMargin.value = settings.marginLeftMm
            rightMargin.value = settings.marginRightMm
            bottomMargin.value = settings.marginBottomMm
        }

        fun settings(): DocumentationPrintSettings = DocumentationPrintSettings(
            formatChoice = formatBox.selectedItem?.toString().orEmpty(),
            includeHeader = headerBox.isSelected,
            includeFooter = footerBox.isSelected,
            marginTopMm = topMargin.numberValue(),
            marginLeftMm = leftMargin.numberValue(),
            marginRightMm = rightMargin.numberValue(),
            marginBottomMm = bottomMargin.numberValue(),
        )

        private fun marginSpinner(): JSpinner = JSpinner(SpinnerNumberModel(15.0, 0.0, 80.0, 0.5)).apply {
            preferredSize = Dimension(70, preferredSize.height)
        }

        private fun JSpinner.numberValue(): Double = (value as Number).toDouble()
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
            label.text = "<html><b>${escape(target.label)}</b><br><span style='font-size:9px'>${if (target.isPdf) "PDF output" else if (target.reachable) "Available" else "Unavailable"}</span></html>"
            label.icon = ReachabilityIcon(if (target.reachable) Color(0x35a854) else Color(0xd64545))
            label.iconTextGap = 8
            label.border = BorderFactory.createEmptyBorder(7, 7, 7, 7)
            label.toolTipText = target.key
            return label
        }

        private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
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

/** Renders the internally generated print pages into a physical printer job. */
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
