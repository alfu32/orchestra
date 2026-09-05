package com.threadwork.app.ui

import com.formdev.flatlaf.FlatClientProperties
import com.threadwork.app.editor.EditorCompletionContext
import com.threadwork.app.editor.EditorHoverInfo
import com.threadwork.app.editor.EditorHoverRequest
import com.threadwork.app.editor.GridCodeEditorAdapter
import com.threadwork.app.editor.ThreadworkEditorSettings
import com.threadwork.app.editor.RegexSyntaxHighlighter
import com.threadwork.app.fonts.ThreadworkFonts
import com.threadwork.app.ai.AiPromptRequest
import com.threadwork.app.ai.AiPromptResponse
import com.threadwork.app.ai.AiSupportProviders
import com.threadwork.app.ai.AiSupportTask
import com.threadwork.Version
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.writeSourceMapBeside
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.c.CCompiler
import com.threadwork.compiler.documentation.DocumentationCompiler
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.filesystem.FilesystemCompiler
import com.threadwork.compiler.api.LayoutStrategy
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.api.SourceSetLayoutStrategy
import com.threadwork.compiler.generated.nodejs.JSCompiler
import com.threadwork.compiler.generic.CompilerCompiler
import com.threadwork.compiler.generic.CompilerTemplateRoles
import com.threadwork.compiler.generic.GenericCompiler
import com.threadwork.compiler.naivekotlin.NaiveKotlinCompiler
import com.threadwork.compiler.php.PhpCompiler
import com.threadwork.compiler.quickjs.QuickJsCompiler
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.completion.ModelAwareCompletionService
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.BuiltInTypeIds
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.LinkTransportKinds
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeLayout
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.NodeText
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.Revision
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.core.model.VOID_LAYOUT_STRATEGY_ID
import com.threadwork.core.model.VOID_LANGUAGE_ID
import com.threadwork.core.model.VOID_TECHNOLOGY_ID
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveLayoutStrategyId
import com.threadwork.core.model.effectiveResponsible
import com.threadwork.core.model.effectiveRevision
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.effectiveTextLanguageId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.linkTypeDisplayName
import com.threadwork.core.model.linksUsingType
import com.threadwork.core.model.projectName
import com.threadwork.core.model.rootNode
import com.threadwork.core.model.typeDisplayName
import com.threadwork.core.model.typeNodes
import com.threadwork.storage.DocumentRepository
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.KotlinxJsonDocumentStore
import com.threadwork.storage.newDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.FlowLayout
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Stroke
import java.awt.Toolkit
import java.awt.geom.Path2D
import java.awt.geom.Line2D
import java.awt.print.PrinterJob
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilenameFilter
import java.io.StringReader
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.nio.file.Files
import java.net.URLClassLoader
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import java.util.ServiceLoader
import java.util.concurrent.Executors
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultCellEditor
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer
import javax.swing.TransferHandler
import javax.swing.WindowConstants
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.text.JTextComponent
import javax.swing.SpinnerNumberModel
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private data class DocumentationPageMetadata(
    val projectDate: String,
    val revisionName: String,
    val revisionDate: String,
    val printDate: String,
)

class ThreadworkDesktopApp(
    private val repository: DocumentRepository = InMemoryDocumentRepository(newDocument("Untitled Threadwork")),
    private val store: KotlinxJsonDocumentStore = KotlinxJsonDocumentStore(),
    private val pluginsFolder: Path = defaultPluginsFolder(),
    private val uiPlugins: List<ThreadworkDesktopPlugin> = loadDesktopPlugins(pluginsFolder),
) {
    private companion object {
        const val NATIVE_PROJECT_EXTENSION = "orch"
        const val LEGACY_PROJECT_EXTENSION = "json"
        const val DEFAULT_PROJECT_NAME = "document.orch"
        const val MAX_HISTORY_SNAPSHOTS = 100
        const val DOCUMENTATION_PAGE_BREAK = "<!-- threadwork:page-break -->"
        val BINARY_FILE_EXTENSIONS = setOf("dll", "so", "dylib", "bin", "exe")
        val RASTER_IMAGE_EXTENSIONS = setOf("png", "jpeg", "jpg", "gif", "webm")
        val IMPORT_LANGUAGE_BY_EXTENSION = mapOf(
            "c" to "c",
            "h" to "c",
            "cc" to "cpp",
            "cpp" to "cpp",
            "cxx" to "cpp",
            "hpp" to "cpp",
            "css" to "css",
            "go" to "go",
            "html" to "html",
            "htm" to "html",
            "java" to "java",
            "js" to "javascript",
            "mjs" to "javascript",
            "cjs" to "javascript",
            "json" to "json",
            "kt" to "kotlin",
            "kts" to "kotlin",
            "md" to "markdown",
            "markdown" to "markdown",
            "php" to "php",
            "py" to "python",
            "rs" to "rust",
            "sh" to "shellscript",
            "bash" to "shellscript",
            "sql" to "sql",
            "ts" to "typescript",
            "tsx" to "typescript",
            "xml" to "xml",
            "yaml" to "yaml",
            "yml" to "yaml",
        )
    }

    private val frame = JFrame()
    private val selection = linkedSetOf<NodeId>()
    private val canvas = GraphCanvas(
        repository,
        selection,
        { onSelectionChanged() },
        ::refreshAll,
        ::onCanvasModeChanged,
        ::updateTileProgress,
        ::openNodeInEntityEditor,
        ::showCommandContextMenu,
        ::importDroppedFiles,
    )
    private val hierarchyTree = JTree()
    private val detailsHierarchyTree = JTree()
    private val selectedEntitiesTree = JTree()
    private val treeExpandedIds = mutableMapOf<JTree, MutableSet<String>>()
    private var restoringTreeExpansion = false
    private val compilerCompiler = CompilerCompiler()
    private val documentationCompiler = DocumentationCompiler()
    private val printProfiles = ThreadworkPrintProfileStore()
    private val markdownOptions = MutableDataSet().set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
    private val markdownParser = Parser.builder(markdownOptions).build()
    private val markdownHtmlRenderer = HtmlRenderer.builder(markdownOptions).build()
    private val compilerPlugins: List<CompilerPlugin> = loadCompilerPlugins(pluginsFolder) + FilesystemCompiler() + JSCompiler() + QuickJsCompiler() + PhpCompiler() + CCompiler() + GenericCompiler() + NaiveKotlinCompiler()
    private val compilerTechnologies = availableCompilerTechnologies()
    private val languageIds = availableLanguageIds(compilerTechnologies)
    private val technologyIds = availableTechnologyIds(compilerTechnologies)
    private val layoutStrategies = availableLayoutStrategies()
    private val compilerCapabilityResolver = CompilerCapabilityResolver(compilerPlugins)
    private val inspector = InspectorPanel(
        repository,
        ::refreshAll,
        languageIds,
        technologyIds,
        layoutStrategies,
        compilerCapabilityResolver,
    )
    private val editorTabs = NodeEditorTabs(
        repository,
        ::refreshAll,
        ::checkpointHistory,
        ::undoDocument,
        ::redoDocument,
        languageIds,
        compilerCapabilityResolver,
        ::scheduleEmbeddedValidation,
    )
    private val nativeValidationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "threadwork-embedded-validation").apply { isDaemon = true }
    }
    private val embeddedSourceValidators: List<EmbeddedSourceValidator> = listOf(TinyCcSourceValidator, QuickJsSourceValidator)
    private var nativeValidationGeneration = 0L
    private var pendingValidationNodeId: NodeId? = null
    private var pendingValidationLanguageId: String? = null
    private var nativeDiagnosticDetails: List<EmbeddedDiagnostic> = emptyList()
    private val nativeValidationTimer = Timer(700) { runEmbeddedValidation() }.apply {
        isRepeats = false
    }
    private val status = JLabel("Status and Messages").apply {
        border = BorderFactory.createEmptyBorder(3, 8, 3, 8)
    }
    private val nativeDiagnosticStatus = JLabel("Validation: idle").apply {
        border = BorderFactory.createEmptyBorder(3, 8, 3, 8)
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        foreground = Color(0xff7777)
        horizontalAlignment = SwingConstants.RIGHT
        toolTipText = "Click to inspect mapped compiler diagnostics"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showNativeDiagnosticDetails()
            }
        })
    }
    private val tileProgress = JProgressBar().apply {
        preferredSize = Dimension(190, 18)
        minimumSize = preferredSize
        maximumSize = preferredSize
        isStringPainted = true
        string = "tiles idle"
        value = 0
    }
    private var lastTileProgressUpdateMs = 0L
    private val resourceStatus = JLabel().apply {
        border = BorderFactory.createEmptyBorder(3, 8, 3, 8)
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        horizontalAlignment = SwingConstants.RIGHT
        preferredSize = Dimension(210, 22)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }
    private val statusRight = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2)).apply {
        add(nativeDiagnosticStatus)
        add(tileProgress)
        add(resourceStatus)
    }
    private val statusBar = JPanel(BorderLayout()).apply {
        add(status, BorderLayout.CENTER)
        add(statusRight, BorderLayout.EAST)
    }
    private lateinit var projectPanels: JTabbedPane
    private lateinit var archetypesPanel: WorkflowArchetypesPanel
    private val modeButtons = mutableMapOf<CanvasMode, JToggleButton>()
    private var sheetButton: JToggleButton? = null
    private val commands = linkedMapOf<String, AppCommand>()
    private val pluginToolbarButtons = mutableListOf<PluginToolbarButton>()
    private val pluginContentTabs = mutableListOf<PluginContentTab>()
    private val historyJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var currentSnapshot = documentSnapshot()
    private var applyingHistory = false
    private var currentFile: Path? = null
    private val applicationIdentityLabel = JLabel(
        "Threadwork-${Version.CURRENT.semver}",
        ThreadworkIcons.titleBarIcon(),
        SwingConstants.LEADING,
    ).apply {
        border = BorderFactory.createEmptyBorder(0, 8, 0, 10)
        iconTextGap = 6
    }
    private val currentFileLabel = JLabel("Untitled").apply {
        border = BorderFactory.createEmptyBorder(0, 14, 0, 8)
    }
    private val autosaveTimer = Timer(10_000) { autosave() }.apply { isRepeats = true }
    private val resourceTimer = Timer(1_000) { updateResourceStatus() }.apply { isRepeats = true }

    fun show() {
        registerBuiltInCommands()
        configurePlugins()
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.iconImages = ThreadworkIcons.appIconImages()
        frame.jMenuBar = menuBar()
        frame.contentPane = layout()
        applyFontOptions()
        installKeyBindings(frame.rootPane)
        frame.minimumSize = Dimension(1100, 720)
        frame.setSize(1400, 900)
        frame.setLocationRelativeTo(null)
        refreshAll()
        resetHistory()
        updateWindowTitle()
        autosaveTimer.start()
        updateResourceStatus()
        resourceTimer.start()
        frame.isVisible = true
        frame.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false)
        frame.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false)
    }

    private fun layout(): JComponent {
        val toolbar = JToolBar(JToolBar.HORIZONTAL).apply {
            isFloatable = true
            isRollover = true
            val modes = ButtonGroup()
            add(
                modeButton(
                    "Select",
                    CanvasMode.Select,
                    "select",
                    "Select, move, and edit entities on the canvas.",
                ).also(modes::add),
            )
            add(toolbarIconButton("Undo", "undo", "Undo the most recent document or editor change.") { executeCommand("edit.undo") })
            add(toolbarIconButton("Redo", "redo", "Redo the most recently undone change.") { executeCommand("edit.redo") })
            add(
                modeButton(
                    "Node",
                    CanvasMode.CreateNode,
                    "node",
                    "Place a processing node at the next canvas click.",
                ).also(modes::add),
            )
            add(
                modeButton(
                    "Type",
                    CanvasMode.CreateType,
                    "document",
                    "Declare a shared structured type at the next canvas click.",
                ).also(modes::add),
            )
            add(
                modeButton(
                    "Link",
                    CanvasMode.CreateLink,
                    "link",
                    "Connect a source entity to a target entity.",
                ).also(modes::add),
            )
            add(
                toolbarToggleButton(
                    "Sheet",
                    "sheet",
                    "Show or hide the technical drawing sheet preview.",
                ) { executeCommand("sheet.toggle") }.also { sheetButton = it },
            )
            pluginToolbarButtons.forEach { button ->
                add(JButton(button.label).apply { addActionListener { button.action() } })
            }
            modeButtons[canvas.mode]?.isSelected = true
        }

        configureHierarchyTree(hierarchyTree, editable = true, dragAndDrop = true, updatesSelection = true)
        configureHierarchyTree(detailsHierarchyTree, editable = true, dragAndDrop = true, updatesSelection = true)
        configureHierarchyTree(selectedEntitiesTree, editable = false, dragAndDrop = false, updatesSelection = false)

        val flowDesigner = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            labeledPanel("Entity Hierarchy Tree", JScrollPane(hierarchyTree)),
            canvas,
        ).apply {
            resizeWeight = 0.16
            ThreadworkUiSettings.rememberDividerLocation(this, "designer.hierarchy.divider")
        }

        val selectedAndHierarchy = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            labeledPanel("selected entities list", JScrollPane(selectedEntitiesTree)),
            labeledPanel("Entity Hierarchy Tree", JScrollPane(detailsHierarchyTree)),
        ).apply {
            resizeWeight = 0.45
            ThreadworkUiSettings.rememberDividerLocation(this, "editor.selection.divider")
        }
        val editorAndInspector = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            editorTabs,
            labeledPanel("Inspector", JScrollPane(inspector)),
        ).apply {
            resizeWeight = 0.78
            ThreadworkUiSettings.rememberDividerLocation(this, "editor.inspector.divider")
        }
        val detailsEditor = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, selectedAndHierarchy, editorAndInspector).apply {
            resizeWeight = 0.18
            ThreadworkUiSettings.rememberDividerLocation(this, "editor.hierarchy.divider")
        }

        projectPanels = JTabbedPane().apply {
            addTab("Designer", flowDesigner)
            addTab("Editor", detailsEditor)
            archetypesPanel = WorkflowArchetypesPanel(store) { archetype ->
                canvas.insertArchetype(archetype)
                checkpointHistory()
            }
            addTab(
                "Archetypes",
                archetypesPanel,
            )
            pluginContentTabs.forEach { tab ->
                addTab(tab.title, tab.createPanel())
            }
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(projectPanels, BorderLayout.CENTER)
            add(statusBar, BorderLayout.SOUTH)
        }
    }

    private fun menuBar() = JMenuBar().apply {
        add(applicationIdentityLabel)
        add(JMenu("File").apply {
            add(commandItem("file.new", "document"))
            add(commandItem("file.open", "open"))
            add(commandItem("file.save", "save"))
            add(commandItem("file.saveAs", "disk"))
            add(commandItem("file.saveAsArchetype", "disk"))
            add(commandItem("file.printPreview", "sheet"))
            add(commandItem("app.options"))
            add(commandItem("file.quit"))
        })
        add(JMenu("Edit").apply {
            add(commandItem("edit.undo"))
            add(commandItem("edit.redo"))
            addSeparator()
            add(commandItem("edit.cut"))
            add(commandItem("edit.copy"))
            add(commandItem("edit.paste"))
            addSeparator()
            add(JMenu("Align and Distribute").apply {
                add(commandItem("edit.align.top", "align_top"))
                add(commandItem("edit.align.bottom", "align_bottom"))
                add(commandItem("edit.align.left", "align_left"))
                add(commandItem("edit.align.right", "align_right"))
                addSeparator()
                add(commandItem("edit.distribute.vertical", "distribute_vertically"))
                add(commandItem("edit.distribute.horizontal", "distribute_horizontally"))
            })
        })
        add(JMenu("Graph").apply {
            add(commandItem("graph.zoomExtents"))
            add(commandItem("graph.mode.select", "select"))
            add(commandItem("graph.mode.node", "node"))
            add(commandItem("graph.mode.link", "link"))
            add(commandItem("graph.deleteSelection"))
            add(commandItem("commands.palette"))
        })
        add(JMenu("Build").apply {
            add(commandItem("compile.project", "build", "Build"))
            add(commandItem("compile.compiler", "build", "Build Compiler"))
            add(commandItem("compile.documentation", "build", "Compile Documentation"))
        })
        add(JMenu("AI Support").apply {
            add(commandItem("ai.copyComponentFiches"))
            addSeparator()
            add(commandItem("ai.generateSource"))
            add(commandItem("ai.tidySpecification"))
            add(commandItem("ai.generateTestData"))
        })
        add(JMenu("Help").apply {
            add(commandItem("help.about"))
        })
        add(currentFileLabel)
    }

    private fun installKeyBindings(component: JComponent) {
        val inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = component.actionMap
        commands.values.forEach { command ->
            command.keyStroke?.let { keyStroke ->
                inputMap.put(keyStroke, command.id)
                actionMap.put(command.id, object : AbstractAction() {
                    override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                        executeCommand(command.id)
                    }
                })
            }
        }
    }

    private fun registerBuiltInCommands() {
        if (commands.isNotEmpty()) return
        val shortcut = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        val shiftShortcut = shortcut or InputEvent.SHIFT_DOWN_MASK
        registerCommand(AppCommand("file.new", "File: New", KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcut)) { newFile() })
        registerCommand(AppCommand("file.open", "File: Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut)) { openFile() })
        registerCommand(AppCommand("file.save", "File: Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut)) { saveFile() })
        registerCommand(AppCommand("file.saveAs", "File: Save As...", KeyStroke.getKeyStroke(KeyEvent.VK_S, shiftShortcut)) { saveAsFile() })
        registerCommand(AppCommand("file.saveAsArchetype", "File: Save Model as Archetype...") { saveAsArchetype() })
        registerCommand(AppCommand("file.printPreview", "File: Export...", KeyStroke.getKeyStroke(KeyEvent.VK_P, shortcut)) { showPreprintDialog() })
        registerCommand(AppCommand("file.quit", "File: Quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut)) { quit() })
        registerCommand(AppCommand("app.options", "Application: Options...") { showOptions() })
        registerCommand(AppCommand("edit.undo", "Edit: Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut)) { undo() })
        registerCommand(AppCommand("edit.redo", "Edit: Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut)) { redo() })
        registerCommand(AppCommand("edit.redoAlt", "Edit: Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, shiftShortcut)) { redo() })
        registerCommand(AppCommand("edit.cut", "Edit: Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcut)) { cut() })
        registerCommand(AppCommand("edit.copy", "Edit: Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut)) { copy() })
        registerCommand(AppCommand("edit.paste", "Edit: Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcut)) { paste() })
        registerCommand(
            AppCommand(
                "ai.copyComponentFiches",
                "AI Support: Copy Selected Component Fiches as Markdown",
                enabled = ::hasSelectedProcessingNodes,
            ) { copySelectedComponentFiches() },
        )
        registerCommand(
            AppCommand(
                "ai.generateSource",
                "AI Support: Generate Source Code for Selected Components",
                enabled = ::hasSelectedProcessingNodes,
            ) { runAiSupport(AiSupportTask.GenerateSource) },
        )
        registerCommand(
            AppCommand(
                "ai.tidySpecification",
                "AI Support: Tidy and Format Selected Specifications",
                enabled = ::hasSelectedProcessingNodes,
            ) { runAiSupport(AiSupportTask.TidySpecification) },
        )
        registerCommand(
            AppCommand(
                "ai.generateTestData",
                "AI Support: Generate Test Data for Selected Components",
                enabled = ::hasSelectedProcessingNodes,
            ) { runAiSupport(AiSupportTask.GenerateTestData) },
        )
        registerCommand(AppCommand("edit.align.top", "Edit: Align Top") { alignAndDistribute(AlignmentOperation.AlignTop) })
        registerCommand(AppCommand("edit.align.bottom", "Edit: Align Bottom") { alignAndDistribute(AlignmentOperation.AlignBottom) })
        registerCommand(AppCommand("edit.align.left", "Edit: Align Left") { alignAndDistribute(AlignmentOperation.AlignLeft) })
        registerCommand(AppCommand("edit.align.right", "Edit: Align Right") { alignAndDistribute(AlignmentOperation.AlignRight) })
        registerCommand(AppCommand("edit.distribute.vertical", "Edit: Distribute Evenly Vertically") { alignAndDistribute(AlignmentOperation.DistributeVertical) })
        registerCommand(AppCommand("edit.distribute.horizontal", "Edit: Distribute Evenly Horizontally") { alignAndDistribute(AlignmentOperation.DistributeHorizontal) })
        registerCommand(AppCommand("graph.zoomExtents", "Graph: Zoom Extents") { canvas.zoomExtents() })
        registerCommand(AppCommand("graph.mode.select", "Graph: Select Mode", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)) { canvas.setMode(CanvasMode.Select) })
        registerCommand(AppCommand("graph.mode.node", "Graph: Node Mode") { canvas.setMode(CanvasMode.CreateNode) })
        registerCommand(AppCommand("graph.mode.type", "Graph: Type Mode") { canvas.setMode(CanvasMode.CreateType) })
        registerCommand(AppCommand("graph.mode.link", "Graph: Link Mode") { canvas.setMode(CanvasMode.CreateLink) })
        registerCommand(AppCommand("graph.deleteSelection", "Graph: Delete Selection", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)) {
            if (graphShortcutEnabled()) deleteSelection()
        })
        registerCommand(AppCommand("sheet.toggle", "Sheet: Toggle Preview") {
            applyPdfPlanSettings()
            sheetButton?.isSelected = canvas.toggleSheet()
        })
        registerCommand(AppCommand("compile.project", "Build: Project or Selection") { compileProject() })
        registerCommand(AppCommand("compile.compiler", "Build: Generate Compiler From @Compiler") { generateCompilerFromDesign() })
        registerCommand(AppCommand("compile.documentation", "Build: Compile Documentation") { compileDocumentation() })
        registerCommand(AppCommand("commands.palette", "Commands: Open Palette", KeyStroke.getKeyStroke(KeyEvent.VK_P, shiftShortcut)) { showCommandPalette() })
        registerCommand(AppCommand("help.about", "Help: About") { showAbout() })
    }

    private fun registerCommand(command: AppCommand) {
        commands[command.id] = command
    }

    private fun configurePlugins() {
        if (uiPlugins.isEmpty()) return
        val context = object : ThreadworkPluginContext {
            override fun document(): ThreadworkDocument = repository.getDocument()

            override fun postModelUpdate(label: String, update: (DocumentRepository) -> Unit) {
                update(repository)
                status.text = label
                refreshAll()
            }

            override fun addToolbarButton(label: String, action: () -> Unit) {
                pluginToolbarButtons += PluginToolbarButton(label, action)
            }

            override fun addContentTab(title: String, createPanel: () -> JComponent) {
                pluginContentTabs += PluginContentTab(title, createPanel)
            }

            override fun addCommand(id: String, title: String, keyStroke: KeyStroke?, action: () -> Unit) {
                registerCommand(AppCommand("plugin.$id", title, keyStroke, action = action))
            }
        }
        uiPlugins.forEach { plugin ->
            runCatching { plugin.configure(context) }
                .onFailure { status.text = "Plugin ${plugin.id} failed: ${it.message}" }
        }
    }

    private fun executeCommand(id: String) {
        val command = commands[id] ?: return
        if (!command.enabled()) return
        command.action()
    }

    private fun commandItem(id: String, iconId: String? = null, labelOverride: String? = null): JMenuItem {
        val command = commands.getValue(id)
        return JMenuItem(labelOverride ?: command.title.substringAfter(": ")).apply {
            accelerator = command.keyStroke
            isEnabled = command.enabled()
            iconId?.let(ThreadworkIcons::buttonIcon)?.let {
                icon = it
                horizontalTextPosition = SwingConstants.RIGHT
                iconTextGap = 6
            }
            addActionListener { executeCommand(id) }
        }
    }

    private fun showCommandContextMenu(component: Component, x: Int, y: Int) {
        val popup = JPopupMenu()
        var previousGroup: String? = null
        commands.values
            .filter { it.enabled() }
            .distinctBy { it.title }
            .forEach { command ->
                val group = command.title.substringBefore(": ", missingDelimiterValue = "")
                if (previousGroup != null && group != previousGroup) popup.addSeparator()
                popup.add(JMenuItem(command.title).apply {
                    accelerator = command.keyStroke
                    addActionListener { executeCommand(command.id) }
                })
                previousGroup = group
            }
        if (popup.componentCount > 0) popup.show(component, x, y)
    }

    private fun updateTileProgress(done: Int, total: Int, active: Boolean) {
        val maximum = if (total <= 0) 1 else total
        val value = if (total <= 0) 0 else done.coerceIn(0, total)
        val text = when {
            total <= 0 -> "tiles idle"
            active && value < total -> "tiles $value/$total"
            else -> "tiles ready"
        }
        val finalState = total <= 0 || !active || value >= total
        val now = System.currentTimeMillis()
        if (!finalState && now - lastTileProgressUpdateMs < 180) return
        if (
            tileProgress.maximum == maximum &&
            tileProgress.value == value &&
            tileProgress.string == text
        ) {
            return
        }
        lastTileProgressUpdateMs = now
        tileProgress.maximum = maximum
        tileProgress.value = value
        tileProgress.string = text
    }

    private fun updateResourceStatus() {
        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).coerceAtLeast(0)
        val maxMb = (runtime.maxMemory() / (1024 * 1024)).coerceAtLeast(0)
        val cpuLoad = (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
            ?.processCpuLoad
            ?.takeIf { it >= 0.0 }
        val cpu = cpuLoad?.let { (it * 100.0).roundToInt().coerceIn(0, 100) }
        resourceStatus.text = if (cpu != null) {
            String.format("CPU %3d%% | MEM %4d/%4dM", cpu, usedMb, maxMb)
        } else {
            String.format("CPU ---%% | MEM %4d/%4dM", usedMb, maxMb)
        }
    }

    private fun newFile() {
        autosave()
        repository.replaceDocument(newDocument("Untitled Threadwork"))
        selection.clear()
        currentFile = null
        resetHistory()
        refreshAll()
        updateWindowTitle()
    }

    private fun importDroppedFiles(files: List<Path>, dropPoint: Point) {
        val accepted = files.mapNotNull { path ->
            val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
            val kind = importedFileKind(extension)
            if (kind == null) {
                status.text = "Unsupported dropped file: ${path.fileName}"
                return@mapNotNull null
            }
            runCatching {
                ImportedFile(path, kind, Files.readAllBytes(path))
            }.onFailure {
                status.text = "Could not read ${path.fileName}: ${it.message}"
            }.getOrNull()
        }
        if (accepted.isEmpty()) return

        val parentId = canvas.dropParentAt(dropPoint) ?: repository.getDocument().rootNodeId
        val created = mutableListOf<NodeId>()
        accepted.forEachIndexed { index, imported ->
            val node = repository.createNode(parentId, imported.path.fileName.toString(), NodeKind.Processor)
            repository.updateNodeLayout(
                node.id,
                node.layout.copy(
                    x = dropPoint.x.toDouble() + index * 40.0,
                    y = dropPoint.y.toDouble() + index * 40.0,
                ),
            )
            repository.updateNodeTechnology(
                node.id,
                TechnologyMetadata(
                    languageId = imported.kind.languageId.orEmpty(),
                    technologyId = "file-export",
                    fileExtension = imported.kind.extension,
                    contentType = imported.kind.contentType,
                ),
            )
            when {
                imported.kind.isSvg || !imported.kind.isBinary -> repository.updateNodeText(
                    node.id,
                    NodeText(
                        declaration = imported.bytes.toString(Charsets.UTF_8),
                        declarationLanguageId = imported.kind.languageId.orEmpty(),
                    ),
                )
                else -> repository.updateNodeBinaryContent(node.id, imported.bytes)
            }
            created += node.id
        }
        checkpointHistory()
        selection.clear()
        selection += created
        status.text = "Imported ${created.size} file${if (created.size == 1) "" else "s"} as node${if (created.size == 1) "" else "s"}."
        refreshAll()
    }

    private fun importedFileKind(extension: String): ImportedFileKind? = when {
        extension in BINARY_FILE_EXTENSIONS -> ImportedFileKind(
            extension = extension,
            languageId = null,
            contentType = "application/octet-stream",
            isBinary = true,
        )
        extension in RASTER_IMAGE_EXTENSIONS -> ImportedFileKind(
            extension = extension,
            languageId = null,
            contentType = when (extension) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webm" -> "image/webm"
                else -> "image/jpeg"
            },
            isBinary = true,
        )
        extension == "svg" -> ImportedFileKind(
            extension = extension,
            languageId = "xml",
            contentType = "image/svg+xml",
            isSvg = true,
        )
        IMPORT_LANGUAGE_BY_EXTENSION[extension] != null -> ImportedFileKind(
            extension = extension,
            languageId = IMPORT_LANGUAGE_BY_EXTENSION.getValue(extension),
            contentType = when (extension) {
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "md", "markdown" -> "text/markdown"
                else -> "text/plain"
            },
        )
        else -> null
    }

    private data class ImportedFile(
        val path: Path,
        val kind: ImportedFileKind,
        val bytes: ByteArray,
    )

    private data class ImportedFileKind(
        val extension: String,
        val languageId: String?,
        val contentType: String,
        val isBinary: Boolean = false,
        val isSvg: Boolean = false,
    )

    private fun quit() {
        autosave()
        frame.dispose()
        kotlin.system.exitProcess(0)
    }

    private fun cut() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        when (focusOwner) {
            is GridCodeEditorAdapter -> focusOwner.commandCut()
            is JTextComponent -> focusOwner.cut()
            else -> if (graphShortcutEnabled()) {
                canvas.copySelection()
                deleteSelection()
            }
        }
    }

    private fun copy() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        when (focusOwner) {
            is GridCodeEditorAdapter -> focusOwner.commandCopy()
            is JTextComponent -> focusOwner.copy()
            else -> if (graphShortcutEnabled()) canvas.copySelection()
        }
    }

    private fun copySelectedComponentFiches() {
        val fiches = documentationCompiler.componentFiches(repository.getDocument(), selection)
        if (fiches.isBlank()) {
            status.text = "Select at least one processing node to copy its component fiche."
            return
        }
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(fiches), null)
        }.onSuccess {
            val count = selection.count { id ->
                repository.getNode(id)?.let { !it.isLink && it.kind !in setOf(NodeKind.Note, NodeKind.Type) } == true
            }
            status.text = "Copied $count component fiche${if (count == 1) "" else "s"} as Markdown."
        }.onFailure {
            status.text = "Could not copy component fiches: ${it.message}"
        }
    }

    private fun hasSelectedProcessingNodes(): Boolean = selection.any { id ->
        repository.getNode(id)?.let { !it.isLink && it.kind !in setOf(NodeKind.Note, NodeKind.Type) } == true
    }

    private fun runAiSupport(task: AiSupportTask) {
        val fiches = documentationCompiler.componentFiches(repository.getDocument(), selection)
        if (fiches.isBlank()) {
            status.text = "Select at least one processing node for AI support."
            return
        }
        val selectedNodeIds = selection.filter { id ->
            repository.getNode(id)?.let { !it.isLink && it.kind !in setOf(NodeKind.Note, NodeKind.Type) } == true
        }
        when (
            val response = AiSupportProviders.current().submit(
                AiPromptRequest(
                    task = task,
                    componentMarkdown = fiches,
                    document = repository.getDocument(),
                    nodeIds = selectedNodeIds,
                ),
            )
        ) {
            is AiPromptResponse.Text -> runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(response.value), null)
            }.onSuccess {
                status.text = "Copied ${task.label.lowercase()} prompt to the system clipboard."
            }.onFailure {
                status.text = "Could not copy AI prompt: ${it.message}"
            }

            is AiPromptResponse.Unavailable -> status.text = response.message
        }
    }

    private fun paste() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        when (focusOwner) {
            is GridCodeEditorAdapter -> focusOwner.commandPaste()
            is JTextComponent -> focusOwner.paste()
            else -> if (graphShortcutEnabled()) canvas.pasteSelection()
        }
    }

    private fun undo() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner is GridCodeEditorAdapter) {
            focusOwner.commandUndo()
            return
        }
        undoDocument()
    }

    private fun redo() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner is GridCodeEditorAdapter) {
            focusOwner.commandRedo()
            return
        }
        redoDocument()
    }

    private fun showCommandPalette() {
        val dialog = JDialog(frame, "Command Palette", true)
        val query = JTextField()
        val model = DefaultListModel<AppCommand>()
        val list = JList(model).apply {
            visibleRowCount = 12
            cellRenderer = CommandListCellRenderer()
        }

        fun refresh() {
            val text = query.text.trim().lowercase()
            model.clear()
            commands.values
                .filter { it.enabled() }
                .filter { command ->
                    text.isBlank() ||
                        command.title.lowercase().contains(text) ||
                        command.id.lowercase().contains(text)
                }
                .distinctBy { it.title }
                .forEach(model::addElement)
            if (model.size() > 0) list.selectedIndex = 0
        }

        fun executeSelected() {
            val command = list.selectedValue ?: return
            dialog.dispose()
            executeCommand(command.id)
        }

        query.document.addDocumentListener(SimpleDocumentListener { refresh() })
        query.addActionListener { executeSelected() }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 2) executeSelected()
            }
        })
        dialog.rootPane.registerKeyboardAction(
            { dialog.dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        refresh()
        dialog.contentPane = JPanel(BorderLayout(6, 6)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(query, BorderLayout.NORTH)
            add(JScrollPane(list), BorderLayout.CENTER)
        }
        dialog.setSize(520, 360)
        dialog.setLocationRelativeTo(frame)
        SwingUtilities.invokeLater { query.requestFocusInWindow() }
        dialog.isVisible = true
    }

    private fun showOptions() {
        val originalTheme = ThreadworkAppearance.theme
        val originalPalette = ThreadworkAppearance.palette(originalTheme)
        val themeSelector = JComboBox(ApplicationTheme.entries.map { it.label }.toTypedArray()).apply {
            selectedItem = originalTheme.label
        }
        val designerSelector = JComboBox(ThreadworkFonts.designerOptions.map { it.label }.toTypedArray()).apply {
            selectedItem = ThreadworkFonts.optionLabel(ThreadworkFonts.designerFontId, ThreadworkFonts.designerOptions)
        }
        val codeSelector = JComboBox(ThreadworkFonts.codeOptions.map { it.label }.toTypedArray()).apply {
            selectedItem = ThreadworkFonts.optionLabel(ThreadworkFonts.codeFontId, ThreadworkFonts.codeOptions)
        }
        val indentSpaces = JSpinner(SpinnerNumberModel(ThreadworkEditorSettings.indentSpaces, 1, 16, 1))
        val compositeTargetPx = JSpinner(
            SpinnerNumberModel(ThreadworkDesignerSettings.compositeTitleTargetScreenPx, 8.0, 48.0, 1.0),
        )
        val compositeReferenceWidth = JSpinner(
            SpinnerNumberModel(ThreadworkDesignerSettings.compositeReferenceViewportWidth, 400.0, 10000.0, 50.0),
        )
        val compositeReferenceHeight = JSpinner(
            SpinnerNumberModel(ThreadworkDesignerSettings.compositeReferenceViewportHeight, 300.0, 10000.0, 50.0),
        )
        var selectedPalette = originalPalette
        val paletteHost = JPanel(BorderLayout())
        fun rebuildPaletteEditor(theme: ApplicationTheme) {
            selectedPalette = ThreadworkAppearance.palette(theme)
            paletteHost.removeAll()
            paletteHost.add(createPaletteEditor(selectedPalette) { palette ->
                selectedPalette = palette
                canvas.setPalette(palette)
                editorTabs.applyPalette(palette)
            }, BorderLayout.CENTER)
            paletteHost.revalidate()
            paletteHost.repaint()
            canvas.setPalette(selectedPalette)
            editorTabs.applyPalette(selectedPalette)
        }
        themeSelector.addActionListener {
            rebuildPaletteEditor(ApplicationTheme.fromLabel(themeSelector.selectedItem?.toString().orEmpty()))
        }
        rebuildPaletteEditor(originalTheme)

        val generalContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("Application theme"))
            add(themeSelector)
            add(JLabel("Flow Designer font"))
            add(designerSelector)
            add(JLabel("Composite title target screen size").apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
            add(compositeTargetPx)
            add(JLabel("Composite reference viewport width").apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
            add(compositeReferenceWidth)
            add(JLabel("Composite reference viewport height").apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
            add(compositeReferenceHeight)
            add(JLabel("Code editor font").apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
            add(codeSelector)
            add(JLabel("Code editor indent spaces").apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
            add(indentSpaces)
        }
        val aiOptions = AiSupportProviders.optionsPanel()
        val content = JTabbedPane().apply {
            addTab("General", generalContent)
            addTab("Flow Designer colors", paletteHost)
            addTab("Inference Providers", aiOptions.component)
            preferredSize = Dimension(560, 500)
        }
        val result = JOptionPane.showConfirmDialog(frame, content, "Options", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result != JOptionPane.OK_OPTION) {
            canvas.setPalette(originalPalette)
            editorTabs.applyPalette(originalPalette)
            return
        }

        val selectedTheme = ApplicationTheme.fromLabel(themeSelector.selectedItem?.toString().orEmpty())
        ThreadworkAppearance.theme = selectedTheme
        ThreadworkAppearance.updatePalette(selectedTheme, selectedPalette)
        ThreadworkAppearance.applyLookAndFeel()
        SwingUtilities.updateComponentTreeUI(frame)
        ThreadworkFonts.designerFontId = ThreadworkFonts.optionId(designerSelector.selectedItem?.toString().orEmpty(), ThreadworkFonts.designerOptions)
        ThreadworkFonts.codeFontId = ThreadworkFonts.optionId(codeSelector.selectedItem?.toString().orEmpty(), ThreadworkFonts.codeOptions)
        ThreadworkEditorSettings.indentSpaces = (indentSpaces.value as? Int) ?: ThreadworkEditorSettings.indentSpaces
        ThreadworkDesignerSettings.compositeTitleTargetScreenPx = (compositeTargetPx.value as Number).toDouble()
        ThreadworkDesignerSettings.compositeReferenceViewportWidth = (compositeReferenceWidth.value as Number).toDouble()
        ThreadworkDesignerSettings.compositeReferenceViewportHeight = (compositeReferenceHeight.value as Number).toDouble()
        aiOptions.commit()
        applyFontOptions()
        canvas.setPalette(ThreadworkAppearance.palette())
        editorTabs.applyPalette(ThreadworkAppearance.palette())
        canvas.refreshBoundsFromChildren()
        canvas.repaint()
        status.text = "Options updated"
    }

    private fun createPaletteEditor(
        initial: DesignerPalette,
        onPaletteChanged: (DesignerPalette) -> Unit,
    ): JComponent {
        var palette = initial
        val rows = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }
        DesignerColorKey.entries.forEach { key ->
            val valueField = JTextField(ThreadworkAppearance.colorToHex(palette[key]), 9)
            fun applyColor() {
                val color = ThreadworkAppearance.colorFromHex(valueField.text) ?: return
                palette = palette.withColor(key, color)
                onPaletteChanged(palette)
            }
            valueField.document.addDocumentListener(SimpleDocumentListener(::applyColor))
            val picker = JButton("Pick").apply {
                toolTipText = "Choose ${key.label}"
                addActionListener {
                    JColorChooser.showDialog(frame, "Choose ${key.label}", palette[key])?.let { color ->
                        valueField.text = ThreadworkAppearance.colorToHex(color)
                    }
                }
            }
            rows.add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
                add(JLabel(key.label).apply { preferredSize = Dimension(185, 24) })
                add(valueField)
                add(picker)
            })
        }
        return JScrollPane(rows).apply {
            border = BorderFactory.createEmptyBorder()
        }
    }

    private fun applyFontOptions() {
        canvas.setDesignerFont(ThreadworkFonts.designerFont(13f))
        editorTabs.applyCodeEditorFont(ThreadworkFonts.codeFont(14f))
    }

    private fun graphShortcutEnabled(): Boolean {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return focusOwner !is JTextComponent && focusOwner !is GridCodeEditorAdapter
    }

    private fun updateWindowTitle() {
        val fileName = currentFile?.fileName?.toString() ?: "Untitled"
        frame.title = "Threadwork-${Version.CURRENT.semver} : $fileName"
        currentFileLabel.text = fileName
        currentFileLabel.toolTipText = currentFile
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
    }

    private fun showAbout() {
        val version = Version.CURRENT
        val details = buildString {
            appendLine("Threadwork")
            appendLine("Version: ${version.semver}")
            appendLine("Git commit: ${version.gitCommitId}")
            appendLine("Git tag: ${version.gitTag ?: "-"}")
            appendLine("Build date: ${version.buildDate}")
            appendLine("Plugins folder: ${pluginsFolder.toAbsolutePath().normalize()}")
            appendLine("Loaded UI plugins: ${uiPlugins.size}")
            appendLine("Loaded compiler plugins: ${compilerPlugins.size}")
        }
        JOptionPane.showMessageDialog(frame, details, "About Threadwork", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun availableCompilerTechnologies(): List<CompilerTechnology> =
        compilerPlugins.flatMap { it.providedTechnologies }
            .filter { it.languageId.isNotBlank() && it.technologyId.isNotBlank() }
            .distinct()
            .sortedWith(compareBy<CompilerTechnology> { it.languageId }.thenBy { it.technologyId })

    private fun availableLanguageIds(technologies: List<CompilerTechnology>): List<String> =
        (RegexSyntaxHighlighter.availableLanguageIds() +
            compilerPlugins.flatMap { it.supportedLanguageIds } +
            technologies.map { it.languageId })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun availableTechnologyIds(technologies: List<CompilerTechnology>): List<String> =
        (compilerPlugins.flatMap { it.supportedTechnologyIds } + technologies.map { it.technologyId })
            .map { it.trim() }
            .filter { it.isNotBlank() && it != VOID_TECHNOLOGY_ID }
            .distinct()
            .sorted()

    private fun availableLayoutStrategies(): List<LayoutStrategy> =
        listOf(
            ClassifiedFilesystemLayoutStrategy,
            DirectFileSystemHomorphismLayoutStrategy,
            SingleFileLayoutStrategy,
            SourceSetLayoutStrategy,
        ).let { available ->
            val supportedIds = compilerPlugins.flatMapTo(linkedSetOf()) { it.supportedLayoutStrategyIds }
            if (supportedIds.isEmpty()) available else available.filter { it.id in supportedIds }
        }

    private fun openFile() {
        val path = chooseDocumentPath("Open .orch", FileDialog.LOAD) ?: return
        autosave()
        repository.replaceDocument(store.load(path))
        currentFile = path
        selection.clear()
        resetHistory()
        refreshAll()
        updateWindowTitle()
        canvas.zoomExtentsAfterLayout()
    }

    private fun saveFile() {
        val path = currentFile ?: chooseDocumentPath("Save .orch", FileDialog.SAVE) ?: return
        store.save(repository.getDocument(), path)
        currentFile = path
        repository.clearDirty()
        updateWindowTitle()
    }

    private fun saveAsFile() {
        val path = chooseDocumentPath("Save As .orch", FileDialog.SAVE) ?: return
        store.save(repository.getDocument(), path)
        currentFile = path
        repository.clearDirty()
        updateWindowTitle()
    }

    private fun saveAsArchetype() {
        val userDefinedFolder = defaultUserArchetypesFolder().resolve("user-defined")
        val folder = runCatching { Files.createDirectories(userDefinedFolder) }.getOrElse { error ->
            JOptionPane.showMessageDialog(
                frame,
                error.message ?: "Could not create the user archetypes folder.",
                "Save Model as Archetype",
                JOptionPane.ERROR_MESSAGE,
            )
            return
        }
        val dialog = FileDialog(frame, "Save Model as Archetype", FileDialog.SAVE).apply {
            directory = folder.toString()
            file = "${archetypeFileStem(repository.getDocument().projectName())}.$NATIVE_PROJECT_EXTENSION"
            filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".$NATIVE_PROJECT_EXTENSION", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val selectedName = dialog.file?.takeIf(String::isNotBlank) ?: return
        val selectedFolder = dialog.directory?.let(Path::of) ?: folder
        val selected = selectedFolder.resolve(selectedName).let { path ->
            if (path.fileName.toString().endsWith(".$NATIVE_PROJECT_EXTENSION", ignoreCase = true)) {
                path
            } else {
                path.resolveSibling("${path.fileName}.$NATIVE_PROJECT_EXTENSION")
            }
        }
        val document = archetypeSnapshot(repository.getDocument(), selection)
        runCatching { store.save(document, selected) }
            .onSuccess {
                archetypesPanel.reload()
                status.text = "Saved ${if (selection.isEmpty()) "model" else "selection"} as archetype ${selected.toAbsolutePath().normalize()}"
            }
            .onFailure { error ->
                JOptionPane.showMessageDialog(
                    frame,
                    error.message ?: "Could not save the archetype.",
                    "Save Model as Archetype",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
    }

    private fun autosave() {
        val path = currentFile ?: return
        if (!repository.isDirty()) return
        runCatching {
            store.save(repository.getDocument(), path)
            repository.clearDirty()
            status.text = "Autosaved ${path.fileName}"
        }.onFailure {
            status.text = "Autosave failed: ${it.message}"
        }
    }

    /**
     * Embedded compiler diagnostics are intentionally delayed until the source has been
     * idle.  The actual compiler run is isolated from Swing and stale results
     * are ignored when a newer edit has since been made.
     */
    private fun scheduleEmbeddedValidation(nodeId: NodeId, languageId: String) {
        val normalizedLanguageId = languageId.trim().lowercase()
        if (embeddedSourceValidators.none { normalizedLanguageId in it.languageIds }) return
        nativeValidationGeneration++
        pendingValidationNodeId = nodeId
        pendingValidationLanguageId = normalizedLanguageId
        nativeValidationTimer.restart()
    }

    private fun clearNativeDiagnostics(nodeId: NodeId? = null) {
        if (nodeId == null) {
            var changed = false
            repository.getDocument().nodes.values.forEach { node ->
                if (node.diagnostics.isNotEmpty()) {
                    node.diagnostics.clear()
                    changed = true
                }
            }
            if (changed) repository.markDirty()
            nativeDiagnosticDetails = emptyList()
            editorTabs.applyNativeDiagnostics(emptyMap())
        } else {
            repository.updateNodeDiagnostics(nodeId, emptyList())
            nativeDiagnosticDetails = nativeDiagnosticDetails.filterNot { it.diagnostic.nodeId == nodeId }
            editorTabs.applyNativeDiagnostics(mapOf(nodeId to emptyList()))
        }
        nativeDiagnosticStatus.text = "Validation: idle"
        nativeDiagnosticStatus.toolTipText = "Click to inspect mapped compiler diagnostics"
        canvas.repaint()
    }

    private fun persistNativeDiagnostics(nodeId: NodeId, details: List<EmbeddedDiagnostic>) {
        val mappedDetails = details
            .map { detail ->
                if (detail.diagnostic.nodeId == null) {
                    detail.copy(diagnostic = detail.diagnostic.copy(nodeId = nodeId))
                } else {
                    detail
                }
            }
        val affectedNodeIds = mappedDetails.mapNotNull { it.diagnostic.nodeId }.toSet() + nodeId
        val diagnosticsByNode = affectedNodeIds.associateWith { affectedNodeId ->
            mappedDetails
                .filter { it.diagnostic.nodeId == affectedNodeId }
                .map(EmbeddedDiagnostic::diagnostic)
        }
        diagnosticsByNode.forEach { (affectedNodeId, diagnostics) ->
            repository.updateNodeDiagnostics(affectedNodeId, diagnostics)
        }
        nativeDiagnosticDetails = nativeDiagnosticDetails
            .filterNot { it.diagnostic.nodeId in affectedNodeIds }
            .plus(mappedDetails)
        editorTabs.applyNativeDiagnostics(
            diagnosticsByNode.entries.associate { (affectedNodeId, diagnostics) ->
                affectedNodeId as NodeId? to diagnostics
            },
        )
        canvas.repaint()
    }

    private fun showNativeDiagnosticDetails() {
        if (nativeDiagnosticDetails.isEmpty()) return
        val text = nativeDiagnosticDetails.joinToString("\n\n") { detail ->
            val diagnostic = detail.diagnostic
            val generated = listOfNotNull(detail.generatedLine, detail.generatedColumn).joinToString(":")
                .ifBlank { "unknown" }
            val source = listOfNotNull(diagnostic.line, diagnostic.column).joinToString(":")
                .ifBlank { "unknown" }
            val nodeName = diagnostic.nodeId?.let(repository.getDocument()::getElementById)?.name ?: "unmapped"
            "${diagnostic.severity}: ${diagnostic.message}\n" +
                "${detail.generatedPath}:$generated -> $nodeName ${diagnostic.textSection ?: "unknown"} $source"
        }
        val area = JTextArea(text, 12, 100).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }
        JOptionPane.showMessageDialog(frame, JScrollPane(area), "Compilation Diagnostics", JOptionPane.WARNING_MESSAGE)
    }

    private fun runEmbeddedValidation() {
        val generation = nativeValidationGeneration
        val nodeId = pendingValidationNodeId ?: return
        val languageId = pendingValidationLanguageId ?: return
        val validator = embeddedSourceValidators.firstOrNull { languageId in it.languageIds } ?: return
        val snapshot = documentSnapshot()
        val document = documentFromSnapshot(snapshot)
        val compiler = selectCompiler(document)
        if (compiler == null) {
            clearNativeDiagnostics(nodeId)
            return
        }
        nativeDiagnosticStatus.text = "${languageId.uppercase()} validation: checking..."
        nativeDiagnosticStatus.toolTipText = "Embedded compiler validation is running"
        status.text = "Validating ${languageId.uppercase()} source..."
        nativeValidationExecutor.submit {
            val validationResults = runCatching {
                val compilation = compiler.compile(
                    document,
                    CompilerOptions(
                        projectName = document.projectName(),
                        scopeNodeIds = nativeValidationScope(document, nodeId),
                        compilerPlugins = compilerPlugins,
                        includeScopeDescendants = false,
                    ),
                )
                compilation.generatedProject
                    ?.files
                    ?.filter { file ->
                        validator.sourceFileExtensions.any { extension ->
                            file.path.endsWith(".$extension", ignoreCase = true)
                        }
                    }
                    ?.flatMap(validator::validateDetailed)
                    .orEmpty()
                    .let { sourceDiagnostics ->
                        sourceDiagnostics + compilation.diagnostics.map { diagnostic ->
                            EmbeddedDiagnostic(
                                diagnostic = diagnostic,
                                generatedPath = "compiler",
                                generatedLine = null,
                                generatedColumn = null,
                            )
                        }
                    }
            }.getOrElse { error ->
                listOf(
                    EmbeddedDiagnostic(
                        diagnostic = Diagnostic(
                            severity = DiagnosticSeverity.Error,
                            message = error.message ?: "Could not generate ${languageId.uppercase()} source for embedded validation.",
                            nodeId = nodeId,
                            textSection = NodeTextSection.Declaration,
                            line = 1,
                            sourcePluginId = "embedded-validator",
                        ),
                        generatedPath = "generated source",
                        generatedLine = null,
                        generatedColumn = null,
                    ),
                )
            }
            SwingUtilities.invokeLater {
                if (generation != nativeValidationGeneration) return@invokeLater
                persistNativeDiagnostics(nodeId, validationResults)
                val diagnostics = nativeDiagnosticDetails.map(EmbeddedDiagnostic::diagnostic)
                val first = nativeDiagnosticDetails.firstOrNull()
                if (first == null) {
                    nativeDiagnosticStatus.text = "${languageId.uppercase()} validation: OK"
                    nativeDiagnosticStatus.toolTipText = "No embedded compiler diagnostics"
                } else {
                    val diagnostic = first.diagnostic
                    validationResults.firstOrNull()?.let { detail ->
                        editorTabs.revealNativeDiagnostic(detail.diagnostic.nodeId, detail.diagnostic.textSection)
                    }
                    val generatedLocation = listOfNotNull(
                        first.generatedLine,
                        first.generatedColumn,
                    ).joinToString(":")
                    val sourceLocation = listOfNotNull(diagnostic.line, diagnostic.column).joinToString(":")
                    val nodeName = diagnostic.nodeId?.let(document::getElementById)?.name ?: "unmapped"
                    val binding = if (editorTabs.hasBoundNode(diagnostic.nodeId)) {
                        "bound"
                    } else {
                        "not-bound"
                    }
                    val mapping = "${first.generatedPath}:$generatedLocation -> $nodeName ${diagnostic.textSection ?: "unknown"} $sourceLocation ($binding)"
                    nativeDiagnosticStatus.text = "${languageId.uppercase()} validation: ${diagnostics.size} issue${if (diagnostics.size == 1) "" else "s"}"
                    nativeDiagnosticStatus.toolTipText = "$mapping\n${diagnostic.message}\nClick for all diagnostics"
                }
                status.text = if (diagnostics.isEmpty()) {
                    "${languageId.uppercase()} source validated"
                } else {
                    "${languageId.uppercase()} source validation found ${diagnostics.size} issue${if (diagnostics.size == 1) "" else "s"}"
                }
            }
        }
    }

    /**
     * Native validation compiles the edited node's executable neighborhood,
     * not unrelated branches. Connected links are followed so their endpoint
     * declarations and dependency capabilities remain available to the validator.
     */
    private fun nativeValidationScope(document: ThreadworkDocument, editedNodeId: NodeId): Set<NodeId> {
        val scope = linkedSetOf<NodeId>()
        val pending = ArrayDeque<NodeId>()

        fun include(nodeId: NodeId) {
            if (nodeId !in scope) pending += nodeId
        }

        include(editedNodeId)
        while (pending.isNotEmpty()) {
            val node = document.getElementById(pending.removeFirst()) ?: continue
            if (!scope.add(node.id)) continue
            if (node.isLink) {
                node.link?.let { link ->
                    include(link.sourceNodeId)
                    include(link.targetNodeId)
                }
            } else {
                (node.incomingLinks + node.outgoingLinks).forEach(::include)
            }
        }
        return scope
    }

    private fun compileProject() {
        clearNativeDiagnostics()
        autosave()
        val document = repository.getDocument()
        val compiler = selectCompiler(document)
        if (compiler == null) {
            JOptionPane.showMessageDialog(frame, "No compiler plugin supports this project.", "Compile", JOptionPane.ERROR_MESSAGE)
            return
        }
        val scopedSelection = selection
            .filter { it in document.nodes }
            .toSet()
        val projectName = document.projectName()
        val options = CompilerOptions(
            projectName = projectName,
            scopeNodeIds = scopedSelection,
            compilerPlugins = compilerPlugins,
        )
        val result = runCatching {
            compiler.compile(
                document,
                options,
            )
        }.getOrElse { error ->
            JOptionPane.showMessageDialog(
                frame,
                error.message ?: "The compiler could not parse a project template.",
                "Compile",
                JOptionPane.ERROR_MESSAGE,
            )
            status.text = "Compilation failed with ${compiler.displayName}"
            return
        }
        val diagnostics = result.diagnostics.joinToString(separator = "\n") { "${it.severity}: ${it.message}" }
        val generatedProject = result.generatedProject
        if (!result.success || generatedProject == null || result.diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            JOptionPane.showMessageDialog(
                frame,
                diagnostics.ifBlank { "Compilation failed." },
                "Compile",
                JOptionPane.ERROR_MESSAGE,
            )
            status.text = "Compilation failed with ${compiler.displayName}"
            return
        }
        val singleFile = generatedProject.files.size == 1
        val outputName = if (scopedSelection.isEmpty()) currentProjectFileStem() ?: projectName else "generated"
        val output = if (singleFile) {
            chooseCompileOutputFile(generatedProject.files.single().path) ?: return
        } else {
            chooseCompileOutputRoot(outputName) ?: return
        }
        runCatching {
            if (singleFile) {
                output.parent?.let(Files::createDirectories)
                generatedProject.files.single().let { file ->
                    Files.writeString(output, file.content)
                    file.writeSourceMapBeside(output)
                }
            } else {
                Files.createDirectories(output)
                generatedProject.writeTo(output)
            }
        }.onSuccess {
            val scope = if (scopedSelection.isEmpty()) "project" else "${scopedSelection.size} selected entities"
            status.text = "Compiled $scope with ${compiler.displayName} to ${output.toAbsolutePath()}"
            JOptionPane.showMessageDialog(
                frame,
                if (singleFile) {
                    "Generated ${output.toAbsolutePath()}"
                } else {
                    "Generated ${generatedProject.files.size} files in ${output.toAbsolutePath()}"
                },
                "Compile",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }.onFailure {
            JOptionPane.showMessageDialog(frame, it.message ?: "Compilation failed.", "Compile", JOptionPane.ERROR_MESSAGE)
            status.text = "Compilation failed: ${it.message}"
        }
    }

    private fun generateCompilerFromDesign() {
        autosave()
        val document = repository.getDocument()
        if (!compilerCompiler.supports(document)) {
            JOptionPane.showMessageDialog(frame, "No @Compiler node found.", "Generate Compiler", JOptionPane.ERROR_MESSAGE)
            return
        }
        val output = chooseOutputDirectory() ?: return
        val result = compilerCompiler.compile(document, CompilerOptions(projectName = document.projectName()))
        val diagnostics = result.diagnostics.joinToString(separator = "\n") { "${it.severity}: ${it.message}" }
        val generatedProject = result.generatedProject
        if (!result.success || generatedProject == null || result.diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            JOptionPane.showMessageDialog(
                frame,
                diagnostics.ifBlank { "Compiler generation failed." },
                "Generate Compiler",
                JOptionPane.ERROR_MESSAGE,
            )
            status.text = "Compiler generation failed"
            return
        }
        runCatching {
            Files.createDirectories(output)
            generatedProject.writeTo(output)
        }.onSuccess {
            status.text = "Generated compiler to ${output.toAbsolutePath()}"
            JOptionPane.showMessageDialog(
                frame,
                "Generated ${generatedProject.files.size} files in ${output.toAbsolutePath()}",
                "Generate Compiler",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }.onFailure {
            JOptionPane.showMessageDialog(frame, it.message ?: "Compiler generation failed.", "Generate Compiler", JOptionPane.ERROR_MESSAGE)
            status.text = "Compiler generation failed: ${it.message}"
        }
    }

    private fun compileDocumentation() {
        autosave()
        val document = repository.getDocument()
        val scopedSelection = selection.filterTo(linkedSetOf()) { it in document.nodes }
        val suggestedName = if (scopedSelection.isEmpty()) {
            "${currentProjectFileStem() ?: document.projectName()}-documentation"
        } else {
            "documentation"
        }
        val output = chooseOutputDirectory(suggestedName) ?: return
        val result = documentationCompiler.compile(
            document,
            CompilerOptions(
                projectName = document.projectName(),
                scopeNodeIds = scopedSelection,
                includeScopeAncestors = false,
            ),
        )
        val generatedProject = result.generatedProject
        if (!result.success || generatedProject == null) {
            JOptionPane.showMessageDialog(frame, "Documentation compilation failed.", "Compile Documentation", JOptionPane.ERROR_MESSAGE)
            status.text = "Documentation compilation failed"
            return
        }
        runCatching {
            Files.createDirectories(output)
            generatedProject.writeTo(output)
        }.onSuccess {
            status.text = "Compiled documentation to ${output.toAbsolutePath()}"
            JOptionPane.showMessageDialog(
                frame,
                "Generated ${generatedProject.files.joinToString { file -> file.path }} in ${output.toAbsolutePath()}",
                "Compile Documentation",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }.onFailure {
            JOptionPane.showMessageDialog(
                frame,
                it.message ?: "Documentation compilation failed.",
                "Compile Documentation",
                JOptionPane.ERROR_MESSAGE,
            )
            status.text = "Documentation compilation failed: ${it.message}"
        }
    }

    private fun exportDocumentation(format: DocumentationExportFormat) {
        val output = chooseOutputDirectory("${repository.getDocument().projectName()}-documentation") ?: return
        runCatching {
            when (format) {
                DocumentationExportFormat.Pdf -> exportDocumentationPdf(output.toFile())
                DocumentationExportFormat.Html -> exportDocumentationHtml(output.toFile())
                DocumentationExportFormat.Markdown -> exportDocumentationMarkdown(output.toFile())
            }
        }.onSuccess {
            status.text = "Exported $format documentation to ${output.toAbsolutePath()}"
            JOptionPane.showMessageDialog(
                frame,
                "Saved $format documentation to ${output.toAbsolutePath()}",
                "Export Documentation",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }.onFailure {
            status.text = "Documentation export failed: ${it.message}"
            JOptionPane.showMessageDialog(
                frame,
                it.message ?: "Documentation export failed.",
                "Export Documentation",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun showPreprintDialog() {
        applyPdfPlanSettings()
        canvas.showSheetPreview()
        sheetButton?.isSelected = true
        PreprintDialog(
            parent = frame,
            profiles = printProfiles,
            formatChoices = canvas.sheetFormatChoices(),
            scaleChoices = canvas.sheetScaleChoices(),
            planFallback = canvas::paginationSettings,
            documentationFallback = ::defaultDocumentationPaginationSettings,
            applyPlanSettings = { _, settings ->
                canvas.applyPaginationSettings(settings)
            },
            planPreview = { canvas.renderPlanPreview() },
            documentationAssets = ::preprintDocumentationAssets,
            exportPlan = { format, settings ->
                canvas.applyPaginationSettings(settings)
                canvas.exportPlan(frame, format)
            },
            exportDocumentation = ::exportDocumentation,
            printPlan = ::printPlan,
            printDocumentation = ::printDocumentation,
            savePdf = ::savePreprintPdf,
            print = ::printPreprint,
            reportStatus = { message -> status.text = message },
        ).isVisible = true
    }

    private fun defaultDocumentationPaginationSettings(): DocumentationPrintSettings =
        ThreadworkPrintProfileStore.DEFAULT_DOCUMENTATION_SETTINGS

    private fun applyPdfPlanSettings() {
        val profile = printProfiles.profileFor(
            PrinterTarget.PDF_PRINTER_KEY,
            canvas.paginationSettings(),
            defaultDocumentationPaginationSettings(),
        )
        canvas.applyPaginationSettings(profile.plan)
    }

    private fun preprintDocumentationAssets(settings: DocumentationPrintSettings): List<PrintDocumentationAsset> =
        compileDocumentationFiles().map { markdownFile ->
            PrintDocumentationAsset(
                title = markdownFile.path,
                markdown = markdownFile.content,
                pages = renderDocumentationPreviewPages(markdownFile.content, settings, documentationPageMetadata()),
            )
        }

    private fun preprintPdfPages(
        documents: List<PrintDocumentationAsset>,
        documentationSettings: DocumentationPrintSettings,
    ): List<PdfRasterPage> {
        val paperSize = canvas.paperSizeMm(documentationSettings.formatChoice)
        return canvas.renderPdfPages() + documents.flatMap { asset ->
            asset.pages.map { image ->
                PdfRasterPage(
                    image = image,
                    widthPoints = paperSize.first * 72.0 / 25.4,
                    heightPoints = paperSize.second * 72.0 / 25.4,
                )
            }
        }
    }

    private fun savePreprintPdf(
        documents: List<PrintDocumentationAsset>,
        planSettings: SheetPaginationSettings,
        documentationSettings: DocumentationPrintSettings,
    ) {
        val dialog = FileDialog(frame, "Save Print PDF", FileDialog.SAVE).apply {
            directory = currentFile?.parent?.toString() ?: Path.of(".").toAbsolutePath().toString()
            file = "${repository.getDocument().projectName()}-print.pdf"
        }
        dialog.isVisible = true
        val directory = dialog.directory?.takeIf { it.isNotBlank() } ?: return
        val selected = dialog.file?.takeIf { it.isNotBlank() } ?: return
        val output = Path.of(directory).resolve(selected).let { path ->
            if (path.fileName.toString().endsWith(".pdf", ignoreCase = true)) path else Path.of("${path}.pdf")
        }
        runCatching {
            writePreprintPdf(output, documents, planSettings, documentationSettings)
        }.onSuccess {
            status.text = "Saved print PDF to ${output.toAbsolutePath()}"
        }.onFailure { error ->
            status.text = "Print PDF export failed: ${error.message}"
            JOptionPane.showMessageDialog(frame, error.message ?: "Could not save the PDF.", "Export", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun writePreprintPdf(
        output: Path,
        documents: List<PrintDocumentationAsset>,
        planSettings: SheetPaginationSettings,
        documentationSettings: DocumentationPrintSettings,
    ) {
        val temporaryFiles = mutableListOf<Path>()
        try {
            val planPdf = Files.createTempFile("threadwork-plan-", ".pdf")
            temporaryFiles.add(planPdf)
            canvas.writePdf(planPdf, planSettings.pdfRenderMode)
            val documentationPdfs = documents.map { asset ->
                Files.createTempFile("threadwork-documentation-", ".pdf").also { file ->
                    temporaryFiles.add(file)
                    Files.write(file, renderDocumentationPdf(asset.markdown, documentationSettings, documentationPageMetadata()))
                }
            }
            PDFMergerUtility().apply {
                destinationFileName = output.toAbsolutePath().toString()
                addSource(planPdf.toFile())
                documentationPdfs.forEach { addSource(it.toFile()) }
                mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            }
        } finally {
            temporaryFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    private fun printPreprint(
        service: javax.print.PrintService,
        documents: List<PrintDocumentationAsset>,
        planSettings: SheetPaginationSettings,
        documentationSettings: DocumentationPrintSettings,
    ) {
        runCatching {
            val spoolFile = Files.createTempFile("threadwork-print-", ".pdf")
            writePreprintPdf(spoolFile, documents, planSettings, documentationSettings)
            spoolFile.toFile().deleteOnExit()
            val pages = preprintPdfPages(documents, documentationSettings)

            val printerJob = PrinterJob.getPrinterJob().apply {
                printService = service
                jobName = "Threadwork ${repository.getDocument().projectName()}"
                setPrintable(RasterPagesPrintable(pages))
            }
            if (printerJob.printDialog()) {
                printerJob.print()
                status.text = "Submitted ${pages.size} print page(s) to ${service.name}"
            }
        }.onFailure { error ->
            status.text = "Printing failed: ${error.message}"
            JOptionPane.showMessageDialog(frame, error.message ?: "Could not submit the print job.", "Export", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun printPlan(service: javax.print.PrintService, settings: SheetPaginationSettings) {
        runCatching {
            canvas.applyPaginationSettings(settings)
            submitPrintPages(service, canvas.renderPdfPages())
        }.onFailure { error ->
            status.text = "Plan printing failed: ${error.message}"
            JOptionPane.showMessageDialog(frame, error.message ?: "Could not print the plan.", "Export", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun printDocumentation(
        service: javax.print.PrintService,
        documents: List<PrintDocumentationAsset>,
        settings: DocumentationPrintSettings,
    ) {
        runCatching {
            val paperSize = canvas.paperSizeMm(settings.formatChoice)
            val pages = documents.flatMap { asset ->
                asset.pages.map { image ->
                    PdfRasterPage(
                        image = image,
                        widthPoints = paperSize.first * 72.0 / 25.4,
                        heightPoints = paperSize.second * 72.0 / 25.4,
                    )
                }
            }
            submitPrintPages(service, pages)
        }.onFailure { error ->
            status.text = "Documentation printing failed: ${error.message}"
            JOptionPane.showMessageDialog(frame, error.message ?: "Could not print the documentation.", "Export", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun submitPrintPages(service: javax.print.PrintService, pages: List<PdfRasterPage>) {
        require(pages.isNotEmpty()) { "Nothing to print." }
        val printerJob = PrinterJob.getPrinterJob().apply {
            printService = service
            jobName = "Threadwork ${repository.getDocument().projectName()}"
            setPrintable(RasterPagesPrintable(pages))
        }
        if (printerJob.printDialog()) {
            printerJob.print()
            status.text = "Submitted ${pages.size} print page(s) to ${service.name}"
        }
    }

    private fun exportDocumentationPdf(directory: File) {
        val documentation = compileDocumentationFiles()
        val settings = defaultDocumentationPaginationSettings()
        Files.createDirectories(directory.toPath())
        documentation.forEach { markdownFile ->
            val pdfPath = directory.toPath().resolve(markdownFile.path.removeSuffix(".md") + ".pdf")
            Files.createDirectories(requireNotNull(pdfPath.parent))
            Files.write(pdfPath, renderDocumentationPdf(markdownFile.content, settings, documentationPageMetadata()))
        }
    }

    private fun renderDocumentationPdf(
        markdown: String,
        settings: DocumentationPrintSettings,
        metadata: DocumentationPageMetadata,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        PdfRendererBuilder()
            .useFastMode()
            .withHtmlContent(markdownToHtml(markdown, settings, metadata), null)
            .toStream(output)
            .run()
        return output.toByteArray()
    }

    private fun exportDocumentationHtml(directory: File) {
        val documentation = compileDocumentationFiles()
        val settings = defaultDocumentationPaginationSettings()
        Files.createDirectories(directory.toPath())
        documentation.forEach { markdownFile ->
            val htmlPath = directory.toPath().resolve(markdownFile.path.removeSuffix(".md") + ".html")
            Files.createDirectories(requireNotNull(htmlPath.parent))
            Files.writeString(htmlPath, markdownToHtml(markdownFile.content, settings, documentationPageMetadata()))
        }
    }

    private fun exportDocumentationMarkdown(directory: File) {
        writeDocumentationMarkdown(directory, compileDocumentationFiles())
    }

    private fun compileDocumentationFiles(): List<GeneratedFile> {
        val document = repository.getDocument()
        val result = documentationCompiler.compile(
            document,
            CompilerOptions(projectName = document.projectName()),
        )
        check(result.success) { "Documentation compilation failed." }
        return result.generatedProject?.files.orEmpty()
    }

    private fun writeDocumentationMarkdown(directory: File, files: List<GeneratedFile>) {
        Files.createDirectories(directory.toPath())
        files.forEach { markdownFile ->
            val markdownPath = directory.toPath().resolve(markdownFile.path)
            Files.createDirectories(requireNotNull(markdownPath.parent))
            Files.writeString(markdownPath, markdownFile.content)
        }
    }

    /**
     * The preview is rendered from the final PDF rather than a separate Swing HTML layout.
     * This keeps page breaks, page counters, margins, and running header/footer boxes identical
     * between preview and exported documentation.
     */
    private fun renderDocumentationPreviewPages(
        markdown: String,
        settings: DocumentationPrintSettings,
        metadata: DocumentationPageMetadata,
    ): List<BufferedImage> {
        val pdf = renderDocumentationPdf(markdown, settings, metadata)
        return PDDocument.load(pdf).use { document ->
            val renderer = PDFRenderer(document)
            (0 until document.numberOfPages).map { pageIndex ->
                renderer.renderImageWithDPI(pageIndex, 120f, ImageType.RGB)
            }
        }
    }

    private fun markdownToHtml(
        markdown: String,
        settings: DocumentationPrintSettings = defaultDocumentationPaginationSettings(),
        metadata: DocumentationPageMetadata = documentationPageMetadata(),
    ): String {
        val body = markdownHtmlRenderer.render(
            markdownParser.parse(markdown.replace(DOCUMENTATION_PAGE_BREAK, "\n<div class=\"threadwork-page-break\"></div>\n")),
        )
        val pageSize = canvas.paperSizeMm(settings.formatChoice)
        return """
            <html>
            <head>
              <style>
                @page {
                  size: ${pageSize.first}mm ${pageSize.second}mm;
                  margin: ${settings.marginTopMm}mm ${settings.marginRightMm}mm ${settings.marginBottomMm}mm ${settings.marginLeftMm}mm;
                  ${documentationMarginBoxes(settings, metadata)}
                }
                html, body { background: #fff; }
                body { font-family: sans-serif; margin: 0; color: #222; }
                h1 { font-size: 28px; border-bottom:2px solid #000; page-break-before: always; }
                h1:first-child { page-break-before: auto; }
                h2 { margin:14px 0 6px;font-size:22px;border-bottom:1px solid #000; }
                h3 { margin:12px 0 5px;font-size:17px;border-bottom:1px dotted #444; }
                h4 { margin:10px 0 4px;font-size:14px;border-bottom:1px dashed #444; }
                p, li { font-size: 11px; line-height: 1.35; }
                strong, b { font-weight: bold; } em, i { font-style: italic; }
                code, pre { font-family: monospace; font-size: 9px; }
                code { background: #f3f3f3; } pre { white-space: pre-wrap; page-break-inside: avoid; }
                table { border-collapse: collapse; font-size: 9px; page-break-inside: avoid; }
                th, td { border: 1px solid #999; padding: 3px; vertical-align: top; }
                .threadwork-page-break { page-break-after: always; height: 0; }
              </style>
            </head>
            <body>$body</body>
            </html>
        """.trimIndent()
    }

    private fun documentationPageMetadata(): DocumentationPageMetadata {
        val document = repository.getDocument()
        val revision = document.masterRevision
        return DocumentationPageMetadata(
            projectDate = document.rootNode().modified.date.trim().ifBlank { revision.date.trim() },
            revisionName = revision.name.trim(),
            revisionDate = revision.date.trim(),
            printDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        )
    }

    private fun documentationMarginBoxes(
        settings: DocumentationPrintSettings,
        metadata: DocumentationPageMetadata,
    ): String = buildString {
        if (settings.includeHeader) {
            append("@top-left { content: \"Project date: ${cssContent(metadata.projectDate)}\"; font-size: 8pt; color: #555; }\n")
            append("@top-right { content: \"Revision: ${cssContent(metadata.revisionName)} ${cssContent(metadata.revisionDate)}\"; font-size: 8pt; color: #555; }\n")
        }
        if (settings.includeFooter) {
            append("@bottom-left { content: \"Printed: ${cssContent(metadata.printDate)}\"; font-size: 8pt; color: #555; }\n")
            append("@bottom-right { content: \"Page \" counter(page) \" / \" counter(pages); font-size: 8pt; color: #555; }\n")
        }
    }

    private fun cssContent(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace('\n', ' ')

    private fun selectCompiler(document: ThreadworkDocument): CompilerPlugin? {
        val root = document.rootNode()
        val requestedCompilerId = root.technology.compilerId.trim()
        val requestedTechnologyId = document.effectiveTechnologyId(root.id)
        val supporting = compilerPlugins.filter { compiler -> runCatching { compiler.supports(document) }.getOrDefault(false) }
        return supporting.filterIsInstance<FilesystemCompiler>().firstOrNull { FilesystemCompiler.shouldAggregate(document) } ?:
            supporting.firstOrNull { it.id == requestedCompilerId } ?:
            supporting.firstOrNull { requestedTechnologyId.isNotBlank() && requestedTechnologyId in it.supportedTechnologyIds } ?:
            supporting.firstOrNull { requestedTechnologyId.isNotBlank() && it.providedTechnologies.any { tech -> tech.technologyId == requestedTechnologyId } } ?:
            supporting.firstOrNull()
    }

    private fun chooseOutputDirectory(suggestedName: String = "generated"): Path? {
        val dialog = FileDialog(frame, "Choose compile output directory", FileDialog.SAVE).apply {
            directory = currentFile?.parent?.toString() ?: Path.of(".").toAbsolutePath().toString()
            file = suggestedName
        }
        dialog.isVisible = true
        val directory = dialog.directory?.takeIf { it.isNotBlank() } ?: return null
        val file = dialog.file?.takeIf { it.isNotBlank() }
        return if (file == null) Path.of(directory) else Path.of(directory).resolve(file)
    }

    /**
     * Multi-file project compilation uses the directory selected by the user as
     * its layout root. The suggested name remains visible in the native dialog
     * without becoming an extra generated directory.
     */
    private fun chooseCompileOutputRoot(suggestedName: String): Path? {
        val dialog = FileDialog(frame, "Choose project output folder", FileDialog.SAVE).apply {
            directory = currentFile?.parent?.toString() ?: Path.of(".").toAbsolutePath().toString()
            file = suggestedName
        }
        dialog.isVisible = true
        val directory = dialog.directory?.takeIf { it.isNotBlank() } ?: return null
        return Path.of(directory)
    }

    private fun chooseCompileOutputFile(suggestedName: String): Path? {
        val dialog = FileDialog(frame, "Save compiled source", FileDialog.SAVE).apply {
            directory = currentFile?.parent?.toString() ?: Path.of(".").toAbsolutePath().toString()
            file = Path.of(suggestedName).fileName.toString()
        }
        dialog.isVisible = true
        val directory = dialog.directory?.takeIf { it.isNotBlank() } ?: return null
        val file = dialog.file?.takeIf { it.isNotBlank() } ?: return null
        return Path.of(directory).resolve(file)
    }

    private fun currentProjectFileStem(): String? =
        currentFile?.fileName?.toString()?.substringBeforeLast('.')?.takeIf(String::isNotBlank)

    private fun documentSnapshot(): String =
        historyJson.encodeToString(ThreadworkDocument.serializer(), repository.getDocument())

    private fun documentFromSnapshot(snapshot: String): ThreadworkDocument =
        historyJson.decodeFromString(ThreadworkDocument.serializer(), snapshot)

    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        currentSnapshot = documentSnapshot()
    }

    private fun checkpointHistory() {
        if (applyingHistory) return
        val snapshot = documentSnapshot()
        if (snapshot == currentSnapshot) return
        undoStack.addLast(currentSnapshot)
        while (undoStack.size > MAX_HISTORY_SNAPSHOTS) undoStack.removeFirst()
        redoStack.clear()
        currentSnapshot = snapshot
    }

    private fun undoDocument() {
        val previous = undoStack.removeLastOrNull() ?: return
        val current = documentSnapshot()
        redoStack.addLast(current)
        applyingHistory = true
        try {
            repository.replaceDocument(documentFromSnapshot(previous))
            repository.markDirty()
            currentSnapshot = previous
            selection.retainAll(repository.getDocument().nodes.keys)
            refreshAll()
        } finally {
            applyingHistory = false
        }
        status.text = "Undo"
    }

    private fun redoDocument() {
        val next = redoStack.removeLastOrNull() ?: return
        val current = documentSnapshot()
        undoStack.addLast(current)
        applyingHistory = true
        try {
            repository.replaceDocument(documentFromSnapshot(next))
            repository.markDirty()
            currentSnapshot = next
            selection.retainAll(repository.getDocument().nodes.keys)
            refreshAll()
        } finally {
            applyingHistory = false
        }
        status.text = "Redo"
    }

    private fun chooseDocumentPath(title: String, mode: Int): Path? {
        val dialog = FileDialog(frame, title, mode).apply {
            currentFile?.parent?.let { directory = it.toString() }
            file = currentFile?.fileName?.toString() ?: if (mode == FileDialog.SAVE) DEFAULT_PROJECT_NAME else "*.$NATIVE_PROJECT_EXTENSION"
            filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".$NATIVE_PROJECT_EXTENSION", ignoreCase = true) ||
                    name.endsWith(".$LEGACY_PROJECT_EXTENSION", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val fileName = dialog.file ?: return null
        val directory = dialog.directory?.let(Path::of) ?: Path.of(".")
        val chosen = directory.resolve(fileName)
        if (mode != FileDialog.SAVE) return chosen
        if (fileName.endsWith(".$NATIVE_PROJECT_EXTENSION", ignoreCase = true) ||
            fileName.endsWith(".$LEGACY_PROJECT_EXTENSION", ignoreCase = true)
        ) return chosen
        return chosen.resolveSibling("${chosen.fileName}.$NATIVE_PROJECT_EXTENSION")
    }

    private fun deleteSelection() {
        selection.toList().filter { it != repository.getDocument().rootNodeId }.forEach(repository::deleteNode)
        selection.clear()
        refreshAll()
    }

    private fun alignAndDistribute(operation: AlignmentOperation) {
        val selectedBoxes = selection.count { id -> repository.getNode(id)?.isLink == false }
        val requiredSelectionSize = if (operation.isDistribution) 3 else 2
        if (selectedBoxes < requiredSelectionSize) {
            status.text = if (operation.isDistribution) {
                "Select at least three boxes to distribute."
            } else {
                "Select at least two boxes to align."
            }
            return
        }
        canvas.alignAndDistribute(operation)
        status.text = operation.statusText
    }

    private fun onSelectionChanged(activeSection: NodeTextSection? = null) {
        inspector.bind(selection.firstOrNull())
        editorTabs.bind(selection.toList(), activeSection)
        refreshSelectedEntitiesTree()
        canvas.invalidateRenderCache()
        canvas.repaint()
        selection.singleOrNull()?.let { nodeId ->
            val languageId = repository.getDocument().effectiveTextLanguageId(nodeId, NodeTextSection.Declaration)
            scheduleEmbeddedValidation(nodeId, languageId)
        }
    }

    private fun openNodeInEntityEditor(id: NodeId) {
        selection.clear()
        selection += id
        onSelectionChanged()
        if (::projectPanels.isInitialized) {
            projectPanels.selectedIndex = 1
        }
    }

    private fun refreshAll() {
        refreshTree()
        canvas.refreshBoundsFromChildren()
        canvas.repaint()
        onSelectionChanged()
        checkpointHistory()
    }

    private fun refreshTree() {
        val document = repository.getDocument()
        setHierarchyModel(hierarchyTree, DefaultTreeModel(treeNode(document, document.rootNodeId)))
        setHierarchyModel(detailsHierarchyTree, DefaultTreeModel(treeNode(document, document.rootNodeId)))
        refreshSelectedEntitiesTree()
    }

    private fun onCanvasModeChanged(mode: CanvasMode) {
        modeButtons[mode]?.isSelected = true
    }

    private fun treeNode(document: ThreadworkDocument, id: NodeId): TreeNodeRef {
        val node = repository.requireNode(id)
        return TreeNodeRef(id, node.name, treeCategory(node)).apply {
            node.children.mapNotNull(document.nodes::get)
                .sortedWith(compareBy<Node> { treeCategory(it).sortOrder }.thenBy { it.name.lowercase() })
                .forEach { add(treeNode(document, it.id)) }
        }
    }

    private fun labeledPanel(label: String, component: JComponent): JComponent =
        JPanel(BorderLayout()).apply {
            add(JLabel(label), BorderLayout.NORTH)
            add(component, BorderLayout.CENTER)
        }

    private fun configureHierarchyTree(
        tree: JTree,
        editable: Boolean,
        dragAndDrop: Boolean,
        updatesSelection: Boolean,
    ) {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.isEditable = editable
        tree.invokesStopCellEditing = true
        tree.font = tree.font.deriveFont(16.5f)
        tree.cellRenderer = HierarchyTreeCellRenderer()
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        treeExpandedIds.getOrPut(tree) { mutableSetOf(repository.getDocument().rootNodeId.value) }
        tree.addTreeExpansionListener(object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent) {
                if (restoringTreeExpansion) return
                val ref = event.path.lastPathComponent as? TreeNodeRef ?: return
                treeExpandedIds.getOrPut(tree) { mutableSetOf() } += ref.id.value
            }

            override fun treeCollapsed(event: TreeExpansionEvent) {
                if (restoringTreeExpansion) return
                val ref = event.path.lastPathComponent as? TreeNodeRef ?: return
                if (ref.id != repository.getDocument().rootNodeId) {
                    treeExpandedIds.getOrPut(tree) { mutableSetOf() } -= ref.id.value
                }
            }
        })
        if (dragAndDrop) {
            tree.dragEnabled = true
            tree.dropMode = DropMode.ON
            tree.transferHandler = HierarchyTransferHandler()
        }
        tree.addTreeSelectionListener {
            val ref = tree.lastSelectedPathComponent as? TreeNodeRef ?: return@addTreeSelectionListener
            if (updatesSelection) {
                val selectedIds = tree.selectionPaths
                    ?.mapNotNull { it.lastPathComponent as? TreeNodeRef }
                    ?.map { it.id }
                    ?.distinct()
                    .orEmpty()
                selection.clear()
                selection += selectedIds.ifEmpty { listOf(ref.id) }
                onSelectionChanged()
            }
        }
    }

    private fun setHierarchyModel(tree: JTree, model: DefaultTreeModel) {
        if (tree.isEditable) {
            model.addTreeModelListener(object : TreeModelListener {
                override fun treeNodesChanged(e: TreeModelEvent) {
                    val changed = if (e.children?.isNotEmpty() == true) e.children.firstOrNull() else e.treePath.lastPathComponent
                    val ref = changed as? TreeNodeRef ?: return
                    val name = ref.toString().trim()
                    if (name.isBlank()) return
                    val node = repository.getNode(ref.id) ?: return
                    if (node.name != name) {
                        repository.renameNode(ref.id, name)
                        status.text = "Renamed ${node.id.value} to $name"
                        SwingUtilities.invokeLater { refreshAll() }
                    }
                }

                override fun treeNodesInserted(e: TreeModelEvent) = Unit
                override fun treeNodesRemoved(e: TreeModelEvent) = Unit
                override fun treeStructureChanged(e: TreeModelEvent) = Unit
            })
        }
        tree.model = model
        restoreTreeExpansion(tree)
    }

    private fun restoreTreeExpansion(tree: JTree) {
        val expanded = treeExpandedIds.getOrPut(tree) { mutableSetOf(repository.getDocument().rootNodeId.value) }
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        restoringTreeExpansion = true
        try {
            restoreTreeExpansion(tree, TreePath(root.path), expanded)
        } finally {
            restoringTreeExpansion = false
        }
    }

    private fun restoreTreeExpansion(tree: JTree, path: TreePath, expanded: Set<String>) {
        val ref = path.lastPathComponent as? TreeNodeRef
        if (ref == null || ref.id.value in expanded) {
            tree.expandPath(path)
        }
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        repeat(node.childCount) { index ->
            val child = node.getChildAt(index) as? DefaultMutableTreeNode ?: return@repeat
            val childPath = path.pathByAddingChild(child)
            val childRef = child as? TreeNodeRef
            if (childRef?.id?.value in expanded) {
                restoreTreeExpansion(tree, childPath, expanded)
            }
        }
    }

    private fun refreshSelectedEntitiesTree() {
        val root = DefaultMutableTreeNode("selected entities list")
        val document = repository.getDocument()
        selection.mapNotNull(document.nodes::get)
            .sortedWith(compareBy<Node> { treeCategory(it).sortOrder }.thenBy { it.name.lowercase() })
            .forEach { node ->
                root.add(TreeNodeRef(node.id, node.name, treeCategory(node)))
            }
        selectedEntitiesTree.model = DefaultTreeModel(root)
        selectedEntitiesTree.expandRow(0)
    }

    private fun treeCategory(node: Node): TreeItemCategory = when {
        node.isLink -> TreeItemCategory.Link
        node.isComposite || node.id == repository.getDocument().rootNodeId -> TreeItemCategory.Composite
        node.isType -> TreeItemCategory.Type
        else -> TreeItemCategory.Processing
    }

    private inner class HierarchyTransferHandler : TransferHandler() {
        override fun getSourceActions(c: JComponent): Int = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val tree = c as? JTree ?: return null
            val ref = tree.lastSelectedPathComponent as? TreeNodeRef ?: return null
            if (ref.id == repository.getDocument().rootNodeId) return null
            val treeSelectedIds = tree.selectionPaths
                ?.mapNotNull { it.lastPathComponent as? TreeNodeRef }
                ?.map { it.id }
                ?.distinct()
                .orEmpty()
            val candidateIds = when {
                ref.id in selection -> selection.toList()
                treeSelectedIds.isNotEmpty() -> treeSelectedIds
                else -> listOf(ref.id)
            }
            val moveIds = treeMoveRoots(candidateIds)
            if (moveIds.isEmpty()) return null
            return StringSelection(moveIds.joinToString("\n") { it.value })
        }

        override fun canImport(support: TransferSupport): Boolean =
            support.isDrop && support.isDataFlavorSupported(DataFlavor.stringFlavor)

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            val tree = support.component as? JTree ?: return false
            val drop = support.dropLocation as? JTree.DropLocation ?: return false
            val targetRef = drop.path?.lastPathComponent as? TreeNodeRef ?: return false
            val targetNode = repository.getNode(targetRef.id) ?: return false
            if (targetNode.isLink) return false
            val sourceValue = support.transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
            val sourceIds = sourceValue
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .map(::NodeId)
                .toList()
            val moveIds = treeMoveRoots(sourceIds)
                .filter { it != targetRef.id && !isTreeAncestor(it, targetRef.id) }
                .filter { repository.getNode(it)?.parentId != targetRef.id }
            if (moveIds.isEmpty()) return false
            return runCatching {
                moveIds.forEach { repository.moveNode(it, targetRef.id) }
                selection.clear()
                selection += moveIds
                status.text = "Moved ${moveIds.size} ${if (moveIds.size == 1) "entity" else "entities"} under ${targetNode.name}"
                refreshAll()
                true
            }.getOrDefault(false)
        }
    }

    private fun treeMoveRoots(ids: Collection<NodeId>): List<NodeId> {
        val document = repository.getDocument()
        val root = document.rootNodeId
        val selected = ids
            .distinct()
            .filter { it != root && it in document.nodes }
            .toSet()
        return selected.filter { id ->
            var current = document.nodes[id]?.parentId
            while (current != null) {
                if (current in selected) return@filter false
                current = document.nodes[current]?.parentId
            }
            true
        }
    }

    private fun isTreeAncestor(candidateAncestor: NodeId, nodeId: NodeId): Boolean {
        val document = repository.getDocument()
        var current = document.nodes[nodeId]?.parentId
        while (current != null) {
            if (current == candidateAncestor) return true
            current = document.nodes[current]?.parentId
        }
        return false
    }

    private fun modeButton(
        label: String,
        mode: CanvasMode,
        iconId: String,
        description: String,
    ) = toolbarToggleButton(label, iconId, description) {
        canvas.setMode(mode)
    }.also { modeButtons[mode] = it }

    private fun toolbarToggleButton(
        label: String,
        iconId: String,
        description: String,
        action: JToggleButton.() -> Unit,
    ) = JToggleButton().apply {
        configureIconOnlyToolbarButton(label, iconId, description)
        addActionListener { action() }
    }

    private fun toolbarIconButton(
        label: String,
        iconId: String,
        description: String,
        action: () -> Unit,
    ) = JButton().apply {
        configureIconOnlyToolbarButton(label, iconId, description)
        addActionListener { action() }
    }

    private fun javax.swing.AbstractButton.configureIconOnlyToolbarButton(
        label: String,
        iconId: String,
        description: String,
    ) {
        text = label
        icon = ThreadworkIcons.buttonIcon(iconId)
        horizontalTextPosition = SwingConstants.RIGHT
        iconTextGap = 5
        toolTipText = "<html><b>$label</b><br>$description</html>"
        accessibleContext.accessibleName = label
        accessibleContext.accessibleDescription = description
    }

    private fun button(label: String, iconId: String? = null, action: () -> Unit) = JButton(label).apply {
        iconId?.let(ThreadworkIcons::buttonIcon)?.let {
            icon = it
            horizontalTextPosition = SwingConstants.RIGHT
            iconTextGap = 6
        }
        addActionListener { action() }
    }
    private fun item(label: String, action: () -> Unit) = JMenuItem(label).apply { addActionListener { action() } }
}

private enum class TreeItemCategory(val sortOrder: Int, val marker: Color) {
    Composite(0, Color(0xffcc33)),
    Type(1, Color(0x00897b)),
    Processing(2, Color(0x2f6bdc)),
    Link(3, Color(0xf39c12)),
}

private class TreeNodeRef(
    val id: NodeId,
    label: String,
    val category: TreeItemCategory,
) : DefaultMutableTreeNode(label) {
    override fun toString(): String = userObject?.toString().orEmpty()
}

private class HierarchyTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        component.font = component.font.deriveFont(16.5f)
        icon = MarkerIcon((value as? TreeNodeRef)?.category?.marker ?: Color.WHITE)
        leafIcon = icon
        openIcon = icon
        closedIcon = icon
        return component
    }
}

private class MarkerIcon(private val color: Color) : Icon {
    override fun getIconWidth(): Int = 12
    override fun getIconHeight(): Int = 12

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        g.color = Color(0x666666)
        g.drawOval(x + 2, y + 2, 8, 8)
        g.color = color
        g.fillOval(x + 3, y + 3, 7, 7)
    }
}

enum class CanvasMode {
    Select,
    CreateNode,
    CreateType,
    CreateLink,
}

enum class SheetExportFormat(
    val extension: String,
    private val label: String,
) {
    Pdf("pdf", "Plan PDF"),
    Svg("svg", "Plan SVG"),
    Png("png", "Plan PNG"),
    Mermaid("mmd", "Mermaid"),
    ;

    override fun toString(): String = label
}

internal enum class DocumentationExportFormat(private val label: String) {
    Pdf("PDF"),
    Html("HTML"),
    Markdown("Markdown"),
    ;

    override fun toString(): String = label
}

enum class AlignmentOperation(
    val statusText: String,
    val isDistribution: Boolean = false,
) {
    AlignTop("Aligned selected boxes to top"),
    AlignBottom("Aligned selected boxes to bottom"),
    AlignLeft("Aligned selected boxes to left"),
    AlignRight("Aligned selected boxes to right"),
    DistributeVertical("Distributed selected boxes vertically", isDistribution = true),
    DistributeHorizontal("Distributed selected boxes horizontally", isDistribution = true),
}

class GraphCanvas(
    private val repository: DocumentRepository,
    private val selection: LinkedHashSet<NodeId>,
    private val onSelectionChanged: () -> Unit,
    private val refreshAll: () -> Unit,
    private val onModeChanged: (CanvasMode) -> Unit,
    private val onTileProgress: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    private val onNodeDoubleClicked: (NodeId) -> Unit = {},
    private val onContextMenu: (Component, Int, Int) -> Unit = { _, _, _ -> },
    private val onFilesDropped: (List<Path>, Point) -> Unit = { _, _ -> },
) : JPanel() {
    var mode: CanvasMode = CanvasMode.Select
        private set
    private var dragStart: Point? = null
    private var selectionDragLeftToRight = true
    private var contextMenuPending = false
    private var contextMenuShown = false
    private var dragAllowsReparent = false
    private var moveDragReference: Point? = null
    private var panDragStart: Point? = null
    private var selectionRect: Rectangle? = null
    private var linkSource: NodeId? = null
    private var clipboard: List<Node> = emptyList()
    private var zoom = 1.0
    private var panX = 0.0
    private var panY = 0.0
    private var showSheet = false
    private var sheetFormatChoice = AUTO_SHEET_FORMAT
    private var sheetScale = 1
    private var multipagePdfEnabled = false
    private var sheetOverlapMm = DEFAULT_SHEET_OVERLAP_MM
    private var pdfRenderMode = PdfRenderMode.Rasterized
    private var designerFont: Font = ThreadworkFonts.designerFont(13f)
    private var activePalette: DesignerPalette = ThreadworkAppearance.palette()
    private val technicalPalette: DesignerPalette = ThreadworkAppearance.defaultPalette(ApplicationTheme.Light)
    private val routeCache = mutableMapOf<NodeId, LinkRoute>()
    private val activeRouteLinks = mutableSetOf<NodeId>()
    private val rerouteTimer = Timer(180) { rebuildRouteCache() }.apply { isRepeats = false }
    private var zoomExtentsPending = false
    private var renderRevision = 0L
    // In-memory only. Do not persist project image tiles without a user-selected project-owned cache path.
    private val tileCache = object : LinkedHashMap<FlowTileKey, BufferedImage>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FlowTileKey, BufferedImage>?): Boolean =
            size > MAX_TILE_CACHE_ENTRIES
    }

    private enum class FlowTileLayer {
        Static,
    }
    private data class FlowTileKey(
        val layer: FlowTileLayer,
        val sceneRevision: Long,
        val zoomBucket: Int,
        val tileX: Int,
        val tileY: Int,
    )
    private data class PortAnchor(val point: Point, val xDirection: Int)
    private data class LinkAnchors(val source: PortAnchor, val target: PortAnchor, val sourceNodeId: NodeId, val targetNodeId: NodeId)
    private data class LinkRoute(
        val source: Point,
        val target: Point,
        val sourceDirection: Int,
        val targetDirection: Int,
        val points: List<Point>,
    )
    private data class SheetFormat(val id: String, val widthMm: Double, val heightMm: Double, val roll: Boolean = false)
    private data class BomRow(
        val index: Int,
        val name: String,
        val kind: String,
        val modifiedDate: String,
        val revision: String,
        val responsible: String,
    )
    private data class PartsListColumns(
        val labels: List<String>,
        val widths: List<Int>,
    ) {
        val totalWidth: Int get() = widths.sum()
        val offsets: List<Int> get() = widths.runningFold(0, Int::plus)
    }
    private data class SheetPlan(
        val format: SheetFormat,
        val scale: Int,
        val pages: List<SheetPage>,
        val overlap: Int,
        val drawing: Rectangle,
        val titleBlock: Rectangle,
        val partsList: Rectangle,
        val contentBounds: Rectangle,
        val scopeIds: Set<NodeId>,
        val bomRows: List<BomRow>,
        val partsColumns: PartsListColumns,
    ) {
        val sheet: Rectangle = pages.map(SheetPage::sheet).reduce(Rectangle::union)
        val isMultipage: Boolean get() = pages.size > 1
    }
    private data class SheetPage(
        val row: Int,
        val column: Int,
        val sheet: Rectangle,
    )
    private data class RouteSegment(val a: Point, val b: Point, val linkId: NodeId)
    private data class SheetCandidate(
        val format: SheetFormat,
        val width: Int,
        val height: Int,
        val coverage: Double,
    ) {
        val area: Long get() = width.toLong() * height
    }
    private data class CompositeTextMetrics(
        val titleSize: Float,
        val infoSize: Float,
        val titleBaselineOffset: Int,
        val stereotypeBaselineOffset: Int,
        val technologyBaselineOffset: Int,
        val topPadding: Int,
    )

    private companion object {
        const val AUTO_SHEET_FORMAT = "Auto"
        const val PORT_TOP_SPACING = 28
        const val PORT_SPACING = 30
        const val PORT_BOTTOM_SPACING = 20
        const val TERMINAL_NODE_BASE_HEIGHT = 70
        const val TERMINAL_TITLE_SIZE = 15f
        const val TERMINAL_INFO_SIZE = 11f
        const val TERMINAL_TITLE_BASELINE = 22
        const val TERMINAL_STEREOTYPE_BASELINE = 39
        const val TERMINAL_TECHNOLOGY_BASELINE = 54
        const val TERMINAL_TYPE_FIELD_BASELINE = 55
        const val TERMINAL_TEXT_LINE_HEIGHT = 16
        const val TERMINAL_BOTTOM_PADDING = 15
        const val DEPENDENCY_ANNOTATION_FONT_SIZE = 10f
        const val DEPENDENCY_SOURCE_GAP = 24
        const val DEPENDENCY_SOURCE_MIN_WIDTH = 72
        const val DEPENDENCY_TARGET_MIN_WIDTH = 80
        const val DEPENDENCY_LABEL_HORIZONTAL_PADDING = 16
        const val DEPENDENCY_TARGET_RULE_RATIO = 0.72
        const val PORT_STUB_LENGTH = 46
        const val PORT_OUTSIDE_OFFSET = 20
        const val ROUTING_STEP = 40
        const val ROUTING_CHAMFER = 22
        const val ROUTING_OBSTACLE_PADDING = 24
        const val ROUTING_LANE_SPAN = 7
        const val SNAP_GRID_STEP = 40
        const val SNAP_RADIUS_PX = 5.0
        const val MIN_ZOOM = 0.02
        const val MAX_ZOOM = 2.5
        const val ZOOM_STEP = 1.12
        const val ZOOM_EXTENTS_SCREEN_MARGIN = 32
        const val ZOOM_EXTENTS_MODEL_MARGIN = 40
        const val TILE_SIZE_PX = 512
        const val TILE_RENDER_PADDING = 240
        const val MAX_TILE_CACHE_ENTRIES = 384
        const val ZOOM_BUCKET_STEP = 1.25
        const val COMPOSITE_TOP_PADDING = 80
        const val COMPOSITE_HEADER_EXTRA_HEIGHT = 36
        const val COMPOSITE_HORIZONTAL_PADDING = 180
        const val COMPOSITE_BOTTOM_PADDING = 48
        const val COMPOSITE_INFO_TEXT_RATIO = 0.875
        const val COMPOSITE_TEXT_CHILD_BOOST_DIVISOR = 64.0
        const val COMPOSITE_TEXT_MIN_MODEL_SIZE = 6.0
        const val COMPOSITE_TEXT_MAX_MODEL_SIZE = 256.0
        const val SHEET_UNITS_PER_MM = 4.0
        const val SHEET_MARGIN_TOP_MM = 5.0
        const val SHEET_MARGIN_RIGHT_MM = 5.0
        const val SHEET_MARGIN_BOTTOM_MM = 5.0
        const val SHEET_MARGIN_LEFT_MM = 20.0
        const val TITLE_BLOCK_WIDTH_MM = 180.0
        const val TITLE_BLOCK_HEIGHT_MM = 36.0
        const val DEFAULT_SHEET_OVERLAP_MM = 5.0
        const val PDF_POINTS_PER_MM = 72.0 / 25.4
        val compilerDesignStereotypes = setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile)
        const val PARTS_ROW_HEIGHT = 20
        const val ROLL_MAX_LENGTH_MM = 1500.0
        val SHEET_SCALES = listOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000)

        val SHEET_FORMATS = listOf(
            SheetFormat("A4", 210.0, 297.0),
            SheetFormat("A3", 297.0, 420.0),
            SheetFormat("A2", 420.0, 594.0),
            SheetFormat("A1", 594.0, 841.0),
            SheetFormat("A0", 841.0, 1189.0),
            SheetFormat("A4-roll", 210.0, ROLL_MAX_LENGTH_MM, roll = true),
            SheetFormat("A3-roll", 297.0, ROLL_MAX_LENGTH_MM, roll = true),
            SheetFormat("A2-roll", 420.0, ROLL_MAX_LENGTH_MM, roll = true),
            SheetFormat("A1-roll", 594.0, ROLL_MAX_LENGTH_MM, roll = true),
            SheetFormat("A0-roll", 841.0, ROLL_MAX_LENGTH_MM, roll = true),
            SheetFormat("A4-landscape", 297.0, 210.0),
            SheetFormat("A3-landscape", 420.0, 297.0),
            SheetFormat("A2-landscape", 594.0, 420.0),
            SheetFormat("A1-landscape", 841.0, 594.0),
            SheetFormat("A0-landscape", 1189.0, 841.0),
        )
    }

    init {
        background = activePalette[DesignerColorKey.CanvasBackground]
        preferredSize = Dimension(900, 700)
        toolTipText = ""
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (isContextGesture(e)) {
                    contextMenuPending = !e.isPopupTrigger
                    if (e.isPopupTrigger) {
                        showContextMenu(e)
                        contextMenuShown = true
                    }
                    return
                }
                handlePressed(e)
            }
            override fun mouseDragged(e: MouseEvent) = handleDragged(e)
            override fun mouseReleased(e: MouseEvent) {
                if (contextMenuPending || contextMenuShown || e.isPopupTrigger) {
                    if (!contextMenuShown) showContextMenu(e)
                    contextMenuPending = false
                    contextMenuShown = false
                    return
                }
                handleReleased(e)
            }
            override fun mouseClicked(e: MouseEvent) = handleClicked(e)
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val before = modelPoint(e.point)
                zoom = (zoom * ZOOM_STEP.pow(-e.preciseWheelRotation)).coerceIn(MIN_ZOOM, MAX_ZOOM)
                panX = e.point.x / zoom - before.x
                panY = e.point.y / zoom - before.y
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
        transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean =
                support.isDrop && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false
                val paths = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
                        .map(File::toPath)
                }.getOrNull().orEmpty()
                if (paths.isEmpty()) return false
                val dropPoint = (support.dropLocation as? TransferHandler.DropLocation)?.dropPoint
                val point = dropPoint?.let(::modelPoint) ?: modelPoint(Point(width / 2, height / 2))
                onFilesDropped(paths, point)
                return true
            }
        }
    }

    fun dropParentAt(point: Point): NodeId? = hitNode(point)
        ?.takeIf { candidate ->
            repository.getNode(candidate)?.let { !it.isLink && !it.isType } == true
        }

    override fun getToolTipText(event: MouseEvent): String? {
        val linkId = hitLink(modelPoint(event.point)) ?: return null
        val link = repository.getNode(linkId) ?: return null
        val linkData = link.link ?: return null
        val declaredType = repository.getDocument().getElementById(linkData.typeDefinitionId)?.takeIf(Node::isType)
        val definition = declaredType?.let { type ->
            buildString {
                append(type.name)
                type.typeDefinition?.fields.orEmpty().forEach { field ->
                    append("\n  ")
                    append(field.name)
                    append(": ")
                    if (field.isReference) append("ref ")
                    append(repository.getDocument().typeDisplayName(field.typeId))
                }
            }
        } ?: linkData.payloadDefinition.trim()
        if (definition.isBlank()) return null
        return "<html><pre>${escapeHtml(definition)}</pre></html>"
    }

    fun setMode(nextMode: CanvasMode) {
        if (mode != nextMode) {
            linkSource = null
            selectionRect = null
        }
        mode = nextMode
        onModeChanged(mode)
        repaint()
    }

    fun invalidateRenderCache() {
        renderRevision++
        tileCache.clear()
        onTileProgress(0, 0, false)
    }

    fun setPalette(palette: DesignerPalette) {
        activePalette = palette
        background = palette[DesignerColorKey.CanvasBackground]
        invalidateRenderCache()
        repaint()
    }

    fun toggleSheet(): Boolean {
        showSheet = !showSheet
        invalidateRenderCache()
        repaint()
        return showSheet
    }

    fun showSheetPreview() {
        if (showSheet) return
        showSheet = true
        invalidateRenderCache()
        repaint()
    }

    fun sheetFormatChoices(): List<String> = listOf(AUTO_SHEET_FORMAT) + SHEET_FORMATS.map { it.id }

    /** Paper size used by text PDF output; Auto defaults to A4. */
    internal fun paperSizeMm(formatChoice: String): Pair<Double, Double> {
        val format = SHEET_FORMATS.firstOrNull { it.id == formatChoice }
            ?: SHEET_FORMATS.first { it.id == "A4" }
        return format.widthMm to format.heightMm
    }

    fun selectedSheetFormatChoice(): String = sheetFormatChoice

    fun setSheetFormatChoice(choice: String) {
        val next = choice.ifBlank { AUTO_SHEET_FORMAT }
        if (sheetFormatChoice == next) return
        sheetFormatChoice = next
        invalidateRenderCache()
        repaint()
    }

    fun sheetScaleChoices(): List<String> = SHEET_SCALES.map { "1:$it" }

    fun selectedSheetScaleChoice(): String = "1:$sheetScale"

    fun setSheetScaleChoice(choice: String) {
        val next = choice.substringAfter(':', choice)
            .trim()
            .toIntOrNull()
            ?.takeIf { it in SHEET_SCALES }
            ?: 1
        if (sheetScale == next) return
        sheetScale = next
        invalidateRenderCache()
        repaint()
    }

    fun isMultipagePdfEnabled(): Boolean = multipagePdfEnabled

    fun setMultipagePdfEnabled(enabled: Boolean) {
        if (multipagePdfEnabled == enabled) return
        multipagePdfEnabled = enabled
        invalidateRenderCache()
        repaint()
    }

    fun sheetOverlapMm(): Double = sheetOverlapMm

    fun setSheetOverlapMm(overlapMm: Double) {
        val next = overlapMm.coerceIn(0.0, 50.0)
        if (sheetOverlapMm == next) return
        sheetOverlapMm = next
        invalidateRenderCache()
        repaint()
    }

    internal fun paginationSettings(): SheetPaginationSettings = SheetPaginationSettings(
        formatChoice = sheetFormatChoice,
        scaleChoice = selectedSheetScaleChoice(),
        multipage = multipagePdfEnabled,
        overlapMm = sheetOverlapMm,
        pdfRenderMode = pdfRenderMode,
    )

    internal fun applyPaginationSettings(settings: SheetPaginationSettings) {
        val nextFormat = settings.formatChoice.ifBlank { AUTO_SHEET_FORMAT }
        val nextScale = settings.scaleChoice.substringAfter(':', settings.scaleChoice)
            .trim()
            .toIntOrNull()
            ?.takeIf { it in SHEET_SCALES }
            ?: 1
        val nextOverlap = settings.overlapMm.coerceIn(0.0, 50.0)
        if (
            sheetFormatChoice == nextFormat &&
            sheetScale == nextScale &&
            multipagePdfEnabled == settings.multipage &&
            sheetOverlapMm == nextOverlap &&
            pdfRenderMode == settings.pdfRenderMode
        ) {
            return
        }
        sheetFormatChoice = nextFormat
        sheetScale = nextScale
        multipagePdfEnabled = settings.multipage
        sheetOverlapMm = nextOverlap
        pdfRenderMode = settings.pdfRenderMode
        invalidateRenderCache()
        repaint()
    }

    fun setDesignerFont(font: Font) {
        designerFont = font
        this.font = font
        invalidateRenderCache()
        repaint()
    }

    fun zoomExtents() {
        zoomExtentsPending = false
        if (width <= 0 || height <= 0) return
        val bounds = contentBounds(repository.getDocument().nodes.keys) ?: return
        val framedBounds = Rectangle(bounds).apply {
            grow(ZOOM_EXTENTS_MODEL_MARGIN, ZOOM_EXTENTS_MODEL_MARGIN)
        }
        val availableWidth = (width - ZOOM_EXTENTS_SCREEN_MARGIN * 2).coerceAtLeast(1)
        val availableHeight = (height - ZOOM_EXTENTS_SCREEN_MARGIN * 2).coerceAtLeast(1)
        zoom = min(
            availableWidth.toDouble() / framedBounds.width.coerceAtLeast(1),
            availableHeight.toDouble() / framedBounds.height.coerceAtLeast(1),
        ).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = width.toDouble() / (2.0 * zoom) - framedBounds.centerX
        panY = height.toDouble() / (2.0 * zoom) - framedBounds.centerY
        repaint()
    }

    fun zoomExtentsAfterLayout() {
        zoomExtentsPending = true
        scheduleReroute()
    }

    fun exportPlan(parent: JFrame, format: SheetExportFormat) {
        val dialog = FileDialog(parent, "Save ${format}", FileDialog.SAVE).apply {
            file = "${repository.getDocument().projectName()}.${format.extension}"
        }
        dialog.isVisible = true
        val directory = dialog.directory?.takeIf { it.isNotBlank() } ?: return
        val selected = dialog.file?.takeIf { it.isNotBlank() } ?: return
        val file = withExtension(Path.of(directory).resolve(selected).toFile(), format.extension)
        runCatching {
            when (format) {
                SheetExportFormat.Svg -> writeSvgSheet(file)
                SheetExportFormat.Png -> writePngSheet(file)
                SheetExportFormat.Pdf -> writePdfSheet(file)
                SheetExportFormat.Mermaid -> writeMermaidPlan(file)
            }
        }.onSuccess {
            JOptionPane.showMessageDialog(
                parent,
                "Saved $format to ${file.absolutePath}",
                "Export Plan",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }.onFailure {
            JOptionPane.showMessageDialog(parent, it.message ?: "Export failed.", "Export Plan", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun copySelection() {
        clipboard = selection.mapNotNull(repository::getNode).map(::clipboardCopy)
    }

    fun pasteSelection() {
        pasteEntities(clipboard)
    }

    fun insertArchetype(document: ThreadworkDocument) {
        val entities = document.nodes.values
            .filter { it.id != document.rootNodeId }
            .map(::clipboardCopy)
        pasteEntities(entities)
        zoomExtentsAfterLayout()
    }

    private fun pasteEntities(entities: List<Node>) {
        val root = repository.getDocument().rootNodeId
        val pasted = mutableListOf<NodeId>()
        val snapshots = entities.associateBy(Node::id)
        val copiedIds = linkedMapOf<NodeId, NodeId>()
        val selectedNodes = entities.filterNot(Node::isLink).sortedBy { snapshot ->
            generateSequence(snapshot.parentId) { snapshots[it]?.parentId }.count()
        }
        selectedNodes.forEach { node ->
            val parentId = node.parentId?.let(copiedIds::get) ?: root
            val copy = repository.createNode(parentId, node.name, node.kind)
            copiedIds[node.id] = copy.id
            applyClipboardNode(copy, node, copiedIds)
            pasted += copy.id
        }
        entities.filter(Node::isLink).forEach { node ->
            val link = node.link ?: return@forEach
            val sourceId = copiedIds[link.sourceNodeId] ?: return@forEach
            val targetId = copiedIds[link.targetNodeId] ?: return@forEach
            val copy = repository.createLink(
                parentId = null,
                name = node.name,
                sourceNodeId = sourceId,
                sourcePortName = link.sourcePortName,
                targetNodeId = targetId,
                targetPortName = link.targetPortName,
            )
            copiedIds[node.id] = copy.id
            applyClipboardNode(copy, node, copiedIds)
            repository.updateLinkData(
                copy.id,
                link.copy(
                    sourceNodeId = sourceId,
                    targetNodeId = targetId,
                    typeDefinitionId = copiedIds[NodeId(link.typeDefinitionId)]?.value ?: link.typeDefinitionId,
                    compositeBoundaryIds = mutableListOf(),
                ),
            )
            pasted += copy.id
        }
        selection.clear()
        selection += pasted
        refreshAll()
    }

    private fun clipboardCopy(node: Node): Node = node.copy(
        children = node.children.toMutableList(),
        incomingLinks = node.incomingLinks.toMutableList(),
        outgoingLinks = node.outgoingLinks.toMutableList(),
        layout = node.layout.copy(),
        text = node.text.copy(),
        technology = node.technology.copy(),
        revision = node.revision?.copy(),
        modified = node.modified.copy(),
        ports = node.ports.map { it.copy(metadata = it.metadata.toMutableMap()) }.toMutableList(),
        link = node.link?.copy(compositeBoundaryIds = node.link!!.compositeBoundaryIds.toMutableList()),
        typeDefinition = node.typeDefinition?.copy(
            fields = node.typeDefinition!!.fields.map { it.copy() }.toMutableList(),
        ),
        metadata = node.metadata.toMutableMap(),
        pluginData = node.pluginData.toMutableMap(),
    )

    private fun applyClipboardNode(copy: Node, source: Node, copiedIds: Map<NodeId, NodeId>) {
        repository.updateNodeLayout(
            copy.id,
            source.layout.copy(x = source.layout.x + 40, y = source.layout.y + 40),
        )
        repository.updateNodeText(copy.id, source.text.copy())
        repository.updateNodeBinaryContent(copy.id, source.binaryContent?.copyOf())
        repository.updateNodeTechnology(copy.id, source.technology.copy())
        repository.updateNodeFileLayoutStrategy(copy.id, source.fileLayoutStrategyId)
        repository.updateNodeMetadata(copy.id, source.metadata)
        repository.updateNodeResponsible(copy.id, source.responsible)
        source.typeDefinition?.let { definition ->
            repository.updateNodeTypeDefinition(
                copy.id,
                definition.copy(
                    fields = definition.fields.map { field ->
                        field.copy(typeId = copiedIds[NodeId(field.typeId)]?.value ?: field.typeId)
                    }.toMutableList(),
                ),
            )
        }
        source.ports.forEach { port ->
            repository.addPort(copy.id, port.copy(metadata = port.metadata.toMutableMap()))
        }
        copy.revision = source.revision?.copy()
        copy.modified = source.modified.copy()
        copy.pluginData.clear()
        copy.pluginData.putAll(source.pluginData)
    }

    fun alignAndDistribute(operation: AlignmentOperation) {
        val nodes = selectedMoveRoots()
        val requiredSelectionSize = if (operation.isDistribution) 3 else 2
        if (nodes.size < requiredSelectionSize) {
            return
        }
        val targets = when (operation) {
            AlignmentOperation.AlignTop -> {
                val y = nodes.minOf { it.layout.y }
                nodes.associateWith { it.layout.x to y }
            }
            AlignmentOperation.AlignBottom -> {
                val bottom = nodes.maxOf { it.layout.y + it.layout.height }
                nodes.associateWith { it.layout.x to bottom - it.layout.height }
            }
            AlignmentOperation.AlignLeft -> {
                val x = nodes.minOf { it.layout.x }
                nodes.associateWith { x to it.layout.y }
            }
            AlignmentOperation.AlignRight -> {
                val right = nodes.maxOf { it.layout.x + it.layout.width }
                nodes.associateWith { right - it.layout.width to it.layout.y }
            }
            AlignmentOperation.DistributeVertical -> distributeTargets(nodes, vertical = true)
            AlignmentOperation.DistributeHorizontal -> distributeTargets(nodes, vertical = false)
        }
        targets.forEach { (node, target) ->
            val dx = target.first - node.layout.x
            val dy = target.second - node.layout.y
            moveNodeAndDescendants(node, dx, dy)
        }
        invalidateRoutesFor(nodes.map { it.id })
        repository.markDirty()
        refreshAll()
    }

    fun refreshBoundsFromChildren() {
        invalidateRenderCache()
        val document = repository.getDocument()
        document.nodes.values
            .filter { !it.isLink && it.id != document.rootNodeId }
            .forEach(::ensureLayoutCanHoldPortsAndLabels)
        document.nodes.values
            .filter { !it.isLink && it.children.isNotEmpty() && it.id != document.rootNodeId }
            .sortedByDescending { depthOf(it) }
            .forEach { parent ->
                val boxes = parent.children.mapNotNull(document.nodes::get).filter { !it.isLink }.map { it.layout.rect() }
                val terminalWidth = max(requiredNodeWidth(parent), parent.layout.closedWidth)
                val terminalHeight = requiredTerminalHeight(parent)
                parent.layout.closedWidth = max(parent.layout.closedWidth, terminalWidth)
                parent.layout.closedHeight = terminalHeight
                if (boxes.isNotEmpty()) {
                    val childLeft = boxes.minOf { it.x }
                    val childTop = boxes.minOf { it.y }
                    val childRight = boxes.maxOf { it.x + it.width }
                    val childBottom = boxes.maxOf { it.y + it.height }
                    val childSpanWidth = childRight - childLeft
                    val childSpanHeight = childBottom - childTop
                    val envelopeWidth = max(
                        childSpanWidth + COMPOSITE_HORIZONTAL_PADDING * 2,
                        parent.layout.closedWidth.roundToInt(),
                    )
                    val firstPassHeight = childSpanHeight + COMPOSITE_TOP_PADDING + COMPOSITE_BOTTOM_PADDING
                    val labelWidth = requiredOpenCompositeLabelWidth(parent, envelopeWidth.toDouble(), firstPassHeight.toDouble())
                    val openWidth = maxOf(
                        envelopeWidth,
                        parent.layout.closedWidth.roundToInt(),
                        labelWidth,
                    ).toDouble()
                    val topPadding = compositeTextMetrics(parent, openWidth, firstPassHeight.toDouble()).topPadding
                    val openX = childLeft - COMPOSITE_HORIZONTAL_PADDING
                    val openY = childTop - topPadding
                    val openHeight = max(
                        childSpanHeight + topPadding + COMPOSITE_BOTTOM_PADDING,
                        max(requiredPortHeight(parent, topPadding), parent.layout.closedHeight).roundToInt(),
                    ).toDouble()
                    parent.layout.openWidth = openWidth
                    parent.layout.openHeight = openHeight
                    parent.layout.x = openX.toDouble()
                    parent.layout.y = openY.toDouble()
                } else {
                    parent.layout.openWidth = parent.layout.closedWidth
                    parent.layout.openHeight = parent.layout.closedHeight + compositeTextMetrics(parent).topPadding - COMPOSITE_TOP_PADDING
                }
                parent.layout.width = if (parent.layout.isExpanded) parent.layout.openWidth else parent.layout.closedWidth
                parent.layout.height = if (parent.layout.isExpanded) parent.layout.openHeight else parent.layout.closedHeight
            }
        scheduleReroute()
    }

    private fun ensureLayoutCanHoldPortsAndLabels(node: Node) {
        val terminalWidth = max(node.layout.closedWidth, requiredNodeWidth(node))
        val terminalHeight = requiredTerminalHeight(node)
        node.layout.closedWidth = max(node.layout.closedWidth, terminalWidth)
        node.layout.closedHeight = terminalHeight
        if (!node.isComposite) {
            node.layout.openWidth = max(node.layout.openWidth, node.layout.closedWidth)
            node.layout.openHeight = node.layout.closedHeight
        } else {
            node.layout.openHeight = max(node.layout.openHeight, node.layout.closedHeight + COMPOSITE_HEADER_EXTRA_HEIGHT)
        }
        node.layout.width = if (node.layout.isExpanded || !node.isComposite) node.layout.openWidth else node.layout.closedWidth
        node.layout.height = if (node.layout.isExpanded || !node.isComposite) node.layout.openHeight else node.layout.closedHeight
    }

    private fun requiredPortHeight(node: Node, topSpacing: Int = portTopSpacing(node)): Double {
        val portCount = max(linksOnSide(node, -1).size, linksOnSide(node, 1).size)
        if (portCount == 0) return 0.0
        return (topSpacing + portCount * PORT_SPACING + PORT_BOTTOM_SPACING).toDouble()
    }

    private fun requiredTerminalHeight(node: Node): Double = maxOf(
        TERMINAL_NODE_BASE_HEIGHT.toDouble(),
        requiredPortHeight(node, PORT_TOP_SPACING),
        requiredTypeHeight(node),
    )

    private fun requiredNodeWidth(node: Node): Double {
        val labels = buildList {
            add(nodeDisplayName(node))
            add(nodeStereotype(node).name)
            technologyLabel(node)?.let(::add)
            typeFieldLabels(node).forEach(::add)
        }
        val contentWidth = labels.mapIndexed { index, label ->
            monospaceTextWidth(label, if (index == 0) TERMINAL_TITLE_SIZE else TERMINAL_INFO_SIZE, 28)
        }.maxOrNull() ?: 0
        val portWidth = node.ports.maxOfOrNull { monospaceTextWidth(it.name, 8, 68) } ?: 0
        return maxOf(200, contentWidth, portWidth).toDouble()
    }

    private fun requiredTypeHeight(node: Node): Double {
        if (!node.isType) return 0.0
        val fields = typeFieldLabels(node)
        if (fields.isEmpty()) return TERMINAL_NODE_BASE_HEIGHT.toDouble()
        val firstBaseline = TERMINAL_TYPE_FIELD_BASELINE +
            if (technologyLabel(node) == null) 0 else TERMINAL_TEXT_LINE_HEIGHT
        return (firstBaseline + (fields.size - 1) * TERMINAL_TEXT_LINE_HEIGHT + TERMINAL_BOTTOM_PADDING).toDouble()
    }

    private fun typeFieldLabels(node: Node): List<String> =
        node.typeDefinition?.fields.orEmpty().map { field ->
            val reference = if (field.isReference) "ref " else ""
            "${field.name}: $reference${repository.getDocument().typeDisplayName(field.typeId)}"
        }

    private fun requiredOpenCompositeLabelWidth(
        node: Node,
        width: Double = node.layout.width,
        height: Double = node.layout.height,
    ): Int {
        if (!node.isComposite) return requiredNodeWidth(node).roundToInt()
        val metrics = compositeTextMetrics(node, width, height)
        return maxOf(
            monospaceTextWidth(nodeDisplayName(node), metrics.titleSize, 28),
            monospaceTextWidth(nodeStereotype(node).name, metrics.infoSize, 28),
            technologyLabel(node)?.let { monospaceTextWidth(it, metrics.infoSize, 28) } ?: 0,
            200,
        )
    }

    private fun portTopSpacing(node: Node): Int =
        if (node.isComposite && node.layout.isExpanded) compositeTextMetrics(node).topPadding else PORT_TOP_SPACING

    private fun compositeTextMetrics(
        node: Node,
        width: Double = node.layout.width,
        height: Double = node.layout.height,
    ): CompositeTextMetrics {
        val normalizedChebyshev = max(
            width / ThreadworkDesignerSettings.compositeReferenceViewportWidth,
            height / ThreadworkDesignerSettings.compositeReferenceViewportHeight,
        ).coerceAtLeast(0.0)
        val compressedChebyshev = if (normalizedChebyshev <= 1.0) {
            normalizedChebyshev
        } else {
            1.0 + ln(normalizedChebyshev)
        }
        val descendantBoost = 1.0 + ln(totalDescendantCount(node) + 1.0) / COMPOSITE_TEXT_CHILD_BOOST_DIVISOR
        val titleSize = (ThreadworkDesignerSettings.compositeTitleTargetScreenPx * compressedChebyshev * descendantBoost)
            .coerceIn(COMPOSITE_TEXT_MIN_MODEL_SIZE, COMPOSITE_TEXT_MAX_MODEL_SIZE)
            .toFloat()
        val infoSize = (titleSize * COMPOSITE_INFO_TEXT_RATIO).toFloat()
        val titleRow = (titleSize * 1.35f).roundToInt().coerceAtLeast(14)
        val infoRow = (infoSize * 1.35f).roundToInt().coerceAtLeast(12)
        val titleBaseline = COMPOSITE_HEADER_EXTRA_HEIGHT + titleSize.roundToInt()
        val stereotypeBaseline = titleBaseline + infoRow
        val technologyBaseline = stereotypeBaseline + infoRow
        val topPadding = max(
            COMPOSITE_TOP_PADDING,
            COMPOSITE_HEADER_EXTRA_HEIGHT + titleRow + infoRow + infoRow + infoRow,
        )
        return CompositeTextMetrics(
            titleSize = titleSize,
            infoSize = infoSize,
            titleBaselineOffset = titleBaseline,
            stereotypeBaselineOffset = stereotypeBaseline,
            technologyBaselineOffset = technologyBaseline,
            topPadding = topPadding,
        )
    }

    private fun totalDescendantCount(node: Node): Int {
        val document = repository.getDocument()
        val seen = mutableSetOf<NodeId>()
        fun countChildren(current: Node): Int =
            current.children.sumOf { childId ->
                val child = document.nodes[childId] ?: return@sumOf 0
                if (!seen.add(child.id)) return@sumOf 0
                1 + countChildren(child)
            }
        return countChildren(node)
    }

    private fun linksOnSide(node: Node, side: Int): List<Node> {
        val document = repository.getDocument()
        val ids = node.outgoingLinks + node.incomingLinks
        return ids.distinct().mapNotNull(document.nodes::get)
            .filter { linkNode ->
                isVisibleLink(linkNode) && run {
                    val link = linkNode.link ?: return@run false
                    val outgoing = link.sourceNodeId == node.id
                    linkSide(node, linkNode, outgoing) == side
                }
            }
    }

    private fun depthOf(node: Node): Int {
        val document = repository.getDocument()
        var depth = 0
        var current = node.parentId
        while (current != null) {
            depth++
            current = document.nodes[current]?.parentId
        }
        return depth
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.font = designerFont
        drawCachedStaticScene(g2)
        drawScreenOverlay(g2)
    }

    private fun drawScreenOverlay(g2: Graphics2D) {
        val previousStroke = g2.stroke
        val previousFont = g2.font
        selectionRect?.let { rect ->
            val screenRect = modelRectToScreen(rect)
            val selectionColor = activePalette[DesignerColorKey.Selection]
            g2.color = Color(selectionColor.red, selectionColor.green, selectionColor.blue, 0x55)
            g2.fill(screenRect)
            g2.color = selectionColor
            g2.stroke = if (selectionDragLeftToRight) {
                BasicStroke(1f)
            } else {
                BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 4f), 0f)
            }
            g2.draw(screenRect)
        }
        drawSelectionBounds(g2)
        drawViewportHierarchyPath(g2)
        g2.stroke = previousStroke
        g2.font = previousFont
    }

    private fun drawSelectionBounds(g2: Graphics2D) {
        if (selection.isEmpty()) return
        val bounds = selectionBounds() ?: return
        val screenRect = modelRectToScreen(bounds).apply { grow(6, 6) }
        g2.color = activePalette[DesignerColorKey.Selection]
        g2.stroke = BasicStroke(1.5f)
        g2.draw(screenRect)
    }

    private fun selectionBounds(): Rectangle? {
        val document = repository.getDocument()
        var bounds: Rectangle? = null
        fun add(rect: Rectangle) {
            bounds = bounds?.union(rect) ?: Rectangle(rect)
        }
        selection.mapNotNull(document.nodes::get).forEach { node ->
            if (node.isLink) {
                if (isDependencyAnnotation(node)) {
                    dependencyAnnotationBounds(node).forEach(::add)
                } else {
                    (cachedRoute(node.id) ?: routeLink(node))?.bounds()?.let(::add)
                }
            } else if (isVisibleInCanvas(node)) {
                add(node.layout.rect())
            }
        }
        return bounds
    }

    private fun modelRectToScreen(rect: Rectangle): Rectangle {
        val left = ((rect.x + panX) * zoom).roundToInt()
        val top = ((rect.y + panY) * zoom).roundToInt()
        val right = ((rect.x + rect.width + panX) * zoom).roundToInt()
        val bottom = ((rect.y + rect.height + panY) * zoom).roundToInt()
        return Rectangle(
            min(left, right),
            min(top, bottom),
            abs(right - left).coerceAtLeast(1),
            abs(bottom - top).coerceAtLeast(1),
        )
    }

    private fun drawViewportHierarchyPath(g2: Graphics2D) {
        val viewport = viewportModelRect(padding = 0)
        val fillingNode = visibleNodes()
            .filter { it.layout.rect().contains(viewport) }
            .maxWithOrNull(compareBy<Node> { depthOf(it) }.thenBy { it.layout.width * it.layout.height })
            ?: return
        g2.font = designerFont.deriveFont(Font.BOLD, 13f)
        val metrics = g2.fontMetrics
        val text = fitScreenText(hierarchyPath(fillingNode), metrics, width - 16)
        g2.color = Color(0xccffffff.toInt(), true)
        g2.drawString(text, 9, 19)
        g2.color = activePalette[DesignerColorKey.TextPrimary]
        g2.drawString(text, 8, 18)
    }

    private fun hierarchyPath(node: Node): String {
        val document = repository.getDocument()
        val parts = mutableListOf<String>()
        var current: Node? = node
        while (current != null) {
            parts += current.name.ifBlank { current.id.value }
            current = current.parentId?.let(document.nodes::get)
        }
        return parts.asReversed().joinToString(separator = "/", prefix = "/")
    }

    private fun fitScreenText(text: String, metrics: FontMetrics, maxWidth: Int): String {
        if (metrics.stringWidth(text) <= maxWidth) return text
        var result = text
        while (result.length > 1 && metrics.stringWidth("...$result") > maxWidth) {
            result = result.drop(1)
        }
        return "...$result"
    }

    private fun drawCachedStaticScene(g2: Graphics2D) {
        if (width <= 0 || height <= 0) return
        // Tiles are rendered at discrete zoom buckets, then scaled to the active zoom.
        // Interpolation keeps diagonal route segments continuous between bucket levels.
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val bucket = zoomBucket()
        val bucketZoom = zoomForBucket(bucket)
        val tileModelSize = TILE_SIZE_PX / bucketZoom
        val viewport = viewportModelRect(padding = 0)
        val minTileX = floor(viewport.x / tileModelSize).toInt()
        val minTileY = floor(viewport.y / tileModelSize).toInt()
        val maxTileX = floor((viewport.x + viewport.width) / tileModelSize).toInt()
        val maxTileY = floor((viewport.y + viewport.height) / tileModelSize).toInt()
        val totalTiles = ((maxTileX - minTileX) + 1).coerceAtLeast(0) *
            ((maxTileY - minTileY) + 1).coerceAtLeast(0)
        var missingTiles = 0
        for (tileY in minTileY..maxTileY) {
            for (tileX in minTileX..maxTileX) {
                val key = FlowTileKey(FlowTileLayer.Static, renderRevision, bucket, tileX, tileY)
                if (key !in tileCache) missingTiles++
            }
        }
        var completedTiles = totalTiles - missingTiles
        if (missingTiles > 0) onTileProgress(completedTiles, totalTiles, true)
        for (tileY in minTileY..maxTileY) {
            for (tileX in minTileX..maxTileX) {
                val key = FlowTileKey(FlowTileLayer.Static, renderRevision, bucket, tileX, tileY)
                val tileLeft = tileX * tileModelSize
                val tileTop = tileY * tileModelSize
                val screenX = ((tileLeft + panX) * zoom).roundToInt()
                val screenY = ((tileTop + panY) * zoom).roundToInt()
                val screenSize = (tileModelSize * zoom).roundToInt().coerceAtLeast(1) + 1
                val tile = tileCache[key] ?: renderStaticTile(tileX, tileY, bucketZoom, tileModelSize).also {
                    tileCache[key] = it
                    completedTiles++
                    if (completedTiles == totalTiles || completedTiles % 4 == 0) {
                        onTileProgress(completedTiles, totalTiles, completedTiles < totalTiles)
                    }
                }
                g2.drawImage(tile, screenX, screenY, screenSize, screenSize, null)
            }
        }
        onTileProgress(totalTiles, totalTiles, false)
    }

    private fun renderStaticTile(tileX: Int, tileY: Int, bucketZoom: Double, tileModelSize: Double): BufferedImage {
        val tileLeft = tileX * tileModelSize
        val tileTop = tileY * tileModelSize
        val tileViewport = Rectangle(
            floor(tileLeft).toInt() - TILE_RENDER_PADDING,
            floor(tileTop).toInt() - TILE_RENDER_PADDING,
            ceil(tileModelSize).toInt() + TILE_RENDER_PADDING * 2,
            ceil(tileModelSize).toInt() + TILE_RENDER_PADDING * 2,
        )
        val image = BufferedImage(TILE_SIZE_PX, TILE_SIZE_PX, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.font = designerFont
        g2.color = background
        g2.fillRect(0, 0, image.width, image.height)
        g2.scale(bucketZoom, bucketZoom)
        g2.translate(-tileLeft, -tileTop)
        drawStaticScene(g2, tileViewport)
        g2.dispose()
        return image
    }

    private fun drawStaticScene(g2: Graphics2D, viewport: Rectangle) {
        val activeSheetPlan = if (showSheet) sheetPlan() else null
        activeSheetPlan?.let { withPalette(technicalPalette) { drawIsoSheet(g2, it, includeGrid = true, drawPreviewGuides = false) } }
        if (!showSheet) drawGrid(g2, viewport)
        if (activeSheetPlan == null) {
            drawGraph(g2, viewport = viewport)
        } else {
            withPalette(technicalPalette) { drawGraph(g2, viewport = viewport) }
        }
        activeSheetPlan?.let { drawSheetPreviewGuides(g2, it) }
    }

    private inline fun <T> withPalette(palette: DesignerPalette, block: () -> T): T {
        val previous = activePalette
        activePalette = palette
        return try {
            block()
        } finally {
            activePalette = previous
        }
    }

    private fun zoomBucket(): Int =
        (ln(zoom) / ln(ZOOM_BUCKET_STEP)).roundToInt()

    private fun zoomForBucket(bucket: Int): Double =
        ZOOM_BUCKET_STEP.pow(bucket.toDouble())

    private fun drawGrid(g2: Graphics2D, bounds: Rectangle? = null) {
        g2.color = activePalette[DesignerColorKey.GridMinor]
        val step = 40
        val minX = bounds?.x?.let { floor(it.toDouble() / step).toInt() * step } ?: (floor(-panX / step).toInt() * step)
        val minY = bounds?.y?.let { floor(it.toDouble() / step).toInt() * step } ?: (floor(-panY / step).toInt() * step)
        val maxX = bounds?.let { ceil((it.x + it.width).toDouble() / step).toInt() * step } ?: (ceil((width / zoom - panX) / step).toInt() * step)
        val maxY = bounds?.let { ceil((it.y + it.height).toDouble() / step).toInt() * step } ?: (ceil((height / zoom - panY) / step).toInt() * step)
        for (x in minX..maxX step step) {
            g2.color = if (x % 200 == 0) activePalette[DesignerColorKey.GridMajor] else activePalette[DesignerColorKey.GridMinor]
            g2.drawLine(x, minY, x, maxY)
        }
        for (y in minY..maxY step step) {
            g2.color = if (y % 200 == 0) activePalette[DesignerColorKey.GridMajor] else activePalette[DesignerColorKey.GridMinor]
            g2.drawLine(minX, y, maxX, y)
        }
    }

    private fun viewportModelRect(padding: Int = 200): Rectangle {
        val left = floor(-panX).toInt() - padding
        val top = floor(-panY).toInt() - padding
        val right = ceil(width / zoom - panX).toInt() + padding
        val bottom = ceil(height / zoom - panY).toInt() + padding
        return Rectangle(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    private fun drawGraph(g2: Graphics2D, scopeIds: Set<NodeId>? = null, viewport: Rectangle? = null) {
        val allLinks = visibleLinks(scopeIds)
        val links = allLinks
            .filter { viewport == null || linkMayIntersectViewport(it, viewport) }
        orderedVisibleNodes(scopeIds)
            .filter { viewport == null || it.id in selection || it.layout.rect().intersects(viewport) }
            .forEach { drawNode(g2, it) }
        links.filterNot(::isDependencyAnnotation).forEach { drawLink(g2, it) }
        // Annotation row indices must be derived from the complete link set. Tile-local
        // culling changes those indices and produces mismatched fragments at tile edges.
        drawDependencyAnnotations(g2, allLinks.filter(::isDependencyAnnotation))
        // Type annotations belong to the type node and must survive route tile culling.
        drawTypeUsageAnnotations(g2, allLinks, scopeIds)
    }

    private fun linkMayIntersectViewport(linkNode: Node, viewport: Rectangle): Boolean {
        if (isDependencyAnnotation(linkNode)) {
            return dependencyAnnotationBounds(linkNode).any { it.intersects(viewport) }
        }
        cachedRoute(linkNode.id)?.let {
            // Endpoint labels are rendered outside the route itself. Keep a generous
            // model-space margin so long names remain visible at tile boundaries.
            return it.bounds().apply { grow(320, 80) }.intersects(viewport)
        }
        val link = linkNode.link ?: return false
        val source = repository.getNode(link.sourceNodeId)?.layout?.rect() ?: return false
        val target = repository.getNode(link.targetNodeId)?.layout?.rect() ?: return false
        return source.union(target).apply { grow(PORT_STUB_LENGTH * 2, PORT_SPACING * 2) }.intersects(viewport)
    }

    private fun drawIsoSheet(
        g2: Graphics2D,
        plan: SheetPlan? = null,
        includeGrid: Boolean = true,
        drawPreviewGuides: Boolean = true,
    ) {
        val activePlan = plan ?: sheetPlan() ?: return
        val previousStroke = g2.stroke
        val previousFont = g2.font
        g2.color = Color.WHITE
        g2.fill(activePlan.sheet)
        if (includeGrid) drawGrid(g2, activePlan.sheet)
        drawTechnicalSheet(g2, activePlan)
        if (drawPreviewGuides) drawSheetPreviewGuides(g2, activePlan)
        g2.font = previousFont
        g2.stroke = previousStroke
    }

    private fun drawSheetPreviewGuides(g2: Graphics2D, plan: SheetPlan) {
        val previousColor = g2.color
        val previousStroke = g2.stroke
        plan.pages.forEach { page ->
            g2.color = Color(0x888888)
            g2.stroke = BasicStroke((0.5 * plan.scale).toFloat().coerceAtLeast(0.5f))
            g2.draw(page.sheet)
            if (plan.isMultipage) drawAlignmentCrosses(g2, plan, page)
        }
        g2.color = previousColor
        g2.stroke = previousStroke
    }

    private fun drawTechnicalSheet(
        g2: Graphics2D,
        plan: SheetPlan,
    ) {
        val sheet = plan.sheet
        val scale = plan.scale
        drawFoldingMarkers(g2, sheet, scale)
        g2.color = Color(0x777777)
        g2.stroke = BasicStroke(1f * scale)
        g2.draw(plan.drawing)

        val titleBlock = plan.titleBlock
        val rowHeight = titleBlock.height / 5
        val firstColumn = titleBlock.x + sheetMm(45.0, scale)
        val secondColumn = titleBlock.x + sheetMm(112.0, scale)
        g2.color = Color(0x777777)
        g2.draw(titleBlock)
        repeat(4) { row ->
            val y = titleBlock.y + (row + 1) * rowHeight
            g2.drawLine(titleBlock.x, y, titleBlock.x + titleBlock.width, y)
        }
        g2.drawLine(firstColumn, titleBlock.y, firstColumn, titleBlock.y + titleBlock.height)
        g2.drawLine(secondColumn, titleBlock.y, secondColumn, titleBlock.y + titleBlock.height)
        g2.color = Color(0x444444)
        g2.font = g2.font.deriveFont(12f * scale)
        val inset = sheetUnits(10, scale)
        val firstRowBaseline = titleBlock.y + rowHeight / 2 + g2.fontMetrics.ascent / 2 - sheetUnits(2, scale)
        val secondRowBaseline = titleBlock.y + rowHeight + rowHeight / 2 + g2.fontMetrics.ascent / 2 - sheetUnits(2, scale)
        val bottomBaseline = titleBlock.y + titleBlock.height - sheetUnits(8, scale)
        g2.drawString("Project", titleBlock.x + inset, firstRowBaseline)
        g2.drawString(projectTitle(), firstColumn + inset, firstRowBaseline)
        g2.drawString("Format: ${plan.format.id}", secondColumn + inset, firstRowBaseline)
        g2.drawString("Revision", titleBlock.x + inset, secondRowBaseline)
        g2.drawString(masterRevisionLabel(), firstColumn + inset, secondRowBaseline)
        g2.drawString("Scale 1:${plan.scale}", secondColumn + inset, secondRowBaseline)
        g2.drawString("version ${versionTimestamp()}", titleBlock.x + inset, bottomBaseline)

        val partsList = plan.partsList
        val partsRowHeight = sheetUnits(PARTS_ROW_HEIGHT, scale)
        g2.color = Color(0xb0b0b0)
        g2.draw(partsList)
        g2.font = g2.font.deriveFont(10f * scale)
        g2.drawString("Parts List", partsList.x + sheetUnits(8, scale), partsList.y + sheetUnits(15, scale))
        repeat((partsList.height / partsRowHeight).coerceAtLeast(1)) { row ->
            val y = partsList.y + row * partsRowHeight
            g2.drawLine(partsList.x, y, partsList.x + partsList.width, y)
        }
        val headerTop = partsList.y + partsRowHeight
        plan.partsColumns.offsets.drop(1).dropLast(1).forEach { offset ->
            g2.drawLine(partsList.x + offset, headerTop, partsList.x + offset, partsList.y + partsList.height)
        }
        plan.partsColumns.labels.forEachIndexed { columnIndex, label ->
            g2.drawString(label, partsList.x + plan.partsColumns.offsets[columnIndex] + sheetUnits(4, scale), partsList.y + partsRowHeight * 2 - sheetUnits(6, scale))
        }
        plan.bomRows.take((partsList.height / partsRowHeight - 2).coerceAtLeast(0)).forEachIndexed { rowIndex, row ->
            val y = partsList.y + (rowIndex + 3) * partsRowHeight - sheetUnits(6, scale)
            g2.color = Color(0x444444)
            partsListValues(row).forEachIndexed { columnIndex, value ->
                g2.drawString(value, partsList.x + plan.partsColumns.offsets[columnIndex] + sheetUnits(4, scale), y)
            }
        }
    }

    private fun sheetPlan(requestMultipage: Boolean = multipagePdfEnabled): SheetPlan? {
        val scopeIds = sheetScopeIds()
        val contentBounds = contentBounds(scopeIds) ?: return null
        val bomRows = bomRows(scopeIds)
        val scale = sheetScale
        val partsColumns = partsListColumns(bomRows, scale)
        val marginTop = sheetMm(SHEET_MARGIN_TOP_MM, scale)
        val marginRight = sheetMm(SHEET_MARGIN_RIGHT_MM, scale)
        val marginBottom = sheetMm(SHEET_MARGIN_BOTTOM_MM, scale)
        val marginLeft = sheetMm(SHEET_MARGIN_LEFT_MM, scale)
        val titleWidth = sheetMm(TITLE_BLOCK_WIDTH_MM, scale)
        val titleHeight = sheetMm(TITLE_BLOCK_HEIGHT_MM, scale)
        val partsWidth = partsColumns.totalWidth
        val partsRowHeight = sheetUnits(PARTS_ROW_HEIGHT, scale)
        val partsRequiredHeight = SheetLayout.partsListHeight(bomRows.size, partsRowHeight)
        val gap = sheetMm(8.0, scale)

        val requiredWidth = marginLeft + marginRight + contentBounds.width
        val requiredHeight = marginTop + marginBottom + contentBounds.height
        val occupiedArea = contentBounds.width.toDouble() * contentBounds.height
        val best = when (val choice = sheetFormatChoice) {
            AUTO_SHEET_FORMAT -> autoSheetCandidate(requiredWidth, requiredHeight, occupiedArea, scale)
            else -> SHEET_FORMATS.firstOrNull { it.id == choice }?.let {
                sheetCandidateFor(it, requiredWidth, requiredHeight, occupiedArea, scale, enforceFit = false)
            }
        } ?: autoSheetCandidate(requiredWidth, requiredHeight, occupiedArea, scale)
            ?: return null

        val format = best.format
        val tiled = requestMultipage && !format.roll
        val tileLayout = SheetLayout.tile(
            contentBounds = contentBounds,
            sheetWidth = best.width,
            sheetHeight = best.height,
            margin = marginTop,
            marginTop = marginTop,
            marginRight = marginRight,
            marginBottom = marginBottom,
            marginLeft = marginLeft,
            requestedOverlap = sheetMm(sheetOverlapMm, scale),
            multipage = tiled,
        )
        val drawing = tileLayout.drawing
        val titleBlock = Rectangle(
            drawing.x + drawing.width - titleWidth,
            drawing.y + drawing.height - titleHeight,
            titleWidth,
            titleHeight,
        )
        val partsHeight = partsRequiredHeight.coerceAtMost((titleBlock.y - drawing.y - gap).coerceAtLeast(partsRowHeight * 2))
        val pages = tileLayout.tiles.map { tile ->
            SheetPage(
                row = tile.row,
                column = tile.column,
                sheet = tile.sheet,
            )
        }
        return SheetPlan(
            format = format,
            scale = scale,
            pages = pages,
            overlap = tileLayout.overlap,
            drawing = drawing,
            titleBlock = titleBlock,
            partsList = Rectangle(
                drawing.x + drawing.width - partsWidth,
                drawing.y,
                partsWidth,
                partsHeight,
            ),
            contentBounds = contentBounds,
            scopeIds = scopeIds,
            bomRows = bomRows,
            partsColumns = partsColumns,
        )
    }

    private fun autoSheetCandidate(
        requiredWidth: Int,
        requiredHeight: Int,
        occupiedArea: Double,
        scale: Int,
    ): SheetCandidate? =
        SHEET_FORMATS
            .mapNotNull { format ->
                sheetCandidateFor(format, requiredWidth, requiredHeight, occupiedArea, scale, enforceFit = true)
            }
            .maxWithOrNull(compareBy<SheetCandidate> { it.coverage }.thenByDescending { it.area })
            ?: SHEET_FORMATS
                .mapNotNull { sheetCandidateFor(it, requiredWidth, requiredHeight, occupiedArea, scale, enforceFit = false) }
                .maxByOrNull { it.area }

    private fun sheetCandidateFor(
        format: SheetFormat,
        requiredWidth: Int,
        requiredHeight: Int,
        occupiedArea: Double,
        scale: Int,
        enforceFit: Boolean,
    ): SheetCandidate? {
        val (sheetWidth, sheetHeight) = if (format.roll) {
            val fixedRollHeight = sheetMm(format.widthMm, scale)
            requiredWidth to fixedRollHeight
        } else {
            sheetMm(format.widthMm, scale) to sheetMm(format.heightMm, scale)
        }
        val fits =
            requiredWidth <= sheetWidth &&
                requiredHeight <= sheetHeight
        if (enforceFit && !fits) return null
        return SheetCandidate(
            format = format,
            width = sheetWidth,
            height = sheetHeight,
            coverage = occupiedArea.toDouble() / (sheetWidth.toDouble() * sheetHeight.toDouble()),
        )
    }

    private fun sheetScopeIds(): Set<NodeId> {
        val document = repository.getDocument()
        val result = linkedSetOf<NodeId>()
        fun include(id: NodeId) {
            val node = document.nodes[id] ?: return
            if (id == document.rootNodeId) return
            if (result.add(id) && !node.isLink) {
                node.children.forEach(::include)
            }
        }

        if (selection.isEmpty()) {
            document.nodes.keys.forEach(::include)
        } else {
            selection.forEach(::include)
            val selectedNodes = result.mapNotNull(document.nodes::get).filterNot { it.isLink }.map { it.id }.toSet()
            document.nodes.values.filter { it.isLink }.forEach { linkNode ->
                val link = linkNode.link ?: return@forEach
                if (link.sourceNodeId in selectedNodes && link.targetNodeId in selectedNodes) result += linkNode.id
            }
        }
        return result
    }

    private fun contentBounds(scopeIds: Set<NodeId>): Rectangle? {
        val document = repository.getDocument()
        var bounds: Rectangle? = null
        fun add(rect: Rectangle) {
            bounds = bounds?.union(rect) ?: Rectangle(rect)
        }
        scopeIds.mapNotNull(document.nodes::get).filter {
            it.id == document.rootNodeId || isVisibleInCanvas(it)
        }.forEach { node ->
            if (node.isLink) {
                if (isDependencyAnnotation(node)) {
                    dependencyAnnotationBounds(node).forEach(::add)
                } else {
                    routeLink(node)?.points?.forEach { add(Rectangle(it.x - 4, it.y - 4, 8, 8)) }
                }
            } else if (node.id != document.rootNodeId) {
                add(node.layout.rect())
            }
        }
        return bounds
    }

    private fun bomRows(scopeIds: Set<NodeId>): List<BomRow> =
        scopeIds.mapNotNull(repository::getNode)
            .filter { !it.isLink && it.id != repository.getDocument().rootNodeId && isVisibleInCanvas(it) }
            .sortedWith(compareBy<Node> { it.name.lowercase() }.thenBy { it.id.value })
            .mapIndexed { index, node ->
                BomRow(
                    index = index + 1,
                    name = node.name,
                    kind = nodeStereotype(node).name,
                    modifiedDate = node.modified.date,
                    revision = repository.getDocument().effectiveRevision(node.id)?.name.orEmpty(),
                    responsible = repository.getDocument().effectiveResponsible(node.id),
                )
            }

    private fun partsListColumns(rows: List<BomRow>, scale: Int): PartsListColumns {
        val labels = listOf("No.", "Part", "Kind", "Modified", "Rev.", "Responsible", "Signature")
        val values = rows.map(::partsListValues)
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        return try {
            graphics.font = designerFont.deriveFont(10f * scale)
            val widths = SheetLayout.measuredColumnWidths(
                headers = labels,
                rows = values,
                metrics = graphics.fontMetrics,
                horizontalPadding = sheetUnits(8, scale),
                fixedWidths = mapOf(labels.lastIndex to sheetUnits(110, scale)),
            )
            PartsListColumns(labels, widths)
        } finally {
            graphics.dispose()
        }
    }

    private fun partsListValues(row: BomRow): List<String> =
        listOf(
            row.index.toString(),
            row.name,
            row.kind,
            partsListDate(row.modifiedDate),
            row.revision,
            row.responsible,
            "",
        )

    private fun partsListDate(value: String): String =
        runCatching { Instant.parse(value).truncatedTo(ChronoUnit.SECONDS).toString() }.getOrDefault(value)

    private fun drawFoldingMarkers(g2: Graphics2D, sheet: Rectangle, scale: Int) {
        val marker = sheetMm(5.0, scale)
        g2.color = Color(0x9a9a9a)
        listOf(sheetMm(210.0, scale), sheetMm(420.0, scale), sheetMm(630.0, scale), sheetMm(840.0, scale), sheetMm(1050.0, scale)).forEach { offset ->
            if (offset < sheet.width) {
                val x = sheet.x + sheet.width - offset
                g2.drawLine(x, sheet.y + sheet.height, x, sheet.y + sheet.height - marker)
            }
        }
        listOf(sheetMm(297.0, scale), sheetMm(594.0, scale), sheetMm(891.0, scale), sheetMm(1188.0, scale), sheetMm(1485.0, scale)).forEach { offset ->
            if (offset < sheet.height) {
                val y = sheet.y + sheet.height - offset
                g2.drawLine(sheet.x + sheet.width, y, sheet.x + sheet.width - marker, y)
            }
        }
    }

    private fun drawAlignmentCrosses(g2: Graphics2D, plan: SheetPlan, page: SheetPage) {
        if (plan.overlap <= 0) return
        val lastRow = plan.pages.maxOf(SheetPage::row)
        val lastColumn = plan.pages.maxOf(SheetPage::column)
        val halfOverlap = plan.overlap / 2
        val horizontalCrosses = listOf(
            page.sheet.x + page.sheet.width / 4,
            page.sheet.x + page.sheet.width * 3 / 4,
        )
        val verticalCrosses = listOf(
            page.sheet.y + page.sheet.height / 4,
            page.sheet.y + page.sheet.height * 3 / 4,
        )
        val crossRadius = sheetMm(3.0, plan.scale)
        val previousColor = g2.color
        val previousStroke = g2.stroke
        g2.color = Color(0x555555)
        g2.stroke = BasicStroke((0.6 * plan.scale).toFloat().coerceAtLeast(0.6f))

        if (page.row > 0) {
            horizontalCrosses.forEach { drawAlignmentCross(g2, it, page.sheet.y + halfOverlap, crossRadius) }
        }
        if (page.row < lastRow) {
            horizontalCrosses.forEach { drawAlignmentCross(g2, it, page.sheet.y + page.sheet.height - halfOverlap, crossRadius) }
        }
        if (page.column > 0) {
            verticalCrosses.forEach { drawAlignmentCross(g2, page.sheet.x + halfOverlap, it, crossRadius) }
        }
        if (page.column < lastColumn) {
            verticalCrosses.forEach { drawAlignmentCross(g2, page.sheet.x + page.sheet.width - halfOverlap, it, crossRadius) }
        }
        g2.color = previousColor
        g2.stroke = previousStroke
    }

    private fun drawAlignmentCross(g2: Graphics2D, x: Int, y: Int, radius: Int) {
        g2.drawLine(x - radius, y, x + radius, y)
        g2.drawLine(x, y - radius, x, y + radius)
    }

    private fun renderSheetImage(): BufferedImage? {
        val plan = sheetPlan(requestMultipage = false) ?: return null
        val image = BufferedImage(plan.sheet.width, plan.sheet.height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.font = designerFont
        g2.color = Color.WHITE
        g2.fillRect(0, 0, image.width, image.height)
        g2.translate(-plan.sheet.x, -plan.sheet.y)
        val previousClip = g2.clip
        g2.clip = plan.sheet
        drawIsoSheet(g2, plan, includeGrid = false, drawPreviewGuides = false)
        g2.clip = plan.drawing
        withPalette(technicalPalette) { drawGraph(g2, plan.scopeIds) }
        g2.clip = previousClip
        g2.dispose()
        return image
    }

    /**
     * Renders the same technical-sheet view shown on the canvas, constrained only for dialog
     * preview. The print/export path still renders at its full paper resolution.
     */
    fun renderPlanPreview(maxWidth: Int = 1_200, maxHeight: Int = 760): BufferedImage? {
        val plan = sheetPlan(requestMultipage = multipagePdfEnabled) ?: return null
        val scale = minOf(
            maxWidth.toDouble() / plan.sheet.width.coerceAtLeast(1),
            maxHeight.toDouble() / plan.sheet.height.coerceAtLeast(1),
            1.0,
        )
        val image = BufferedImage(
            (plan.sheet.width * scale).roundToInt().coerceAtLeast(1),
            (plan.sheet.height * scale).roundToInt().coerceAtLeast(1),
            BufferedImage.TYPE_INT_ARGB,
        )
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.scale(scale, scale)
            graphics.translate(-plan.sheet.x, -plan.sheet.y)
            graphics.font = designerFont
            graphics.clip = plan.sheet
            drawIsoSheet(graphics, plan, includeGrid = false, drawPreviewGuides = true)
            graphics.clip = plan.drawing
            withPalette(technicalPalette) { drawGraph(graphics, plan.scopeIds) }
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun withExtension(file: File, extension: String): File =
        if (file.name.endsWith(".$extension", ignoreCase = true)) file else File(file.parentFile, "${file.name}.$extension")

    private fun writeSvgSheet(file: File) {
        val plan = sheetPlan(requestMultipage = false) ?: error("Nothing to export.")
        val svg = StringBuilder()
        svg.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        svg.appendLine(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${plan.sheet.width / SHEET_UNITS_PER_MM}mm\" height=\"${plan.sheet.height / SHEET_UNITS_PER_MM}mm\" viewBox=\"0 0 ${plan.sheet.width} ${plan.sheet.height}\" preserveAspectRatio=\"xMinYMin meet\">",
        )
        svg.appendLine("  <title>${xml(projectTitle())} - ${xml(plan.format.id)}</title>")
        svg.appendLine("  <desc>Generated by Threadwork ${xml(Version.CURRENT.semver)} (${xml(Version.CURRENT.gitCommitId)})</desc>")
        svg.appendLine("  <defs>")
        svg.appendLine("    <clipPath id=\"drawing-clip\" clipPathUnits=\"userSpaceOnUse\">")
        svg.appendLine("      <rect x=\"${plan.drawing.x}\" y=\"${plan.drawing.y}\" width=\"${plan.drawing.width}\" height=\"${plan.drawing.height}\" />")
        svg.appendLine("    </clipPath>")
        svg.appendLine("  </defs>")
        svg.appendLine("  <g transform=\"translate(${-plan.sheet.x} ${-plan.sheet.y})\" font-family=\"monospace\" text-rendering=\"geometricPrecision\" shape-rendering=\"crispEdges\">")
        svgSheet(svg, plan)
        svg.appendLine("    <g clip-path=\"url(#drawing-clip)\">")
        withPalette(technicalPalette) { svgGraph(svg, plan.scopeIds) }
        svg.appendLine("    </g>")
        svg.appendLine("  </g>")
        svg.appendLine("</svg>")
        Files.writeString(file.toPath(), svg.toString())
    }

    private fun writeMermaidPlan(file: File) {
        val scopeIds = sheetPlan(requestMultipage = false)?.scopeIds ?: error("Nothing to export.")
        val nodes = orderedVisibleNodes(scopeIds)
        val links = visibleLinks(scopeIds).filterNot(::isDependencyAnnotation)
        val mermaid = buildString {
            appendLine("flowchart LR")
            nodes.forEach { node ->
                appendLine("    ${mermaidPlanId(node.id)}[\"${mermaidPlanText(node.name)}\"]")
            }
            links.forEach { linkNode ->
                val link = linkNode.link ?: return@forEach
                val label = mermaidPlanText(linkNode.name)
                appendLine("    ${mermaidPlanId(link.sourceNodeId)} -->|\"$label\"| ${mermaidPlanId(link.targetNodeId)}")
            }
        }
        Files.writeString(file.toPath(), mermaid)
    }

    private fun mermaidPlanId(nodeId: NodeId): String =
        "node_${nodeId.value.replace(Regex("[^A-Za-z0-9_]"), "_")}"

    private fun mermaidPlanText(value: String): String =
        value.replace('"', '\'').replace('|', '/').replace(Regex("\\s+"), " ").trim()

    private fun writePngSheet(file: File) {
        ImageIO.write(renderSheetImage() ?: error("Nothing to export."), "png", file)
    }

    private fun svgSheet(svg: StringBuilder, plan: SheetPlan) {
        val sheet = plan.sheet
        svgRect(svg, sheet, fill = "#ffffff", stroke = "#999999")
        svgFoldingMarkers(svg, sheet, plan.scale)
        svgRect(svg, plan.drawing, fill = "none", stroke = "#777777")

        val titleBlock = plan.titleBlock
        val scale = plan.scale
        val rowHeight = titleBlock.height / 5
        val firstColumn = titleBlock.x + sheetMm(45.0, scale)
        val secondColumn = titleBlock.x + sheetMm(112.0, scale)
        val inset = sheetUnits(10, scale)
        svgRect(svg, titleBlock, fill = "none", stroke = "#777777")
        repeat(4) { row ->
            val y = titleBlock.y + (row + 1) * rowHeight
            svgLine(svg, titleBlock.x, y, titleBlock.x + titleBlock.width, y, "#777777")
        }
        svgLine(svg, firstColumn, titleBlock.y, firstColumn, titleBlock.y + titleBlock.height, "#777777")
        svgLine(svg, secondColumn, titleBlock.y, secondColumn, titleBlock.y + titleBlock.height, "#777777")
        val textSize = 12 * scale
        val firstRowBaseline = titleBlock.y + rowHeight / 2 + textSize / 2
        val secondRowBaseline = titleBlock.y + rowHeight + rowHeight / 2 + textSize / 2
        svgText(svg, "Project", titleBlock.x + inset, firstRowBaseline, textSize, "#444444")
        svgText(svg, projectTitle(), firstColumn + inset, firstRowBaseline, textSize, "#444444")
        svgText(svg, "Format: ${plan.format.id}", secondColumn + inset, firstRowBaseline, textSize, "#444444")
        svgText(svg, "Revision", titleBlock.x + inset, secondRowBaseline, textSize, "#444444")
        svgText(svg, masterRevisionLabel(), firstColumn + inset, secondRowBaseline, textSize, "#444444")
        svgText(svg, "Scale 1:${plan.scale}", secondColumn + inset, secondRowBaseline, textSize, "#444444")
        svgText(svg, "version ${versionTimestamp()}", titleBlock.x + inset, titleBlock.y + titleBlock.height - sheetUnits(8, scale), textSize, "#444444")

        val partsList = plan.partsList
        val partsRowHeight = sheetUnits(PARTS_ROW_HEIGHT, scale)
        svgRect(svg, partsList, fill = "none", stroke = "#b0b0b0")
        svgText(svg, "Parts List", partsList.x + sheetUnits(8, scale), partsList.y + sheetUnits(15, scale), 10 * scale, "#444444")
        repeat((partsList.height / partsRowHeight).coerceAtLeast(1)) { row ->
            val y = partsList.y + row * partsRowHeight
            svgLine(svg, partsList.x, y, partsList.x + partsList.width, y, "#b0b0b0")
        }
        val headerTop = partsList.y + partsRowHeight
        plan.partsColumns.offsets.drop(1).dropLast(1).forEach { offset ->
            svgLine(svg, partsList.x + offset, headerTop, partsList.x + offset, partsList.y + partsList.height, "#b0b0b0")
        }
        plan.partsColumns.labels.forEachIndexed { columnIndex, label ->
            svgText(svg, label, partsList.x + plan.partsColumns.offsets[columnIndex] + sheetUnits(4, scale), partsList.y + partsRowHeight * 2 - sheetUnits(6, scale), 10 * scale, "#444444")
        }
        plan.bomRows.take((partsList.height / partsRowHeight - 2).coerceAtLeast(0)).forEachIndexed { rowIndex, row ->
            val y = partsList.y + (rowIndex + 3) * partsRowHeight - sheetUnits(6, scale)
            partsListValues(row).forEachIndexed { columnIndex, value ->
                svgText(svg, value, partsList.x + plan.partsColumns.offsets[columnIndex] + sheetUnits(4, scale), y, 10 * scale, "#444444")
            }
        }
    }

    private fun svgGrid(svg: StringBuilder, bounds: Rectangle) {
        val step = 40
        val minX = floor(bounds.x.toDouble() / step).toInt() * step
        val minY = floor(bounds.y.toDouble() / step).toInt() * step
        val maxX = ceil((bounds.x + bounds.width).toDouble() / step).toInt() * step
        val maxY = ceil((bounds.y + bounds.height).toDouble() / step).toInt() * step
        for (x in minX..maxX step step) {
            svgLine(svg, x, minY, x, maxY, if (x % 200 == 0) "#d1d1df" else "#e2e2e2")
        }
        for (y in minY..maxY step step) {
            svgLine(svg, minX, y, maxX, y, if (y % 200 == 0) "#d1d1df" else "#e2e2e2")
        }
    }

    private fun svgFoldingMarkers(svg: StringBuilder, sheet: Rectangle, scale: Int) {
        val marker = sheetMm(5.0, scale)
        listOf(sheetMm(210.0, scale), sheetMm(420.0, scale), sheetMm(630.0, scale), sheetMm(840.0, scale), sheetMm(1050.0, scale)).forEach { offset ->
            if (offset < sheet.width) {
                val x = sheet.x + sheet.width - offset
                svgLine(svg, x, sheet.y + sheet.height, x, sheet.y + sheet.height - marker, "#9a9a9a")
            }
        }
        listOf(sheetMm(297.0, scale), sheetMm(594.0, scale), sheetMm(891.0, scale), sheetMm(1188.0, scale), sheetMm(1485.0, scale)).forEach { offset ->
            if (offset < sheet.height) {
                val y = sheet.y + sheet.height - offset
                svgLine(svg, sheet.x + sheet.width, y, sheet.x + sheet.width - marker, y, "#9a9a9a")
            }
        }
    }

    private fun svgGraph(svg: StringBuilder, scopeIds: Set<NodeId>) {
        val links = visibleLinks(scopeIds)
        orderedVisibleNodes(scopeIds).forEach { svgNode(svg, it) }
        links.filterNot(::isDependencyAnnotation).forEach { svgLink(svg, it) }
        svgDependencyAnnotations(svg, links.filter(::isDependencyAnnotation))
        svgTypeUsageAnnotations(svg, links, scopeIds)
    }

    private fun svgNode(svg: StringBuilder, node: Node) {
        val r = node.layout.rect()
        val stereotype = nodeStereotype(node)
        val strokeDash = if (node.children.isNotEmpty()) "24 8 4 8" else null
        val strokeWidth = when {
            node.children.isNotEmpty() -> 2.2
            stereotype == NodeStereotype.ServiceLibrary -> 2.2
            stereotype in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> 2.2
            stereotype in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> 2.2
            else -> 2.0
        }
        svgRect(
            svg,
            r,
            fill = hex(fillFor(node)),
            stroke = hex(strokeFor(node, selected = false)),
            strokeWidth = strokeWidth,
            dashArray = strokeDash,
        )
        if (node.isComposite && node.layout.isExpanded) {
            val metrics = compositeTextMetrics(node)
            svgNodeName(svg, node, r.x + 12, r.y + metrics.titleBaselineOffset, metrics.titleSize.roundToInt())
            svgText(svg, stereotype.name, r.x + 12, r.y + metrics.stereotypeBaselineOffset, metrics.infoSize.roundToInt(), "#555555")
            technologyLabel(node)?.let {
                svgText(svg, it, r.x + 12, r.y + metrics.technologyBaselineOffset, metrics.infoSize.roundToInt(), "#666666")
            }
        } else {
            val headerOffset = if (node.isComposite) COMPOSITE_HEADER_EXTRA_HEIGHT else 0
            svgNodeName(svg, node, r.x + 12, r.y + TERMINAL_TITLE_BASELINE + headerOffset, TERMINAL_TITLE_SIZE.roundToInt())
            svgText(svg, stereotype.name, r.x + 12, r.y + TERMINAL_STEREOTYPE_BASELINE + headerOffset, TERMINAL_INFO_SIZE.roundToInt(), "#555555")
            val technology = technologyLabel(node)
            technology?.let {
                svgText(svg, it, r.x + 12, r.y + TERMINAL_TECHNOLOGY_BASELINE + headerOffset, TERMINAL_INFO_SIZE.roundToInt(), "#666666")
            }
            if (node.isType) {
                val firstBaseline = TERMINAL_TYPE_FIELD_BASELINE +
                    if (technology == null) 0 else TERMINAL_TEXT_LINE_HEIGHT
                typeFieldLabels(node).forEachIndexed { index, label ->
                    svgText(svg, label, r.x + 12, r.y + firstBaseline + index * TERMINAL_TEXT_LINE_HEIGHT, TERMINAL_INFO_SIZE.roundToInt(), "#00695c")
                }
            }
        }
        svgCompositeToggle(svg, node)
    }

    private fun svgLink(svg: StringBuilder, node: Node) {
        node.link ?: return
        val route = routeLink(node) ?: return
        val stereotype = LinkClassifier.classify(repository.getDocument(), node)
        val color = hex(linkColor(stereotype, selected = false))
        val strokeWidth = when (stereotype) {
            LinkStereotype.ErrorPipe -> 2.0
            LinkStereotype.DependencyInjection -> 1.6
            LinkStereotype.UsageImport -> 1.8
            LinkStereotype.SourceCapability,
            LinkStereotype.RunnableCapability -> 2.0
            else -> 1.5
        }
        val dash = linkDashPattern(stereotype, isBackflow(route))
        svgPath(svg, route.points, color, strokeWidth, dash)
        svgArrowAlongRoute(svg, route.points, color)
        svgPortMarker(svg, route.source, route.sourceDirection, outgoing = true, color = color)
        svgPortMarker(svg, route.target, route.targetDirection, outgoing = false, color = color)
        compositeBoundaryIntersections(node, route.points).forEach { point ->
            val (dx, dy) = routeDirectionAt(route.points, point)
            svgBoundaryPierceMarker(svg, point, dx, dy, color)
        }
        svgEndpointLinkLabels(svg, node, route, color)
    }

    private fun svgPortMarker(svg: StringBuilder, point: Point, side: Int, outgoing: Boolean, color: String) {
        svgPolygon(
            svg,
            svgPortCapPoints(point, side, outgoing),
            fill = color,
            stroke = color,
            strokeWidth = 1.0,
        )
    }

    private fun svgBoundaryPierceMarker(svg: StringBuilder, point: Point, dx: Int, dy: Int, color: String) {
        val arrowCenter = boundaryArrowCenter(point, dx, dy)
        svgDirectionalArrow(svg, arrowCenter, dx, dy, color)
        svg.appendLine("    <circle cx=\"${point.x}\" cy=\"${point.y}\" r=\"5\" fill=\"#ffffff\" stroke=\"$color\" stroke-width=\"1.5\"/>")
    }

    private fun svgArrowAlongRoute(svg: StringBuilder, points: List<Point>, color: String) {
        val total = points.zipWithNext().sumOf { (a, b) -> a.distance(b) }
        if (total <= 0.0) return
        val target = total * 0.75
        var travelled = 0.0
        points.zipWithNext().forEach { (a, b) ->
            val segment = a.distance(b)
            if (segment > 0.0 && travelled + segment >= target) {
                val ratio = ((target - travelled) / segment).coerceIn(0.0, 1.0)
                val x = (a.x + (b.x - a.x) * ratio).toInt()
                val y = (a.y + (b.y - a.y) * ratio).toInt()
                svgDirectionalArrow(svg, Point(x, y), b.x - a.x, b.y - a.y, color)
                return
            }
            travelled += segment
        }
    }

    private fun svgDirectionalArrow(svg: StringBuilder, point: Point, dx: Int, dy: Int, color: String) {
        if (dx == 0 && dy == 0) return
        val length = hypot(dx.toDouble(), dy.toDouble())
        val ux = dx / length
        val uy = dy / length
        val size = 10.0
        val wing = 5.0
        val tipX = point.x + ux * size
        val tipY = point.y + uy * size
        val baseX = point.x - ux * size
        val baseY = point.y - uy * size
        val px = -uy
        val py = ux
        svgPolygon(
            svg,
            listOf(
                tipX to tipY,
                (baseX + px * wing) to (baseY + py * wing),
                (baseX - px * wing) to (baseY - py * wing),
            ),
            fill = color,
        )
    }

    private fun svgTriangle(svg: StringBuilder, tip: Point, horizontalDirection: Int, size: Int, color: String) {
        val baseX = tip.x - horizontalDirection * size
        svgPolygon(
            svg,
            listOf(
                tip.x.toDouble() to tip.y.toDouble(),
                baseX.toDouble() to (tip.y - size).toDouble(),
                baseX.toDouble() to (tip.y + size).toDouble(),
            ),
            fill = color,
        )
    }

    private fun svgEndpointLinkLabels(svg: StringBuilder, node: Node, route: LinkRoute, color: String) {
        val (name, typeName) = linkLabelParts(node)
        if (name.isBlank() && typeName == null) return
        svgEndpointLinkLabel(svg, name, typeName, route.source, route.sourceDirection, color)
        svgEndpointLinkLabel(svg, name, typeName, route.target, route.targetDirection, color)
    }

    private fun svgEndpointLinkLabel(
        svg: StringBuilder,
        name: String,
        typeName: String?,
        anchor: Point,
        side: Int,
        color: String,
    ) {
        val separator = if (typeName == null) "" else ":"
        val full = name + separator + typeName.orEmpty()
        val width = monospaceTextWidth(full, 8, 0)
        val x = if (side < 0) anchor.x - width - 8 else anchor.x + 8
        val y = anchor.y - 14
        svgText(svg, name, x, y, 10, color)
        typeName?.let {
            val offset = monospaceTextWidth(name + separator, 8, 0)
            svgText(svg, ":$it", x + offset, y, 10, "#008c4a")
        }
    }

    private fun svgDependencyAnnotations(svg: StringBuilder, links: List<Node>) {
        links.groupBy { it.link?.sourceNodeId }.forEach { (sourceId, sourceLinks) ->
            val source = sourceId?.let(repository::getNode) ?: return@forEach
            svgLibraryDependents(svg, source, sourceLinks)
        }
        links.groupBy { it.link?.targetNodeId }.forEach { (targetId, targetLinks) ->
            val target = targetId?.let(repository::getNode) ?: return@forEach
            svgDependencyList(svg, target, targetLinks)
        }
    }

    private fun svgLibraryDependents(svg: StringBuilder, library: Node, links: List<Node>) {
        val r = library.layout.rect()
        links.forEachIndexed { index, linkNode ->
            val link = linkNode.link ?: return@forEachIndexed
            repository.getNode(link.targetNodeId) ?: return@forEachIndexed
            val label = dependencyInjectionLabel(linkNode, library)
            val labelWidth = dependencyAnnotationWidth(label, DEPENDENCY_SOURCE_MIN_WIDTH)
            val labelHeight = 24
            val y = r.y + 10 + index * (labelHeight + 8)
            val x = r.x + r.width + DEPENDENCY_SOURCE_GAP
            val anchor = Point(r.x + r.width, y + labelHeight / 2)
            val color = hex(annotationColor(linkNode, selected = false))
            svgLine(svg, anchor.x, anchor.y, x, anchor.y, color, strokeWidth = 1.5)
            svgCircle(svg, anchor.x, anchor.y, 4, color)
            svgRect(svg, Rectangle(x, y, labelWidth, labelHeight), fill = "#f8f9ff", stroke = color, strokeWidth = 1.5)
            svgText(svg, label, x + 8, y + 17, DEPENDENCY_ANNOTATION_FONT_SIZE.roundToInt(), color)
        }
    }

    private fun svgDependencyList(svg: StringBuilder, dependent: Node, links: List<Node>) {
        val r = dependent.layout.rect()
        val rows = links.mapNotNull { linkNode ->
            val link = linkNode.link ?: return@mapNotNull null
            val source = repository.getNode(link.sourceNodeId) ?: return@mapNotNull null
            linkNode to dependencyInjectionLabel(linkNode, source)
        }
        if (rows.isEmpty()) return

        val x = r.x + 36
        val rowHeight = 24
        val startY = r.y - rows.size * rowHeight - 16
        val stemBottom = r.y
        val stemColor = hex(annotationColor(rows.first().first, selected = false))
        svgLine(svg, x, startY, x, stemBottom, stemColor, strokeWidth = 1.5)
        rows.forEachIndexed { index, (linkNode, label) ->
            val y = startY + index * rowHeight + 8
            val width = dependencyTargetRuleWidth(label)
            val color = hex(annotationColor(linkNode, selected = false))
            svgCircle(svg, x, y, 5, color)
            svgLine(svg, x, y, x + width, y, color, strokeWidth = 1.5)
            svgText(svg, label, x + 10, y - 4, DEPENDENCY_ANNOTATION_FONT_SIZE.roundToInt(), color)
        }
    }

    private fun svgTypeUsageAnnotations(svg: StringBuilder, links: List<Node>, scopeIds: Set<NodeId>?) {
        links.groupBy { it.link?.typeDefinitionId?.takeIf(String::isNotBlank) }.forEach { (typeId, usages) ->
            val type = typeId?.let(::NodeId)?.let(repository::getNode)?.takeIf(Node::isType) ?: return@forEach
            if (scopeIds != null && type.id !in scopeIds) return@forEach
            val r = type.layout.rect()
            usages.forEachIndexed { index, link ->
                val label = link.name
                val width = max(110, label.length * 8 + 24)
                val y = r.y + 10 + index * 32
                val x = r.x + r.width + 34
                val anchorY = y + 12
                svgLine(svg, r.x + r.width, anchorY, x, anchorY, "#00897b", strokeWidth = 1.5)
                svgCircle(svg, r.x + r.width, anchorY, 4, "#00897b")
                svgRect(svg, Rectangle(x, y, width, 24), fill = "#f1fbf9", stroke = "#00897b", strokeWidth = 1.5)
                svgText(svg, label, x + 10, y + 17, 12, "#00695c")
            }
        }
    }

    private fun svgRect(
        svg: StringBuilder,
        rect: Rectangle,
        fill: String,
        stroke: String,
        strokeWidth: Double = 1.0,
        dashArray: String? = null,
    ) {
        svg.append("    <rect x=\"${rect.x}\" y=\"${rect.y}\" width=\"${rect.width}\" height=\"${rect.height}\" fill=\"$fill\" stroke=\"$stroke\"")
        if (stroke != "none") svg.append(" stroke-width=\"${fmt(strokeWidth)}\"")
        dashArray?.let { svg.append(" stroke-dasharray=\"$it\"") }
        svg.appendLine("/>")
    }

    private fun svgEllipse(
        svg: StringBuilder,
        rect: Rectangle,
        fill: String,
        stroke: String,
        strokeWidth: Double = 1.0,
    ) {
        val cx = rect.x + rect.width / 2.0
        val cy = rect.y + rect.height / 2.0
        val rx = rect.width / 2.0
        val ry = rect.height / 2.0
        svg.appendLine(
            "    <ellipse cx=\"${fmt(cx)}\" cy=\"${fmt(cy)}\" rx=\"${fmt(rx)}\" ry=\"${fmt(ry)}\" fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"${fmt(strokeWidth)}\"/>",
        )
    }

    private fun svgLine(
        svg: StringBuilder,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        stroke: String,
        strokeWidth: Double = 1.0,
    ) {
        svg.appendLine("    <line x1=\"$x1\" y1=\"$y1\" x2=\"$x2\" y2=\"$y2\" stroke=\"$stroke\" stroke-width=\"${fmt(strokeWidth)}\"/>")
    }

    private fun svgPath(svg: StringBuilder, points: List<Point>, stroke: String, strokeWidth: Double, dashArray: String? = null) {
        if (points.size < 2) return
        val data = buildString {
            append("M ${points.first().x} ${points.first().y}")
            points.drop(1).forEach { append(" L ${it.x} ${it.y}") }
        }
        svg.append("    <path d=\"$data\" fill=\"none\" stroke=\"$stroke\" stroke-width=\"${fmt(strokeWidth)}\"")
        dashArray?.let { svg.append(" stroke-dasharray=\"$it\"") }
        svg.appendLine(" stroke-linejoin=\"miter\"/>")
    }

    private fun svgNodeName(svg: StringBuilder, node: Node, x: Int, baseline: Int, size: Int) {
        val name = node.name
        val detail = node.nameDetail.trim()
        val override = isOverrideNode(node)
        svgText(svg, name, x, baseline, size, "#222222")
        if (detail.isBlank()) return
        val renderedDetail = if (override) "($detail)" else detail
        val detailX = x + if (name.isBlank()) 0 else monospaceTextWidth("$name ", size.toFloat(), 0)
        val color = if (override) {
            ThreadworkAppearance.colorToHex(activePalette[DesignerColorKey.OverrideDetailText])
        } else {
            ThreadworkAppearance.colorToHex(activePalette[DesignerColorKey.TextMuted])
        }
        svgText(svg, renderedDetail, detailX, baseline, size, color)
    }

    private fun svgText(svg: StringBuilder, text: String, x: Int, y: Int, size: Int, fill: String) {
        svg.appendLine("    <text x=\"$x\" y=\"$y\" font-size=\"$size\" fill=\"$fill\">${xml(text)}</text>")
    }

    private fun svgCircle(svg: StringBuilder, cx: Int, cy: Int, radius: Int, fill: String) {
        svg.appendLine("    <circle cx=\"$cx\" cy=\"$cy\" r=\"$radius\" fill=\"$fill\"/>")
    }

    private fun svgPolygon(
        svg: StringBuilder,
        points: List<Pair<Double, Double>>,
        fill: String,
        stroke: String = fill,
        strokeWidth: Double = 1.0,
    ) {
        val pointData = points.joinToString(" ") { "${fmt(it.first)},${fmt(it.second)}" }
        svg.appendLine(
            "    <polygon points=\"$pointData\" fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"${fmt(strokeWidth)}\"/>",
        )
    }

    private fun svgPortCapPoints(point: Point, side: Int, outgoing: Boolean): List<Pair<Double, Double>> {
        val direction = if (side >= 0) 1 else -1
        val width = 30.0
        val height = 10.0
        val inset = 6.0
        val x = point.x.toDouble()
        val y = point.y.toDouble()
        return if (outgoing) {
            if (direction > 0) {
                listOf(
                    x to (y - height / 2),
                    (x + width - inset) to (y - height / 2),
                    (x + width) to y,
                    (x + width - inset) to (y + height / 2),
                    x to (y + height / 2),
                )
            } else {
                listOf(
                    x to (y - height / 2),
                    (x - width + inset) to (y - height / 2),
                    (x - width) to y,
                    (x - width + inset) to (y + height / 2),
                    x to (y + height / 2),
                )
            }
        } else {
            if (direction > 0) {
                listOf(
                    x to (y - height / 2),
                    (x + inset) to (y - height / 2),
                    (x + width) to y,
                    (x + inset) to (y + height / 2),
                    x to (y + height / 2),
                )
            } else {
                listOf(
                    x to (y - height / 2),
                    (x - inset) to (y - height / 2),
                    (x - width) to y,
                    (x - inset) to (y + height / 2),
                    x to (y + height / 2),
                )
            }
        }
    }

    private fun hex(color: Color): String =
        "#%02x%02x%02x".format(color.red, color.green, color.blue)

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(java.util.Locale.ROOT, value)

    private fun writePdfSheet(file: File) {
        writePdf(file.toPath(), pdfRenderMode)
    }

    internal fun writePdf(path: Path, renderMode: PdfRenderMode) {
        when (renderMode) {
            PdfRenderMode.Rasterized -> RasterPdfWriter.write(path, renderPdfPages())
            PdfRenderMode.Searchable -> FopPdfWriter.write(path, renderVectorPdfPages())
        }
    }

    internal fun renderPdfPages(): List<PdfRasterPage> {
        val plan = sheetPlan(requestMultipage = multipagePdfEnabled) ?: error("Nothing to export.")
        return plan.pages.map { page ->
            val image = renderPdfPage(plan, page)
            val widthMm = page.sheet.width / SHEET_UNITS_PER_MM / plan.scale
            val heightMm = page.sheet.height / SHEET_UNITS_PER_MM / plan.scale
            PdfRasterPage(
                image = image,
                widthPoints = widthMm * PDF_POINTS_PER_MM,
                heightPoints = heightMm * PDF_POINTS_PER_MM,
            )
        }
    }

    private fun renderVectorPdfPages(): List<PdfVectorPage> {
        val plan = sheetPlan(requestMultipage = multipagePdfEnabled) ?: error("Nothing to export.")
        return plan.pages.map { page ->
            val widthPoints = page.sheet.width
                .toDouble()
                .div(SHEET_UNITS_PER_MM * plan.scale)
                .times(PDF_POINTS_PER_MM)
                .roundToInt()
                .coerceAtLeast(1)
            val heightPoints = page.sheet.height
                .toDouble()
                .div(SHEET_UNITS_PER_MM * plan.scale)
                .times(PDF_POINTS_PER_MM)
                .roundToInt()
                .coerceAtLeast(1)
            PdfVectorPage(widthPoints, heightPoints) { graphics ->
                drawVectorPdfPage(graphics, plan, page, widthPoints, heightPoints)
            }
        }
    }

    private fun drawVectorPdfPage(
        graphics: Graphics2D,
        plan: SheetPlan,
        page: SheetPage,
        widthPoints: Int,
        heightPoints: Int,
    ) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, widthPoints, heightPoints)
        graphics.font = designerFont
        val pointsPerModelUnit = PDF_POINTS_PER_MM / SHEET_UNITS_PER_MM / plan.scale
        graphics.scale(pointsPerModelUnit, pointsPerModelUnit)
        graphics.translate(-page.sheet.x.toDouble(), -page.sheet.y.toDouble())
        graphics.clip = page.sheet
        drawTechnicalSheet(graphics, plan)
        graphics.clip = page.sheet
        graphics.clip(plan.drawing)
        withPalette(technicalPalette) { drawGraph(graphics, plan.scopeIds) }
        graphics.clip = page.sheet
        if (plan.isMultipage) drawAlignmentCrosses(graphics, plan, page)
    }

    private fun renderPdfPage(plan: SheetPlan, page: SheetPage): BufferedImage {
        val imageWidth = (page.sheet.width.toDouble() / plan.scale).roundToInt().coerceAtLeast(1)
        val imageHeight = (page.sheet.height.toDouble() / plan.scale).roundToInt().coerceAtLeast(1)
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = Color.WHITE
        g2.fillRect(0, 0, image.width, image.height)
        g2.font = designerFont
        g2.scale(1.0 / plan.scale, 1.0 / plan.scale)
        g2.translate(-page.sheet.x, -page.sheet.y)
        g2.clip = page.sheet
        drawTechnicalSheet(g2, plan)
        g2.clip = page.sheet
        g2.clip(plan.drawing)
        withPalette(technicalPalette) { drawGraph(g2, plan.scopeIds) }
        g2.clip = page.sheet
        if (plan.isMultipage) drawAlignmentCrosses(g2, plan, page)
        g2.dispose()
        return image
    }

    private fun mm(value: Double): Int = (value * SHEET_UNITS_PER_MM).roundToInt()

    private fun sheetMm(value: Double, scale: Int = sheetScale): Int =
        (value * SHEET_UNITS_PER_MM * scale).roundToInt()

    private fun sheetUnits(value: Int, scale: Int): Int = value * scale

    private fun projectTitle(): String =
        repository.getDocument().rootNode().name.trim().ifBlank { repository.getDocument().name }

    private fun masterRevisionLabel(): String {
        val revision = repository.getDocument().masterRevision
        return listOf(revision.name.trim(), revision.date.trim()).filter(String::isNotBlank).joinToString(" - ")
    }

    private fun versionTimestamp(): String =
        Version.CURRENT.buildDate
            .replace('T', ' ')
            .replace(Regex("[Z+]\\S*$"), "")
            .take(19)

    private fun drawNode(g2: Graphics2D, node: Node) {
        val r = node.layout.rect()
        val selected = node.id in selection
        val stereotype = nodeStereotype(node)
        val previousStroke = g2.stroke
        val previousFont = g2.font
        g2.color = fillFor(node)
        g2.fillRect(r.x, r.y, r.width, r.height)
        g2.color = strokeFor(node, selected)
        g2.stroke = nodeStroke(node, selected)
        g2.drawRect(r.x, r.y, r.width, r.height)
        g2.color = activePalette[DesignerColorKey.TextPrimary]
        if (node.isComposite && node.layout.isExpanded) {
            val metrics = compositeTextMetrics(node)
            g2.font = designerFont.deriveFont(metrics.titleSize)
            drawNodeName(g2, node, r.x + 12, r.y + metrics.titleBaselineOffset)
            g2.color = activePalette[DesignerColorKey.TextSecondary]
            g2.font = designerFont.deriveFont(metrics.infoSize)
            g2.drawString(stereotype.name, r.x + 12, r.y + metrics.stereotypeBaselineOffset)
            technologyLabel(node)?.let {
                g2.color = activePalette[DesignerColorKey.TextMuted]
                g2.drawString(it, r.x + 12, r.y + metrics.technologyBaselineOffset)
            }
        } else {
            val headerOffset = if (node.isComposite) COMPOSITE_HEADER_EXTRA_HEIGHT else 0
            g2.font = designerFont.deriveFont(TERMINAL_TITLE_SIZE)
            drawNodeName(g2, node, r.x + 12, r.y + TERMINAL_TITLE_BASELINE + headerOffset)
            g2.color = activePalette[DesignerColorKey.TextSecondary]
            g2.font = designerFont.deriveFont(TERMINAL_INFO_SIZE)
            g2.drawString(stereotype.name, r.x + 12, r.y + TERMINAL_STEREOTYPE_BASELINE + headerOffset)
            val technology = technologyLabel(node)
            technology?.let {
                g2.color = activePalette[DesignerColorKey.TextMuted]
                g2.drawString(it, r.x + 12, r.y + TERMINAL_TECHNOLOGY_BASELINE + headerOffset)
            }
            if (node.isType) {
                g2.color = activePalette[DesignerColorKey.TypeStroke]
                val firstBaseline = TERMINAL_TYPE_FIELD_BASELINE +
                    if (technology == null) 0 else TERMINAL_TEXT_LINE_HEIGHT
                typeFieldLabels(node).forEachIndexed { index, label ->
                    g2.drawString(label, r.x + 12, r.y + firstBaseline + index * TERMINAL_TEXT_LINE_HEIGHT)
                }
            }
        }
        drawCompositeToggle(g2, node)
        drawDiagnosticBadge(g2, node)
        g2.stroke = previousStroke
        g2.font = previousFont
    }

    private fun nodeDisplayName(node: Node): String {
        val name = node.name.trim()
        val detail = node.nameDetail.trim()
        if (detail.isBlank()) return name
        val renderedDetail = if (isOverrideNode(node)) {
            "($detail)"
        } else {
            detail
        }
        return listOf(name, renderedDetail).filter(String::isNotBlank).joinToString(" ")
    }

    private fun drawNodeName(g2: Graphics2D, node: Node, x: Int, baseline: Int) {
        val name = node.name
        val detail = node.nameDetail.trim()
        g2.color = activePalette[DesignerColorKey.TextPrimary]
        g2.drawString(name, x, baseline)
        if (detail.isBlank()) return
        val renderedDetail = if (isOverrideNode(node)) {
            "($detail)"
        } else {
            detail
        }
        val detailX = x + if (name.isBlank()) 0 else g2.fontMetrics.stringWidth("$name ")
        g2.color = if (isOverrideNode(node)) {
            activePalette[DesignerColorKey.OverrideDetailText]
        } else {
            activePalette[DesignerColorKey.TextMuted]
        }
        g2.drawString(renderedDetail, detailX, baseline)
    }

    private fun isOverrideNode(node: Node): Boolean =
        nodeStereotype(node) in setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile)

    private fun drawDiagnosticBadge(g2: Graphics2D, node: Node) {
        if (node.diagnostics.isEmpty()) return
        val bounds = node.layout.rect()
        val size = 16
        val x = bounds.x + bounds.width - size - 6
        val y = bounds.y + 6
        val previousColor = g2.color
        val previousFont = g2.font
        g2.color = Color(0xd94b4b)
        g2.fillOval(x, y, size, size)
        g2.color = Color.WHITE
        g2.font = designerFont.deriveFont(Font.BOLD, 11f)
        val metrics = g2.fontMetrics
        g2.drawString("!", x + (size - metrics.stringWidth("!")) / 2, y + (size - metrics.height) / 2 + metrics.ascent)
        g2.color = previousColor
        g2.font = previousFont
    }

    private fun drawCompositeToggle(g2: Graphics2D, node: Node) {
        val label = if (node.layout.isExpanded) "-" else "+"
        val rect = compositeToggleRect(node,label) ?: return
        val selected = node.id in selection
        val stroke = if (selected) activePalette[DesignerColorKey.Selection] else activePalette[DesignerColorKey.TextMuted]
        val previousColor = g2.color
        val previousStroke = g2.stroke
        val previousFont = g2.font
        g2.color = activePalette[DesignerColorKey.PortFill]
        g2.fillRect(rect.x, rect.y, rect.width, rect.height)
        g2.color = stroke
        g2.stroke = BasicStroke(1f)
        g2.drawRect(rect.x, rect.y, rect.width, rect.height)
        g2.font = g2.font.deriveFont(9f)
        val metrics = g2.fontMetrics
        val textX = rect.x + (rect.width - metrics.stringWidth(label)) / 2
        val textY = rect.y + (rect.height - metrics.height) / 2 + metrics.ascent
        g2.drawString(label, textX, textY)
        g2.color = previousColor
        g2.stroke = previousStroke
        g2.font = previousFont
    }

    private fun svgCompositeToggle(svg: StringBuilder, node: Node) {
        // val rect = compositeToggleRect(node) ?: return
        // val stroke = if (node.id in selection) "#3366cc" else "#666666"
        // svgRect(svg, rect, fill = "#ffffff", stroke = stroke, strokeWidth = 1.0)
        // svgText(svg, if (node.layout.isExpanded) "min" else "max", rect.x + 7, rect.y + 12, 9, stroke)
    }

    private fun drawLink(g2: Graphics2D, node: Node) {
        node.link ?: return
        val route = cachedRoute(node.id) ?: routeLink(node) ?: return
        val previousStroke = g2.stroke
        val previousFont = g2.font
        val selected = node.id in selection
        val stereotype = LinkClassifier.classify(repository.getDocument(), node)
        val color = linkColor(stereotype, selected)
        g2.color = color
        g2.stroke = linkStroke(stereotype, selected, isBackflow(route))
        route.points.zipWithNext().forEach { (a, b) -> g2.drawLine(a.x, a.y, b.x, b.y) }
        drawArrowAlongRoute(g2, route.points)
        g2.color = color
        drawPortMarker(g2, route.source, route.sourceDirection, outgoing = true)
        drawPortMarker(g2, route.target, route.targetDirection, outgoing = false)
        compositeBoundaryIntersections(node, route.points).forEach { point ->
            val (dx, dy) = routeDirectionAt(route.points, point)
            drawBoundaryPierceMarker(g2, point, dx, dy)
        }
        drawEndpointLinkLabels(g2, node, route, color)
        g2.font = previousFont
        g2.stroke = previousStroke
    }

    private fun cachedRoute(id: NodeId): LinkRoute? =
        routeCache[id]

    private fun linkLabel(node: Node): String {
        val typeName = repository.getDocument().linkTypeDisplayName(node)
        return if (typeName.isBlank()) node.name else "${node.name}:$typeName"
    }

    private fun linkLabelParts(node: Node): Pair<String, String?> {
        val typeName = repository.getDocument().linkTypeDisplayName(node).trim()
        return node.name to typeName.takeIf { it.isNotEmpty() }
    }

    private fun visibleNodes(scopeIds: Set<NodeId>? = null): List<Node> {
        val document = repository.getDocument()
        return document.nodes.values.filter { node ->
            !node.isLink &&
                node.id != document.rootNodeId &&
                (scopeIds == null || node.id in scopeIds) &&
                isVisibleInCanvas(node)
        }
    }

    private fun orderedVisibleNodes(scopeIds: Set<NodeId>? = null): List<Node> =
        visibleNodes(scopeIds).sortedWith(
            compareBy<Node> { depthOf(it) }
                .thenBy { it.children.isEmpty() }
                .thenBy { it.layout.y }
                .thenBy { it.layout.x }
                .thenBy { it.id.value },
        )

    private fun visibleLinks(scopeIds: Set<NodeId>? = null): List<Node> {
        val document = repository.getDocument()
        return document.nodes.values.filter { linkNode ->
            (scopeIds == null || linkNode.id in scopeIds) && isVisibleLink(linkNode)
        }
    }

    private fun isVisibleLink(linkNode: Node): Boolean =
        linkNode.isLink &&
            isVisibleInCanvas(linkNode) &&
            linkEndpointsVisible(linkNode)

    private fun isVisibleInCanvas(node: Node): Boolean {
        val document = repository.getDocument()
        var current = node.parentId?.let(document.nodes::get)
        while (current != null && current.id != document.rootNodeId) {
            if (!current.layout.isExpanded) return false
            current = current.parentId?.let(document.nodes::get)
        }
        return true
    }

    private fun linkEndpointsVisible(linkNode: Node): Boolean {
        val link = linkNode.link ?: return false
        val document = repository.getDocument()
        val source = document.nodes[link.sourceNodeId] ?: return false
        val target = document.nodes[link.targetNodeId] ?: return false
        return isVisibleInCanvas(source) && isVisibleInCanvas(target)
    }

    private fun hitCompositeToggle(point: Point): NodeId? =
        visibleNodes().firstOrNull { node ->
            node.isComposite && compositeToggleRect(node,"   ")?.contains(point) == true
        }?.id

    private fun compositeToggleRect(node: Node,txt:String): Rectangle? {
        if (!node.isComposite || node.id == repository.getDocument().rootNodeId) return null
        val r = node.layout.rect()
        val width = 4+txt.length*10
        val height = 16
        return Rectangle(r.x + 8, r.y + 8, width, height)
    }

    private fun toggleCompositeExpansion(nodeId: NodeId) {
        val node = repository.getNode(nodeId) ?: return
        if (!node.isComposite) return
        node.layout.isExpanded = !node.layout.isExpanded
        node.layout.width = if (node.layout.isExpanded) node.layout.openWidth else node.layout.closedWidth
        node.layout.height = if (node.layout.isExpanded) node.layout.openHeight else node.layout.closedHeight
        if (!node.layout.isExpanded) {
            selection.retainAll { selectedId ->
                selectedId == node.id || !isDescendantOf(selectedId, node.id)
            }
        }
        invalidateRoutesFor(listOf(node.id) + node.children)
        repository.markDirty()
        refreshAll()
    }

    private fun isDescendantOf(nodeId: NodeId, ancestorId: NodeId): Boolean {
        var current = repository.getNode(nodeId)?.parentId
        while (current != null) {
            if (current == ancestorId) return true
            current = repository.getNode(current)?.parentId
        }
        return false
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun drawPortMarker(g2: Graphics2D, point: Point, side: Int, outgoing: Boolean) {
        val previousStroke = g2.stroke
        g2.stroke = BasicStroke(1f)
        val (shape,fill) = portCapShape(point, side, outgoing)
        if(fill) {
            g2.fill(shape)
        }
        g2.draw(shape)
        g2.stroke = previousStroke
    }

    private fun drawBoundaryPierceMarker(g2: Graphics2D, point: Point, dx: Int, dy: Int) {
        val color = g2.color
        drawDirectionalArrow(g2, boundaryArrowCenter(point, dx, dy), dx, dy)
        g2.color = activePalette[DesignerColorKey.PortFill]
        g2.fillOval(point.x - 5, point.y - 5, 10, 10)
        g2.color = color
        g2.drawOval(point.x - 5, point.y - 5, 10, 10)
    }

    private fun drawArrowAlongRoute(g2: Graphics2D, points: List<Point>) {
        val total = points.zipWithNext().sumOf { (a, b) -> a.distance(b) }
        if (total <= 0.0) return
        val target = total * 0.75
        var travelled = 0.0
        points.zipWithNext().forEach { (a, b) ->
            val segment = a.distance(b)
            if (segment > 0.0 && travelled + segment >= target) {
                val ratio = ((target - travelled) / segment).coerceIn(0.0, 1.0)
                val x = (a.x + (b.x - a.x) * ratio).toInt()
                val y = (a.y + (b.y - a.y) * ratio).toInt()
                drawDirectionalArrow(g2, Point(x, y), b.x - a.x, b.y - a.y)
                return
            }
            travelled += segment
        }
    }

    private fun drawDirectionalArrow(g2: Graphics2D, point: Point, dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val length = hypot(dx.toDouble(), dy.toDouble())
        val ux = dx / length
        val uy = dy / length
        val size = 10.0
        val wing = 5.0
        val tipX = point.x + ux * size
        val tipY = point.y + uy * size
        val baseX = point.x - ux * size
        val baseY = point.y - uy * size
        val px = -uy
        val py = ux
        g2.fillPolygon(
            intArrayOf(tipX.toInt(), (baseX + px * wing).toInt(), (baseX - px * wing).toInt()),
            intArrayOf(tipY.toInt(), (baseY + py * wing).toInt(), (baseY - py * wing).toInt()),
            3,
        )
    }

    private fun portCapShape(point: Point, side: Int, outgoing: Boolean): Pair<Path2D.Double,Boolean> {
        val (points,fill) = portCapPoints(point, side, outgoing)
        return Pair(Path2D.Double().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { lineTo(it.first, it.second) }
            closePath()
        }, fill)
    }

    private fun portCapPoints(point: Point, side: Int, outgoing: Boolean): Pair<List<Pair<Double, Double>>, Boolean> {
        val scale = 0.6
        val template = when {
            outgoing && side < 0 -> PortTemplate(
                points = listOf(
                    0.0 to 0.0,
                    -25.0 to 0.0,
                    -33.0 to -8.0,
                    -25.0 to -16.0,
                    0.0 to -16.0,
                ),
                connection = -33.0 to -8.0,
                fillRendering = false,
            )
            outgoing && side >= 0 -> PortTemplate(
                points = listOf(
                    0.0 to 0.0,
                    25.0 to 0.0,
                    33.0 to 8.0,
                    25.0 to 16.0,
                    0.0 to 16.0,
                ),
                connection = 33.0 to 8.0,
                fillRendering = false,
            )
            !outgoing && side < 0 -> PortTemplate(
                points = listOf(
                    0.0 to 0.0,
                    8.0 to 8.0,
                    0.0 to 16.0,
                    33.0 to 16.0,
                    33.0 to 0.0,
                ),
                connection = 33.0 to 8.0,
                fillRendering = true,
            )
            else -> PortTemplate(
                points = listOf(
                    0.0 to 0.0,
                    -8.0 to -8.0,
                    0.0 to -16.0,
                    -33.0 to -16.0,
                    -33.0 to 0.0,
                ),
                connection = -33.0 to -8.0,
                fillRendering = true,
            )
        }
        val (connX, connY) = template.connection
        val fillRendering = template.fillRendering
        return Pair(template.points.map { (dx, dy) ->
            point.x + (dx - connX) * scale to point.y + (dy - connY) * scale
        }, fillRendering)
    }

    private data class PortTemplate(
        val points: List<Pair<Double, Double>>,
        val connection: Pair<Double, Double>,
        val fillRendering: Boolean,
    ){
    }

    private fun drawDependencyAnnotations(g2: Graphics2D, links: List<Node>) {
        links.groupBy { it.link?.sourceNodeId }.forEach { (sourceId, sourceLinks) ->
            val source = sourceId?.let(repository::getNode) ?: return@forEach
            drawLibraryDependents(g2, source, sourceLinks)
        }
        links.groupBy { it.link?.targetNodeId }.forEach { (targetId, targetLinks) ->
            val target = targetId?.let(repository::getNode) ?: return@forEach
            drawDependencyList(g2, target, targetLinks)
        }
    }

    private fun drawLibraryDependents(g2: Graphics2D, library: Node, links: List<Node>) {
        val r = library.layout.rect()
        val previousStroke = g2.stroke
        val previousFont = g2.font
        g2.font = designerFont.deriveFont(DEPENDENCY_ANNOTATION_FONT_SIZE)
        links.forEachIndexed { index, linkNode ->
            val link = linkNode.link ?: return@forEachIndexed
            repository.getNode(link.targetNodeId) ?: return@forEachIndexed
            val selected = linkNode.id in selection
            val label = dependencyInjectionLabel(linkNode, library)
            val labelWidth = max(
                DEPENDENCY_SOURCE_MIN_WIDTH,
                g2.fontMetrics.stringWidth(label) + DEPENDENCY_LABEL_HORIZONTAL_PADDING,
            )
            val labelHeight = 24
            val y = r.y + 10 + index * (labelHeight + 8)
            val x = r.x + r.width + DEPENDENCY_SOURCE_GAP
            val anchor = Point(r.x + r.width, y + labelHeight / 2)
            val color = annotationColor(linkNode, selected)
            g2.color = color
            g2.stroke = BasicStroke(if (selected) 2.4f else 1.5f)
            g2.drawLine(anchor.x, anchor.y, x, anchor.y)
            g2.fillOval(anchor.x - 4, anchor.y - 4, 8, 8)
            g2.color = activePalette[DesignerColorKey.AnnotationFill]
            g2.fillRect(x, y, labelWidth, labelHeight)
            g2.color = color
            g2.drawRect(x, y, labelWidth, labelHeight)
            g2.drawString(label, x + 8, y + 17)
        }
        g2.stroke = previousStroke
        g2.font = previousFont
    }

    private fun drawDependencyList(g2: Graphics2D, dependent: Node, links: List<Node>) {
        val r = dependent.layout.rect()
        val previousStroke = g2.stroke
        val previousFont = g2.font
        val rows = links.mapNotNull { linkNode ->
            val link = linkNode.link ?: return@mapNotNull null
            val source = repository.getNode(link.sourceNodeId) ?: return@mapNotNull null
            linkNode to dependencyInjectionLabel(linkNode, source)
        }
        if (rows.isEmpty()) return
        g2.font = designerFont.deriveFont(DEPENDENCY_ANNOTATION_FONT_SIZE)

        val x = r.x + 36
        val rowHeight = 24
        val startY = r.y - rows.size * rowHeight - 16
        val stemBottom = r.y
        val selected = rows.any { it.first.id in selection }
        val stemColor = if (selected) activePalette[DesignerColorKey.Selection] else annotationColor(rows.first().first, false)
        g2.color = stemColor
        g2.stroke = BasicStroke(if (selected) 2.2f else 1.5f)
        g2.drawLine(x, startY, x, stemBottom)
        rows.forEachIndexed { index, (linkNode, label) ->
            val y = startY + index * rowHeight + 8
            val width = max(
                DEPENDENCY_TARGET_MIN_WIDTH,
                ((g2.fontMetrics.stringWidth(label) + DEPENDENCY_LABEL_HORIZONTAL_PADDING) *
                    DEPENDENCY_TARGET_RULE_RATIO).roundToInt(),
            )
            val color = annotationColor(linkNode, linkNode.id in selection)
            g2.color = color
            g2.fillOval(x - 5, y - 5, 10, 10)
            g2.drawLine(x, y, x + width, y)
            if (linkNode.id in selection) {
                g2.stroke = BasicStroke(2.4f)
                g2.drawLine(x, y + 2, x + width, y + 2)
                g2.stroke = BasicStroke(1.5f)
            }
            g2.drawString(label, x + 10, y - 4)
        }
        g2.stroke = previousStroke
        g2.font = previousFont
    }

    private fun drawTypeUsageAnnotations(g2: Graphics2D, links: List<Node>, scopeIds: Set<NodeId>?) {
        links.groupBy { it.link?.typeDefinitionId?.takeIf(String::isNotBlank) }.forEach { (typeId, usages) ->
            val type = typeId?.let(::NodeId)?.let(repository::getNode)?.takeIf(Node::isType) ?: return@forEach
            if (scopeIds != null && type.id !in scopeIds) return@forEach
            val r = type.layout.rect()
            val previousStroke = g2.stroke
            val previousFont = g2.font
            g2.font = designerFont.deriveFont(12f)
            usages.forEachIndexed { index, link ->
                val label = link.name
                val width = max(110, monospaceTextWidth(label, 8, 24))
                val y = r.y + 10 + index * 32
                val x = r.x + r.width + 34
                val anchorY = y + 12
                val selected = link.id in selection
                val color = if (selected) activePalette[DesignerColorKey.Selection] else activePalette[DesignerColorKey.TypeStroke]
                g2.color = color
                g2.stroke = BasicStroke(if (selected) 2.4f else 1.5f)
                g2.drawLine(r.x + r.width, anchorY, x, anchorY)
                g2.fillOval(r.x + r.width - 4, anchorY - 4, 8, 8)
                g2.color = activePalette[DesignerColorKey.TypeFill]
                g2.fillRect(x, y, width, 24)
                g2.color = color
                g2.drawRect(x, y, width, 24)
                g2.drawString(label, x + 10, y + 17)
            }
            g2.stroke = previousStroke
            g2.font = previousFont
        }
    }

    private fun isDependencyAnnotation(linkNode: Node): Boolean = when (LinkClassifier.classify(repository.getDocument(), linkNode)) {
            LinkStereotype.UsageImport,
            LinkStereotype.DependencyInjection,
            LinkStereotype.SourceCapability,
            LinkStereotype.RunnableCapability -> true
            else -> false
        }

    private fun annotationColor(linkNode: Node, selected: Boolean): Color =
        if (selected) activePalette[DesignerColorKey.Selection]
        else activePalette.colorForLink(LinkClassifier.classify(repository.getDocument(), linkNode))

    private fun routeLink(linkNode: Node): LinkRoute? {
        if (!activeRouteLinks.add(linkNode.id)) return null
        return try {
            val anchors = linkAnchors(linkNode) ?: return null
            routeCache[linkNode.id]
                ?.takeIf { it.source == anchors.source.point && it.target == anchors.target.point }
                ?.let { return it }
            routedRoute(linkNode, anchors, emptyList())
        } finally {
            activeRouteLinks.remove(linkNode.id)
        }
    }

    private fun linkAnchors(linkNode: Node): LinkAnchors? {
        val link = linkNode.link ?: return null
        val sourceNode = repository.getNode(link.sourceNodeId) ?: return null
        val targetNode = repository.getNode(link.targetNodeId) ?: return null
        val source = portAnchor(sourceNode, linkNode, outgoing = true) ?: return null
        val target = portAnchor(targetNode, linkNode, outgoing = false) ?: return null
        return LinkAnchors(source, target, sourceNode.id, targetNode.id)
    }

    private fun rebuildRouteCache() {
        val routedSegments = mutableListOf<RouteSegment>()
        val nextCache = mutableMapOf<NodeId, LinkRoute>()
        visibleLinks()
            .filterNot(::isDependencyAnnotation)
            .sortedWith(compareBy<Node> { it.layout.y }.thenBy { it.layout.x }.thenBy { it.id.value })
            .forEach { linkNode ->
                val anchors = linkAnchors(linkNode) ?: return@forEach
                val route = routedRoute(linkNode, anchors, routedSegments)
                nextCache[linkNode.id] = route
                route.points.zipWithNext().forEach { (a, b) ->
                    routedSegments += RouteSegment(a, b, linkNode.id)
                }
            }
        routeCache.clear()
        routeCache.putAll(nextCache)
        invalidateRenderCache()
        repaint()
        if (zoomExtentsPending) {
            SwingUtilities.invokeLater(::zoomExtents)
        }
    }

    private fun routedRoute(linkNode: Node, anchors: LinkAnchors, routedSegments: List<RouteSegment>): LinkRoute {
        val source = anchors.source
        val target = anchors.target
        val sourceStub = Point(source.point.x + PORT_STUB_LENGTH * source.xDirection, source.point.y)
        val targetStub = Point(target.point.x + PORT_STUB_LENGTH * target.xDirection, target.point.y)
        val obstacles = routeObstacles(linkNode, anchors.sourceNodeId, anchors.targetNodeId)
        val container = routingContainer(linkNode)
        val candidates = routeCandidates(sourceStub, targetStub)
            .map { compact(chamferOrthogonalTurns(it)) }
            .filter { it.size >= 2 }
            .filter(::isOctilinearPath)
            .filter { points -> container == null || points.all { container.containsInclusive(it) } }

        val best = candidates.minWithOrNull(compareBy<List<Point>> {
            routeCost(it, obstacles, routedSegments, linkNode.id)
        }.thenBy { routeLength(it) }) ?: listOf(sourceStub, targetStub)

        return LinkRoute(
            source.point,
            target.point,
            source.xDirection,
            target.xDirection,
            compact(listOf(source.point, sourceStub) + best + listOf(targetStub, target.point)),
        )
    }

    private fun routeCandidates(start: Point, end: Point): List<List<Point>> {
        val candidates = mutableListOf<List<Point>>()
        candidates += listOf(start, end)

        routingLanes(start.x, end.x).forEach { laneX ->
            candidates += listOf(start, Point(laneX, start.y), Point(laneX, end.y), end)
        }
        routingLanes(start.y, end.y).forEach { laneY ->
            candidates += listOf(start, Point(start.x, laneY), Point(end.x, laneY), end)
        }
        val dx = end.x - start.x
        val dy = end.y - start.y
        val diagonal = min(abs(dx), abs(dy))
        if (diagonal > ROUTING_STEP) {
            val sx = dx.sign()
            val sy = dy.sign()
            val firstDiagonal = Point(start.x + sx * diagonal, start.y + sy * diagonal)
            val lastDiagonal = Point(end.x - sx * diagonal, end.y - sy * diagonal)
            candidates += listOf(start, firstDiagonal, end)
            candidates += listOf(start, lastDiagonal, end)
        }
        return candidates
    }

    private fun routingLanes(start: Int, end: Int): List<Int> {
        val midpoint = ((start + end) / 2.0).roundToInt()
        return (-ROUTING_LANE_SPAN..ROUTING_LANE_SPAN)
            .map { midpoint + it * ROUTING_STEP }
            .plus(listOf(start + ROUTING_STEP, start - ROUTING_STEP, end + ROUTING_STEP, end - ROUTING_STEP))
            .distinct()
            .sortedBy { abs(it - midpoint) }
    }

    private fun chamferOrthogonalTurns(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        val result = mutableListOf(points.first())
        for (index in 1 until points.lastIndex) {
            val previous = points[index - 1]
            val current = points[index]
            val next = points[index + 1]
            val incomingHorizontal = previous.y == current.y
            val incomingVertical = previous.x == current.x
            val outgoingHorizontal = current.y == next.y
            val outgoingVertical = current.x == next.x
            if ((incomingHorizontal && outgoingVertical) || (incomingVertical && outgoingHorizontal)) {
                val incomingLength = current.distance(previous)
                val outgoingLength = current.distance(next)
                val chamfer = min(ROUTING_CHAMFER.toDouble(), min(incomingLength, outgoingLength) / 2.0).roundToInt()
                if (chamfer >= 4) {
                    val before = Point(
                        current.x - (current.x - previous.x).sign() * chamfer,
                        current.y - (current.y - previous.y).sign() * chamfer,
                    )
                    val after = Point(
                        current.x + (next.x - current.x).sign() * chamfer,
                        current.y + (next.y - current.y).sign() * chamfer,
                    )
                    result += before
                    result += after
                } else {
                    result += current
                }
            } else {
                result += current
            }
        }
        result += points.last()
        return result
    }

    private fun routeCost(points: List<Point>, obstacles: List<Rectangle>, routedSegments: List<RouteSegment>, linkId: NodeId): Double {
        var cost = routeLength(points)
        cost += max(0, points.size - 2) * 18.0
        points.zipWithNext().forEach { (a, b) ->
            obstacles.forEach { obstacle ->
                if (obstacle.intersectsLine(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble())) {
                    cost += 75_000.0
                }
            }
            routedSegments.filter { it.linkId != linkId }.forEach { segment ->
                if (segmentsCross(a, b, segment.a, segment.b)) cost += 2_500.0
            }
        }
        return cost
    }

    private fun routeLength(points: List<Point>): Double =
        points.zipWithNext().sumOf { (a, b) -> a.distance(b) }

    private fun LinkRoute.bounds(): Rectangle {
        if (points.isEmpty()) return Rectangle(source)
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        return Rectangle(minX, minY, (maxX - minX).coerceAtLeast(1), (maxY - minY).coerceAtLeast(1)).apply {
            grow(PORT_STUB_LENGTH, PORT_SPACING)
        }
    }

    private fun isOctilinearPath(points: List<Point>): Boolean =
        points.zipWithNext().all { (a, b) ->
            val dx = abs(b.x - a.x)
            val dy = abs(b.y - a.y)
            dx == 0 || dy == 0 || dx == dy
        }

    private fun routeObstacles(linkNode: Node, sourceId: NodeId, targetId: NodeId): List<Rectangle> {
        val ignoredIds = mutableSetOf(sourceId, targetId)
        ignoredIds += linkNode.link?.compositeBoundaryIds.orEmpty()
        var ancestorId = linkNode.parentId
        while (ancestorId != null) {
            ignoredIds += ancestorId
            ancestorId = repository.getNode(ancestorId)?.parentId
        }
        return visibleNodes()
            .filter { it.id !in ignoredIds }
            .map {
                val r = it.layout.rect()
                Rectangle(
                    r.x - ROUTING_OBSTACLE_PADDING,
                    r.y - ROUTING_OBSTACLE_PADDING,
                    r.width + ROUTING_OBSTACLE_PADDING * 2,
                    r.height + ROUTING_OBSTACLE_PADDING * 2,
                )
            }
    }

    private fun compositeBoundaryIntersections(linkNode: Node, points: List<Point>): List<Point> {
        val document = repository.getDocument()
        return linkNode.link?.compositeBoundaryIds.orEmpty()
            .mapNotNull(document.nodes::get)
            .flatMap { boundary ->
                val rect = boundary.layout.rect()
                val topLeft = Point(rect.x, rect.y)
                val topRight = Point(rect.x + rect.width, rect.y)
                val bottomRight = Point(rect.x + rect.width, rect.y + rect.height)
                val bottomLeft = Point(rect.x, rect.y + rect.height)
                val edges = listOf(
                    topLeft to topRight,
                    topRight to bottomRight,
                    bottomRight to bottomLeft,
                    bottomLeft to topLeft,
                )
                points.zipWithNext().flatMap { (start, end) ->
                    edges.mapNotNull { (edgeStart, edgeEnd) ->
                        segmentIntersectionPoint(start, end, edgeStart, edgeEnd)
                    }
                }
            }
            .distinctBy { it.x to it.y }
    }

    private fun routeDirectionAt(points: List<Point>, point: Point): Pair<Int, Int> {
        val segment = points.zipWithNext().firstOrNull { (start, end) ->
            Line2D.ptSegDist(
                start.x.toDouble(),
                start.y.toDouble(),
                end.x.toDouble(),
                end.y.toDouble(),
                point.x.toDouble(),
                point.y.toDouble(),
            ) <= 1.0
        } ?: return 1 to 0
        return (segment.second.x - segment.first.x) to (segment.second.y - segment.first.y)
    }

    private fun boundaryArrowCenter(point: Point, dx: Int, dy: Int): Point {
        val length = hypot(dx.toDouble(), dy.toDouble())
        if (length <= 0.0) return point
        val distanceToCenter = 15.0
        return Point(
            (point.x - dx / length * distanceToCenter).roundToInt(),
            (point.y - dy / length * distanceToCenter).roundToInt(),
        )
    }

    private fun segmentIntersectionPoint(a: Point, b: Point, c: Point, d: Point): Point? {
        val denominator = (a.x - b.x).toDouble() * (c.y - d.y) -
            (a.y - b.y).toDouble() * (c.x - d.x)
        if (abs(denominator) < 0.000001) return null
        val firstDeterminant = a.x.toDouble() * b.y - a.y.toDouble() * b.x
        val secondDeterminant = c.x.toDouble() * d.y - c.y.toDouble() * d.x
        val x = (firstDeterminant * (c.x - d.x) - (a.x - b.x) * secondDeterminant) / denominator
        val y = (firstDeterminant * (c.y - d.y) - (a.y - b.y) * secondDeterminant) / denominator
        val epsilon = 0.001
        if (x < min(a.x, b.x) - epsilon || x > max(a.x, b.x) + epsilon ||
            y < min(a.y, b.y) - epsilon || y > max(a.y, b.y) + epsilon ||
            x < min(c.x, d.x) - epsilon || x > max(c.x, d.x) + epsilon ||
            y < min(c.y, d.y) - epsilon || y > max(c.y, d.y) + epsilon
        ) return null
        return Point(x.roundToInt(), y.roundToInt())
    }

    private fun routingContainer(linkNode: Node): Rectangle? {
        val document = repository.getDocument()
        val parent = linkNode.parentId?.let(document.nodes::get) ?: return null
        return parent
            .takeIf { it.id != document.rootNodeId && it.isComposite && it.layout.isExpanded }
            ?.layout
            ?.rect()
    }

    private fun Rectangle.containsInclusive(point: Point): Boolean =
        point.x >= x && point.x <= x + width && point.y >= y && point.y <= y + height

    private fun segmentsCross(a: Point, b: Point, c: Point, d: Point): Boolean {
        if (a == c || a == d || b == c || b == d) return false
        return Line2D.linesIntersect(
            a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(),
            c.x.toDouble(), c.y.toDouble(), d.x.toDouble(), d.y.toDouble(),
        )
    }

    private fun scheduleReroute() {
        rerouteTimer.restart()
    }

    private fun invalidateRoutesFor(nodeIds: Collection<NodeId>) {
        if (nodeIds.isEmpty()) return
        invalidateRenderCache()
        val document = repository.getDocument()
        val affectedNodes = linkedSetOf<NodeId>().apply {
            addAll(nodeIds)
            nodeIds.forEach { nodeId ->
                val node = document.nodes[nodeId] ?: return@forEach
                (node.incomingLinks + node.outgoingLinks).mapNotNull(document.nodes::get).forEach { linkNode ->
                    val link = linkNode.link ?: return@forEach
                    add(link.sourceNodeId)
                    add(link.targetNodeId)
                }
            }
        }
        document.nodes.values
            .filter { linkNode ->
                val link = linkNode.link ?: return@filter false
                link.sourceNodeId in affectedNodes || link.targetNodeId in affectedNodes
            }
            .forEach { routeCache.remove(it.id) }
        scheduleReroute()
    }

    private fun portAnchor(node: Node, linkNode: Node, outgoing: Boolean): PortAnchor? {
        linkNode.link ?: return null
        if (node.isLink) return null
        val r = node.layout.rect()
        val side = linkSide(node, linkNode, outgoing)
        val sorted = normalLinksOnSide(node, side)
            .sortedWith(compareBy<Node> { portOrderValue(node, it, side) }.thenBy { it.id.value })
        val index = sorted.indexOfFirst { it.id == linkNode.id }.takeIf { it >= 0 } ?: 0
        val x = when {
            outgoing && side > 0 -> r.x + r.width + PORT_OUTSIDE_OFFSET
            outgoing && side < 0 -> r.x - PORT_OUTSIDE_OFFSET
            side > 0 -> r.x + r.width
            else -> r.x
        }
        val y = r.y + portTopSpacing(node) + index * PORT_SPACING
        return PortAnchor(Point(x, y), side)
    }

    private fun routeDirectionNear(points: List<Point>, point: Point): Int {
        val segment = points.zipWithNext().minByOrNull { (a, b) -> distanceToSegment(point, a, b) }
            ?: return 1
        val dx = segment.second.x - segment.first.x
        return when {
            dx > 0 -> 1
            dx < 0 -> -1
            else -> 1
        }
    }

    private fun normalLinksOnSide(node: Node, side: Int): List<Node> {
        val document = repository.getDocument()
        val ids = node.outgoingLinks + node.incomingLinks
        return ids.distinct().mapNotNull(document.nodes::get)
            .filterNot(::isDependencyAnnotation)
            .filter { linkNode ->
                isVisibleLink(linkNode) && run {
                    val link = linkNode.link ?: return@run false
                    val outgoing = link.sourceNodeId == node.id
                    linkSide(node, linkNode, outgoing) == side
                }
            }
    }

    private fun linkSide(node: Node, linkNode: Node, outgoing: Boolean): Int {
        val center = node.layout.center()
        val otherPoint = linkedEndpointReferencePoint(linkNode, outgoing, center)
        return if (otherPoint.x >= center.x) 1 else -1
    }

    private fun portOrderValue(node: Node, linkNode: Node, side: Int): Double {
        val center = node.layout.center()
        val link = linkNode.link ?: return 0.0
        val outgoing = link.sourceNodeId == node.id
        val otherPoint = linkedEndpointReferencePoint(linkNode, outgoing, center)
        val sideRelativeX = ((otherPoint.x - center.x) * side).toDouble().coerceAtLeast(1.0)
        return atan2((otherPoint.y - center.y).toDouble(), sideRelativeX)
    }

    private fun linkedEndpointReferencePoint(linkNode: Node, outgoingFromNode: Boolean, fallback: Point): Point {
        val link = linkNode.link ?: return fallback
        val otherId = if (outgoingFromNode) link.targetNodeId else link.sourceNodeId
        val other = repository.getNode(otherId) ?: return fallback
        return other.layout.center()
    }

    private fun compact(points: List<Point>): List<Point> =
        points.fold(mutableListOf()) { acc, point ->
            if (acc.lastOrNull() != point) acc += point
            acc
        }

    private fun drawEndpointLinkLabels(g2: Graphics2D, node: Node, route: LinkRoute, color: Color) {
        val (name, typeName) = linkLabelParts(node)
        if (name.isBlank() && typeName == null) return
        g2.font = g2.font.deriveFont(10f)
        drawEndpointLinkLabel(g2, name, typeName, route.source, route.sourceDirection, color)
        drawEndpointLinkLabel(g2, name, typeName, route.target, route.targetDirection, color)
    }

    private fun drawEndpointLinkLabel(
        g2: Graphics2D,
        name: String,
        typeName: String?,
        anchor: Point,
        side: Int,
        color: Color,
    ) {
        val metrics = g2.fontMetrics
        val separator = if (typeName == null) "" else ":"
        val width = metrics.stringWidth(name + separator + typeName.orEmpty())
        val spacing = 8
        val x = if (side < 0) anchor.x - width - spacing else anchor.x + spacing
        val y = anchor.y - metrics.height - 4
        drawColoredLinkLabel(g2, name, typeName, x, y + metrics.ascent, color)
    }

    private fun drawColoredLinkLabel(
        g2: Graphics2D,
        name: String,
        typeName: String?,
        x: Int,
        baseline: Int,
        color: Color,
    ) {
        var cursor = x
        g2.color = color
        g2.drawString(name, cursor, baseline)
        cursor += g2.fontMetrics.stringWidth(name)
        typeName?.let {
            g2.drawString(":", cursor, baseline)
            cursor += g2.fontMetrics.stringWidth(":")
            g2.color = activePalette[DesignerColorKey.TypeText]
            g2.drawString(it, cursor, baseline)
        }
    }

    private fun pointAlongRoute(points: List<Point>, fraction: Double): Point? {
        val total = points.zipWithNext().sumOf { (a, b) -> a.distance(b) }
        if (total <= 0.0) return null
        val target = total * fraction.coerceIn(0.0, 1.0)
        var travelled = 0.0
        points.zipWithNext().forEach { (a, b) ->
            val segment = a.distance(b)
            if (segment > 0.0 && travelled + segment >= target) {
                val ratio = ((target - travelled) / segment).coerceIn(0.0, 1.0)
                return Point(
                    (a.x + (b.x - a.x) * ratio).toInt(),
                    (a.y + (b.y - a.y) * ratio).toInt(),
                )
            }
            travelled += segment
        }
        return points.lastOrNull()
    }

    private fun Int.sign(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    private fun handleClicked(e: MouseEvent) {
        if (e.clickCount < 2 || !SwingUtilities.isLeftMouseButton(e)) return
        val point = modelPoint(e.point)
        if (hitCompositeToggle(point) != null) return
        val id = hitNode(point) ?: hitLink(point) ?: return
        onNodeDoubleClicked(id)
    }

    private fun isContextGesture(e: MouseEvent): Boolean =
        e.isPopupTrigger || SwingUtilities.isRightMouseButton(e)

    private fun showContextMenu(e: MouseEvent) {
        onContextMenu(this, e.x, e.y)
    }

    private fun handlePressed(e: MouseEvent) {
        if (SwingUtilities.isMiddleMouseButton(e)) {
            panDragStart = e.point
            return
        }
        val point = modelPoint(e.point)
        if (!e.isAltDown) hitCompositeToggle(point)?.let {
            toggleCompositeExpansion(it)
            return
        }
        dragStart = point
        moveDragReference = null
        selectionDragLeftToRight = true
        val pickSelectionEnabled = mode != CanvasMode.Select || !e.isAltDown
        val hit = if (pickSelectionEnabled) hitNode(point) else null
        val hitLink = if (pickSelectionEnabled) hitLink(point) else null
        when (mode) {
            CanvasMode.CreateNode -> {
                val parent = hit?.takeIf { candidate ->
                    repository.getNode(candidate)?.let { !it.isLink && !it.isType } == true
                }
                    ?: repository.getDocument().rootNodeId
                val node = repository.createNode(parent, "", NodeKind.Processor)
                repository.updateNodeLayout(
                    node.id,
                    NodeLayout(
                        x = point.x.toDouble(),
                        y = point.y.toDouble(),
                        width = 180.0,
                        height = TERMINAL_NODE_BASE_HEIGHT.toDouble(),
                        closedWidth = 180.0,
                        closedHeight = TERMINAL_NODE_BASE_HEIGHT.toDouble(),
                        openWidth = 180.0,
                        openHeight = TERMINAL_NODE_BASE_HEIGHT.toDouble(),
                        isExpanded = true,
                    ),
                )
                invalidateRoutesFor(listOf(node.id))
                refreshAll()
            }
            CanvasMode.CreateType -> {
                val parent = hit?.takeIf { candidate ->
                    repository.getNode(candidate)?.let { !it.isLink && !it.isType } == true
                } ?: repository.getDocument().rootNodeId
                val node = repository.createNode(parent, "", NodeKind.Type)
                repository.updateNodeLayout(
                    node.id,
                    NodeLayout(
                        x = point.x.toDouble(),
                        y = point.y.toDouble(),
                        width = 220.0,
                        height = TERMINAL_NODE_BASE_HEIGHT.toDouble(),
                        closedWidth = 220.0,
                        closedHeight = TERMINAL_NODE_BASE_HEIGHT.toDouble(),
                        openWidth = 220.0,
                        openHeight = TERMINAL_NODE_BASE_HEIGHT.toDouble(),
                        isExpanded = true,
                    ),
                )
                invalidateRoutesFor(listOf(node.id))
                refreshAll()
            }
            CanvasMode.CreateLink -> {
                val clickedType = hit?.let(repository::getNode)?.takeIf(Node::isType)
                if (linkSource?.let(repository::getNode)?.isType == true && hitLink != null) {
                    assignTypeToLink(linkSource!!, hitLink)
                    linkSource = null
                    onSelectionChanged()
                } else if (linkSource?.let(repository::getNode)?.isType == true) {
                    // A Type gesture only assigns an existing link; it never creates a data-flow edge.
                } else if (clickedType != null) {
                    linkSource = clickedType.id
                    selection.clear()
                    selection += clickedType.id
                    onSelectionChanged()
                } else {
                    val endpoint = hit?.takeIf { repository.getNode(it)?.isType != true }
                    if (endpoint == null) return
                    if (linkSource == null) {
                        linkSource = endpoint
                        selection.clear()
                        selection += endpoint
                    } else if (linkSource != endpoint) {
                        createLink(linkSource!!, endpoint)
                        linkSource = null
                    }
                    onSelectionChanged()
                }
            }
            CanvasMode.Select -> {
                val hitNode = hit?.let(repository::getNode)
                if (hitLink != null && hitNode?.isComposite == true) {
                    if (!e.isShiftDown) selection.clear()
                    selection += hitLink
                    dragAllowsReparent = false
                    onSelectionChanged()
                } else if (hit != null) {
                    if (hit !in selection) {
                        if (!e.isShiftDown) selection.clear()
                        selection += hit
                    }
                    dragAllowsReparent = e.isControlDown
                    hitNode?.let { moveDragReference = initialMoveReference(point, it.layout.rect()) }
                    onSelectionChanged()
                } else if (hitLink != null) {
                    if (!e.isShiftDown) selection.clear()
                    selection += hitLink
                    dragAllowsReparent = false
                    onSelectionChanged()
                } else {
                    selection.clear()
                    selectionRect = Rectangle(point)
                    selectionDragLeftToRight = true
                    dragAllowsReparent = false
                    onSelectionChanged()
                }
            }
        }
    }

    private fun handleDragged(e: MouseEvent) {
        panDragStart?.let { start ->
            panX += (e.point.x - start.x) / zoom
            panY += (e.point.y - start.y) / zoom
            panDragStart = e.point
            repaint()
            return
        }
        val point = modelPoint(e.point)
        if (selectionRect != null) {
            val start = dragStart ?: return
            selectionDragLeftToRight = point.x >= start.x
            selectionRect = Rectangle(min(start.x, point.x), min(start.y, point.y), kotlin.math.abs(point.x - start.x), kotlin.math.abs(point.y - start.y))
        } else if (selection.isNotEmpty() && mode == CanvasMode.Select) {
            val start = moveDragReference ?: dragStart ?: return
            val snappedPoint = snapMoveReferenceToGrid(point)
            val dx = snappedPoint.x - start.x
            val dy = snappedPoint.y - start.y
            val movedNodes = selectedMoveRoots()
            movedNodes.forEach { moved ->
                moveNodeAndDescendants(moved, dx.toDouble(), dy.toDouble())
            }
            invalidateRoutesFor(movedNodes.map { it.id })
            repository.markDirty()
            dragStart = point
            moveDragReference = snappedPoint
        }
        repaint()
    }

    private fun handleReleased(e: MouseEvent) {
        if (panDragStart != null) {
            panDragStart = null
            return
        }
        selectionRect?.let { rect ->
            selection.clear()
            val containsOnly = selectionDragLeftToRight
            visibleNodes()
                .filter { if (containsOnly) rect.contains(it.layout.rect()) else it.layout.rect().intersects(rect) }
                .forEach { selection += it.id }
            visibleLinks()
                .filter { linkInsideSelection(it, rect, containsOnly) }
                .forEach { selection += it.id }
            selectionRect = null
        }
        if (selection.isNotEmpty() && dragAllowsReparent) updateParentAfterDrag(modelPoint(e.point))
        dragAllowsReparent = false
        dragStart = null
        moveDragReference = null
        refreshAll()
    }

    private fun createLink(sourceId: NodeId, targetId: NodeId) {
        ensureDefaultPort(sourceId, PortDirection.Output, "out")
        ensureDefaultPort(targetId, PortDirection.Input, "in")
        val source = repository.requireNode(sourceId)
        val target = repository.requireNode(targetId)
        val link = repository.createLink(null, "${source.name} -> ${target.name}", sourceId, "out", targetId, "in")
        link.link?.transportKind = LinkTransportKinds.Default
        selection.clear()
        selection += link.id
        routeCache.remove(link.id)
        invalidateRoutesFor(listOf(sourceId, targetId))
        repository.markDirty()
    }

    private fun assignTypeToLink(typeId: NodeId, linkId: NodeId) {
        val type = repository.requireNode(typeId)
        val linkNode = repository.requireNode(linkId)
        if (!type.isType || !linkNode.isLink) return
        linkNode.link?.copy()?.let { link ->
            link.typeDefinitionId = type.id.value
            repository.updateLinkData(linkNode.id, link)
        }
        selection.clear()
        selection += linkNode.id
        routeCache.remove(linkNode.id)
        invalidateRenderCache()
        refreshAll()
    }

    private fun ensureDefaultPort(nodeId: NodeId, direction: PortDirection, name: String) {
        val node = repository.requireNode(nodeId)
        if (node.ports.none { it.direction == direction && it.name == name }) {
            repository.addPort(nodeId, NodePort("${direction.name.lowercase()}_$name", name, direction))
        }
    }

    private fun selectedMoveRoots(): List<Node> {
        val selectedNodes = selection.mapNotNull(repository::getNode).filter { !it.isLink }
        return selectedNodes.filter { candidate ->
            selectedNodes.none { other -> other.id != candidate.id && isAncestor(other.id, candidate.id) }
        }
    }

    private fun moveNodeAndDescendants(node: Node, dx: Double, dy: Double) {
        node.layout.x += dx
        node.layout.y += dy
        node.children.mapNotNull(repository::getNode).filter { !it.isLink }.forEach {
            moveNodeAndDescendants(it, dx, dy)
        }
    }

    private fun initialMoveReference(point: Point, rect: Rectangle): Point {
        val tolerance = snapToleranceModelUnits()
        entityCorners(rect).minByOrNull { it.distance(point) }
            ?.takeIf { it.distance(point) <= tolerance }
            ?.let { return it }
        val grid = nearestGridPoint(point)
        return if (grid.distance(point) <= tolerance) grid else point
    }

    private fun snapMoveReferenceToGrid(point: Point): Point {
        val grid = nearestGridPoint(point)
        return if (grid.distance(point) <= snapToleranceModelUnits()) grid else point
    }

    private fun snapToleranceModelUnits(): Double = SNAP_RADIUS_PX / zoom

    private fun nearestGridPoint(point: Point): Point =
        Point(
            (point.x.toDouble() / SNAP_GRID_STEP).roundToInt() * SNAP_GRID_STEP,
            (point.y.toDouble() / SNAP_GRID_STEP).roundToInt() * SNAP_GRID_STEP,
        )

    private fun entityCorners(rect: Rectangle): List<Point> =
        listOf(
            Point(rect.x, rect.y),
            Point(rect.x + rect.width, rect.y),
            Point(rect.x, rect.y + rect.height),
            Point(rect.x + rect.width, rect.y + rect.height),
        )

    private fun updateParentAfterDrag(dropPoint: Point) {
        val root = repository.getDocument().rootNodeId
        val newParent = repository.getDocument().nodes.values
            .filter { it.id != root && !it.isLink && it.layout.rect().contains(dropPoint) && it.id !in selection }
            .filter { candidate -> selection.none { selectedId -> isAncestor(selectedId, candidate.id) } }
            .minByOrNull { it.layout.width * it.layout.height }
            ?.id
            ?: root
        selectedMoveRoots().filter { it.id != root }.forEach { moved ->
            if (moved.parentId != newParent) {
                runCatching { repository.moveNode(moved.id, newParent) }
                invalidateRoutesFor(listOf(moved.id))
            }
        }
    }

    private fun isAncestor(candidateAncestor: NodeId, nodeId: NodeId): Boolean {
        var current = repository.getNode(nodeId)?.parentId
        while (current != null) {
            if (current == candidateAncestor) return true
            current = repository.getNode(current)?.parentId
        }
        return false
    }

    private fun hitNode(point: Point): NodeId? =
        visibleNodes()
            .filter { it.layout.rect().contains(point) }
            .minByOrNull { it.layout.width * it.layout.height }
            ?.id

    private fun hitLink(point: Point): NodeId? =
        visibleLinks()
            .firstOrNull { link ->
                if (isDependencyAnnotation(link)) {
                    dependencyAnnotationBounds(link).any { it.contains(point) }
                } else {
                    routeLink(link)?.points?.zipWithNext()?.any { (a, b) -> distanceToSegment(point, a, b) <= 8.0 } == true
                }
            }
            ?.id

    private fun linkInsideSelection(link: Node, rect: Rectangle, containsOnly: Boolean): Boolean {
        if (isDependencyAnnotation(link)) {
            val bounds = dependencyAnnotationBounds(link)
            return if (containsOnly) bounds.isNotEmpty() && bounds.all { rect.contains(it) } else bounds.any { rect.intersects(it) }
        }
        val route = routeCache[link.id] ?: routeLink(link) ?: return false
        return if (containsOnly) {
            route.points.all { rect.contains(it) }
        } else {
            route.points.any { rect.contains(it) } ||
                route.points.zipWithNext().any { (a, b) ->
                    rect.intersectsLine(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble())
                }
        }
    }

    private fun dependencyAnnotationBounds(linkNode: Node): List<Rectangle> {
        val link = linkNode.link ?: return emptyList()
        val source = repository.getNode(link.sourceNodeId) ?: return emptyList()
        val target = repository.getNode(link.targetNodeId) ?: return emptyList()
        val sourceLinks = source.outgoingLinks.mapNotNull(repository::getNode).filter(::isDependencyAnnotation)
        val targetLinks = target.incomingLinks.mapNotNull(repository::getNode).filter(::isDependencyAnnotation)
        val sourceIndex = sourceLinks.indexOfFirst { it.id == linkNode.id }.coerceAtLeast(0)
        val targetIndex = targetLinks.indexOfFirst { it.id == linkNode.id }.coerceAtLeast(0)
        val sourceRect = source.layout.rect()
        val targetRect = target.layout.rect()
        val label = dependencyInjectionLabel(linkNode, source)
        val sourceLabelWidth = dependencyAnnotationWidth(label, DEPENDENCY_SOURCE_MIN_WIDTH)
        val sourceBounds = Rectangle(
            sourceRect.x + sourceRect.width,
            sourceRect.y + 10 + sourceIndex * 32,
            DEPENDENCY_SOURCE_GAP + sourceLabelWidth,
            24,
        )
        val dependencyWidth = dependencyAnnotationWidth(label, DEPENDENCY_TARGET_MIN_WIDTH)
        val dependencyBounds = Rectangle(
            targetRect.x + 30,
            targetRect.y - targetLinks.size * 24 - 20 + targetIndex * 24,
            dependencyWidth + 12,
            24,
        )
        return listOf(sourceBounds, dependencyBounds)
    }

    private fun dependencyAnnotationWidth(label: String, minimum: Int): Int = max(
        minimum,
        monospaceTextWidth(label, DEPENDENCY_ANNOTATION_FONT_SIZE, DEPENDENCY_LABEL_HORIZONTAL_PADDING),
    )

    private fun dependencyTargetRuleWidth(label: String): Int = max(
        DEPENDENCY_TARGET_MIN_WIDTH,
        (dependencyAnnotationWidth(label, DEPENDENCY_TARGET_MIN_WIDTH) * DEPENDENCY_TARGET_RULE_RATIO).roundToInt(),
    )

    private fun dependencyInjectionLabel(linkNode: Node, sourceNode: Node): String {
        val linkName = linkNode.name.trim().ifBlank { linkNode.id.value }
        val sourceName = sourceNode.name.trim().ifBlank { sourceNode.id.value }
        return "$linkName : $sourceName"
    }

    private fun distanceToSegment(point: Point, a: Point, b: Point): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        if (dx == 0.0 && dy == 0.0) return hypot((point.x - a.x).toDouble(), (point.y - a.y).toDouble())
        val t = (((point.x - a.x) * dx) + ((point.y - a.y) * dy)) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0.0, 1.0)
        val x = a.x + clamped * dx
        val y = a.y + clamped * dy
        return hypot(point.x - x, point.y - y)
    }

    private fun modelPoint(point: Point): Point = Point(((point.x / zoom) - panX).toInt(), ((point.y / zoom) - panY).toInt())

    private fun fillFor(node: Node): Color = when {
        node.isType -> activePalette[DesignerColorKey.TypeFill]
        nodeStereotype(node) in compilerDesignStereotypes -> activePalette[DesignerColorKey.CompilerFill]
        node.children.isNotEmpty() -> activePalette[DesignerColorKey.NodeFill]
        nodeStereotype(node) == NodeStereotype.ServiceLibrary -> activePalette[DesignerColorKey.LibraryFill]
        nodeStereotype(node) in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> activePalette[DesignerColorKey.ErrorFill]
        nodeStereotype(node) in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> activePalette[DesignerColorKey.TestFill]
        else -> activePalette[DesignerColorKey.NodeFill]
    }

    private fun strokeFor(node: Node, selected: Boolean): Color = when {
        selected -> activePalette[DesignerColorKey.Selection]
        node.isType -> activePalette[DesignerColorKey.TypeStroke]
        nodeStereotype(node) in compilerDesignStereotypes -> activePalette[DesignerColorKey.CompilerStroke]
        nodeStereotype(node) in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> activePalette[DesignerColorKey.ErrorStroke]
        nodeStereotype(node) in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> activePalette[DesignerColorKey.TestStroke]
        nodeStereotype(node) == NodeStereotype.ServiceLibrary -> activePalette[DesignerColorKey.LibraryStroke]
        else -> activePalette[DesignerColorKey.NodeStroke]
    }

    private fun nodeStroke(node: Node, selected: Boolean): Stroke = when {
        selected -> BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
        node.isType -> BasicStroke(2.2f)
        nodeStereotype(node) in compilerDesignStereotypes -> BasicStroke(2.4f)
        node.children.isNotEmpty() -> BasicStroke(2.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, floatArrayOf(24f, 8f, 4f, 8f), 0f)
        nodeStereotype(node) == NodeStereotype.ServiceLibrary -> BasicStroke(2.2f)
        nodeStereotype(node) in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> BasicStroke(2.2f)
        nodeStereotype(node) in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> BasicStroke(2.2f)
        else -> BasicStroke(2f)
    }

    private fun nodeStereotype(node: Node): NodeStereotype = node.stereotype(repository.getDocument())

    private fun technologyLabel(node: Node): String? {
        if (node.kind != NodeKind.Processor || node.isLink) return null
        val document = repository.getDocument()
        val language = document.effectiveLanguageId(node.id).trim()
        val technology = document.effectiveTechnologyId(node.id).trim()
        return when {
            language.isBlank() && technology.isBlank() -> null
            language.isBlank() -> "tech: $technology"
            technology.isBlank() -> "lang: $language"
            else -> "lang: $language | tech: $technology"
        }
    }

    private fun monospaceTextWidth(text: String, charWidth: Int = 8, padding: Int = 24): Int =
        text.length * charWidth + padding

    private fun monospaceTextWidth(text: String, fontSize: Float, padding: Int = 24): Int =
        (text.length * fontSize * 0.62f).roundToInt() + padding

    private fun linkColor(stereotype: LinkStereotype, selected: Boolean): Color = when {
        selected -> activePalette[DesignerColorKey.Selection]
        else -> activePalette.colorForLink(stereotype)
    }

    private fun isBackflow(route: LinkRoute): Boolean = route.source.x > route.target.x

    private fun linkDashPattern(stereotype: LinkStereotype, backflow: Boolean): String? = when {
        backflow -> "10 6"
        stereotype == LinkStereotype.DependencyInjection -> "8 6"
        stereotype == LinkStereotype.SourceCapability -> "14 4 3 4"
        stereotype == LinkStereotype.RunnableCapability -> "4 4"
        else -> null
    }

    private fun linkDashArray(stereotype: LinkStereotype, backflow: Boolean): FloatArray? = when {
        backflow -> floatArrayOf(10f, 6f)
        stereotype == LinkStereotype.DependencyInjection -> floatArrayOf(8f, 6f)
        stereotype == LinkStereotype.SourceCapability -> floatArrayOf(14f, 4f, 3f, 4f)
        stereotype == LinkStereotype.RunnableCapability -> floatArrayOf(4f, 4f)
        else -> null
    }

    private fun linkStroke(stereotype: LinkStereotype, selected: Boolean, backflow: Boolean): Stroke {
        val width = when {
            selected -> 3f
            stereotype == LinkStereotype.UsageImport -> 1.8f
            stereotype == LinkStereotype.ErrorPipe -> 2f
            stereotype == LinkStereotype.DependencyInjection -> 1.6f
            stereotype in setOf(LinkStereotype.SourceCapability, LinkStereotype.RunnableCapability) -> 2.0f
            else -> 1.5f
        }
        val dash = linkDashArray(stereotype, backflow)
        return if (dash == null) {
            BasicStroke(width)
        } else {
            BasicStroke(
                width,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10f,
                dash,
                0f,
            )
        }
    }
}

private class InspectorPanel(
    private val repository: DocumentRepository,
    private val refreshAll: () -> Unit,
    languageIds: List<String>,
    technologyIds: List<String>,
    layoutStrategies: List<LayoutStrategy>,
    private val compilerCapabilityResolver: CompilerCapabilityResolver,
) : JPanel(BorderLayout()) {
    private data class NameSuggestion(
        val token: String,
        val explanation: String,
    )

    private val knownLanguageIds = languageIds
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals(NoneLanguageChoice, ignoreCase = true) && !it.equals(OtherLanguageChoice, ignoreCase = true) }
        .distinct()
        .sorted()
    private val languageOptions = listOf(NoneLanguageChoice) + knownLanguageIds + OtherLanguageChoice
    private val knownTechnologyIds = technologyIds
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals(NoneTechnologyChoice, ignoreCase = true) && !it.equals(OtherTechnologyChoice, ignoreCase = true) }
        .distinct()
        .sorted()
    private val technologyDisplayById = knownTechnologyIds.associateWith(::technologyDisplay)
    private val technologyIdByDisplay = technologyDisplayById.entries.associate { (id, display) -> display to id }
    private val knownTransportKindIds = LinkTransportKinds.catalog.map { it.id }
    private val transportDisplayById = LinkTransportKinds.catalog.associate { it.id to "${it.label} (${it.id})" }
    private val transportIdByDisplay = transportDisplayById.entries.associate { (id, display) -> display to id }
    private val transportKindOptions = LinkTransportKinds.catalog.map { transportDisplayById.getValue(it.id) } + OtherTransportChoice
    private val interactionDisplayById = LinkInteractionKinds.catalog.associate { it.id to it.label }
    private val interactionIdByDisplay = interactionDisplayById.entries.associate { (id, display) -> display to id }
    private val interactionKindOptions = LinkInteractionKinds.catalog.map { interactionDisplayById.getValue(it.id) }
    private val knownLayoutStrategies = layoutStrategies
    private val layoutDisplayById = knownLayoutStrategies.associate { it.id to "${it.displayName} (${it.id})" }
    private val layoutIdByDisplay = layoutDisplayById.entries.associate { (id, display) -> display to id }
    private var nodeId: NodeId? = null
    private var boundNodeIsLink = false
    private var boundNodeIsType = false
    private var boundNodeIsCompiler = false
    private var boundNodeIsRoot = false
    private var boundHasNode = false
    private var binding = false
    private var unsupportedLayoutSelectionId: String? = null
    private var compilerTechnologyProposal = "generated"
    private val nameField = JTextField()
    private val nameDetail = JTextField()
    private val nameSuggestions = (
        CompilerTemplateRoles.suggestedOverrideNodeNames +
            NodeStereotype.entries.map { "@${it.name}" } +
            listOf("@Compiler", "@ProjectFile", "@StaticFile")
        ).distinct().sorted().map { token ->
            NameSuggestion(token, "compiler override")
        } + listOf(
            NameSuggestion("lib_", "library"),
            NameSuggestion("library_", "library"),
            NameSuggestion("service_", "service library"),
            NameSuggestion("client_", "service client"),
            NameSuggestion("err_", "error handler"),
            NameSuggestion("error_", "error handler"),
            NameSuggestion("test_", "test"),
        )
        .distinctBy(NameSuggestion::token)
        .sortedBy(NameSuggestion::token)
    private val nameCompletionModel = DefaultListModel<NameSuggestion>()
    private val nameCompletionList = JList<NameSuggestion>(nameCompletionModel).apply {
        visibleRowCount = 10
        fixedCellWidth = 300
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val suggestion = value as? NameSuggestion
                component.text = suggestion?.let {
                    "<html><b>${escapeHtml(it.token)}</b>&nbsp;&nbsp;<font color=\"#777777\">(${escapeHtml(it.explanation)})</font></html>"
                }.orEmpty()
                return component
            }
        }
    }
    private val nameCompletionPopup = JPopupMenu().apply {
        add(JScrollPane(nameCompletionList).apply {
            preferredSize = Dimension(340, 180)
        })
    }
    private val language = JComboBox(languageOptions.toTypedArray()).apply { isEditable = false }
    private val customLanguage = JTextField()
    private val customLanguagePanel = JPanel(BorderLayout(0, 4)).apply {
        add(JLabel("Custom language identifier"), BorderLayout.NORTH)
        add(customLanguage, BorderLayout.CENTER)
        isVisible = false
    }
    private val technology = JComboBox(arrayOf(NoneTechnologyChoice, OtherTechnologyChoice)).apply { isEditable = false }
    private val customTechnology = JTextField()
    private val customTechnologyPanel = JPanel(BorderLayout(0, 4)).apply {
        add(JLabel("Custom technology identifier"), BorderLayout.NORTH)
        add(customTechnology, BorderLayout.CENTER)
        isVisible = false
    }
    private val state = JTextField()
    private val responsible = JTextField()
    private val computedResponsible = JLabel()
    private val revisionName = JTextField().apply { isEditable = false }
    private val revisionDate = JTextField().apply { isEditable = false }
    private val modifiedDate = JTextField().apply { isEditable = false }
    private val modifiedBy = JTextField().apply { isEditable = false }
    private val masterRevisionName = JTextField()
    private val masterRevisionDate = JTextField()
    private val linkTransportKind = JComboBox(transportKindOptions.toTypedArray()).apply { isEditable = false }
    private val linkInteractionKind = JComboBox(interactionKindOptions.toTypedArray()).apply { isEditable = false }
    private val layoutStrategy = JComboBox(arrayOf(NoneLayoutChoice)).apply { isEditable = false }
    private val computedLayoutStrategy = JLabel()
    private val layoutCompilerCapability = JLabel()
    private val linkTypeDefinition = JComboBox(arrayOf(NoneTypeChoice)).apply { isEditable = false }
    private var typeIdByDisplay = emptyMap<String, String>()
    private var typeDisplayById = emptyMap<String, String>()
    private val typeFieldsModel = object : DefaultTableModel(arrayOf("Name", "Type", "Reference"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 2) java.lang.Boolean::class.java else String::class.java
    }
    private val typeFields = JTable(typeFieldsModel).apply {
        rowHeight = 24
        fillsViewportHeight = true
        preferredScrollableViewportSize = Dimension(320, 150)
    }
    private val customTransportKind = JTextField()
    private val customTransportKindPanel = JPanel(BorderLayout(0, 4)).apply {
        add(JLabel("Custom transport identifier"), BorderLayout.NORTH)
        add(customTransportKind, BorderLayout.CENTER)
        isVisible = false
    }
    private val metadata = JTextArea(5, 24)
    private val nameRow = fieldRow("Name", nameField)
    private val nameDetailRow = fieldRow("Name detail", nameDetail)
    private val languageRow = fieldRow("Language", language)
    private val technologyRow = fieldRow("Technology", technology)
    private val layoutStrategyRow = fieldRow(
        "File layout strategy",
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(layoutStrategy)
            add(Box.createVerticalStrut(4))
            add(computedLayoutStrategy)
            add(Box.createVerticalStrut(2))
            add(layoutCompilerCapability)
        },
    )
    private val stateRow = fieldRow("State", state)
    private val responsibleRow = fieldRow(
        "Responsible",
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(responsible)
            add(Box.createVerticalStrut(4))
            add(computedResponsible)
        },
    )
    private val revisionNameRow = fieldRow("Effective revision", revisionName)
    private val revisionDateRow = fieldRow("Effective revision date", revisionDate)
    private val modifiedDateRow = fieldRow("Modified date", modifiedDate)
    private val modifiedByRow = fieldRow("Modified by", modifiedBy)
    private val masterRevisionNameRow = fieldRow("Master revision", masterRevisionName)
    private val masterRevisionDateRow = fieldRow("Master revision date", masterRevisionDate)
    private val linkTransportKindRow = fieldRow("Link transport kind", linkTransportKind)
    private val linkInteractionKindRow = fieldRow("Link interaction", linkInteractionKind)
    private val linkTypeDefinitionRow = fieldRow("Link Type Definition", linkTypeDefinition)
    private val typeFieldsRow = fieldRow(
        "Type fields",
        JPanel(BorderLayout(0, 4)).apply {
            add(JScrollPane(typeFields), BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    add(JButton("Add field").apply {
                        addActionListener {
                            typeFieldsModel.addRow(arrayOf<Any>("field_${typeFieldsModel.rowCount + 1}", BuiltInTypeIds.String, false))
                            apply()
                        }
                    })
                    add(JButton("Remove field").apply {
                        addActionListener {
                            typeFields.selectedRows.sortedDescending().forEach(typeFieldsModel::removeRow)
                            apply()
                        }
                    })
                },
                BorderLayout.SOUTH,
            )
        },
    )
    private val metadataRow = fieldRow("Metadata key=value", JScrollPane(metadata))

    init {
        installNameCompletions()
        listOf(
            nameField,
            nameDetail,
            customLanguage,
            customTechnology,
            state,
            responsible,
            customTransportKind,
            masterRevisionName,
            masterRevisionDate,
        ).forEach(::applyOnCommit)
        applyOnCommit(language)
        applyOnCommit(technology)
        applyOnCommit(layoutStrategy)
        applyOnCommit(linkTransportKind)
        applyOnCommit(linkInteractionKind)
        applyOnCommit(linkTypeDefinition)
        applyOnFocusLost(metadata)
        val form = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(nameRow)
            add(nameDetailRow)
            add(languageRow)
            add(customLanguagePanel)
            add(technologyRow)
            add(customTechnologyPanel)
            add(layoutStrategyRow)
            add(stateRow)
            add(responsibleRow)
            add(revisionNameRow)
            add(revisionDateRow)
            add(modifiedDateRow)
            add(modifiedByRow)
            add(masterRevisionNameRow)
            add(masterRevisionDateRow)
            add(linkTransportKindRow)
            add(linkInteractionKindRow)
            add(linkTypeDefinitionRow)
            add(customTransportKindPanel)
            add(typeFieldsRow)
            add(metadataRow)
            add(JButton("Apply").apply { addActionListener { apply() } })
        }
        add(form, BorderLayout.NORTH)
    }

    fun bind(id: NodeId?) {
        if (nodeId == id && isEditing()) return
        hideNameCompletions()
        binding = true
        nodeId = id
        val node = id?.let(repository::getNode)
        boundHasNode = node != null
        boundNodeIsLink = node?.isLink == true
        boundNodeIsType = node?.isType == true
        boundNodeIsCompiler = node?.name?.trim()?.equals("@Compiler", ignoreCase = true) == true
        boundNodeIsRoot = node?.id == repository.getDocument().rootNodeId
        compilerTechnologyProposal = proposedCompilerTechnologyId()
        nameField.text = node?.name.orEmpty()
        nameDetail.text = node?.nameDetail.orEmpty()
        bindLanguage(node?.technology?.languageId.orEmpty())
        refreshTechnologyOptions()
        bindTechnology(node?.technology?.technologyId.orEmpty(), forceCustom = boundNodeIsCompiler)
        bindLayoutStrategy(node?.fileLayoutStrategyId.orEmpty())
        state.text = node?.metadata?.get("state").orEmpty()
        responsible.text = node?.responsible.orEmpty()
        val effectiveRevision = node?.let { repository.getDocument().effectiveRevision(it.id) }
        revisionName.text = effectiveRevision?.name.orEmpty()
        revisionDate.text = effectiveRevision?.date.orEmpty()
        modifiedDate.text = node?.modified?.date.orEmpty()
        modifiedBy.text = node?.modified?.user.orEmpty()
        masterRevisionName.text = if (boundNodeIsRoot) repository.getDocument().masterRevision.name else ""
        masterRevisionDate.text = if (boundNodeIsRoot) repository.getDocument().masterRevision.date else ""
        refreshResponsibleComputedLabel()
        bindTransportKind(node?.link?.transportKind.orEmpty())
        bindInteractionKind(node?.link?.interactionKind.orEmpty())
        refreshTypeChoices(node?.link?.typeDefinitionId.orEmpty())
        bindTypeFields(node)
        metadata.text = metadataText(node)
        updateEntityFieldVisibility()
        binding = false
    }

    private fun applyOnCommit(field: JTextField) {
        field.addActionListener { apply() }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (field == nameField && !isNameCompletionFocusTarget(e.oppositeComponent)) hideNameCompletions()
                apply()
            }
        })
    }

    private fun installNameCompletions() {
        nameField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = updateNameCompletions()
        })
        nameField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (!nameCompletionPopup.isVisible) return
                when (e.keyCode) {
                    KeyEvent.VK_UP -> {
                        nameCompletionList.selectedIndex = (nameCompletionList.selectedIndex - 1).coerceAtLeast(0)
                        nameCompletionList.ensureIndexIsVisible(nameCompletionList.selectedIndex)
                        e.consume()
                    }
                    KeyEvent.VK_DOWN -> {
                        nameCompletionList.selectedIndex = (nameCompletionList.selectedIndex + 1).coerceAtMost(nameCompletionModel.size() - 1)
                        nameCompletionList.ensureIndexIsVisible(nameCompletionList.selectedIndex)
                        e.consume()
                    }
                    KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
                        applyNameCompletion()
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        hideNameCompletions()
                        e.consume()
                    }
                }
            }

            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode in setOf(KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_ESCAPE)) return
                updateNameCompletions()
            }
        })
        nameCompletionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 1) applyNameCompletion()
            }
        })
    }

    private fun updateNameCompletions() {
        if (binding) {
            hideNameCompletions()
            return
        }
        val prefix = nameField.text.trim()
        val matches = nameSuggestions.filter { prefix.isBlank() || it.token.startsWith(prefix, ignoreCase = true) }
        if (matches.isEmpty()) {
            hideNameCompletions()
            return
        }
        nameCompletionModel.clear()
        matches.forEach(nameCompletionModel::addElement)
        nameCompletionList.selectedIndex = 0
        if (!nameCompletionPopup.isVisible) {
            nameCompletionPopup.show(nameField, 0, nameField.height)
        }
    }

    private fun applyNameCompletion() {
        val value = nameCompletionList.selectedValue?.token ?: return
        nameField.text = value
        nameField.caretPosition = value.length
        hideNameCompletions()
        apply()
    }

    private fun hideNameCompletions() {
        if (nameCompletionPopup.isVisible) nameCompletionPopup.isVisible = false
    }

    private fun isNameCompletionFocusTarget(component: Component?): Boolean =
        component != null && SwingUtilities.isDescendingFrom(component, nameCompletionPopup)

    private fun applyOnCommit(field: JComboBox<String>) {
        field.addActionListener {
            updateConditionalChoiceVisibility()
            if (!binding && field.selectedItem != OtherLanguageChoice) apply()
        }
    }

    private fun applyOnFocusLost(area: JTextArea) {
        area.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = apply()
        })
    }

    private fun isEditing(): Boolean =
        nameField.hasFocus() ||
            nameDetail.hasFocus() ||
            language.hasFocus() ||
            customLanguage.hasFocus() ||
            technology.hasFocus() ||
            layoutStrategy.hasFocus() ||
            customTechnology.hasFocus() ||
            state.hasFocus() ||
            responsible.hasFocus() ||
            masterRevisionName.hasFocus() ||
            masterRevisionDate.hasFocus() ||
            linkTransportKind.hasFocus() ||
            linkInteractionKind.hasFocus() ||
            linkTypeDefinition.hasFocus() ||
            typeFields.hasFocus() ||
            typeFields.isEditing ||
            customTransportKind.hasFocus() ||
            metadata.hasFocus()

    private fun parseMetadata(includeState: Boolean): MutableMap<String, String> {
        val next = mutableMapOf<String, String>()
        metadata.text.lines().filter { "=" in it }.forEach {
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim()
            if (key.isNotBlank()) next[key] = value
        }
        if (includeState && state.text.isNotBlank()) next["state"] = state.text
        return next
    }

    private fun apply() {
        if (binding) return
        val id = nodeId ?: return
        if (typeFields.isEditing) typeFields.cellEditor?.stopCellEditing()
        val node = repository.requireNode(id)
        if (boundNodeIsRoot) {
            repository.updateMasterRevision(Revision(masterRevisionName.text.trim(), masterRevisionDate.text.trim()))
        }
        repository.renameNode(id, nameField.text.trim())
        repository.updateNodeNameDetail(id, nameDetail.text.trim())
        val isLink = node.isLink
        if (!isLink) {
            repository.updateNodeTechnology(id, node.technology.copy(languageId = selectedLanguage(), technologyId = selectedTechnology()))
        }
        val requestedLayout = selectedLayoutStrategy()
        val supportedLayouts = compilerCapabilityResolver.supportedLayoutStrategyIds(repository.getDocument(), id)
        val normalizedLayout = when {
            requestedLayout in supportedLayouts -> requestedLayout
            requestedLayout == VOID_LAYOUT_STRATEGY_ID && supportedLayouts.size == 1 -> supportedLayouts.single()
            else -> VOID_LAYOUT_STRATEGY_ID
        }
        repository.updateNodeFileLayoutStrategy(id, normalizedLayout)
        repository.updateNodeMetadata(id, parseMetadata(includeState = !isLink))
        repository.updateNodeResponsible(id, responsible.text)
        if (node.isType) repository.updateNodeTypeDefinition(id, typeDefinitionFromTable())
        if (isLink) node.link?.copy()?.let {
            it.transportKind = selectedTransportKind().ifBlank { LinkTransportKinds.Default }
            it.interactionKind = selectedInteractionKind()
            it.typeDefinitionId = selectedTypeDefinitionId()
            repository.updateLinkData(id, it)
        }
        refreshAll()
        val previousBinding = binding
        binding = true
        bindLayoutStrategy(repository.requireNode(id).fileLayoutStrategyId)
        binding = previousBinding
    }

    private fun selectedLanguage(): String =
        when (val selected = language.selectedItem?.toString().orEmpty()) {
            NoneLanguageChoice -> ""
            OtherLanguageChoice -> customLanguage.text.trim()
            else -> selected.trim()
        }

    private fun selectedTechnology(): String =
        if (boundNodeIsCompiler) {
            customTechnology.text.trim().ifBlank { compilerTechnologyProposal }
        } else {
            when (val selected = technology.selectedItem?.toString().orEmpty()) {
                NoneTechnologyChoice -> ""
                OtherTechnologyChoice -> customTechnology.text.trim()
                else -> technologyIdByDisplay[selected] ?: selected.trim()
            }
        }

    private fun refreshTechnologyOptions() {
        val values = buildList {
            add(NoneTechnologyChoice)
            addAll(
                knownTechnologyIds
                    .filter { id -> id != CompilerTemplateSetTechnologyId || isCompilerTemplateContext() }
                    .map(technologyDisplayById::getValue),
            )
            add(OtherTechnologyChoice)
        }
        val previousBinding = binding
        binding = true
        technology.model = DefaultComboBoxModel(values.toTypedArray())
        binding = previousBinding
    }

    private fun isCompilerTemplateContext(): Boolean {
        val document = repository.getDocument()
        val node = nodeId?.let(document::getElementById) ?: return false
        if (boundNodeIsCompiler || node.stereotype(document) == NodeStereotype.CompilerTemplate) return true
        if (boundNodeIsRoot && document.nodes.values.any { it.name.equals("@Compiler", ignoreCase = true) }) return true
        var parentId = node.parentId
        while (parentId != null) {
            val parent = document.getElementById(parentId) ?: break
            if (parent.name.equals("@Compiler", ignoreCase = true)) return true
            parentId = parent.parentId
        }
        return false
    }

    private fun selectedTransportKind(): String =
        when (val selected = linkTransportKind.selectedItem?.toString().orEmpty()) {
            OtherTransportChoice -> customTransportKind.text.trim()
            else -> transportIdByDisplay[selected].orEmpty()
        }

    private fun selectedInteractionKind(): String =
        interactionIdByDisplay[linkInteractionKind.selectedItem?.toString().orEmpty()]
            ?: LinkInteractionKinds.Data

    private fun selectedTypeDefinitionId(): String =
        typeIdByDisplay[linkTypeDefinition.selectedItem?.toString().orEmpty()].orEmpty()

    private fun typeDefinitionFromTable(): TypeDefinition = TypeDefinition(
        fields = (0 until typeFieldsModel.rowCount).map { row ->
            val displayType = typeFieldsModel.getValueAt(row, 1)?.toString().orEmpty()
            TypeFieldDefinition(
                name = typeFieldsModel.getValueAt(row, 0)?.toString().orEmpty().trim(),
                typeId = typeIdByDisplay[displayType] ?: displayType.ifBlank { BuiltInTypeIds.String },
                isReference = typeFieldsModel.getValueAt(row, 2) as? Boolean ?: false,
            )
        }.toMutableList(),
    )

    private fun refreshTypeChoices(selectedTypeId: String) {
        val document = repository.getDocument()
        val choices = linkedMapOf(NoneTypeChoice to "")
        BuiltInTypeIds.all.forEach { choices[it] = it }
        document.typeNodes()
            .sortedBy { it.name.lowercase() }
            .forEach { type -> choices["${type.name} (${type.id.value})"] = type.id.value }
        typeIdByDisplay = choices
        typeDisplayById = choices.entries.associate { (display, id) -> id to display }
        linkTypeDefinition.model = DefaultComboBoxModel(choices.keys.toTypedArray())
        linkTypeDefinition.selectedItem = typeDisplayById[selectedTypeId].orEmpty().ifBlank { NoneTypeChoice }
        val fieldChoices = choices.filterValues(String::isNotBlank).keys.toTypedArray()
        typeFields.columnModel.getColumn(1).cellEditor = DefaultCellEditor(JComboBox(fieldChoices))
    }

    private fun bindTypeFields(node: Node?) {
        typeFieldsModel.rowCount = 0
        node?.typeDefinition?.fields.orEmpty().forEach { field ->
            val display = typeDisplayById[field.typeId] ?: repository.getDocument().typeDisplayName(field.typeId)
            typeFieldsModel.addRow(arrayOf<Any>(field.name, display, field.isReference))
        }
    }

    private fun bindLanguage(languageId: String) {
        val value = languageId.trim()
        when {
            value.isBlank() -> {
                language.selectedItem = NoneLanguageChoice
                customLanguage.text = ""
            }
            value in knownLanguageIds -> {
                language.selectedItem = value
                customLanguage.text = ""
            }
            else -> {
                language.selectedItem = OtherLanguageChoice
                customLanguage.text = value
            }
        }
        updateConditionalChoiceVisibility()
    }

    private fun bindTechnology(technologyId: String, forceCustom: Boolean = false) {
        val value = technologyId.trim()
        when {
            forceCustom -> {
                technology.selectedItem = OtherTechnologyChoice
                customTechnology.text = value.ifBlank { compilerTechnologyProposal }
            }
            value.isBlank() -> {
                technology.selectedItem = NoneTechnologyChoice
                customTechnology.text = ""
            }
            value in knownTechnologyIds -> {
                technology.selectedItem = technologyDisplayById.getValue(value)
                customTechnology.text = ""
            }
            else -> {
                technology.selectedItem = OtherTechnologyChoice
                customTechnology.text = value
            }
        }
        updateConditionalChoiceVisibility()
    }

    private fun bindTransportKind(transportKind: String) {
        val value = transportKind.trim()
        val canonical = LinkTransportKinds.canonicalId(value)
        when {
            value.isBlank() -> {
                linkTransportKind.selectedItem = transportDisplayById.getValue(LinkTransportKinds.Default)
                customTransportKind.text = ""
            }
            canonical in knownTransportKindIds -> {
                linkTransportKind.selectedItem = transportDisplayById.getValue(canonical)
                customTransportKind.text = ""
            }
            else -> {
                linkTransportKind.selectedItem = OtherTransportChoice
                customTransportKind.text = value
            }
        }
        updateConditionalChoiceVisibility()
    }

    private fun bindInteractionKind(interactionKind: String) {
        val canonical = LinkInteractionKinds.canonicalId(interactionKind)
        linkInteractionKind.selectedItem = interactionDisplayById[canonical]
            ?: interactionDisplayById.getValue(LinkInteractionKinds.Auto)
    }

    private fun bindLayoutStrategy(layoutStrategyId: String) {
        val value = layoutStrategyId.trim()
        refreshLayoutStrategyOptions(value)
        val display = layoutDisplayById[value]
        val supportedIds = nodeId?.let {
            compilerCapabilityResolver.supportedLayoutStrategyIds(repository.getDocument(), it)
        }.orEmpty()
        layoutStrategy.selectedItem = when {
            supportedIds == setOf(VOID_LAYOUT_STRATEGY_ID) -> NoneLayoutChoice
            value.isBlank() || value == VOID_LAYOUT_STRATEGY_ID -> NoneLayoutChoice
            value == unsupportedLayoutSelectionId -> unsupportedLayoutDisplay(value)
            display == null -> NoneLayoutChoice
            else -> display
        }
        refreshLayoutStrategyComputedLabel()
    }

    private fun refreshLayoutStrategyOptions(selectedId: String) {
        val id = nodeId
        val document = repository.getDocument()
        val compiler = id?.let { compilerCapabilityResolver.compilerFor(document, it) }
        val supportedIds = id?.let { compilerCapabilityResolver.supportedLayoutStrategyIds(document, it) }.orEmpty()
        val supportedStrategies = knownLayoutStrategies.filter { it.id in supportedIds }
        val normalizedSelectedId = selectedId.trim()
        unsupportedLayoutSelectionId = normalizedSelectedId.takeIf {
            it.isNotBlank() && it != VOID_LAYOUT_STRATEGY_ID && it !in supportedIds
        }
        val values = buildList {
            add(NoneLayoutChoice)
            addAll(supportedStrategies.map { layoutDisplayById.getValue(it.id) })
            unsupportedLayoutSelectionId?.let { add(unsupportedLayoutDisplay(it)) }
        }
        val previousBinding = binding
        binding = true
        layoutStrategy.model = DefaultComboBoxModel(values.toTypedArray())
        binding = previousBinding
        layoutStrategy.toolTipText = compiler?.let {
            "${it.displayName} supports ${supportedStrategies.joinToString { strategy -> strategy.displayName }.ifBlank { "no declared layout strategies" }}"
        } ?: "No compiler is available for this entity"
    }

    private fun unsupportedLayoutDisplay(layoutId: String): String =
        "Unsupported: ${layoutDisplayById[layoutId] ?: layoutId}"

    private fun updateConditionalChoiceVisibility() {
        customLanguagePanel.isVisible = languageRow.isVisible && language.selectedItem == OtherLanguageChoice
        customLanguage.isEnabled = customLanguagePanel.isVisible
        customTechnologyPanel.isVisible = technologyRow.isVisible && (boundNodeIsCompiler || technology.selectedItem == OtherTechnologyChoice)
        customTechnology.isEnabled = customTechnologyPanel.isVisible
        customTransportKindPanel.isVisible = linkTransportKindRow.isVisible && linkTransportKind.selectedItem == OtherTransportChoice
        customTransportKind.isEnabled = customTransportKindPanel.isVisible
        refreshLayoutStrategyComputedLabel()
        revalidate()
        repaint()
    }

    private fun updateEntityFieldVisibility() {
        val showNodeFields = boundHasNode && !boundNodeIsLink
        val showLinkFields = boundHasNode && boundNodeIsLink
        layoutStrategyRow.isVisible = boundHasNode
        languageRow.isVisible = showNodeFields
        technologyRow.isVisible = showNodeFields
        customTechnologyPanel.isVisible = showNodeFields && (boundNodeIsCompiler || technology.selectedItem == OtherTechnologyChoice)
        stateRow.isVisible = showNodeFields
        responsibleRow.isVisible = boundHasNode
        revisionNameRow.isVisible = boundHasNode
        revisionDateRow.isVisible = boundHasNode
        modifiedDateRow.isVisible = boundHasNode
        modifiedByRow.isVisible = boundHasNode
        masterRevisionNameRow.isVisible = boundNodeIsRoot
        masterRevisionDateRow.isVisible = boundNodeIsRoot
        linkTransportKindRow.isVisible = showLinkFields
        linkInteractionKindRow.isVisible = showLinkFields
        linkTypeDefinitionRow.isVisible = showLinkFields
        typeFieldsRow.isVisible = boundNodeIsType
        updateConditionalChoiceVisibility()
    }

    private fun refreshResponsibleComputedLabel() {
        val effective = nodeId?.let { repository.getDocument().effectiveResponsible(it) }.orEmpty()
        computedResponsible.text = "effective: ${effective.ifBlank { "none" }}"
    }

    private fun selectedLayoutStrategy(): String =
        when (val selected = layoutStrategy.selectedItem?.toString().orEmpty()) {
            "" -> VOID_LAYOUT_STRATEGY_ID
            NoneLayoutChoice -> VOID_LAYOUT_STRATEGY_ID
            unsupportedLayoutSelectionId?.let(::unsupportedLayoutDisplay) -> unsupportedLayoutSelectionId.orEmpty()
            else -> layoutIdByDisplay[selected] ?: selected.trim()
        }

    private fun refreshLayoutStrategyComputedLabel() {
        val node = nodeId?.let(repository::getNode)
        val computed = node?.id?.let { nodeId -> repository.getDocument().effectiveLayoutStrategyId(nodeId) }.orEmpty()
        val compiler = node?.id?.let { compilerCapabilityResolver.compilerFor(repository.getDocument(), it) }
        val supportedIds = node?.id?.let {
            compilerCapabilityResolver.supportedLayoutStrategyIds(repository.getDocument(), it)
        }.orEmpty()
        val unsupported = computed.isNotBlank() && computed != VOID_LAYOUT_STRATEGY_ID && computed !in supportedIds
        computedLayoutStrategy.text = when {
            computed.isBlank() || computed == VOID_LAYOUT_STRATEGY_ID -> "effective: none"
            unsupported -> "effective: ${layoutDisplayById[computed] ?: computed} (unsupported)"
            else -> "effective: ${layoutDisplayById[computed] ?: computed}"
        }
        layoutCompilerCapability.text = compiler?.let { "compiler: ${it.displayName}" } ?: "compiler: unavailable"
        computedLayoutStrategy.isVisible = layoutStrategyRow.isVisible
        layoutCompilerCapability.isVisible = layoutStrategyRow.isVisible
    }

    private fun metadataText(node: Node?): String =
        node?.metadata
            ?.entries
            ?.filterNot { (key, _) -> !node.isLink && key == "state" }
            ?.joinToString("\n") { "${it.key}=${it.value}" }
            .orEmpty()

    private fun proposedCompilerTechnologyId(): String =
        repository.getDocument().name
            .lowercase()
            .replace(Regex("[^a-z0-9_.-]+"), "-")
            .trim('-', '.', '_')
            .ifBlank { "generated" }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun technologyDisplay(id: String): String =
        when (id) {
            "multi-tech" -> "multi-tech"
            "file-export" -> "filesystem-layout"
            CompilerTemplateSetTechnologyId -> "Pebble Template Set"
            else -> id
        }

    private fun fieldRow(label: String, component: JComponent): JPanel =
        JPanel(BorderLayout(0, 4)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(label), BorderLayout.NORTH)
            add(component, BorderLayout.CENTER)
        }

    private companion object {
        private const val NoneLanguageChoice = "None"
        private const val OtherLanguageChoice = "Other"
        private const val NoneTechnologyChoice = "None"
        private const val OtherTechnologyChoice = "Other"
        private const val CompilerTemplateSetTechnologyId = "compiler-template-set"
        private const val OtherTransportChoice = "Other"
        private const val NoneLayoutChoice = "None"
        private const val NoneTypeChoice = "None"
    }
}

private class NodeEditorTabs(
    private val repository: DocumentRepository,
    private val refreshAll: () -> Unit,
    private val checkpointHistory: () -> Unit,
    private val undoDocument: () -> Unit,
    private val redoDocument: () -> Unit,
    private val languageIds: List<String>,
    private val compilerCapabilityResolver: CompilerCapabilityResolver,
    private val requestNativeValidation: (NodeId, String) -> Unit,
) : JTabbedPane() {
    private var boundIds: List<NodeId> = emptyList()
    private var activePalette: DesignerPalette = ThreadworkAppearance.palette()
    private var nativeDiagnosticsByNode: Map<NodeId?, List<Diagnostic>> = emptyMap()
    private val loadedEditors = mutableMapOf<NodeId, NodeTextEditor>()
    private var bindingTabs = false

    init {
        addChangeListener {
            if (!bindingTabs) ensureSelectedEditor()
        }
    }

    fun bind(ids: List<NodeId>, activeSection: NodeTextSection? = null) {
        val validIds = ids.filter { repository.getNode(it) != null }
        if (validIds != boundIds) {
            bindingTabs = true
            try {
                removeAll()
                loadedEditors.clear()
                boundIds = validIds
                boundIds.mapNotNull(repository::getNode).forEach { node ->
                    addTab(node.name, null, JPanel())
                }
            } finally {
                bindingTabs = false
            }
            ensureSelectedEditor()
        } else {
            refreshTitlesAndMetadata()
        }
        applyNativeDiagnosticsToBoundEditors()
        activeSection?.let { section ->
            boundIds.firstOrNull()?.let { selectSection(it, section) }
        }
    }

    private fun selectSection(nodeId: NodeId, section: NodeTextSection) {
        val index = boundIds.indexOf(nodeId)
        if (index < 0) return
        selectedIndex = index
        ensureEditorAt(index)?.selectSection(section)
    }

    private fun ensureSelectedEditor() {
        if (selectedIndex >= 0) ensureEditorAt(selectedIndex)
    }

    private fun ensureEditorAt(index: Int): NodeTextEditor? {
        val nodeId = boundIds.getOrNull(index) ?: return null
        loadedEditors[nodeId]?.let { return it }
        val editor = NodeTextEditor(
            repository,
            nodeId,
            refreshAll,
            checkpointHistory,
            undoDocument,
            redoDocument,
            languageIds,
            compilerCapabilityResolver,
            activePalette,
            requestNativeValidation,
        )
        loadedEditors[nodeId] = editor
        if (index < tabCount && getComponentAt(index) !is NodeTextEditor) {
            setComponentAt(index, editor)
        }
        editor.setNativeDiagnostics(
            nativeDiagnosticsByNode[nodeId]
                ?: repository.getNode(nodeId)?.diagnostics.orEmpty(),
        )
        return editor
    }

    private fun refreshTitlesAndMetadata() {
        boundIds.forEachIndexed { index, nodeId ->
            repository.getNode(nodeId)?.let { node -> setTitleAt(index, node.name) }
            loadedEditors[nodeId]?.refreshMetadata()
        }
    }

    fun applyCodeEditorFont(font: Font) {
        loadedEditors.values.forEach { it.applyCodeEditorFont(font) }
    }

    fun applyPalette(palette: DesignerPalette) {
        activePalette = palette
        loadedEditors.values.forEach { it.applyPalette(palette) }
    }

    fun applyNativeDiagnostics(diagnosticsByNode: Map<NodeId?, List<Diagnostic>>) {
        nativeDiagnosticsByNode = diagnosticsByNode
        applyNativeDiagnosticsToBoundEditors()
    }

    fun hasBoundNode(nodeId: NodeId?): Boolean = nodeId != null && boundIds.contains(nodeId)

    fun revealNativeDiagnostic(nodeId: NodeId?, section: NodeTextSection?) {
        if (nodeId == null || section == null) return
        selectSection(nodeId, section)
        for (index in 0 until tabCount) {
            val editor = getComponentAt(index) as? NodeTextEditor ?: continue
            if (editor.nodeId == nodeId) {
                editor.revealNativeDiagnostic(section)
                return
            }
        }
    }

    private fun applyNativeDiagnosticsToBoundEditors() {
        loadedEditors.values.forEach { editor ->
            editor.setNativeDiagnostics(
                nativeDiagnosticsByNode[editor.nodeId]
                    ?: repository.getNode(editor.nodeId)?.diagnostics.orEmpty(),
            )
        }
    }
}

private class NodeTextEditor(
    private val repository: DocumentRepository,
    val nodeId: NodeId,
    private val refreshAll: () -> Unit,
    private val checkpointHistory: () -> Unit,
    private val undoDocument: () -> Unit,
    private val redoDocument: () -> Unit,
    languageIds: List<String>,
    private val compilerCapabilityResolver: CompilerCapabilityResolver,
    initialPalette: DesignerPalette,
    private val requestNativeValidation: (NodeId, String) -> Unit,
) : JTabbedPane() {
    private val completionService = ModelAwareCompletionService(
        documentProvider = repository::getDocument,
        compilerProvider = { document, node -> compilerCapabilityResolver.compilerFor(document, node.id) },
    )
    private val editorsBySection = mutableMapOf<NodeTextSection, GridCodeEditorAdapter>()
    private val languageSelectorsBySection = mutableMapOf<NodeTextSection, JComboBox<String>>()
    private val effectiveLanguageLabelsBySection = mutableMapOf<NodeTextSection, JLabel>()
    private val componentsBySection = mutableMapOf<NodeTextSection, JComponent>()
    private var testsPanel: TestTextEditorPanel? = null
    private var binaryPanel: BinaryContentPanel? = null
    private var imagePanel: ImagePreviewPanel? = null
    private var binding = false
    private var activePalette = initialPalette
    private var textTabsVisible = repository.requireNode(nodeId).binaryContent == null
    private val inheritedLanguageChoice = "Inherited"
    private val selectableLanguages = (listOf(VOID_LANGUAGE_ID) + languageIds).distinct()
    private val textTabs = listOf(
        TextTabSpec(
            label = "Declaration",
            iconId = "ide_tab_declaration",
            section = NodeTextSection.Declaration,
            allowInheritance = true,
            defaultLanguageId = VOID_LANGUAGE_ID,
            textGetter = { it.declaration },
            textSetter = { text, value -> text.copy(declaration = value) },
            languageGetter = { it.declarationLanguageId },
            languageSetter = { text, value -> text.copy(declarationLanguageId = value) },
        ),
        TextTabSpec(
            label = "Specification",
            iconId = "ide_tab_specification",
            section = NodeTextSection.Specification,
            allowInheritance = false,
            defaultLanguageId = "markdown",
            textGetter = { it.specification },
            textSetter = { text, value -> text.copy(specification = value) },
            languageGetter = { it.specificationLanguageId },
            languageSetter = { text, value -> text.copy(specificationLanguageId = value) },
        ),
        TextTabSpec(
            label = "Usage Instructions",
            iconId = "ide_tab_usage",
            section = NodeTextSection.AiInstructions,
            allowInheritance = false,
            defaultLanguageId = "markdown",
            textGetter = { it.aiInstructions },
            textSetter = { text, value -> text.copy(aiInstructions = value) },
            languageGetter = { it.aiInstructionsLanguageId },
            languageSetter = { text, value -> text.copy(aiInstructionsLanguageId = value) },
        ),
        TextTabSpec(
            label = "Instantiation",
            iconId = "ide_tab_instantiation",
            section = NodeTextSection.Instantiation,
            allowInheritance = true,
            defaultLanguageId = VOID_LANGUAGE_ID,
            textGetter = { it.instantiation },
            textSetter = { text, value -> text.copy(instantiation = value) },
            languageGetter = { it.instantiationLanguageId },
            languageSetter = { text, value -> text.copy(instantiationLanguageId = value) },
        ),
    )

    init {
        if (textTabsVisible) addTextTabs()
        syncSpecialContentTabs()
    }

    private fun addTextTabs() {
        if (componentsBySection.isNotEmpty()) return
        textTabs.dropLast(1).forEach(::addTextTab)
        addTestsTab()
        addTextTab(textTabs.last())
    }

    fun selectSection(section: NodeTextSection) {
        componentsBySection[section]?.let { component ->
            selectedComponent = component
            when (section) {
                NodeTextSection.Tests -> testsPanel?.focusEditor()
                else -> editorsBySection[section]?.focus()
            }
        }
    }

    fun refreshMetadata() {
        val technology = effectiveTechnology()
        binding = true
        try {
            val node = repository.requireNode(nodeId)
            textTabs.forEach { spec ->
                val effectiveLanguage = repository.getDocument().effectiveTextLanguageId(nodeId, spec.section)
                languageSelectorsBySection[spec.section]?.let { selector ->
                    selector.selectedItem = languageDisplayFor(spec, node.text, effectiveLanguage)
                }
                refreshEffectiveLanguageLabel(spec)
                editorsBySection[spec.section]?.let { editor ->
                    editor.setText(spec.textGetter(node.text))
                    editor.setTechnology(technology.copy(languageId = effectiveLanguage))
                    editor.setCompletionContext(EditorCompletionContext(node.id.value, spec.section))
                    applySemanticIdentifierPresentation(editor)
                    editor.setPinnedHeader(generatedFunctionHeader(node, spec.section))
                }
            }
            editorsBySection[NodeTextSection.Tests]?.setText(node.text.tests)
            editorsBySection[NodeTextSection.Tests]?.let(::applySemanticIdentifierPresentation)
            testsPanel?.bindLanguage(effectiveTextLanguage(NodeTextSection.Tests))
            testsPanel?.refreshTopology()
            syncSpecialContentTabs()
        } finally {
            binding = false
        }
    }

    fun applyCodeEditorFont(font: Font) {
        editorsBySection.values.forEach { it.setEditorFont(font) }
    }

    fun applyPalette(palette: DesignerPalette) {
        activePalette = palette
        editorsBySection.values.forEach(::applySemanticIdentifierPresentation)
    }

    fun setNativeDiagnostics(diagnostics: List<Diagnostic>) {
        editorsBySection.forEach { (section, editor) ->
            editor.setDiagnostics(
                diagnostics.filter { it.textSection == section || (it.textSection == null && section == NodeTextSection.Declaration) },
            )
        }
    }

    fun revealNativeDiagnostic(section: NodeTextSection) {
        editorsBySection[section]?.revealDiagnostic(
            repository.getNode(nodeId)?.diagnostics?.firstOrNull { it.textSection == section }?.line,
        )
    }

    private data class SemanticIdentifierPresentation(
        val colors: Map<String, Color>,
    )

    private fun applySemanticIdentifierPresentation(editor: GridCodeEditorAdapter) {
        val presentation = semanticIdentifierPresentation()
        editor.setSemanticIdentifierColors(presentation.colors)
    }

    private fun semanticIdentifierPresentation(): SemanticIdentifierPresentation {
        val document = repository.getDocument()
        val node = document.getElementById(nodeId) ?: return SemanticIdentifierPresentation(emptyMap())
        val compiler = compilerCapabilityResolver.compilerFor(document, node.id)
            ?: return SemanticIdentifierPresentation(emptyMap())
        val colors = linkedMapOf<String, Color>()
        compiler.codeIntelligence(document, node).symbols.forEach { symbol ->
            val originLink = symbol.originNodeId?.let(document::getElementById)
            val color = when {
                symbol.kind == CompilerCodeSymbolKind.Type -> activePalette[DesignerColorKey.TypeText]
                originLink?.link != null -> activePalette.colorForLink(LinkClassifier.classify(document, originLink))
                else -> null
            }
            if (color != null) {
                colors[symbol.name] = color
                if (!symbol.name.startsWith("$")) {
                    colors["${'$'}${symbol.name}"] = color
                }
            }
        }
        if (node.isComposite) {
            node.children.mapNotNull(document::getElementById)
                .filterNot { it.isLink }
                .forEach { child ->
                    val childCompiler = compilerCapabilityResolver.compilerFor(document, child.id) ?: compiler
                    childCompiler.generatedEntitySymbols(document, child).forEach { symbol ->
                        if (symbol.kind == CompilerCodeSymbolKind.GeneratedFunction) {
                            colors[symbol.name] = Color(0xe06cbb)
                        }
                    }
                }
        }
        return SemanticIdentifierPresentation(colors)
    }

    private fun generatedFunctionHeader(node: Node, section: NodeTextSection): String {
        if (section !in setOf(NodeTextSection.Declaration, NodeTextSection.Instantiation)) return ""
        val document = repository.getDocument()
        return compilerCapabilityResolver.compilerFor(document, node.id)
            ?.generatedFunctionHeader(document, node, section)
            .orEmpty()
    }

    private fun addTextTab(spec: TextTabSpec) {
        val editor = GridCodeEditorAdapter()
        editor.setEditorFont(ThreadworkFonts.codeFont(14f))
        val node = repository.requireNode(nodeId)
        val languageSelector = JComboBox(languageChoices(spec).toTypedArray()).apply {
            isEditable = false
        }
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            add(JLabel("Language"))
            add(languageSelector)
            if (spec.section == NodeTextSection.Instantiation || spec.section == NodeTextSection.Declaration) {
                add(JLabel("Effective"))
                add(JLabel().also { effectiveLanguageLabelsBySection[spec.section] = it })
            }
        }
        val editorPanel = JPanel(BorderLayout()).apply {
            add(controls, BorderLayout.NORTH)
            add(editor, BorderLayout.CENTER)
        }
        editor.setTechnology(effectiveTechnology().copy(languageId = effectiveTextLanguage(spec.section)))
        editor.setCompletionContext(EditorCompletionContext(node.id.value, spec.section))
        applySemanticIdentifierPresentation(editor)
        editor.setPinnedHeader(generatedFunctionHeader(node, spec.section))
        editor.onCompletionRequested = completionService::getSuggestions
        editor.onDeclarationSymbolsRequested = completionService::getDeclarationSymbols
        editor.onHoverInfoRequested = ::typeHoverInfo
        editor.setText(spec.textGetter(node.text))
        fun saveNow() {
            val current = repository.requireNode(nodeId)
            val next = spec.textSetter(current.text, editor.getText())
            if (next != current.text) {
                repository.updateNodeText(nodeId, next)
                checkpointHistory()
            }
        }
        val timer = Timer(250) {
            saveNow()
        }
        timer.isRepeats = false
        editor.onTextChanged = {
            timer.restart()
            requestNativeValidation(nodeId, effectiveTextLanguage(spec.section))
        }
        editor.onUndoRequested = {
            timer.stop()
            saveNow()
            undoDocument()
            requestNativeValidation(nodeId, effectiveTextLanguage(spec.section))
        }
        editor.onRedoRequested = {
            timer.stop()
            saveNow()
            redoDocument()
            requestNativeValidation(nodeId, effectiveTextLanguage(spec.section))
        }
        languageSelector.addActionListener {
            if (binding) return@addActionListener
            val current = repository.requireNode(nodeId)
            val selected = selectedLanguageForSpec(spec, languageSelector.selectedItem?.toString().orEmpty(), current.text)
            repository.updateNodeText(nodeId, spec.languageSetter(current.text, selected))
            refreshAll()
        }
        editorsBySection[spec.section] = editor
        languageSelectorsBySection[spec.section] = languageSelector
        componentsBySection[spec.section] = editorPanel
        addTab(spec.label, ThreadworkIcons.buttonIcon(spec.iconId), editorPanel)
        binding = true
        try {
            syncTextLanguageBinding(spec, node.text)
            refreshEffectiveLanguageLabel(spec)
        } finally {
            binding = false
        }
    }

    private fun addTestsTab() {
        val editor = GridCodeEditorAdapter()
        editor.setEditorFont(ThreadworkFonts.codeFont(14f))
        val node = repository.requireNode(nodeId)
        val languageSelector = JComboBox(languageChoicesForTests().toTypedArray()).apply {
            isEditable = false
        }
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            add(JLabel("Language"))
            add(languageSelector)
        }
        val editorPanel = JPanel(BorderLayout()).apply {
            add(controls, BorderLayout.NORTH)
            add(editor, BorderLayout.CENTER)
        }
        editor.setTechnology(effectiveTechnology().copy(languageId = effectiveTextLanguage(NodeTextSection.Tests)))
        editor.setCompletionContext(EditorCompletionContext(node.id.value, NodeTextSection.Tests))
        applySemanticIdentifierPresentation(editor)
        editor.onCompletionRequested = completionService::getSuggestions
        editor.onDeclarationSymbolsRequested = completionService::getDeclarationSymbols
        editor.onHoverInfoRequested = ::typeHoverInfo
        editor.setText(node.text.tests)
        fun saveNow() {
            val current = repository.requireNode(nodeId)
            val next = current.text.copy(tests = editor.getText())
            if (next != current.text) {
                repository.updateNodeText(nodeId, next)
                checkpointHistory()
            }
        }
        val timer = Timer(250) {
            saveNow()
        }
        timer.isRepeats = false
        editor.onTextChanged = { timer.restart() }
        editor.onUndoRequested = {
            timer.stop()
            saveNow()
            undoDocument()
        }
        editor.onRedoRequested = {
            timer.stop()
            saveNow()
            redoDocument()
        }
        languageSelector.addActionListener {
            if (binding) return@addActionListener
            val current = repository.requireNode(nodeId)
            repository.updateNodeText(
                nodeId,
                current.text.copy(testsLanguageId = languageSelector.selectedItem?.toString().orEmpty().ifBlank { "json" }),
            )
            refreshAll()
        }
        val panel = TestTextEditorPanel(repository, nodeId, editor, languageSelector) { value ->
            val current = repository.requireNode(nodeId)
            repository.updateNodeText(nodeId, current.text.copy(tests = value))
            checkpointHistory()
        }
        testsPanel = panel
        editorsBySection[NodeTextSection.Tests] = editor
        languageSelectorsBySection[NodeTextSection.Tests] = languageSelector
        componentsBySection[NodeTextSection.Tests] = panel
        addTab("Tests", ThreadworkIcons.buttonIcon("ide_tab_tests"), panel)
        binding = true
        try {
            panel.bindLanguage(effectiveTextLanguage(NodeTextSection.Tests))
        } finally {
            binding = false
        }
    }

    private fun effectiveTechnology(): TechnologyMetadata {
        val document = repository.getDocument()
        val node = repository.requireNode(nodeId)
        return node.technology.copy(
            languageId = document.effectiveLanguageId(nodeId),
            technologyId = document.effectiveTechnologyId(nodeId),
        )
    }

    private fun syncSpecialContentTabs() {
        val node = repository.getNode(nodeId) ?: return
        val binary = node.binaryContent
        syncTextTabsForBinary(binary != null)
        if (binary != null) {
            if (binaryPanel == null) {
                val panel = BinaryContentPanel()
                binaryPanel = panel
                addTab("Binary", panel)
            }
            binaryPanel?.bind(node)
        } else if (binaryPanel != null) {
            binaryPanel?.let(::remove)
            binaryPanel = null
        }

        val isRasterImage = node.technology.contentType.startsWith("image/") && binary != null
        val isSvgImage = node.technology.contentType == "image/svg+xml" && node.text.declaration.isNotBlank()
        if (isRasterImage || isSvgImage) {
            if (imagePanel == null) {
                val panel = ImagePreviewPanel()
                imagePanel = panel
                addTab("Image", panel)
            }
            imagePanel?.bind(node, binary ?: node.text.declaration.toByteArray(Charsets.UTF_8))
        } else if (imagePanel != null) {
            imagePanel?.let(::remove)
            imagePanel = null
        }
    }

    private fun syncTextTabsForBinary(hasBinaryContent: Boolean) {
        if (hasBinaryContent == !textTabsVisible) return
        if (hasBinaryContent) {
            textTabs.forEach { spec ->
                componentsBySection[spec.section]?.let(::remove)
            }
            componentsBySection[NodeTextSection.Tests]?.let(::remove)
            textTabsVisible = false
            return
        }

        addTextTabs()
        var index = 0
        textTabs.dropLast(1).forEach { spec ->
            val component = componentsBySection[spec.section] ?: return@forEach
            insertTab(spec.label, ThreadworkIcons.buttonIcon(spec.iconId), component, null, index++)
        }
        componentsBySection[NodeTextSection.Tests]?.let { component ->
            insertTab("Tests", ThreadworkIcons.buttonIcon("ide_tab_tests"), component, null, index++)
        }
        textTabs.lastOrNull()?.let { spec ->
            componentsBySection[spec.section]?.let { component ->
                insertTab(spec.label, ThreadworkIcons.buttonIcon(spec.iconId), component, null, index)
            }
        }
        textTabsVisible = true
    }

    private fun typeHoverInfo(request: EditorHoverRequest): EditorHoverInfo? {
        val document = repository.getDocument()
        val node = document.getElementById(NodeId(request.nodeId)) ?: return null
        val compiler = compilerCapabilityResolver.compilerFor(document, node.id) ?: return null
        val type = compiler.typeInformation(document, node, request.symbol) ?: return null
        val fields = type.fields.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { field ->
                "${field.name}: ${field.typeName}${if (field.isReference) " (reference)" else ""}"
            }
            .orEmpty()
        val declaration = type.declaration.trim().takeIf(String::isNotBlank).orEmpty()
        val body = listOfNotNull(
            type.documentation.takeIf(String::isNotBlank),
            declaration.takeIf(String::isNotBlank),
            fields.takeIf(String::isNotBlank),
        ).joinToString("\n")
        return EditorHoverInfo(
            title = "${type.name} [${type.languageId.ifBlank { request.languageId }}]",
            body = body.ifBlank { "No generated type details are available." },
        )
    }

    private fun effectiveTextLanguage(section: NodeTextSection): String =
        repository.getDocument().effectiveTextLanguageId(nodeId, section)

    private fun languageChoices(spec: TextTabSpec): List<String> =
        if (spec.allowInheritance) listOf(inheritedLanguageChoice) + selectableLanguages else selectableLanguages

    private fun languageChoicesForTests(): List<String> = selectableLanguages

    private fun languageDisplayFor(spec: TextTabSpec, nodeText: NodeText, effectiveLanguageId: String): String {
        val stored = spec.languageGetter(nodeText).trim()
        return when {
            spec.allowInheritance && stored.isBlank() -> inheritedLanguageChoice
            stored.isNotBlank() -> stored
            else -> effectiveLanguageId.ifBlank { spec.defaultLanguageId }
        }
    }

    private fun selectedLanguageForSpec(spec: TextTabSpec, selected: String, nodeText: NodeText): String =
        when {
            spec.allowInheritance && selected == inheritedLanguageChoice -> ""
            selected.isBlank() -> spec.defaultLanguageId
            else -> selected
        }

    private fun syncTextLanguageBinding(spec: TextTabSpec, nodeText: NodeText) {
        val selector = languageSelectorsBySection[spec.section] ?: return
        val current = languageDisplayFor(spec, nodeText, effectiveTextLanguage(spec.section))
        selector.selectedItem = current
    }

    private fun refreshEffectiveLanguageLabel(spec: TextTabSpec) {
        val label = effectiveLanguageLabelsBySection[spec.section] ?: return
        val effective = effectiveTextLanguage(spec.section)
        label.text = effective.ifBlank { spec.defaultLanguageId }
    }

    private data class TextTabSpec(
        val label: String,
        val iconId: String,
        val section: NodeTextSection,
        val allowInheritance: Boolean,
        val defaultLanguageId: String,
        val textGetter: (NodeText) -> String,
        val textSetter: (NodeText, String) -> NodeText,
        val languageGetter: (NodeText) -> String,
        val languageSetter: (NodeText, String) -> NodeText,
    )
}

private class BinaryContentPanel : JPanel(BorderLayout()) {
    private val summary = JLabel()
    private val content = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
        wrapStyleWord = false
    }
    private var renderGeneration = 0L
    private var renderWorker: SwingWorker<String, Unit>? = null

    init {
        add(summary, BorderLayout.NORTH)
        add(JScrollPane(content), BorderLayout.CENTER)
    }

    fun bind(node: Node) {
        val bytes = node.binaryContent ?: byteArrayOf()
        val generation = ++renderGeneration
        summary.text = "${bytes.size} bytes${node.technology.contentType.takeIf(String::isNotBlank)?.let { " | $it" }.orEmpty()}"
        content.text = "Loading binary preview..."
        renderWorker?.cancel(true)
        renderWorker = object : SwingWorker<String, Unit>() {
            override fun doInBackground(): String = binaryHexDump(bytes)

            override fun done() {
                if (isCancelled || generation != renderGeneration) return
                content.text = runCatching { get() }
                    .getOrElse { "Unable to render binary preview." }
                content.caretPosition = 0
            }
        }.also { it.execute() }
    }
}

private class ImagePreviewPanel : JPanel(BorderLayout()) {
    private val status = JLabel("No preview", SwingConstants.CENTER)
    private val imageLabel = JLabel("", SwingConstants.CENTER)
    private var renderGeneration = 0L
    private var renderWorker: SwingWorker<BufferedImage?, Unit>? = null

    init {
        add(status, BorderLayout.NORTH)
        add(JScrollPane(imageLabel), BorderLayout.CENTER)
    }

    fun bind(node: Node, bytes: ByteArray) {
        val contentType = node.technology.contentType
        val nodeName = node.name
        val generation = ++renderGeneration
        renderWorker?.cancel(true)
        imageLabel.icon = null
        status.text = "$nodeName | Loading image preview..."
        renderWorker = object : SwingWorker<BufferedImage?, Unit>() {
            override fun doInBackground(): BufferedImage? = when {
                contentType == "image/svg+xml" -> renderSvgPreview(bytes)
                else -> runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            }

            override fun done() {
                if (isCancelled || generation != renderGeneration) return
                val image = runCatching { get() }.getOrNull()
                if (image != null) {
                    imageLabel.icon = javax.swing.ImageIcon(image)
                    status.text = "$nodeName | ${image.width} x ${image.height}"
                } else {
                    imageLabel.icon = null
                    status.text = "$nodeName | preview unavailable; binary content is preserved"
                }
            }
        }.also { it.execute() }
    }
}

private fun binaryHexDump(bytes: ByteArray): String {
    val displayed = bytes.take(1_048_576).toByteArray()
    return buildString {
        displayed.asList().chunked(16).forEachIndexed { row, chunk ->
            append(String.format("%08x  ", row * 16))
            chunk.forEach { byte -> append(String.format("%02x ", byte.toInt() and 0xff)) }
            repeat(16 - chunk.size) { append("   ") }
            append(" | ")
            chunk.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(if (value in 32..126) value.toChar() else '.')
            }
            appendLine("|")
        }
        if (bytes.size > displayed.size) {
            appendLine("... ${bytes.size - displayed.size} more bytes not displayed")
        }
    }
}

private fun renderSvgPreview(bytes: ByteArray): BufferedImage? = runCatching {
    val output = ByteArrayOutputStream()
    PNGTranscoder().apply {
        addTranscodingHint(PNGTranscoder.KEY_WIDTH, 900f)
        addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 700f)
    }.transcode(
        TranscoderInput(ByteArrayInputStream(bytes)),
        TranscoderOutput(output),
    )
    ImageIO.read(ByteArrayInputStream(output.toByteArray()))
}.getOrNull()

private class TestTextEditorPanel(
    private val repository: DocumentRepository,
    private val nodeId: NodeId,
    private val rawEditor: GridCodeEditorAdapter,
    private val languageSelector: JComboBox<String>,
    private val saveText: (String) -> Unit,
) : JTabbedPane() {
    private val format = JComboBox(TabularFormat.entries.toTypedArray())
    private val message = JLabel("Table view uses link names as test input and expected output columns.")
    private val tableModel = object : DefaultTableModel() {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }
    private val table = JTable(tableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_OFF
    }
    private var loadingTable = false
    private var tableDirty = false
    private var previousTabIndex = 0
    private val tableSyncTimer = Timer(300) {
        syncTableToRaw(force = false, commitEditing = false, status = "Table changes synchronized to the raw tests text.")
    }.apply {
        isRepeats = false
    }

    init {
        addTab("Raw", rawPanel())
        addTab("Table", tablePanel())
        tableModel.addTableModelListener {
            if (!loadingTable) {
                tableDirty = true
                message.text = "Table changes are pending; raw tests text will be synchronized automatically."
                tableSyncTimer.restart()
            }
        }
        format.addActionListener {
            if (!loadingTable && selectedIndex == 1) {
                tableDirty = true
                syncTableToRaw(force = true, commitEditing = true, status = "Saved ${selectedFormat().label} table to the tests text property.")
            }
        }
        addChangeListener {
            if (previousTabIndex == 1 && selectedIndex != 1) {
                syncTableToRaw(force = true, commitEditing = true, status = "Table changes synchronized to the raw tests text.")
            }
            if (selectedIndex == 1) loadTable()
            previousTabIndex = selectedIndex
        }
    }

    fun bindLanguage(languageId: String) {
        val selected = languageId.ifBlank { "json" }
        languageSelector.selectedItem = selected
        rawEditor.setTechnology(effectiveTechnology().copy(languageId = selected))
    }

    fun focusEditor() {
        rawEditor.focus()
    }

    fun refreshTopology() {
        if (selectedIndex == 1) {
            syncTableToRaw(force = false, commitEditing = true, status = "Table changes synchronized to the raw tests text.")
            loadTable()
        }
    }

    private fun tablePanel(): JComponent {
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(JLabel("Format"))
            add(format)
            add(JButton("Load Table").apply { addActionListener { loadTable() } })
            add(JButton("Apply Table").apply { addActionListener { applyTable() } })
            add(JButton("Add Row").apply { addActionListener { tableModel.addRow(emptyRow()) } })
            add(JButton("Remove Row").apply { addActionListener { removeSelectedRows() } })
        }
        return JPanel(BorderLayout()).apply {
            add(controls, BorderLayout.NORTH)
            add(JScrollPane(table), BorderLayout.CENTER)
            add(message, BorderLayout.SOUTH)
        }
    }

    private fun rawPanel(): JComponent {
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(JLabel("Language"))
            add(languageSelector)
        }
        return JPanel(BorderLayout()).apply {
            add(controls, BorderLayout.NORTH)
            add(rawEditor, BorderLayout.CENTER)
        }
    }

    private fun loadTable() {
        syncTableToRaw(force = false, commitEditing = true, status = "Table changes synchronized to the raw tests text.")
        val expectedColumns = expectedColumns()
        val parsed = TabularTextCodec.parse(rawEditor.getText())
        val data = (parsed?.data ?: TabularData(expectedColumns, mutableListOf()))
            .withColumns(expectedColumns)
            .withBlankRowIfEmpty()
        loadingTable = true
        try {
            parsed?.format?.let { format.selectedItem = it }
            tableModel.setColumnIdentifiers(data.columns.toTypedArray())
            tableModel.rowCount = 0
            data.rows.forEach { row ->
                tableModel.addRow(data.columns.map { row[it].orEmpty() }.toTypedArray())
            }
            tableDirty = false
            tableSyncTimer.stop()
        } finally {
            loadingTable = false
        }
        message.text = when {
            rawEditor.getText().isBlank() -> "Initialized from current input/output link names."
            parsed == null -> "Raw tests are not recognized as tabular data; initialized from link names."
            else -> "Loaded ${parsed.format.label} table. Edits synchronize automatically; Apply Table forces a write."
        }
    }

    private fun applyTable() {
        syncTableToRaw(force = true, commitEditing = true, status = "Saved ${selectedFormat().label} table to the tests text property.")
    }

    private fun syncTableToRaw(force: Boolean, commitEditing: Boolean, status: String? = null) {
        if (loadingTable) return
        if (commitEditing && table.isEditing && table.cellEditor?.stopCellEditing() == false) return
        if (!force && !tableDirty) return
        tableSyncTimer.stop()
        val data = tableData()
        val selectedFormat = selectedFormat()
        val next = TabularTextCodec.write(data, selectedFormat)
        rawEditor.setText(next)
        saveText(next)
        tableDirty = false
        status?.let { message.text = it }
    }

    private fun selectedFormat(): TabularFormat =
        format.selectedItem as? TabularFormat ?: TabularFormat.Json

    private fun effectiveTechnology(): TechnologyMetadata {
        val document = repository.getDocument()
        val node = repository.requireNode(nodeId)
        return node.technology.copy(
            languageId = document.effectiveLanguageId(nodeId),
            technologyId = document.effectiveTechnologyId(nodeId),
        )
    }

    private fun tableData(): TabularData {
        val columns = (0 until tableModel.columnCount).map { tableModel.getColumnName(it) }
        val rows = mutableListOf<MutableMap<String, String>>()
        for (rowIndex in 0 until tableModel.rowCount) {
            val row = mutableMapOf<String, String>()
            columns.forEachIndexed { columnIndex, column ->
                row[column] = tableModel.getValueAt(rowIndex, columnIndex)?.toString().orEmpty()
            }
            if (row.values.any { it.isNotBlank() }) rows += row
        }
        return TabularData(columns, rows)
    }

    private fun expectedColumns(): List<String> {
        val document = repository.getDocument()
        val node = document.nodes[nodeId] ?: return listOf(TestCaseColumn)
        val ignored = setOf(LinkStereotype.UsageImport, LinkStereotype.DependencyInjection)
        fun linkColumnNames(ids: List<NodeId>, incoming: Boolean): List<String> =
            ids.mapNotNull(document.nodes::get)
                .filter { linkNode -> LinkClassifier.classify(document, linkNode) !in ignored }
                .mapNotNull { linkNode ->
                    val link = linkNode.link ?: return@mapNotNull null
                    val fallback = if (incoming) link.targetPortName else link.sourcePortName
                    linkNode.name.ifBlank { fallback }.trim().takeIf { it.isNotBlank() }
                }

        val inputs = linkColumnNames(node.incomingLinks, incoming = true).map { "input.$it" }
        val outputs = linkColumnNames(node.outgoingLinks, incoming = false).map { "expected.$it" }
        return (listOf(TestCaseColumn) + inputs + outputs).distinct()
    }

    private fun emptyRow(): Array<String> = Array(tableModel.columnCount.coerceAtLeast(1)) { "" }

    private fun removeSelectedRows() {
        table.selectedRows.sortedDescending().forEach(tableModel::removeRow)
    }

    private companion object {
        private const val TestCaseColumn = "case"
    }
}

private enum class TabularFormat(val label: String) {
    Json("JSON"),
    Csv("CSV"),
    Yaml("YAML"),
    Xml("XML"),
}

private data class TabularParseResult(
    val format: TabularFormat,
    val data: TabularData,
)

private data class TabularData(
    val columns: List<String>,
    val rows: MutableList<MutableMap<String, String>>,
) {
    fun withColumns(expectedColumns: List<String>): TabularData {
        val nextColumns = (expectedColumns + columns).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val nextRows = rows.map { row ->
            val next = mutableMapOf<String, String>()
            nextColumns.forEach { column -> next[column] = row[column].orEmpty() }
            next
        }.toMutableList()
        return TabularData(nextColumns, nextRows)
    }

    fun withBlankRowIfEmpty(): TabularData {
        if (rows.isNotEmpty()) return this
        rows += columns.associateWith { "" }.toMutableMap()
        return this
    }
}

private object TabularTextCodec {
    private val json = Json { prettyPrint = true }

    fun parse(text: String): TabularParseResult? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("[") || trimmed.startsWith("{") -> parseJson(trimmed)
            trimmed.startsWith("<") -> parseXml(trimmed)
            trimmed.startsWith("-") -> parseYaml(trimmed)
            trimmed.lines().firstOrNull()?.contains(",") == true -> parseCsv(trimmed)
            else -> null
        }
    }

    fun write(data: TabularData, format: TabularFormat): String =
        when (format) {
            TabularFormat.Json -> writeJson(data)
            TabularFormat.Csv -> writeCsv(data)
            TabularFormat.Yaml -> writeYaml(data)
            TabularFormat.Xml -> writeXml(data)
        }

    private fun parseJson(text: String): TabularParseResult? = runCatching {
        val element = json.parseToJsonElement(text)
        val rows = when (element) {
            is JsonArray -> element
            is JsonObject -> element["cases"] as? JsonArray ?: JsonArray(listOf(element))
            else -> return null
        }
        val maps = rows.mapNotNull { row ->
            when (row) {
                is JsonObject -> row.entries.associate { it.key to scalarText(it.value) }.toMutableMap()
                is JsonArray -> row.withIndex().associate { (index, value) -> "column_${index + 1}" to scalarText(value) }.toMutableMap()
                else -> null
            }
        }.toMutableList()
        val columns = maps.flatMap { it.keys }.distinct()
        TabularParseResult(TabularFormat.Json, TabularData(columns, maps))
    }.getOrNull()

    private fun writeJson(data: TabularData): String {
        val rows = buildJsonArray {
            data.rows.forEach { row ->
                add(
                    buildJsonObject {
                        data.columns.forEach { column ->
                            put(column, JsonPrimitive(row[column].orEmpty()))
                        }
                    },
                )
            }
        }
        return json.encodeToString(JsonElement.serializer(), rows)
    }

    private fun parseCsv(text: String): TabularParseResult? {
        val rows = text.lines().filter { it.isNotBlank() }.map(::parseCsvRow)
        if (rows.isEmpty()) return null
        val columns = rows.first().mapIndexed { index, value -> value.ifBlank { "column_${index + 1}" } }
        val maps = rows.drop(1).map { row ->
            columns.mapIndexed { index, column -> column to row.getOrElse(index) { "" } }.toMap().toMutableMap()
        }.toMutableList()
        return TabularParseResult(TabularFormat.Csv, TabularData(columns, maps))
    }

    private fun writeCsv(data: TabularData): String =
        (listOf(data.columns) + data.rows.map { row -> data.columns.map { row[it].orEmpty() } })
            .joinToString("\n") { values -> values.joinToString(",") { csvEscape(it) } }

    private fun parseYaml(text: String): TabularParseResult? {
        val rows = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        text.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("- ") -> {
                    current = mutableMapOf()
                    rows += current!!
                    parseYamlPair(line.removePrefix("- ").trim())?.let { (key, value) -> current!![key] = value }
                }
                current != null && ":" in line -> {
                    parseYamlPair(line)?.let { (key, value) -> current!![key] = value }
                }
            }
        }
        if (rows.isEmpty()) return null
        return TabularParseResult(TabularFormat.Yaml, TabularData(rows.flatMap { it.keys }.distinct(), rows))
    }

    private fun writeYaml(data: TabularData): String =
        data.rows.joinToString("\n") { row ->
            data.columns.mapIndexed { index, column ->
                val prefix = if (index == 0) "- " else "  "
                "$prefix$column: ${yamlScalar(row[column].orEmpty())}"
            }.joinToString("\n")
        }

    private fun parseXml(text: String): TabularParseResult? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        val root = document.documentElement ?: return null
        val rowElements = elementChildren(root)
        if (rowElements.isEmpty()) return null
        val rows = rowElements.map { rowElement ->
            val row = mutableMapOf<String, String>()
            elementChildren(rowElement).forEach { child ->
                val name = child.getAttribute("name").ifBlank { child.tagName }
                row[name] = child.textContent.orEmpty()
            }
            row
        }.toMutableList()
        TabularParseResult(TabularFormat.Xml, TabularData(rows.flatMap { it.keys }.distinct(), rows))
    }.getOrNull()

    private fun writeXml(data: TabularData): String = buildString {
        appendLine("<tests>")
        data.rows.forEach { row ->
            appendLine("  <case>")
            data.columns.forEach { column ->
                appendLine("    <cell name=\"${xmlEscape(column)}\">${xmlEscape(row[column].orEmpty())}</cell>")
            }
            appendLine("  </case>")
        }
        appendLine("</tests>")
    }

    private fun scalarText(element: JsonElement): String =
        when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }

    private fun parseCsvRow(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun parseYamlPair(line: String): Pair<String, String>? {
        val key = line.substringBefore(":").trim().takeIf { it.isNotBlank() } ?: return null
        val value = line.substringAfter(":", "").trim().trimMatchingQuotes()
        return key to value
    }

    private fun yamlScalar(value: String): String =
        "'${value.replace("'", "''")}'"

    private fun String.trimMatchingQuotes(): String {
        if (length >= 2 && ((first() == '\'' && last() == '\'') || (first() == '"' && last() == '"'))) {
            return substring(1, length - 1)
        }
        return this
    }

    private fun elementChildren(element: org.w3c.dom.Element): List<org.w3c.dom.Element> {
        val result = mutableListOf<org.w3c.dom.Element>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is org.w3c.dom.Element) result += child
        }
        return result
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}

interface PluginUiProvider {
    val id: String
    val displayName: String
    fun createPanel(repository: DocumentRepository, selectedNodeIds: List<NodeId>): JComponent
}

private data class AppCommand(
    val id: String,
    val title: String,
    val keyStroke: KeyStroke? = null,
    val enabled: () -> Boolean = { true },
    val action: () -> Unit,
)

private data class PluginToolbarButton(
    val label: String,
    val action: () -> Unit,
)

private data class PluginContentTab(
    val title: String,
    val createPanel: () -> JComponent,
)

interface ThreadworkDesktopPlugin {
    val id: String
    val displayName: String
    fun configure(context: ThreadworkPluginContext)
}

interface ThreadworkPluginContext {
    fun document(): ThreadworkDocument
    fun postModelUpdate(label: String, update: (DocumentRepository) -> Unit)
    fun addToolbarButton(label: String, action: () -> Unit)
    fun addContentTab(title: String, createPanel: () -> JComponent)
    fun addCommand(id: String, title: String, keyStroke: KeyStroke? = null, action: () -> Unit)
}

fun defaultPluginsFolder(): Path {
    val location = ThreadworkDesktopApp::class.java.protectionDomain.codeSource?.location?.toURI()
    val binary = location?.let(Path::of)
    val base = when {
        binary == null -> Path.of(".")
        Files.isRegularFile(binary) -> binary.parent ?: Path.of(".")
        else -> binary
    }
    return base.resolve("plugins").toAbsolutePath().normalize()
}

fun loadDesktopPlugins(folder: Path): List<ThreadworkDesktopPlugin> {
    val urls = pluginJarUrls(folder)
    if (urls.isEmpty()) return emptyList()
    val classLoader = URLClassLoader(urls, ThreadworkDesktopPlugin::class.java.classLoader)
    return ServiceLoader.load(ThreadworkDesktopPlugin::class.java, classLoader).toList()
}

fun loadCompilerPlugins(folder: Path): List<CompilerPlugin> {
    val urls = pluginJarUrls(folder)
    if (urls.isEmpty()) return emptyList()
    val classLoader = URLClassLoader(urls, CompilerPlugin::class.java.classLoader)
    return ServiceLoader.load(CompilerPlugin::class.java, classLoader).toList()
}

private fun pluginJarUrls(folder: Path): Array<java.net.URL> {
    Files.createDirectories(folder)
    val jars = Files.list(folder).use { stream ->
        stream
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
            .sorted()
            .toList()
    }
    return jars.map { it.toUri().toURL() }.toTypedArray()
}

private class CommandListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: javax.swing.JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (component is JLabel && value is AppCommand) {
            component.text = value.title
        }
        return component
    }
}

private class SimpleDocumentListener(private val onChange: () -> Unit) : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) = onChange()
    override fun removeUpdate(e: DocumentEvent) = onChange()
    override fun changedUpdate(e: DocumentEvent) = onChange()
}

private fun distributeTargets(nodes: List<Node>, vertical: Boolean): Map<Node, Pair<Double, Double>> {
    val sorted = if (vertical) {
        nodes.sortedWith(compareBy<Node> { it.layout.y }.thenBy { it.layout.x }.thenBy { it.id.value })
    } else {
        nodes.sortedWith(compareBy<Node> { it.layout.x }.thenBy { it.layout.y }.thenBy { it.id.value })
    }
    val first = sorted.first()
    val last = sorted.last()
    val start = if (vertical) first.layout.y else first.layout.x
    val end = if (vertical) last.layout.y else last.layout.x
    val step = (end - start) / (sorted.size - 1)
    return sorted.mapIndexed { index, node ->
        val position = start + step * index
        node to if (vertical) {
            node.layout.x to position
        } else {
            position to node.layout.y
        }
    }.toMap()
}

private fun NodeLayout.rect(): Rectangle = Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())
private fun NodeLayout.center(): Point = Point((x + width / 2).toInt(), (y + height / 2).toInt())

fun launchDesktopApp(pluginsFolder: Path? = null) {
    SwingUtilities.invokeLater {
        ThreadworkAppearance.applyLookAndFeel()
        JFrame.setDefaultLookAndFeelDecorated(true)
        ThreadworkDesktopApp(pluginsFolder = pluginsFolder ?: defaultPluginsFolder()).show()
    }
}
