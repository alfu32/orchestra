package com.threadwork.app.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Dialog.ModalityType
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.print.PrintService
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker
import javax.swing.SwingConstants
import javax.swing.Timer
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
    parent: JFrame,
    private val profiles: ThreadworkPrintProfileStore,
    private val formatChoices: List<String>,
    private val scaleChoices: List<String>,
    private val planFallback: () -> SheetPaginationSettings,
    private val documentationFallback: () -> DocumentationPrintSettings,
    private val applyPlanSettings: (String, SheetPaginationSettings) -> Unit,
    private val planPreview: () -> BufferedImage?,
    private val documentationAssets: (DocumentationPrintSettings) -> List<PrintDocumentationAsset>,
    private val exportPlan: (SheetExportFormat, SheetPaginationSettings) -> Unit,
    private val exportDocumentation: (DocumentationExportFormat) -> Unit,
    private val printPlan: (PrintService, SheetPaginationSettings) -> Unit,
    private val printDocumentation: (PrintService, List<PrintDocumentationAsset>, DocumentationPrintSettings) -> Unit,
    private val savePdf: (List<PrintDocumentationAsset>, SheetPaginationSettings, DocumentationPrintSettings) -> Unit,
    private val print: (PrintService, List<PrintDocumentationAsset>, SheetPaginationSettings, DocumentationPrintSettings) -> Unit,
    private val reportStatus: (String) -> Unit,
) : JDialog(parent, "Export", ModalityType.DOCUMENT_MODAL) {
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
    private val planPreviewScroll = JScrollPane(planPreviewLabel).apply {
        border = BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 32
        horizontalScrollBar.unitIncrement = 32
    }
    private val planPreviewHost = JPanel(BorderLayout())
    private val documentPreviewPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(18, 18, 18, 18)
        background = Color(0x6d6d6d)
    }
    private val documentPreviewScroll = JScrollPane(documentPreviewPanel).apply {
        border = BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 32
        horizontalScrollBar.unitIncrement = 32
        viewport.background = documentPreviewPanel.background
    }
    private val documentPreviewHost = JPanel(BorderLayout())
    private val planPrintButton = JButton("Print")
    private val documentationPrintButton = JButton("Print")
    private val planExportGroup = planExportGroup()
    private val documentationExportGroup = documentationExportGroup()
    private val savePdfButton = JButton("PDF All")
    private val printButton = JButton("Print All")
    private val removeButton = JButton("Remove Stored Settings")
    private val resetButton = JButton("Reset Settings")
    private var documents = emptyList<PrintDocumentationAsset>()
    private var updating = false
    private var documentationReady = false
    private var planRefreshGeneration = 0L
    private var documentationRefreshGeneration = 0L
    private val planRefreshTimer = Timer(PREVIEW_DEBOUNCE_MS) {
        renderPlanPreviewAsync(planRefreshGeneration)
    }.apply { isRepeats = false }
    private val documentationRefreshTimer = Timer(PREVIEW_DEBOUNCE_MS) {
        renderDocumentationPreviewAsync(documentationRefreshGeneration)
    }.apply { isRepeats = false }

    init {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        minimumSize = Dimension(1080, 700)
        setSize(1280, 840)
        isAutoRequestFocus = true

        val ownerFocusRestorer = object : WindowAdapter() {
            override fun windowGainedFocus(event: WindowEvent) {
                if (!isShowing) return
                javax.swing.SwingUtilities.invokeLater {
                    if (isShowing) {
                        toFront()
                        requestFocusInWindow()
                    }
                }
            }
        }
        parent.addWindowFocusListener(ownerFocusRestorer)
        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) {
                toFront()
                requestFocusInWindow()
            }

            override fun windowClosed(event: WindowEvent) {
                parent.removeWindowFocusListener(ownerFocusRestorer)
            }
        })

        planPreviewHost.add(planPreviewScroll, BorderLayout.CENTER)
        documentPreviewHost.add(documentPreviewScroll, BorderLayout.CENTER)

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
        addTab("Plan", previewTab(planControls, planPreviewHost, planPrintButton, planExportGroup))
        addTab("Documentation", previewTab(documentationControls, documentPreviewHost, documentationPrintButton, documentationExportGroup))
    }

    private fun previewTab(controls: PaginationControls, preview: Component, printButton: JButton, exports: Component): JPanel = JPanel(BorderLayout(0, 8)).apply {
        add(JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(controls.panel)
            add(FlowLayoutPanel(printButton, exports))
        }, BorderLayout.NORTH)
        add(preview, BorderLayout.CENTER)
    }

    private fun previewTab(controls: DocumentationControls, preview: Component, printButton: JButton, exports: Component): JPanel = JPanel(BorderLayout(0, 8)).apply {
        add(JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(controls.panel)
            add(FlowLayoutPanel(printButton, exports))
        }, BorderLayout.NORTH)
        add(preview, BorderLayout.CENTER)
    }

    private fun planExportGroup(): JPanel = exportGroup(
        listOf(
            "PDF" to { exportPlan(SheetExportFormat.Pdf, planControls.settings()) },
            "SVG" to { exportPlan(SheetExportFormat.Svg, planControls.settings()) },
            "PNG" to { exportPlan(SheetExportFormat.Png, planControls.settings()) },
            "mermaid" to { exportPlan(SheetExportFormat.Mermaid, planControls.settings()) },
        ),
    )

    private fun documentationExportGroup(): JPanel = exportGroup(
        DocumentationExportFormat.entries.map { format -> format.toString() to { exportDocumentation(format) } },
    )

    private fun exportGroup(actions: List<Pair<String, () -> Unit>>): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        add(JLabel("export:"))
        actions.forEach { (label, action) ->
            add(JButton(label).apply { addActionListener { action() } })
        }
    }

    private fun FlowLayoutPanel(printButton: JButton, exports: Component): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        add(printButton)
        add(exports)
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
        planPrintButton.addActionListener {
            selectedTarget()?.service?.let { printPlan(it, planControls.settings()) }
        }
        documentationPrintButton.addActionListener {
            selectedTarget()?.service?.let { printDocumentation(it, documents, documentationControls.settings()) }
        }
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
        savePdfButton.addActionListener {
            savePdf(documents, planControls.settings(), documentationControls.settings())
        }
        printButton.addActionListener {
            selectedTarget()?.service?.let { service ->
                print(service, documents, planControls.settings(), documentationControls.settings())
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
            planExportGroup.isVisible = target.isPdf
            documentationExportGroup.isVisible = target.isPdf
            planPrintButton.isVisible = !target.isPdf
            documentationPrintButton.isVisible = !target.isPdf
            planPrintButton.isEnabled = target.reachable && target.service != null
            documentationPrintButton.isEnabled = target.reachable && target.service != null
            removeButton.isEnabled = !target.isPdf
        } finally {
            updating = false
        }
        updateActionAvailability()
        applyPlanSettings(target.key, profile.plan)
        requestPlanPreview(immediate = true)
        requestDocumentationPreview(immediate = true)
    }

    private fun saveProfileAndRefreshPlan() {
        val target = selectedTarget() ?: return
        val current = profiles.profileFor(target.key, planFallback(), documentationFallback())
        val profile = current.copy(plan = planControls.settings())
        profiles.save(target.key, profile)
        applyPlanSettings(target.key, profile.plan)
        requestPlanPreview()
    }

    private fun saveProfileAndRefreshDocumentation() {
        val target = selectedTarget() ?: return
        val current = profiles.profileFor(target.key, planFallback(), documentationFallback())
        profiles.save(target.key, current.copy(documentation = documentationControls.settings()))
        requestDocumentationPreview()
    }

    private fun selectedTarget(): PrinterTarget? = printerList.selectedValue

    private fun requestPlanPreview(immediate: Boolean = false) {
        planRefreshGeneration += 1
        showPlanLoading()
        if (immediate) {
            planRefreshTimer.stop()
            renderPlanPreviewAsync(planRefreshGeneration)
        } else {
            planRefreshTimer.restart()
        }
    }

    private fun requestDocumentationPreview(immediate: Boolean = false) {
        documentationRefreshGeneration += 1
        showDocumentationLoading()
        if (immediate) {
            documentationRefreshTimer.stop()
            renderDocumentationPreviewAsync(documentationRefreshGeneration)
        } else {
            documentationRefreshTimer.restart()
        }
    }

    private fun renderPlanPreviewAsync(generation: Long) {
        object : SwingWorker<BufferedImage?, Unit>() {
            override fun doInBackground(): BufferedImage? = planPreview()

            override fun done() {
                if (generation != planRefreshGeneration) return
                val image = runCatching { get() }.getOrElse { error ->
                    reportStatus("Plan preview failed: ${error.cause?.message ?: error.message}")
                    null
                }
                planPreviewLabel.icon = image?.let { javax.swing.ImageIcon(scalePreview(it, 1000, 680)) }
                planPreviewLabel.text = if (image == null) "No plan to preview" else ""
                planPreviewHost.removeAll()
                planPreviewHost.add(planPreviewScroll, BorderLayout.CENTER)
                planPreviewHost.revalidate()
                planPreviewHost.repaint()
            }
        }.execute()
    }

    private fun renderDocumentationPreviewAsync(generation: Long) {
        val settings = documentationControls.settings()
        object : SwingWorker<List<PrintDocumentationAsset>, Unit>() {
            override fun doInBackground(): List<PrintDocumentationAsset> = documentationAssets(settings)

            override fun done() {
                if (generation != documentationRefreshGeneration) return
                documents = runCatching { get() }.getOrElse { error ->
                    reportStatus("Documentation preview failed: ${error.cause?.message ?: error.message}")
                    emptyList()
                }
                documentationReady = true
                updateActionAvailability()
                documentPreviewHost.removeAll()
                documentPreviewHost.add(documentPreviewScroll, BorderLayout.CENTER)
                populateDocumentationPreview()
                documentPreviewHost.revalidate()
                documentPreviewHost.repaint()
            }
        }.execute()
    }

    private fun showPlanLoading() {
        planPreviewHost.removeAll()
        planPreviewHost.add(loadingPanel("Rendering plan preview..."), BorderLayout.CENTER)
        planPreviewHost.revalidate()
        planPreviewHost.repaint()
    }

    private fun showDocumentationLoading() {
        documentationReady = false
        updateActionAvailability()
        documentPreviewHost.removeAll()
        documentPreviewHost.add(loadingPanel("Rendering documentation preview..."), BorderLayout.CENTER)
        documentPreviewHost.revalidate()
        documentPreviewHost.repaint()
    }

    private fun loadingPanel(message: String): JPanel = JPanel(BorderLayout(0, 12)).apply {
        background = Color(0x6d6d6d)
        border = BorderFactory.createEmptyBorder(32, 32, 32, 32)
        add(JLabel(message, SwingConstants.CENTER).apply {
            foreground = Color.WHITE
        }, BorderLayout.CENTER)
        add(javax.swing.JProgressBar().apply {
            isIndeterminate = true
            preferredSize = Dimension(220, preferredSize.height)
        }, BorderLayout.SOUTH)
    }

    private fun populateDocumentationPreview() {
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

    private fun updateActionAvailability() {
        val target = selectedTarget()
        savePdfButton.isEnabled = documentationReady && target?.isPdf == true
        printButton.isEnabled = documentationReady && target?.reachable == true && target.service != null
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

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 220
    }

    private class PaginationControls(
        label: String,
        formatChoices: List<String>,
        scaleChoices: List<String>,
    ) {
        private val formatBox = JComboBox(formatChoices.toTypedArray())
        private val scaleBox = JComboBox(scaleChoices.toTypedArray())
        private val multipageBox = JCheckBox("Split fixed sheets across pages")
        private val overlapSpinner = JSpinner(SpinnerNumberModel(5.0, 0.0, 50.0, 0.5))
        private val rasterizedPdf = JRadioButton("Rasterized")
        private val searchablePdf = JRadioButton("Searchable")
        private val pdfModeGroup = ButtonGroup().apply {
            add(rasterizedPdf)
            add(searchablePdf)
        }
        val panel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("$label pagination")
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(JLabel("Format"))
                add(formatBox.apply { preferredSize = Dimension(118, preferredSize.height) })
                add(JLabel("Scale"))
                add(scaleBox.apply { preferredSize = Dimension(76, preferredSize.height) })
                add(multipageBox)
                add(JLabel("Overlap (mm)"))
                add(overlapSpinner.apply { preferredSize = Dimension(76, preferredSize.height) })
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(JLabel("PDF output"))
                add(rasterizedPdf)
                add(searchablePdf)
            }, BorderLayout.SOUTH)
        }

        fun onChange(listener: () -> Unit) {
            formatBox.addActionListener { listener() }
            scaleBox.addActionListener { listener() }
            multipageBox.addActionListener { listener() }
            overlapSpinner.addChangeListener(ChangeListener { listener() })
            rasterizedPdf.addActionListener { listener() }
            searchablePdf.addActionListener { listener() }
        }

        fun setSettings(settings: SheetPaginationSettings) {
            formatBox.selectedItem = settings.formatChoice
            scaleBox.selectedItem = settings.scaleChoice
            multipageBox.isSelected = settings.multipage
            overlapSpinner.value = settings.overlapMm
            (if (settings.pdfRenderMode == PdfRenderMode.Searchable) searchablePdf else rasterizedPdf).isSelected = true
        }

        fun settings(): SheetPaginationSettings = SheetPaginationSettings(
            formatChoice = formatBox.selectedItem?.toString().orEmpty(),
            scaleChoice = scaleBox.selectedItem?.toString().orEmpty(),
            multipage = multipageBox.isSelected,
            overlapMm = (overlapSpinner.value as Number).toDouble(),
            pdfRenderMode = if (searchablePdf.isSelected) PdfRenderMode.Searchable else PdfRenderMode.Rasterized,
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
