package com.threadwork.app.ui

import com.threadwork.core.model.NodeId
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.KotlinxJsonDocumentStore
import com.threadwork.storage.newDocument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JToggleButton
import javax.swing.SwingUtilities

internal data class WorkflowArchetypeTemplate(
    val id: String,
    val group: String,
    val label: String,
    val description: String,
    val document: ThreadworkDocument,
)

internal class WorkflowArchetypesPanel(
    private val store: KotlinxJsonDocumentStore,
    onInsert: (ThreadworkDocument) -> Unit,
) : JPanel(BorderLayout()) {
    private val templates = loadTemplates(store)
    private val snapshotJson = Json { encodeDefaults = true }
    private val previewRepository = InMemoryDocumentRepository(newDocument("Workflow Archetype"))
    private val previewSelection = linkedSetOf<NodeId>()
    private lateinit var previewCanvas: GraphCanvas
    private var selectedTemplate: WorkflowArchetypeTemplate? = null
    private val insertButton = JButton("Insert Archetype into Design").apply {
        isEnabled = false
    }

    init {
        previewCanvas = GraphCanvas(
            repository = previewRepository,
            selection = previewSelection,
            onSelectionChanged = {},
            refreshAll = ::refreshPreviewLayout,
            onModeChanged = {},
        )
        val accordion = createAccordion()
        insertButton.addActionListener {
            selectedTemplate?.let { template -> onInsert(template.document) }
        }
        val left = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            preferredSize = Dimension(300, 500)
            add(
                JScrollPane(accordion).apply {
                    border = BorderFactory.createEmptyBorder()
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                },
                BorderLayout.CENTER,
            )
            add(insertButton, BorderLayout.SOUTH)
        }
        add(
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, previewCanvas).apply {
                resizeWeight = 0.22
                dividerLocation = 300
            },
            BorderLayout.CENTER,
        )
        templates.firstOrNull()?.let(::selectTemplate)
    }

    private fun createAccordion(): JComponent {
        val selectionGroup = ButtonGroup()
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            templates.groupBy(WorkflowArchetypeTemplate::group).forEach { (group, entries) ->
                val body = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = BorderFactory.createEmptyBorder(0, 12, 4, 0)
                }
                val header = JToggleButton("- ${displayGroupName(group)}", true).apply {
                    horizontalAlignment = JToggleButton.LEFT
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                    addActionListener {
                        body.isVisible = isSelected
                        text = "${if (isSelected) "-" else "+"} ${displayGroupName(group)}"
                        body.revalidate()
                    }
                }
                add(header)
                entries.forEach { template ->
                    val item = JToggleButton(
                        "<html><b>${escapeHtml(template.label)}</b><br>" +
                            "${wrapHtml(template.description)}</html>",
                    ).apply {
                        horizontalAlignment = JToggleButton.LEFT
                        border = BorderFactory.createEmptyBorder(7, 9, 7, 9)
                        maximumSize = Dimension(Int.MAX_VALUE, 84)
                        addActionListener { selectTemplate(template) }
                    }
                    selectionGroup.add(item)
                    body.add(item)
                    if (template == templates.firstOrNull()) item.isSelected = true
                }
                add(body)
            }
        }
    }

    private fun selectTemplate(template: WorkflowArchetypeTemplate) {
        selectedTemplate = template
        insertButton.isEnabled = true
        val snapshot = snapshotJson.encodeToString(ThreadworkDocument.serializer(), template.document)
        previewRepository.replaceDocument(store.loadText(snapshot))
        previewSelection.clear()
        refreshPreviewLayout()
        SwingUtilities.invokeLater(previewCanvas::zoomExtentsAfterLayout)
    }

    private fun refreshPreviewLayout() {
        if (!::previewCanvas.isInitialized) return
        previewCanvas.refreshBoundsFromChildren()
        previewCanvas.invalidateRenderCache()
        previewCanvas.repaint()
    }

    private companion object {
        fun loadTemplates(store: KotlinxJsonDocumentStore): List<WorkflowArchetypeTemplate> {
            val loader = WorkflowArchetypesPanel::class.java
            val catalog = loader.getResourceAsStream("/workflow-archetypes/catalog.tsv")
                ?.bufferedReader()
                ?.use { it.readLines() }
                .orEmpty()
            return catalog.asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .mapNotNull { row ->
                    val fields = row.split('\t', limit = 4)
                    if (fields.size != 4) return@mapNotNull null
                    val resourcePath = fields[3]
                    val group = resourcePath.substringBefore('/', "")
                    if (group.isBlank() || resourcePath.substringAfter('/', "").contains('/')) return@mapNotNull null
                    val source = loader.getResourceAsStream("/workflow-archetypes/$resourcePath")
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: return@mapNotNull null
                    runCatching {
                        WorkflowArchetypeTemplate(fields[0], group, fields[1], fields[2], store.loadText(source))
                    }.getOrNull()
                }
                .toList()
        }

        fun displayGroupName(value: String): String = value
            .replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        fun escapeHtml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        fun wrapHtml(value: String, lineLength: Int = 34): String {
            val lines = mutableListOf<String>()
            val current = StringBuilder()
            value.trim().split(Regex("\\s+")).forEach { word ->
                if (current.isNotEmpty() && current.length + word.length + 1 > lineLength) {
                    lines += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines.joinToString("<br>") { escapeHtml(it) }
        }
    }
}
