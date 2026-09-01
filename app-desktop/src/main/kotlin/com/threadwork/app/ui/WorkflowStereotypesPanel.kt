package com.threadwork.app.ui

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.KotlinxJsonDocumentStore
import com.threadwork.storage.newDocument
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

internal data class WorkflowStereotypeTemplate(
    val id: String,
    val label: String,
    val description: String,
    val document: ThreadworkDocument,
)

internal class WorkflowStereotypesPanel(
    store: KotlinxJsonDocumentStore,
    onInsert: (ThreadworkDocument) -> Unit,
) : JPanel(BorderLayout()) {
    private val templates = loadTemplates(store)
    private val listModel = DefaultListModel<WorkflowStereotypeTemplate>().apply {
        templates.forEach(::addElement)
    }
    private val previewRepository = InMemoryDocumentRepository(newDocument("Workflow Template"))
    private val previewCanvas = GraphCanvas(
        repository = previewRepository,
        selection = linkedSetOf(),
        onSelectionChanged = {},
        refreshAll = {},
        onModeChanged = {},
    ).apply {
        isEnabled = false
    }
    private val insertButton = JButton("Insert into Design").apply {
        isEnabled = false
    }

    init {
        val templateList = JList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = TemplateRenderer()
            fixedCellHeight = 64
            preferredSize = Dimension(280, 500)
        }
        templateList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val template = templateList.selectedValue
            insertButton.isEnabled = template != null
            if (template != null) {
                previewRepository.replaceDocument(template.document)
                previewCanvas.invalidateRenderCache()
                previewCanvas.repaint()
                SwingUtilities.invokeLater(previewCanvas::zoomExtentsAfterLayout)
            }
        }
        insertButton.addActionListener {
            templateList.selectedValue?.let { template -> onInsert(template.document) }
        }
        val left = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            add(JScrollPane(templateList), BorderLayout.CENTER)
            add(insertButton, BorderLayout.SOUTH)
        }
        add(
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, previewCanvas).apply {
                resizeWeight = 0.22
                dividerLocation = 300
            },
            BorderLayout.CENTER,
        )
        if (templates.isNotEmpty()) templateList.selectedIndex = 0
    }

    private class TemplateRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val template = value as? WorkflowStereotypeTemplate ?: return component
            text = "<html><b>${escapeHtml(template.label)}</b><br><span>${escapeHtml(template.description)}</span></html>"
            font = font.deriveFont(Font.PLAIN, font.size2D)
            border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            return component
        }
    }

    private companion object {
        fun loadTemplates(store: KotlinxJsonDocumentStore): List<WorkflowStereotypeTemplate> {
            val loader = WorkflowStereotypesPanel::class.java
            val catalog = loader.getResourceAsStream("/workflow-stereotypes/catalog.tsv")
                ?.bufferedReader()
                ?.use { it.readLines() }
                .orEmpty()
            return catalog.asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .mapNotNull { row ->
                    val fields = row.split('\t', limit = 4)
                    if (fields.size != 4) return@mapNotNull null
                    val source = loader.getResourceAsStream("/workflow-stereotypes/${fields[3]}")
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: return@mapNotNull null
                    runCatching {
                        WorkflowStereotypeTemplate(fields[0], fields[1], fields[2], store.loadText(source))
                    }.getOrNull()
                }
                .toList()
        }

        fun escapeHtml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
