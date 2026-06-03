package com.orchestra.app.ui

import com.orchestra.app.editor.EditorCompletionContext
import com.orchestra.app.editor.GridCodeEditorAdapter
import com.orchestra.app.editor.RegexSyntaxHighlighter
import com.orchestra.app.fonts.OrchestraFonts
import com.orchestra.Version
import com.orchestra.compiler.api.CompilerPlugin
import com.orchestra.compiler.naivekotlin.NaiveKotlinCompiler
import com.orchestra.completion.ModelAwareCompletionService
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.LinkTransportKinds
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodeLayout
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.NodeText
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.PortDirection
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.core.model.VOID_LANGUAGE_ID
import com.orchestra.core.model.effectiveLanguageId
import com.orchestra.core.model.effectiveTechnologyId
import com.orchestra.core.model.effectiveTextLanguageId
import com.orchestra.storage.DocumentRepository
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.KotlinxJsonDocumentStore
import com.orchestra.storage.newDocument
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
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilenameFilter
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.Files
import java.net.URLClassLoader
import java.util.ServiceLoader
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.TransferHandler
import javax.swing.WindowConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.roundToInt

class OrchestraDesktopApp(
    private val repository: DocumentRepository = InMemoryDocumentRepository(newDocument("Untitled Orchestra")),
    private val store: KotlinxJsonDocumentStore = KotlinxJsonDocumentStore(),
    private val pluginsFolder: Path = defaultPluginsFolder(),
    private val uiPlugins: List<OrchestraDesktopPlugin> = loadDesktopPlugins(pluginsFolder),
) {
    private companion object {
        const val NATIVE_PROJECT_EXTENSION = "orch"
        const val LEGACY_PROJECT_EXTENSION = "json"
        const val DEFAULT_PROJECT_NAME = "document.orch"
        const val MAX_HISTORY_SNAPSHOTS = 100
    }

    private val frame = JFrame()
    private val selection = linkedSetOf<NodeId>()
    private val canvas = GraphCanvas(
        repository,
        selection,
        { onSelectionChanged() },
        ::refreshAll,
        ::onCanvasModeChanged,
        ::openNodeInEntityEditor,
    )
    private val hierarchyTree = JTree()
    private val detailsHierarchyTree = JTree()
    private val selectedEntitiesTree = JTree()
    private val compilerPlugins: List<CompilerPlugin> = loadCompilerPlugins(pluginsFolder) + NaiveKotlinCompiler()
    private val languageIds = availableLanguageIds()
    private val inspector = InspectorPanel(repository, ::refreshAll, languageIds)
    private val editorTabs = NodeEditorTabs(repository, ::refreshAll, ::checkpointHistory, ::undoDocument, ::redoDocument, languageIds)
    private val status = JLabel("Status and Messages").apply {
        border = BorderFactory.createEmptyBorder(3, 8, 3, 8)
    }
    private lateinit var workflows: JTabbedPane
    private val modeButtons = mutableMapOf<CanvasMode, JToggleButton>()
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
    private val autosaveTimer = Timer(10_000) { autosave() }.apply { isRepeats = true }

    fun show() {
        registerBuiltInCommands()
        configurePlugins()
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
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
        frame.isVisible = true
    }

    private fun layout(): JComponent {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            val modes = ButtonGroup()
            listOf(
                modeButton("Select", CanvasMode.Select),
                modeButton("Node", CanvasMode.CreateNode),
                modeButton("Link", CanvasMode.CreateLink),
            ).forEach {
                modes.add(it)
                add(it)
            }
            add(button("Sheet") { executeCommand("sheet.toggle") })
            add(JComboBox(canvas.sheetFormatChoices().toTypedArray()).apply {
                isEditable = false
                selectedItem = canvas.selectedSheetFormatChoice()
                addActionListener { canvas.setSheetFormatChoice(selectedItem?.toString().orEmpty()) }
                preferredSize = Dimension(120, preferredSize.height)
                maximumSize = preferredSize
            })
            pluginToolbarButtons.forEach { button ->
                add(JButton(button.label).apply { addActionListener { button.action() } })
            }
            add(button("About") { executeCommand("help.about") })
            modeButtons[canvas.mode]?.isSelected = true
        }

        configureHierarchyTree(hierarchyTree, editable = true, dragAndDrop = true, updatesSelection = true)
        configureHierarchyTree(detailsHierarchyTree, editable = true, dragAndDrop = true, updatesSelection = true)
        configureHierarchyTree(selectedEntitiesTree, editable = false, dragAndDrop = false, updatesSelection = false)

        val flowDesigner = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            labeledPanel("Entity Hierarchy Tree", JScrollPane(hierarchyTree)),
            JScrollPane(canvas),
        ).apply {
            resizeWeight = 0.16
        }

        val selectedAndHierarchy = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            labeledPanel("selected entities list", JScrollPane(selectedEntitiesTree)),
            labeledPanel("Entity Hierarchy Tree", JScrollPane(detailsHierarchyTree)),
        ).apply {
            resizeWeight = 0.45
        }
        val editorAndInspector = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            editorTabs,
            labeledPanel("Inspector", JScrollPane(inspector)),
        ).apply {
            resizeWeight = 0.78
        }
        val detailsEditor = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, selectedAndHierarchy, editorAndInspector).apply {
            resizeWeight = 0.18
        }

        workflows = JTabbedPane().apply {
            addTab("Flow Designer", flowDesigner)
            addTab("Entities Edit(IDE)", detailsEditor)
            pluginContentTabs.forEach { tab ->
                addTab(tab.title, tab.createPanel())
            }
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(workflows, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }
    }

    private fun menuBar() = JMenuBar().apply {
        add(JMenu("File").apply {
            add(commandItem("file.new"))
            add(commandItem("file.open"))
            add(commandItem("file.save"))
            add(commandItem("file.saveAs"))
            add(commandItem("sheet.export"))
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
        })
        add(JMenu("Graph").apply {
            add(commandItem("graph.mode.select"))
            add(commandItem("graph.mode.node"))
            add(commandItem("graph.mode.link"))
            add(commandItem("graph.deleteSelection"))
            add(commandItem("commands.palette"))
        })
        add(JMenu("Help").apply {
            add(commandItem("help.about"))
        })
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
        registerCommand(AppCommand("file.quit", "File: Quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut)) { quit() })
        registerCommand(AppCommand("app.options", "Application: Options...") { showOptions() })
        registerCommand(AppCommand("edit.undo", "Edit: Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut)) { undo() })
        registerCommand(AppCommand("edit.redo", "Edit: Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut)) { redo() })
        registerCommand(AppCommand("edit.redoAlt", "Edit: Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, shiftShortcut)) { redo() })
        registerCommand(AppCommand("edit.cut", "Edit: Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcut)) { cut() })
        registerCommand(AppCommand("edit.copy", "Edit: Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut)) { copy() })
        registerCommand(AppCommand("edit.paste", "Edit: Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcut)) { paste() })
        registerCommand(AppCommand("graph.mode.select", "Graph: Select Mode", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)) { canvas.setMode(CanvasMode.Select) })
        registerCommand(AppCommand("graph.mode.node", "Graph: Node Mode") { canvas.setMode(CanvasMode.CreateNode) })
        registerCommand(AppCommand("graph.mode.link", "Graph: Link Mode") { canvas.setMode(CanvasMode.CreateLink) })
        registerCommand(AppCommand("graph.deleteSelection", "Graph: Delete Selection", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)) {
            if (graphShortcutEnabled()) deleteSelection()
        })
        registerCommand(AppCommand("sheet.toggle", "Sheet: Toggle Preview") { canvas.toggleSheet() })
        registerCommand(AppCommand("sheet.export", "Sheet: Export...") { canvas.exportSheet(frame) })
        registerCommand(AppCommand("commands.palette", "Commands: Open Palette", KeyStroke.getKeyStroke(KeyEvent.VK_P, shiftShortcut)) { showCommandPalette() })
        registerCommand(AppCommand("help.about", "Help: About") { showAbout() })
    }

    private fun registerCommand(command: AppCommand) {
        commands[command.id] = command
    }

    private fun configurePlugins() {
        if (uiPlugins.isEmpty()) return
        val context = object : OrchestraPluginContext {
            override fun document(): InflowDocument = repository.getDocument()

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

    private fun commandItem(id: String): JMenuItem {
        val command = commands.getValue(id)
        return JMenuItem(command.title.substringAfter(": ")).apply {
            accelerator = command.keyStroke
            isEnabled = command.enabled()
            addActionListener { executeCommand(id) }
        }
    }

    private fun newFile() {
        autosave()
        repository.replaceDocument(newDocument("Untitled Orchestra"))
        selection.clear()
        currentFile = null
        resetHistory()
        refreshAll()
        updateWindowTitle()
    }

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
        val designerSelector = JComboBox(OrchestraFonts.designerOptions.map { it.label }.toTypedArray()).apply {
            selectedItem = OrchestraFonts.optionLabel(OrchestraFonts.designerFontId, OrchestraFonts.designerOptions)
        }
        val codeSelector = JComboBox(OrchestraFonts.codeOptions.map { it.label }.toTypedArray()).apply {
            selectedItem = OrchestraFonts.optionLabel(OrchestraFonts.codeFontId, OrchestraFonts.codeOptions)
        }
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("Flow Designer font"))
            add(designerSelector)
            add(JLabel("Code editor font").apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
            add(codeSelector)
        }
        val result = JOptionPane.showConfirmDialog(frame, content, "Options", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result != JOptionPane.OK_OPTION) return

        OrchestraFonts.designerFontId = OrchestraFonts.optionId(designerSelector.selectedItem?.toString().orEmpty(), OrchestraFonts.designerOptions)
        OrchestraFonts.codeFontId = OrchestraFonts.optionId(codeSelector.selectedItem?.toString().orEmpty(), OrchestraFonts.codeOptions)
        applyFontOptions()
        status.text = "Options updated"
    }

    private fun applyFontOptions() {
        canvas.setDesignerFont(OrchestraFonts.designerFont(13f))
        editorTabs.applyCodeEditorFont(OrchestraFonts.codeFont(14f))
    }

    private fun graphShortcutEnabled(): Boolean {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return focusOwner !is JTextComponent && focusOwner !is GridCodeEditorAdapter
    }

    private fun updateWindowTitle() {
        val file = currentFile?.toAbsolutePath()?.toString() ?: "Untitled"
        frame.title = "orchestra - $file"
    }

    private fun showAbout() {
        val version = Version.CURRENT
        val details = buildString {
            appendLine("orchestra")
            appendLine("Version: ${version.semver}")
            appendLine("Git commit: ${version.gitCommitId}")
            appendLine("Git tag: ${version.gitTag ?: "-"}")
            appendLine("Build date: ${version.buildDate}")
            appendLine("Plugins folder: ${pluginsFolder.toAbsolutePath().normalize()}")
            appendLine("Loaded UI plugins: ${uiPlugins.size}")
            appendLine("Loaded compiler plugins: ${compilerPlugins.size}")
        }
        JOptionPane.showMessageDialog(frame, details, "About orchestra", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun availableLanguageIds(): List<String> =
        (RegexSyntaxHighlighter.availableLanguageIds() + compilerPlugins.flatMap { it.supportedLanguageIds })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun openFile() {
        val path = chooseDocumentPath("Open .orch", FileDialog.LOAD) ?: return
        autosave()
        repository.replaceDocument(store.load(path))
        currentFile = path
        selection.clear()
        resetHistory()
        refreshAll()
        updateWindowTitle()
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

    private fun documentSnapshot(): String =
        historyJson.encodeToString(InflowDocument.serializer(), repository.getDocument())

    private fun documentFromSnapshot(snapshot: String): InflowDocument =
        historyJson.decodeFromString(InflowDocument.serializer(), snapshot)

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

    private fun onSelectionChanged(activeSection: NodeTextSection? = null) {
        inspector.bind(selection.firstOrNull())
        editorTabs.bind(selection.toList(), activeSection)
        refreshSelectedEntitiesTree()
        canvas.repaint()
    }

    private fun openNodeInEntityEditor(id: NodeId) {
        selection.clear()
        selection += id
        onSelectionChanged()
        if (::workflows.isInitialized) {
            workflows.selectedIndex = 1
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

    private fun treeNode(document: InflowDocument, id: NodeId): TreeNodeRef {
        val node = repository.requireNode(id)
        return TreeNodeRef(id, node.name, treeCategory(node)).apply {
            node.children.mapNotNull(document.nodes::get)
                .sortedWith(compareBy<Node> { treeCategory(it).sortOrder }.thenBy { it.name.lowercase() })
                .forEach { add(treeNode(document, it.id)) }
            if (node.id != document.rootNodeId) {
                textFeatureRefs().forEach { (feature, section) ->
                    add(TreeNodeRef(id, feature, TreeItemCategory.Feature, section))
                }
            }
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
        tree.cellRenderer = HierarchyTreeCellRenderer()
        if (dragAndDrop) {
            tree.dragEnabled = true
            tree.dropMode = DropMode.ON
            tree.transferHandler = HierarchyTransferHandler()
        }
        tree.addTreeSelectionListener {
            val ref = tree.lastSelectedPathComponent as? TreeNodeRef ?: return@addTreeSelectionListener
            if (ref.category == TreeItemCategory.Feature) {
                selection.clear()
                selection += ref.id
                onSelectionChanged(ref.textSection)
                return@addTreeSelectionListener
            }
            if (updatesSelection) {
                selection.clear()
                selection += ref.id
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
                    if (ref.category == TreeItemCategory.Feature) return
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
    }

    private fun refreshSelectedEntitiesTree() {
        val root = DefaultMutableTreeNode("selected entities list")
        val document = repository.getDocument()
        selection.mapNotNull(document.nodes::get)
            .sortedWith(compareBy<Node> { treeCategory(it).sortOrder }.thenBy { it.name.lowercase() })
            .forEach { node ->
                root.add(TreeNodeRef(node.id, node.name, treeCategory(node)).apply {
                    textFeatureRefs().forEach { (feature, section) ->
                        add(TreeNodeRef(node.id, feature, TreeItemCategory.Feature, section))
                    }
                })
            }
        selectedEntitiesTree.model = DefaultTreeModel(root)
        selectedEntitiesTree.expandRow(0)
    }

    private fun textFeatureRefs(): List<Pair<String, NodeTextSection>> = listOf(
        "init-code" to NodeTextSection.Initialization,
        "code" to NodeTextSection.Source,
        "spec" to NodeTextSection.Specification,
        "test-data" to NodeTextSection.Tests,
        "usage-instructions" to NodeTextSection.AiInstructions,
    )

    private fun treeCategory(node: Node): TreeItemCategory = when {
        node.isLink -> TreeItemCategory.Link
        node.isComposite || node.id == repository.getDocument().rootNodeId -> TreeItemCategory.Composite
        else -> TreeItemCategory.Processing
    }

    private inner class HierarchyTransferHandler : TransferHandler() {
        override fun getSourceActions(c: JComponent): Int = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val tree = c as? JTree ?: return null
            val ref = tree.lastSelectedPathComponent as? TreeNodeRef ?: return null
            if (ref.category == TreeItemCategory.Feature || ref.id == repository.getDocument().rootNodeId) return null
            return StringSelection(ref.id.value)
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
            val sourceId = NodeId(sourceValue)
            if (sourceId == targetRef.id || sourceId == repository.getDocument().rootNodeId) return false
            return runCatching {
                repository.moveNode(sourceId, targetRef.id)
                selection.clear()
                selection += sourceId
                status.text = "Moved $sourceValue under ${targetNode.name}"
                refreshAll()
                tree.selectionPath = drop.path
                true
            }.getOrDefault(false)
        }
    }

    private fun modeButton(label: String, mode: CanvasMode) = JToggleButton(label).apply {
        modeButtons[mode] = this
        addActionListener { canvas.setMode(mode) }
    }

    private fun button(label: String, action: () -> Unit) = JButton(label).apply { addActionListener { action() } }
    private fun item(label: String, action: () -> Unit) = JMenuItem(label).apply { addActionListener { action() } }
}

private enum class TreeItemCategory(val sortOrder: Int, val marker: Color) {
    Composite(0, Color(0xffcc33)),
    Processing(1, Color(0x2f6bdc)),
    Link(2, Color(0xf39c12)),
    Feature(3, Color.WHITE),
}

private class TreeNodeRef(
    val id: NodeId,
    label: String,
    val category: TreeItemCategory,
    val textSection: NodeTextSection? = null,
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
    CreateLink,
}

class GraphCanvas(
    private val repository: DocumentRepository,
    private val selection: LinkedHashSet<NodeId>,
    private val onSelectionChanged: () -> Unit,
    private val refreshAll: () -> Unit,
    private val onModeChanged: (CanvasMode) -> Unit,
    private val onNodeDoubleClicked: (NodeId) -> Unit = {},
) : JPanel() {
    var mode: CanvasMode = CanvasMode.Select
        private set
    private var dragStart: Point? = null
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
    private var designerFont: Font = OrchestraFonts.designerFont(13f)
    private val routeCache = mutableMapOf<NodeId, LinkRoute>()
    private val rerouteTimer = Timer(180) { rebuildRouteCache() }.apply { isRepeats = false }

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
    private data class BomRow(val index: Int, val name: String, val kind: String)
    private data class SheetPlan(
        val format: SheetFormat,
        val sheet: Rectangle,
        val drawing: Rectangle,
        val titleBlock: Rectangle,
        val partsList: Rectangle,
        val contentBounds: Rectangle,
        val scopeIds: Set<NodeId>,
        val bomRows: List<BomRow>,
    )
    private data class RouteSegment(val a: Point, val b: Point, val linkId: NodeId)
    private data class SheetCandidate(
        val format: SheetFormat,
        val width: Int,
        val height: Int,
        val coverage: Double,
    ) {
        val area: Int get() = width * height
    }

    private companion object {
        const val AUTO_SHEET_FORMAT = "Auto"
        const val PORT_TOP_SPACING = 28
        const val PORT_SPACING = 30
        const val PORT_BOTTOM_SPACING = 20
        const val PORT_STUB_LENGTH = 46
        const val PORT_OUTSIDE_OFFSET = 20
        const val ROUTING_STEP = 40
        const val ROUTING_CHAMFER = 22
        const val ROUTING_OBSTACLE_PADDING = 24
        const val ROUTING_LANE_SPAN = 7
        const val SNAP_GRID_STEP = 40
        const val SNAP_GRAB_RADIUS_PX = 5.0
        const val COMPOSITE_TOP_PADDING = 80
        const val SHEET_UNITS_PER_MM = 4.0
        const val SHEET_MARGIN_MM = 10.0
        const val DRAWING_PAD_MM = 12.0
        const val TITLE_BLOCK_WIDTH_MM = 180.0
        const val TITLE_BLOCK_HEIGHT_MM = 36.0
        const val PARTS_LIST_WIDTH_MM = 72.0
        const val PARTS_ROW_HEIGHT = 20
        const val ROLL_MAX_LENGTH_MM = 1500.0

        val SHEET_FORMATS = listOf(
            SheetFormat("A4", 210.0, 297.0),
            SheetFormat("A3", 297.0, 420.0),
            SheetFormat("A2", 420.0, 594.0),
            SheetFormat("A1", 594.0, 841.0),
            SheetFormat("A0", 841.0, 1189.0),
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
        background = Color(0xf7f7f7)
        preferredSize = Dimension(2400, 1800)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = handlePressed(e)
            override fun mouseDragged(e: MouseEvent) = handleDragged(e)
            override fun mouseReleased(e: MouseEvent) = handleReleased(e)
            override fun mouseClicked(e: MouseEvent) = handleClicked(e)
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val before = modelPoint(e.point)
                zoom = (zoom - e.preciseWheelRotation * 0.08).coerceIn(0.25, 2.5)
                panX = e.point.x / zoom - before.x
                panY = e.point.y / zoom - before.y
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
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

    fun toggleSheet() {
        showSheet = !showSheet
        repaint()
    }

    fun sheetFormatChoices(): List<String> = listOf(AUTO_SHEET_FORMAT) + SHEET_FORMATS.map { it.id }

    fun selectedSheetFormatChoice(): String = sheetFormatChoice

    fun setSheetFormatChoice(choice: String) {
        val next = choice.ifBlank { AUTO_SHEET_FORMAT }
        if (sheetFormatChoice == next) return
        sheetFormatChoice = next
        repaint()
    }

    fun setDesignerFont(font: Font) {
        designerFont = font
        this.font = font
        repaint()
    }

    fun exportSheet(parent: JFrame) {
        val format = JOptionPane.showInputDialog(
            parent,
            "Export format",
            "Export Sheet",
            JOptionPane.PLAIN_MESSAGE,
            null,
            arrayOf("pdf", "png", "svg"),
            "pdf",
        ) as? String ?: return
        val chooser = JFileChooser().apply {
            dialogTitle = "Save sheet as ${format.uppercase()}"
            fileFilter = FileNameExtensionFilter(format.uppercase(), format)
            selectedFile = File("orchestra-sheet.$format")
        }
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val file = withExtension(chooser.selectedFile, format)
        runCatching {
            when (format) {
                "svg" -> writeSvgSheet(file)
                "png",
                "pdf" -> {
                    val image = renderSheetImage() ?: error("Nothing to export.")
                    if (format == "png") ImageIO.write(image, "png", file) else writePdfWithJpeg(file, image)
                }
            }
        }.onSuccess {
            JOptionPane.showMessageDialog(parent, "Saved ${file.absolutePath}", "Export Sheet", JOptionPane.INFORMATION_MESSAGE)
        }.onFailure {
            JOptionPane.showMessageDialog(parent, it.message ?: "Export failed.", "Export Sheet", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun copySelection() {
        clipboard = selection.mapNotNull { repository.getNode(it) }.filter { !it.isLink }.map { it.copy() }
    }

    fun pasteSelection() {
        val root = repository.getDocument().rootNodeId
        val pasted = mutableListOf<NodeId>()
        clipboard.forEachIndexed { index, node ->
            val copy = repository.createNode(root, "${node.name} copy", node.kind)
            repository.updateNodeLayout(copy.id, node.layout.copy(x = node.layout.x + 40 + index * 20, y = node.layout.y + 40 + index * 20))
            repository.updateNodeText(copy.id, node.text.copy())
            repository.updateNodeTechnology(copy.id, node.technology.copy())
            node.ports.forEach { repository.addPort(copy.id, it.copy(id = "${it.id}_copy_$index")) }
            pasted += copy.id
        }
        selection.clear()
        selection += pasted
        refreshAll()
    }

    fun refreshBoundsFromChildren() {
        val document = repository.getDocument()
        document.nodes.values
            .filter { !it.isLink && it.id != document.rootNodeId }
            .forEach(::ensureLayoutCanHoldPorts)
        document.nodes.values.filter { it.children.isNotEmpty() }.sortedByDescending { depthOf(it) }.forEach { parent ->
            val boxes = parent.children.mapNotNull(document.nodes::get).filter { !it.isLink }.map { it.layout }
            if (boxes.isNotEmpty()) {
                val childHeight = boxes.maxOf { it.y + it.height } - boxes.minOf { it.y } + 96
                parent.layout = NodeLayout(
                    x = boxes.minOf { it.x } - 32,
                    y = boxes.minOf { it.y } - COMPOSITE_TOP_PADDING,
                    width = boxes.maxOf { it.x + it.width } - boxes.minOf { it.x } + 64,
                    height = max(childHeight + (COMPOSITE_TOP_PADDING - 48), requiredPortHeight(parent)),
                )
            }
        }
        scheduleReroute()
    }

    private fun ensureLayoutCanHoldPorts(node: Node) {
        node.layout.height = max(node.layout.height, requiredPortHeight(node))
    }

    private fun requiredPortHeight(node: Node): Double {
        val portCount = max(linksOnSide(node, -1).size, linksOnSide(node, 1).size)
        if (portCount == 0) return 0.0
        return (PORT_TOP_SPACING + portCount * PORT_SPACING + PORT_BOTTOM_SPACING).toDouble()
    }

    private fun linksOnSide(node: Node, side: Int): List<Node> {
        val document = repository.getDocument()
        val ids = node.outgoingLinks + node.incomingLinks
        return ids.distinct().mapNotNull(document.nodes::get)
            .filter { linkNode ->
                val link = linkNode.link ?: return@filter false
                val outgoing = link.sourceNodeId == node.id
                linkSide(node, linkNode, outgoing) == side
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
        g2.scale(zoom, zoom)
        g2.translate(panX, panY)
        if (showSheet) drawIsoSheet(g2, includeGrid = true)
        if (!showSheet) drawGrid(g2)
        drawGraph(g2)
        selectionRect?.let {
            g2.color = Color(0x3366cc55, true)
            g2.fill(it)
            g2.color = Color(0x3366cc)
            g2.draw(it)
        }
    }

    private fun drawGrid(g2: Graphics2D, bounds: Rectangle? = null) {
        g2.color = Color(0xe2e2e2)
        val step = 40
        val minX = bounds?.x?.let { floor(it.toDouble() / step).toInt() * step } ?: (floor(-panX / step).toInt() * step)
        val minY = bounds?.y?.let { floor(it.toDouble() / step).toInt() * step } ?: (floor(-panY / step).toInt() * step)
        val maxX = bounds?.let { ceil((it.x + it.width).toDouble() / step).toInt() * step } ?: (ceil((width / zoom - panX) / step).toInt() * step)
        val maxY = bounds?.let { ceil((it.y + it.height).toDouble() / step).toInt() * step } ?: (ceil((height / zoom - panY) / step).toInt() * step)
        for (x in minX..maxX step step) {
            g2.color = if (x % 200 == 0) Color(0xd1d1df) else Color(0xe2e2e2)
            g2.drawLine(x, minY, x, maxY)
        }
        for (y in minY..maxY step step) {
            g2.color = if (y % 200 == 0) Color(0xd1d1df) else Color(0xe2e2e2)
            g2.drawLine(minX, y, maxX, y)
        }
    }

    private fun drawGraph(g2: Graphics2D, scopeIds: Set<NodeId>? = null) {
        val document = repository.getDocument()
        val links = document.nodes.values.filter { it.isLink && (scopeIds == null || it.id in scopeIds) }
        document.nodes.values
            .filter { !it.isLink && it.id != document.rootNodeId && (scopeIds == null || it.id in scopeIds) }
            .sortedBy { it.children.isEmpty() }
            .forEach { drawNode(g2, it) }
        links.filterNot(::isDependencyAnnotation).forEach { drawLink(g2, it) }
        drawDependencyAnnotations(g2, links.filter(::isDependencyAnnotation))
    }

    private fun drawIsoSheet(g2: Graphics2D, plan: SheetPlan? = null, includeGrid: Boolean = true) {
        val activePlan = plan ?: sheetPlan() ?: return
        val sheet = activePlan.sheet
        val previousStroke = g2.stroke
        val previousFont = g2.font
        g2.color = Color.WHITE
        g2.fill(sheet)
        if (includeGrid) drawGrid(g2, sheet)
        g2.color = Color(0x999999)
        g2.stroke = BasicStroke(1f)
        g2.draw(sheet)

        drawFoldingMarkers(g2, sheet)
        g2.color = Color(0x777777)
        g2.draw(activePlan.drawing)

        val titleBlock = activePlan.titleBlock
        g2.color = Color(0x777777)
        g2.draw(titleBlock)
        repeat(4) { row ->
            val y = titleBlock.y + (row + 1) * titleBlock.height / 5
            g2.drawLine(titleBlock.x, y, titleBlock.x + titleBlock.width, y)
        }
        g2.drawLine(titleBlock.x + mm(45.0), titleBlock.y, titleBlock.x + mm(45.0), titleBlock.y + titleBlock.height)
        g2.drawLine(titleBlock.x + mm(112.0), titleBlock.y, titleBlock.x + mm(112.0), titleBlock.y + titleBlock.height)
        g2.color = Color(0x444444)
        g2.font = g2.font.deriveFont(12f)
        g2.drawString("orchestra", titleBlock.x + 12, titleBlock.y + 22)
        g2.drawString(repository.getDocument().name, titleBlock.x + 132, titleBlock.y + 22)
        g2.drawString(activePlan.format.id, titleBlock.x + titleBlock.width - 72, titleBlock.y + 22)
        g2.drawString("build ${Version.CURRENT.buildDate.take(10)}", titleBlock.x + 12, titleBlock.y + titleBlock.height - 14)

        val partsList = activePlan.partsList
        g2.color = Color(0xb0b0b0)
        g2.draw(partsList)
        g2.font = g2.font.deriveFont(10f)
        g2.drawString("Parts List", partsList.x + 8, partsList.y + 15)
        repeat((partsList.height / PARTS_ROW_HEIGHT).coerceAtLeast(1)) { row ->
            val y = partsList.y + row * PARTS_ROW_HEIGHT
            g2.drawLine(partsList.x, y, partsList.x + partsList.width, y)
        }
        g2.drawLine(partsList.x + 42, partsList.y, partsList.x + 42, partsList.y + partsList.height)
        g2.drawLine(partsList.x + 128, partsList.y, partsList.x + 128, partsList.y + partsList.height)
        activePlan.bomRows.take((partsList.height / PARTS_ROW_HEIGHT - 1).coerceAtLeast(0)).forEachIndexed { rowIndex, row ->
            val y = partsList.y + (rowIndex + 2) * PARTS_ROW_HEIGHT - 6
            g2.color = Color(0x444444)
            g2.drawString(row.index.toString(), partsList.x + 6, y)
            g2.drawString(row.name.take(18), partsList.x + 48, y)
            g2.drawString(row.kind.take(14), partsList.x + 132, y)
        }
        g2.font = previousFont
        g2.stroke = previousStroke
    }

    private fun sheetPlan(): SheetPlan? {
        val scopeIds = sheetScopeIds()
        val contentBounds = contentBounds(scopeIds) ?: return null
        val bomRows = bomRows(scopeIds)
        val drawingPad = mm(DRAWING_PAD_MM)
        val margin = mm(SHEET_MARGIN_MM)
        val titleWidth = mm(TITLE_BLOCK_WIDTH_MM)
        val titleHeight = mm(TITLE_BLOCK_HEIGHT_MM)
        val partsWidth = mm(PARTS_LIST_WIDTH_MM)
        val partsRequiredHeight = ((bomRows.size + 2) * PARTS_ROW_HEIGHT).coerceAtLeast(mm(80.0))
        val gap = mm(8.0)

        val requiredWidth = margin * 2 + partsWidth + gap + contentBounds.width + drawingPad * 2
        val requiredHeight = margin * 2 + titleHeight + gap + max(partsRequiredHeight, contentBounds.height + drawingPad * 2)
        val occupiedArea = (contentBounds.width + drawingPad * 2) * (contentBounds.height + drawingPad * 2) +
            partsWidth * partsRequiredHeight +
            titleWidth * titleHeight
        val best = when (val choice = sheetFormatChoice) {
            AUTO_SHEET_FORMAT -> autoSheetCandidate(requiredWidth, requiredHeight, partsRequiredHeight, margin, titleHeight, gap, occupiedArea)
            else -> SHEET_FORMATS.firstOrNull { it.id == choice }?.let {
                sheetCandidateFor(it, requiredWidth, requiredHeight, partsRequiredHeight, margin, titleHeight, gap, occupiedArea, enforceFit = false)
            }
        } ?: autoSheetCandidate(requiredWidth, requiredHeight, partsRequiredHeight, margin, titleHeight, gap, occupiedArea)
            ?: return null

        val format = best.format
        val sheetWidth = best.width
        val sheetHeight = best.height
        val sheetX = contentBounds.x - margin - drawingPad
        val sheetY = contentBounds.y - margin - drawingPad
        val sheet = Rectangle(sheetX, sheetY, sheetWidth, sheetHeight)
        val drawing = Rectangle(
            sheet.x + margin,
            sheet.y + margin,
            sheet.width - margin * 2,
            sheet.height - margin * 2,
        )
        val titleBlock = Rectangle(
            drawing.x + drawing.width - titleWidth,
            drawing.y + drawing.height - titleHeight,
            titleWidth,
            titleHeight,
        )
        val partsHeight = partsRequiredHeight.coerceAtMost(titleBlock.y - drawing.y - gap)
        val partsList = Rectangle(
            drawing.x + drawing.width - partsWidth,
            drawing.y + gap,
            partsWidth,
            partsHeight,
        )
        return SheetPlan(format, sheet, drawing, titleBlock, partsList, contentBounds, scopeIds, bomRows)
    }

    private fun autoSheetCandidate(
        requiredWidth: Int,
        requiredHeight: Int,
        partsRequiredHeight: Int,
        margin: Int,
        titleHeight: Int,
        gap: Int,
        occupiedArea: Int,
    ): SheetCandidate? =
        SHEET_FORMATS
            .mapNotNull { format ->
                sheetCandidateFor(format, requiredWidth, requiredHeight, partsRequiredHeight, margin, titleHeight, gap, occupiedArea, enforceFit = true)
            }
            .maxWithOrNull(compareBy<SheetCandidate> { it.coverage }.thenByDescending { -it.area })
            ?: SHEET_FORMATS
                .map { SheetCandidate(it, mm(it.widthMm), mm(it.heightMm), coverage = 0.0) }
                .maxByOrNull { it.area }

    private fun sheetCandidateFor(
        format: SheetFormat,
        requiredWidth: Int,
        requiredHeight: Int,
        partsRequiredHeight: Int,
        margin: Int,
        titleHeight: Int,
        gap: Int,
        occupiedArea: Int,
        enforceFit: Boolean,
    ): SheetCandidate? {
        val (sheetWidth, sheetHeight) = if (format.roll) {
            val fixedRollHeight = mm(format.widthMm)
            val maxRollLength = mm(format.heightMm)
            requiredWidth.coerceAtMost(maxRollLength) to fixedRollHeight
        } else {
            mm(format.widthMm) to mm(format.heightMm)
        }
        val fits =
            requiredWidth <= sheetWidth &&
                requiredHeight <= sheetHeight &&
                partsRequiredHeight <= sheetHeight - margin * 2 - titleHeight - gap
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
        scopeIds.mapNotNull(document.nodes::get).forEach { node ->
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
            .filter { !it.isLink && it.id != repository.getDocument().rootNodeId }
            .sortedWith(compareBy<Node> { it.name.lowercase() }.thenBy { it.id.value })
            .mapIndexed { index, node -> BomRow(index + 1, node.name, nodeStereotype(node).name) }

    private fun drawFoldingMarkers(g2: Graphics2D, sheet: Rectangle) {
        val marker = mm(5.0)
        g2.color = Color(0x9a9a9a)
        listOf(mm(210.0), mm(420.0), mm(630.0), mm(840.0), mm(1050.0)).forEach { offset ->
            if (offset < sheet.width) {
                val x = sheet.x + sheet.width - offset
                g2.drawLine(x, sheet.y + sheet.height, x, sheet.y + sheet.height - marker)
            }
        }
        listOf(mm(297.0), mm(594.0), mm(891.0), mm(1188.0), mm(1485.0)).forEach { offset ->
            if (offset < sheet.height) {
                val y = sheet.y + sheet.height - offset
                g2.drawLine(sheet.x + sheet.width, y, sheet.x + sheet.width - marker, y)
            }
        }
    }

    private fun renderSheetImage(): BufferedImage? {
        val plan = sheetPlan() ?: return null
        val image = BufferedImage(plan.sheet.width, plan.sheet.height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = Color.WHITE
        g2.fillRect(0, 0, image.width, image.height)
        g2.translate(-plan.sheet.x, -plan.sheet.y)
        val previousClip = g2.clip
        g2.clip = plan.sheet
        drawIsoSheet(g2, plan, includeGrid = false)
        drawGraph(g2, plan.scopeIds)
        g2.clip = previousClip
        g2.dispose()
        return image
    }

    private fun withExtension(file: File, extension: String): File =
        if (file.name.endsWith(".$extension", ignoreCase = true)) file else File(file.parentFile, "${file.name}.$extension")

    private fun writeSvgSheet(file: File) {
        val plan = sheetPlan() ?: error("Nothing to export.")
        val svg = StringBuilder()
        svg.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        svg.appendLine(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${plan.sheet.width}\" height=\"${plan.sheet.height}\" viewBox=\"${plan.sheet.x} ${plan.sheet.y} ${plan.sheet.width} ${plan.sheet.height}\">",
        )
        svg.appendLine("  <title>${xml(repository.getDocument().name)} - ${xml(plan.format.id)}</title>")
        svg.appendLine("  <desc>Generated by orchestra ${xml(Version.CURRENT.semver)} (${xml(Version.CURRENT.gitCommitId)})</desc>")
        svg.appendLine("  <g font-family=\"monospace\" text-rendering=\"geometricPrecision\" shape-rendering=\"crispEdges\">")
        svgSheet(svg, plan)
        svgGraph(svg, plan.scopeIds)
        svg.appendLine("  </g>")
        svg.appendLine("</svg>")
        Files.writeString(file.toPath(), svg.toString())
    }

    private fun svgSheet(svg: StringBuilder, plan: SheetPlan) {
        val sheet = plan.sheet
        svgRect(svg, sheet, fill = "#ffffff", stroke = "#999999")
        svgFoldingMarkers(svg, sheet)
        svgRect(svg, plan.drawing, fill = "none", stroke = "#777777")

        val titleBlock = plan.titleBlock
        svgRect(svg, titleBlock, fill = "none", stroke = "#777777")
        repeat(4) { row ->
            val y = titleBlock.y + (row + 1) * titleBlock.height / 5
            svgLine(svg, titleBlock.x, y, titleBlock.x + titleBlock.width, y, "#777777")
        }
        svgLine(svg, titleBlock.x + mm(45.0), titleBlock.y, titleBlock.x + mm(45.0), titleBlock.y + titleBlock.height, "#777777")
        svgLine(svg, titleBlock.x + mm(112.0), titleBlock.y, titleBlock.x + mm(112.0), titleBlock.y + titleBlock.height, "#777777")
        svgText(svg, "orchestra", titleBlock.x + 12, titleBlock.y + 22, 12, "#444444")
        svgText(svg, repository.getDocument().name, titleBlock.x + 132, titleBlock.y + 22, 12, "#444444")
        svgText(svg, plan.format.id, titleBlock.x + titleBlock.width - 72, titleBlock.y + 22, 12, "#444444")
        svgText(svg, "build ${Version.CURRENT.buildDate.take(10)}", titleBlock.x + 12, titleBlock.y + titleBlock.height - 14, 12, "#444444")

        val partsList = plan.partsList
        svgRect(svg, partsList, fill = "none", stroke = "#b0b0b0")
        svgText(svg, "Parts List", partsList.x + 8, partsList.y + 15, 10, "#444444")
        repeat((partsList.height / PARTS_ROW_HEIGHT).coerceAtLeast(1)) { row ->
            val y = partsList.y + row * PARTS_ROW_HEIGHT
            svgLine(svg, partsList.x, y, partsList.x + partsList.width, y, "#b0b0b0")
        }
        svgLine(svg, partsList.x + 42, partsList.y, partsList.x + 42, partsList.y + partsList.height, "#b0b0b0")
        svgLine(svg, partsList.x + 128, partsList.y, partsList.x + 128, partsList.y + partsList.height, "#b0b0b0")
        plan.bomRows.take((partsList.height / PARTS_ROW_HEIGHT - 1).coerceAtLeast(0)).forEachIndexed { rowIndex, row ->
            val y = partsList.y + (rowIndex + 2) * PARTS_ROW_HEIGHT - 6
            svgText(svg, row.index.toString(), partsList.x + 6, y, 10, "#444444")
            svgText(svg, row.name.take(18), partsList.x + 48, y, 10, "#444444")
            svgText(svg, row.kind.take(14), partsList.x + 132, y, 10, "#444444")
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

    private fun svgFoldingMarkers(svg: StringBuilder, sheet: Rectangle) {
        val marker = mm(5.0)
        listOf(mm(210.0), mm(420.0), mm(630.0), mm(840.0), mm(1050.0)).forEach { offset ->
            if (offset < sheet.width) {
                val x = sheet.x + sheet.width - offset
                svgLine(svg, x, sheet.y + sheet.height, x, sheet.y + sheet.height - marker, "#9a9a9a")
            }
        }
        listOf(mm(297.0), mm(594.0), mm(891.0), mm(1188.0), mm(1485.0)).forEach { offset ->
            if (offset < sheet.height) {
                val y = sheet.y + sheet.height - offset
                svgLine(svg, sheet.x + sheet.width, y, sheet.x + sheet.width - marker, y, "#9a9a9a")
            }
        }
    }

    private fun svgGraph(svg: StringBuilder, scopeIds: Set<NodeId>) {
        val document = repository.getDocument()
        val links = document.nodes.values.filter { it.isLink && it.id in scopeIds }
        document.nodes.values
            .filter { !it.isLink && it.id != document.rootNodeId && it.id in scopeIds }
            .sortedBy { it.children.isEmpty() }
            .forEach { svgNode(svg, it) }
        links.filterNot(::isDependencyAnnotation).forEach { svgLink(svg, it) }
        svgDependencyAnnotations(svg, links.filter(::isDependencyAnnotation))
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
        svgText(svg, node.name, r.x + 12, r.y + 22, if (node.children.isEmpty()) 13 else 12, "#222222")
        svgText(svg, stereotype.name, r.x + 12, r.y + 42, 12, "#555555")
        technologyLabel(node)?.let { svgText(svg, it, r.x + 12, r.y + 58, 11, "#666666") }
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
            else -> 1.5
        }
        val dash = if (stereotype == LinkStereotype.DependencyInjection) "8 6" else null
        svgPath(svg, route.points, color, strokeWidth, dash)
        svgArrowAlongRoute(svg, route.points, color)
        svgPortMarker(svg, route.source, route.sourceDirection, outgoing = true, color = color)
        svgPortMarker(svg, route.target, route.targetDirection, outgoing = false, color = color)
        svgLinkLabel(svg, node.name, route.points, color)
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

    private fun svgLinkLabel(svg: StringBuilder, label: String, points: List<Point>, color: String) {
        val text = label.take(24)
        if (text.isBlank()) return
        val anchor = pointAlongRoute(points, 0.5) ?: return
        val width = text.length * 7
        val x = anchor.x - width / 2
        val y = anchor.y - 14
        svgRect(svg, Rectangle(x - 3, y - 10, width + 6, 14), fill = "#fdfdfd", stroke = "none")
        svgText(svg, text, x, y, 10, color)
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
            val dependent = repository.getNode(link.targetNodeId) ?: return@forEachIndexed
            val label = dependent.name.take(28)
            val labelWidth = max(96, label.length * 8 + 22)
            val labelHeight = 24
            val y = r.y + 10 + index * (labelHeight + 8)
            val x = r.x + r.width + 34
            val anchor = Point(r.x + r.width, y + labelHeight / 2)
            val color = "#3333cc"
            svgLine(svg, anchor.x, anchor.y, x, anchor.y, color, strokeWidth = 1.5)
            svgCircle(svg, anchor.x, anchor.y, 4, color)
            svgRect(svg, Rectangle(x, y, labelWidth, labelHeight), fill = "#f8f9ff", stroke = color, strokeWidth = 1.5)
            svgText(svg, label, x + 10, y + 17, 12, color)
        }
    }

    private fun svgDependencyList(svg: StringBuilder, dependent: Node, links: List<Node>) {
        val r = dependent.layout.rect()
        val rows = links.mapNotNull { linkNode ->
            val link = linkNode.link ?: return@mapNotNull null
            val source = repository.getNode(link.sourceNodeId) ?: return@mapNotNull null
            linkNode to source.name.take(28)
        }
        if (rows.isEmpty()) return

        val x = r.x + 36
        val rowHeight = 24
        val startY = r.y - rows.size * rowHeight - 16
        val stemBottom = r.y + 16
        val color = "#1037ff"
        svgLine(svg, x, startY, x, stemBottom, color, strokeWidth = 1.5)
        rows.forEachIndexed { index, (_, label) ->
            val y = startY + index * rowHeight + 8
            val width = max(110, label.length * 8 + 36)
            svgCircle(svg, x, y, 5, color)
            svgLine(svg, x, y, x + width, y, color, strokeWidth = 1.5)
            svgText(svg, label, x + 14, y - 4, 12, color)
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

    private fun writePdfWithJpeg(file: File, image: BufferedImage) {
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val rgbGraphics = rgb.createGraphics()
        rgbGraphics.color = Color.WHITE
        rgbGraphics.fillRect(0, 0, rgb.width, rgb.height)
        rgbGraphics.drawImage(image, 0, 0, null)
        rgbGraphics.dispose()

        val jpeg = ByteArrayOutputStream()
        ImageIO.write(rgb, "jpg", jpeg)
        val jpegBytes = jpeg.toByteArray()
        val content = "q ${image.width} 0 0 ${image.height} 0 0 cm /Im0 Do Q\n".toByteArray(Charsets.ISO_8859_1)
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>\n".toByteArray(Charsets.ISO_8859_1),
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n".toByteArray(Charsets.ISO_8859_1),
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${image.width} ${image.height}] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>\n".toByteArray(Charsets.ISO_8859_1),
            pdfStream(content),
            pdfImageStream(image.width, image.height, jpegBytes),
        )
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, bytes ->
            offsets += output.size()
            output.write("${index + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            output.write(bytes)
            output.write("endobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xref = output.size()
        output.write("xref\n0 ${objects.size + 1}\n".toByteArray(Charsets.ISO_8859_1))
        output.write("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        offsets.drop(1).forEach { offset ->
            output.write(String.format("%010d 00000 n \n", offset).toByteArray(Charsets.ISO_8859_1))
        }
        output.write("trailer << /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        Files.write(file.toPath(), output.toByteArray())
    }

    private fun pdfStream(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("<< /Length ${bytes.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.write("endstream\n".toByteArray(Charsets.ISO_8859_1))
        return output.toByteArray()
    }

    private fun pdfImageStream(width: Int, height: Int, bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(
            "<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${bytes.size} >>\nstream\n"
                .toByteArray(Charsets.ISO_8859_1),
        )
        output.write(bytes)
        output.write("\nendstream\n".toByteArray(Charsets.ISO_8859_1))
        return output.toByteArray()
    }

    private fun mm(value: Double): Int = (value * SHEET_UNITS_PER_MM).roundToInt()

    private fun drawNode(g2: Graphics2D, node: Node) {
        val r = node.layout.rect()
        val selected = node.id in selection
        val stereotype = nodeStereotype(node)
        val previousStroke = g2.stroke
        g2.color = fillFor(node)
        g2.fillRect(r.x, r.y, r.width, r.height)
        g2.color = strokeFor(node, selected)
        g2.stroke = nodeStroke(node, selected)
        g2.drawRect(r.x, r.y, r.width, r.height)
        g2.color = Color(0x222222)
        g2.font = g2.font.deriveFont(if (node.children.isEmpty()) 13f else 12f)
        g2.drawString(node.name, r.x + 12, r.y + 22)
        g2.color = Color(0x555555)
        g2.drawString(stereotype.name, r.x + 12, r.y + 42)
        technologyLabel(node)?.let {
            g2.color = Color(0x666666)
            g2.font = g2.font.deriveFont(11f)
            g2.drawString(it, r.x + 12, r.y + 58)
        }
        g2.stroke = previousStroke
    }

    private fun drawLink(g2: Graphics2D, node: Node) {
        node.link ?: return
        val route = routeLink(node) ?: return
        val previousStroke = g2.stroke
        val previousFont = g2.font
        val selected = node.id in selection
        val stereotype = LinkClassifier.classify(repository.getDocument(), node)
        val color = linkColor(stereotype, selected)
        g2.color = color
        g2.stroke = linkStroke(stereotype, selected)
        route.points.zipWithNext().forEach { (a, b) -> g2.drawLine(a.x, a.y, b.x, b.y) }
        drawArrowAlongRoute(g2, route.points)
        g2.color = color
        drawPortMarker(g2, route.source, route.sourceDirection, outgoing = true)
        drawPortMarker(g2, route.target, route.targetDirection, outgoing = false)
        drawEndpointLinkLabels(g2, node.name, route, color)
        g2.font = previousFont
        g2.stroke = previousStroke
    }

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
        links.forEachIndexed { index, linkNode ->
            val link = linkNode.link ?: return@forEachIndexed
            val dependent = repository.getNode(link.targetNodeId) ?: return@forEachIndexed
            val selected = linkNode.id in selection
            val label = dependent.name.take(28)
            val metrics = g2.fontMetrics
            val labelWidth = max(96, metrics.stringWidth(label) + 22)
            val labelHeight = 24
            val y = r.y + 10 + index * (labelHeight + 8)
            val x = r.x + r.width + 34
            val anchor = Point(r.x + r.width, y + labelHeight / 2)
            val color = if (selected) Color(0x1565c0) else Color(0x3333cc)
            g2.color = color
            g2.stroke = BasicStroke(if (selected) 2.4f else 1.5f)
            g2.drawLine(anchor.x, anchor.y, x, anchor.y)
            g2.fillOval(anchor.x - 4, anchor.y - 4, 8, 8)
            g2.color = Color(0xf8f9ff)
            g2.fillRect(x, y, labelWidth, labelHeight)
            g2.color = color
            g2.drawRect(x, y, labelWidth, labelHeight)
            g2.drawString(label, x + 10, y + 17)
        }
        g2.stroke = previousStroke
    }

    private fun drawDependencyList(g2: Graphics2D, dependent: Node, links: List<Node>) {
        val r = dependent.layout.rect()
        val previousStroke = g2.stroke
        val rows = links.mapNotNull { linkNode ->
            val link = linkNode.link ?: return@mapNotNull null
            val source = repository.getNode(link.sourceNodeId) ?: return@mapNotNull null
            linkNode to source.name.take(28)
        }
        if (rows.isEmpty()) return

        val x = r.x + 36
        val rowHeight = 24
        val startY = r.y - rows.size * rowHeight - 16
        val stemBottom = r.y + 16
        val selected = rows.any { it.first.id in selection }
        val color = if (selected) Color(0x1565c0) else Color(0x1037ff)
        g2.color = color
        g2.stroke = BasicStroke(if (selected) 2.2f else 1.5f)
        g2.drawLine(x, startY, x, stemBottom)
        rows.forEachIndexed { index, (linkNode, label) ->
            val y = startY + index * rowHeight + 8
            val width = max(110, g2.fontMetrics.stringWidth(label) + 36)
            g2.fillOval(x - 5, y - 5, 10, 10)
            g2.drawLine(x, y, x + width, y)
            if (linkNode.id in selection) {
                g2.stroke = BasicStroke(2.4f)
                g2.drawLine(x, y + 2, x + width, y + 2)
                g2.stroke = BasicStroke(1.5f)
            }
            g2.drawString(label, x + 14, y - 4)
        }
        g2.stroke = previousStroke
    }

    private fun isDependencyAnnotation(linkNode: Node): Boolean =
        when (LinkClassifier.classify(repository.getDocument(), linkNode)) {
            LinkStereotype.UsageImport,
            LinkStereotype.DependencyInjection -> true
            else -> false
        }

    private fun routeLink(linkNode: Node): LinkRoute? {
        val anchors = linkAnchors(linkNode) ?: return null
        routeCache[linkNode.id]
            ?.takeIf { it.source == anchors.source.point && it.target == anchors.target.point }
            ?.let { return it }
        return routedRoute(linkNode, anchors, emptyList())
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
        val document = repository.getDocument()
        val routedSegments = mutableListOf<RouteSegment>()
        val nextCache = mutableMapOf<NodeId, LinkRoute>()
        document.nodes.values
            .filter { it.isLink && !isDependencyAnnotation(it) }
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
        repaint()
    }

    private fun routedRoute(linkNode: Node, anchors: LinkAnchors, routedSegments: List<RouteSegment>): LinkRoute {
        val source = anchors.source
        val target = anchors.target
        val sourceStub = Point(source.point.x + PORT_STUB_LENGTH * source.xDirection, source.point.y)
        val targetStub = Point(target.point.x + PORT_STUB_LENGTH * target.xDirection, target.point.y)
        val obstacles = routeObstacles(anchors.sourceNodeId, anchors.targetNodeId)
        val candidates = routeCandidates(sourceStub, targetStub)
            .map { compact(chamferOrthogonalTurns(it)) }
            .filter { it.size >= 2 }
            .filter(::isOctilinearPath)

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

    private fun isOctilinearPath(points: List<Point>): Boolean =
        points.zipWithNext().all { (a, b) ->
            val dx = abs(b.x - a.x)
            val dy = abs(b.y - a.y)
            dx == 0 || dy == 0 || dx == dy
        }

    private fun routeObstacles(sourceId: NodeId, targetId: NodeId): List<Rectangle> =
        repository.getDocument().nodes.values
            .filter { !it.isLink && it.id != repository.getDocument().rootNodeId && it.id != sourceId && it.id != targetId }
            .map {
                val r = it.layout.rect()
                Rectangle(
                    r.x - ROUTING_OBSTACLE_PADDING,
                    r.y - ROUTING_OBSTACLE_PADDING,
                    r.width + ROUTING_OBSTACLE_PADDING * 2,
                    r.height + ROUTING_OBSTACLE_PADDING * 2,
                )
            }

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
        val y = r.y + PORT_TOP_SPACING + index * PORT_SPACING
        return PortAnchor(Point(x, y), side)
    }

    private fun normalLinksOnSide(node: Node, side: Int): List<Node> {
        val document = repository.getDocument()
        val ids = node.outgoingLinks + node.incomingLinks
        return ids.distinct().mapNotNull(document.nodes::get)
            .filterNot(::isDependencyAnnotation)
            .filter { linkNode ->
                val link = linkNode.link ?: return@filter false
                val outgoing = link.sourceNodeId == node.id
                linkSide(node, linkNode, outgoing) == side
            }
    }

    private fun linkSide(node: Node, linkNode: Node, outgoing: Boolean): Int {
        val link = linkNode.link ?: return 1
        val otherId = if (outgoing) link.targetNodeId else link.sourceNodeId
        val center = node.layout.center()
        val otherCenter = repository.getNode(otherId)?.layout?.center() ?: center
        return if (otherCenter.x >= center.x) 1 else -1
    }

    private fun portOrderValue(node: Node, linkNode: Node, side: Int): Double {
        val center = node.layout.center()
        val link = linkNode.link ?: return 0.0
        val outgoing = link.sourceNodeId == node.id
        val otherId = if (outgoing) link.targetNodeId else link.sourceNodeId
        val otherCenter = repository.getNode(otherId)?.layout?.center() ?: center
        val sideRelativeX = ((otherCenter.x - center.x) * side).toDouble().coerceAtLeast(1.0)
        return atan2((otherCenter.y - center.y).toDouble(), sideRelativeX)
    }

    private fun compact(points: List<Point>): List<Point> =
        points.fold(mutableListOf()) { acc, point ->
            if (acc.lastOrNull() != point) acc += point
            acc
        }

    private fun drawEndpointLinkLabels(g2: Graphics2D, label: String, route: LinkRoute, color: Color) {
        val text = label.take(24)
        if (text.isBlank()) return
        g2.font = g2.font.deriveFont(10f)
        g2.color = color
        drawEndpointLinkLabel(g2, text, route.source, route.sourceDirection)
        drawEndpointLinkLabel(g2, text, route.target, route.targetDirection)
    }

    private fun drawEndpointLinkLabel(g2: Graphics2D, text: String, anchor: Point, side: Int) {
        val metrics = g2.fontMetrics
        val width = metrics.stringWidth(text)
        val spacing = 8
        val x = if (side < 0) anchor.x - width - spacing else anchor.x + spacing
        val y = anchor.y - metrics.height - 4
        g2.drawString(text, x, y + metrics.ascent)
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
        val id = hitNode(modelPoint(e.point)) ?: return
        onNodeDoubleClicked(id)
    }

    private fun handlePressed(e: MouseEvent) {
        if (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
            panDragStart = e.point
            return
        }
        val point = modelPoint(e.point)
        dragStart = point
        moveDragReference = null
        val hit = hitNode(point)
        val hitLink = hitLink(point)
        when (mode) {
            CanvasMode.CreateNode -> {
                val parent = hit?.takeIf { repository.getNode(it)?.isLink != true }
                    ?: repository.getDocument().rootNodeId
                val node = repository.createNode(parent, "New Node", NodeKind.Processor)
                repository.updateNodeLayout(node.id, NodeLayout(point.x.toDouble(), point.y.toDouble(), 180.0, 90.0))
                selection.clear()
                selection += node.id
                invalidateRoutesFor(listOf(node.id))
                refreshAll()
            }
            CanvasMode.CreateLink -> {
                if (hit != null) {
                    if (linkSource == null) {
                        linkSource = hit
                        selection.clear()
                        selection += hit
                    } else if (linkSource != hit) {
                        createLink(linkSource!!, hit)
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
            repository.getDocument().nodes.values
                .filter { !it.isLink && it.id != repository.getDocument().rootNodeId && it.layout.rect().intersects(rect) }
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
        val link = repository.createLink(repository.getDocument().rootNodeId, "${source.name} -> ${target.name}", sourceId, "out", targetId, "in")
        link.link?.transportKind = LinkTransportKinds.Default
        selection.clear()
        selection += link.id
        routeCache.remove(link.id)
        invalidateRoutesFor(listOf(sourceId, targetId))
        repository.markDirty()
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
        val tolerance = SNAP_GRAB_RADIUS_PX / zoom
        entityCorners(rect).minByOrNull { it.distance(point) }
            ?.takeIf { it.distance(point) <= tolerance }
            ?.let { return it }
        val grid = nearestGridPoint(point)
        return if (grid.distance(point) <= tolerance) grid else point
    }

    private fun snapMoveReferenceToGrid(point: Point): Point = nearestGridPoint(point)

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
        repository.getDocument().nodes.values
            .filter { !it.isLink && it.id != repository.getDocument().rootNodeId && it.layout.rect().contains(point) }
            .minByOrNull { it.layout.width * it.layout.height }
            ?.id

    private fun hitLink(point: Point): NodeId? =
        repository.getDocument().nodes.values
            .filter { it.isLink }
            .firstOrNull { link ->
                if (isDependencyAnnotation(link)) {
                    dependencyAnnotationBounds(link).any { it.contains(point) }
                } else {
                    routeLink(link)?.points?.zipWithNext()?.any { (a, b) -> distanceToSegment(point, a, b) <= 8.0 } == true
                }
            }
            ?.id

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
        val labelWidth = max(96, target.name.length * 8 + 22)
        val sourceBounds = Rectangle(
            sourceRect.x + sourceRect.width,
            sourceRect.y + 10 + sourceIndex * 32,
            labelWidth + 36,
            24,
        )
        val dependencyBounds = Rectangle(
            targetRect.x + 24,
            targetRect.y - targetLinks.size * 24 - 20 + targetIndex * 24,
            max(120, source.name.length * 8 + 52),
            24,
        )
        return listOf(sourceBounds, dependencyBounds)
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
        node.children.isNotEmpty() -> Color(0xfafafa)
        nodeStereotype(node) == NodeStereotype.ServiceLibrary -> Color(0xf6f7ff)
        nodeStereotype(node) in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> Color(0xfffbfb)
        nodeStereotype(node) in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> Color(0xf8fff8)
        else -> Color.WHITE
    }

    private fun strokeFor(node: Node, selected: Boolean): Color = when {
        selected -> Color(0x3366cc)
        nodeStereotype(node) in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> Color(0xcc3333)
        nodeStereotype(node) in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> Color(0x33aa33)
        nodeStereotype(node) == NodeStereotype.ServiceLibrary -> Color(0x3333cc)
        else -> Color(0x222222)
    }

    private fun nodeStroke(node: Node, selected: Boolean): Stroke = when {
        selected -> BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
        node.children.isNotEmpty() -> BasicStroke(2.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, floatArrayOf(24f, 8f, 4f, 8f), 0f)
        nodeStereotype(node) == NodeStereotype.ServiceLibrary -> BasicStroke(2.2f)
        nodeStereotype(node) in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> BasicStroke(2.2f)
        nodeStereotype(node) in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> BasicStroke(2.2f)
        else -> BasicStroke(2f)
    }

    private fun nodeStereotype(node: Node): NodeStereotype = node.stereotype(repository.getDocument())

    private fun technologyLabel(node: Node): String? {
        if (node.kind != NodeKind.Processor || node.isLink) return null
        val language = inheritedTechnologyValue(node.id) { it.languageId }
        val technology = inheritedTechnologyValue(node.id) { it.technologyId }
        return when {
            language.isBlank() && technology.isBlank() -> null
            language.isBlank() -> "tech: $technology"
            technology.isBlank() -> "lang: $language"
            else -> "lang: $language | tech: $technology"
        }
    }

    private fun inheritedTechnologyValue(nodeId: NodeId, selector: (TechnologyMetadata) -> String): String {
        val document = repository.getDocument()
        val visited = mutableSetOf<NodeId>()
        var current = document.nodes[nodeId]
        while (current != null && current.id !in visited) {
            visited += current.id
            val value = selector(current.technology).trim()
            if (value.isNotBlank()) return value
            current = current.parentId?.let(document.nodes::get)
        }
        return ""
    }

    private fun linkColor(stereotype: LinkStereotype, selected: Boolean): Color = when {
        selected -> Color(0x1565c0)
        stereotype == LinkStereotype.UsageImport -> Color(0x3333cc)
        stereotype == LinkStereotype.ErrorPipe -> Color(0xcc3333)
        stereotype == LinkStereotype.DependencyInjection -> Color(0xb36b00)
        else -> Color(0x222222)
    }

    private fun linkStroke(stereotype: LinkStereotype, selected: Boolean): Stroke = when {
        selected -> BasicStroke(3f)
        stereotype == LinkStereotype.UsageImport -> BasicStroke(1.8f)
        stereotype == LinkStereotype.ErrorPipe -> BasicStroke(2f)
        stereotype == LinkStereotype.DependencyInjection -> BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(8f, 6f), 0f)
        else -> BasicStroke(1.5f)
    }
}

private class InspectorPanel(
    private val repository: DocumentRepository,
    private val refreshAll: () -> Unit,
    languageIds: List<String>,
) : JPanel(BorderLayout()) {
    private val knownLanguageIds = languageIds
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals(NoneLanguageChoice, ignoreCase = true) && !it.equals(OtherLanguageChoice, ignoreCase = true) }
        .distinct()
        .sorted()
    private val languageOptions = listOf(NoneLanguageChoice) + knownLanguageIds + OtherLanguageChoice
    private val knownTransportKindIds = LinkTransportKinds.catalog.map { it.id }
    private val transportDisplayById = LinkTransportKinds.catalog.associate { it.id to "${it.label} (${it.id})" }
    private val transportIdByDisplay = transportDisplayById.entries.associate { (id, display) -> display to id }
    private val transportKindOptions = LinkTransportKinds.catalog.map { transportDisplayById.getValue(it.id) } + OtherTransportChoice
    private var nodeId: NodeId? = null
    private var boundNodeIsLink = false
    private var boundHasNode = false
    private var binding = false
    private val nameField = JTextField()
    private val language = JComboBox(languageOptions.toTypedArray()).apply { isEditable = false }
    private val customLanguage = JTextField()
    private val customLanguagePanel = JPanel(BorderLayout(0, 4)).apply {
        add(JLabel("Custom language identifier"), BorderLayout.NORTH)
        add(customLanguage, BorderLayout.CENTER)
        isVisible = false
    }
    private val technology = JTextField()
    private val state = JTextField()
    private val linkTransportKind = JComboBox(transportKindOptions.toTypedArray()).apply { isEditable = false }
    private val customTransportKind = JTextField()
    private val customTransportKindPanel = JPanel(BorderLayout(0, 4)).apply {
        add(JLabel("Custom transport identifier"), BorderLayout.NORTH)
        add(customTransportKind, BorderLayout.CENTER)
        isVisible = false
    }
    private val payloadDefinition = JTextArea(5, 24)
    private val metadata = JTextArea(5, 24)
    private val nameRow = fieldRow("Name", nameField)
    private val languageRow = fieldRow("Language", language)
    private val technologyRow = fieldRow("Technology", technology)
    private val stateRow = fieldRow("State", state)
    private val linkTransportKindRow = fieldRow("Link transport kind", linkTransportKind)
    private val payloadDefinitionRow = fieldRow("Link payload definition", JScrollPane(payloadDefinition))
    private val metadataRow = fieldRow("Metadata key=value", JScrollPane(metadata))

    init {
        listOf(nameField, customLanguage, technology, state, customTransportKind).forEach(::applyOnCommit)
        applyOnCommit(language)
        applyOnCommit(linkTransportKind)
        listOf(metadata, payloadDefinition).forEach(::applyOnFocusLost)
        val form = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(nameRow)
            add(languageRow)
            add(customLanguagePanel)
            add(technologyRow)
            add(stateRow)
            add(linkTransportKindRow)
            add(customTransportKindPanel)
            add(payloadDefinitionRow)
            add(metadataRow)
            add(JButton("Apply").apply { addActionListener { apply() } })
        }
        add(form, BorderLayout.NORTH)
    }

    fun bind(id: NodeId?) {
        if (nodeId == id && isEditing()) return
        binding = true
        nodeId = id
        val node = id?.let(repository::getNode)
        boundHasNode = node != null
        boundNodeIsLink = node?.isLink == true
        nameField.text = node?.name.orEmpty()
        bindLanguage(node?.technology?.languageId.orEmpty())
        technology.text = node?.technology?.technologyId.orEmpty()
        state.text = node?.metadata?.get("state").orEmpty()
        bindTransportKind(node?.link?.transportKind.orEmpty())
        payloadDefinition.text = node?.link?.payloadDefinition.orEmpty()
        metadata.text = metadataText(node)
        updateEntityFieldVisibility()
        binding = false
    }

    private fun applyOnCommit(field: JTextField) {
        field.addActionListener { apply() }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = apply()
        })
    }

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
            language.hasFocus() ||
            customLanguage.hasFocus() ||
            technology.hasFocus() ||
            state.hasFocus() ||
            linkTransportKind.hasFocus() ||
            customTransportKind.hasFocus() ||
            payloadDefinition.hasFocus() ||
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
        val node = repository.requireNode(id)
        repository.renameNode(id, nameField.text)
        val isLink = node.isLink
        if (!isLink) {
            repository.updateNodeTechnology(id, node.technology.copy(languageId = selectedLanguage(), technologyId = technology.text))
        }
        node.metadata.clear()
        node.metadata.putAll(parseMetadata(includeState = !isLink))
        if (isLink) node.link?.let {
            it.transportKind = selectedTransportKind().ifBlank { LinkTransportKinds.Default }
            it.payloadDefinition = payloadDefinition.text
        }
        repository.markDirty()
        refreshAll()
    }

    private fun selectedLanguage(): String =
        when (val selected = language.selectedItem?.toString().orEmpty()) {
            NoneLanguageChoice -> ""
            OtherLanguageChoice -> customLanguage.text.trim()
            else -> selected.trim()
        }

    private fun selectedTransportKind(): String =
        when (val selected = linkTransportKind.selectedItem?.toString().orEmpty()) {
            OtherTransportChoice -> customTransportKind.text.trim()
            else -> transportIdByDisplay[selected].orEmpty()
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

    private fun updateConditionalChoiceVisibility() {
        customLanguagePanel.isVisible = languageRow.isVisible && language.selectedItem == OtherLanguageChoice
        customLanguage.isEnabled = customLanguagePanel.isVisible
        customTransportKindPanel.isVisible = linkTransportKindRow.isVisible && linkTransportKind.selectedItem == OtherTransportChoice
        customTransportKind.isEnabled = customTransportKindPanel.isVisible
        revalidate()
        repaint()
    }

    private fun updateEntityFieldVisibility() {
        val showNodeFields = boundHasNode && !boundNodeIsLink
        val showLinkFields = boundHasNode && boundNodeIsLink
        languageRow.isVisible = showNodeFields
        technologyRow.isVisible = showNodeFields
        stateRow.isVisible = showNodeFields
        linkTransportKindRow.isVisible = showLinkFields
        payloadDefinitionRow.isVisible = showLinkFields
        updateConditionalChoiceVisibility()
    }

    private fun metadataText(node: Node?): String =
        node?.metadata
            ?.entries
            ?.filterNot { (key, _) -> !node.isLink && key == "state" }
            ?.joinToString("\n") { "${it.key}=${it.value}" }
            .orEmpty()

    private fun fieldRow(label: String, component: JComponent): JPanel =
        JPanel(BorderLayout(0, 4)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(label), BorderLayout.NORTH)
            add(component, BorderLayout.CENTER)
        }

    private companion object {
        private const val NoneLanguageChoice = "None"
        private const val OtherLanguageChoice = "Other"
        private const val OtherTransportChoice = "Other"
    }
}

private class NodeEditorTabs(
    private val repository: DocumentRepository,
    private val refreshAll: () -> Unit,
    private val checkpointHistory: () -> Unit,
    private val undoDocument: () -> Unit,
    private val redoDocument: () -> Unit,
    private val languageIds: List<String>,
) : JTabbedPane() {
    private var boundIds: List<NodeId> = emptyList()

    fun bind(ids: List<NodeId>, activeSection: NodeTextSection? = null) {
        if (ids != boundIds) {
            removeAll()
            ids.mapNotNull(repository::getNode).forEach { node ->
                addTab(node.name, NodeTextEditor(repository, node.id, refreshAll, checkpointHistory, undoDocument, redoDocument, languageIds))
            }
            boundIds = ids
        } else {
            refreshTitlesAndMetadata()
        }
        activeSection?.let { section ->
            ids.firstOrNull()?.let { selectSection(it, section) }
        }
    }

    private fun selectSection(nodeId: NodeId, section: NodeTextSection) {
        for (index in 0 until tabCount) {
            val editor = getComponentAt(index) as? NodeTextEditor ?: continue
            if (editor.nodeId == nodeId) {
                selectedIndex = index
                editor.selectSection(section)
                return
            }
        }
    }

    private fun refreshTitlesAndMetadata() {
        for (index in 0 until tabCount) {
            val editor = getComponentAt(index) as? NodeTextEditor ?: continue
            repository.getNode(editor.nodeId)?.let { node -> setTitleAt(index, node.name) }
            editor.refreshMetadata()
        }
    }

    fun applyCodeEditorFont(font: Font) {
        for (index in 0 until tabCount) {
            (getComponentAt(index) as? NodeTextEditor)?.applyCodeEditorFont(font)
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
) : JTabbedPane() {
    private val completionService = ModelAwareCompletionService(repository::getDocument)
    private val editorsBySection = mutableMapOf<NodeTextSection, GridCodeEditorAdapter>()
    private val languageSelectorsBySection = mutableMapOf<NodeTextSection, JComboBox<String>>()
    private val componentsBySection = mutableMapOf<NodeTextSection, JComponent>()
    private var testsPanel: TestTextEditorPanel? = null
    private var binding = false
    private val inheritedLanguageChoice = "Inherited"
    private val selectableLanguages = (listOf(VOID_LANGUAGE_ID) + languageIds).distinct()
    private val textTabs = listOf(
        TextTabSpec(
            label = "Initialization",
            section = NodeTextSection.Initialization,
            allowInheritance = true,
            defaultLanguageId = VOID_LANGUAGE_ID,
            textGetter = { it.initialization },
            textSetter = { text, value -> text.copy(initialization = value) },
            languageGetter = { it.initializationLanguageId },
            languageSetter = { text, value -> text.copy(initializationLanguageId = value) },
        ),
        TextTabSpec(
            label = "Source",
            section = NodeTextSection.Source,
            allowInheritance = true,
            defaultLanguageId = VOID_LANGUAGE_ID,
            textGetter = { it.source },
            textSetter = { text, value -> text.copy(source = value) },
            languageGetter = { it.sourceLanguageId },
            languageSetter = { text, value -> text.copy(sourceLanguageId = value) },
        ),
        TextTabSpec(
            label = "Specification",
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
            section = NodeTextSection.AiInstructions,
            allowInheritance = false,
            defaultLanguageId = "markdown",
            textGetter = { it.aiInstructions },
            textSetter = { text, value -> text.copy(aiInstructions = value) },
            languageGetter = { it.aiInstructionsLanguageId },
            languageSetter = { text, value -> text.copy(aiInstructionsLanguageId = value) },
        ),
    )

    init {
        textTabs.forEach(::addTextTab)
        addTestsTab()
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
                editorsBySection[spec.section]?.let { editor ->
                    editor.setText(spec.textGetter(node.text))
                    editor.setTechnology(technology.copy(languageId = effectiveLanguage))
                    editor.setCompletionContext(EditorCompletionContext(node.id.value, spec.section))
                }
            }
            editorsBySection[NodeTextSection.Tests]?.setText(node.text.tests)
            testsPanel?.bindLanguage(effectiveTextLanguage(NodeTextSection.Tests))
            testsPanel?.refreshTopology()
        } finally {
            binding = false
        }
    }

    fun applyCodeEditorFont(font: Font) {
        editorsBySection.values.forEach { it.setEditorFont(font) }
    }

    private fun addTextTab(spec: TextTabSpec) {
        val editor = GridCodeEditorAdapter()
        editor.setEditorFont(OrchestraFonts.codeFont(14f))
        val node = repository.requireNode(nodeId)
        val languageSelector = JComboBox(languageChoices(spec).toTypedArray()).apply {
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
        editor.setTechnology(effectiveTechnology().copy(languageId = effectiveTextLanguage(spec.section)))
        editor.setCompletionContext(EditorCompletionContext(node.id.value, spec.section))
        editor.onCompletionRequested = completionService::getSuggestions
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
            val selected = selectedLanguageForSpec(spec, languageSelector.selectedItem?.toString().orEmpty(), current.text)
            repository.updateNodeText(nodeId, spec.languageSetter(current.text, selected))
            refreshAll()
        }
        editorsBySection[spec.section] = editor
        languageSelectorsBySection[spec.section] = languageSelector
        componentsBySection[spec.section] = editorPanel
        addTab(spec.label, editorPanel)
        binding = true
        try {
            syncTextLanguageBinding(spec, node.text)
        } finally {
            binding = false
        }
    }

    private fun addTestsTab() {
        val editor = GridCodeEditorAdapter()
        editor.setEditorFont(OrchestraFonts.codeFont(14f))
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
        editor.onCompletionRequested = completionService::getSuggestions
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
        addTab("Tests", panel)
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

    private data class TextTabSpec(
        val label: String,
        val section: NodeTextSection,
        val allowInheritance: Boolean,
        val defaultLanguageId: String,
        val textGetter: (NodeText) -> String,
        val textSetter: (NodeText, String) -> NodeText,
        val languageGetter: (NodeText) -> String,
        val languageSetter: (NodeText, String) -> NodeText,
    )
}

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

interface OrchestraDesktopPlugin {
    val id: String
    val displayName: String
    fun configure(context: OrchestraPluginContext)
}

interface OrchestraPluginContext {
    fun document(): InflowDocument
    fun postModelUpdate(label: String, update: (DocumentRepository) -> Unit)
    fun addToolbarButton(label: String, action: () -> Unit)
    fun addContentTab(title: String, createPanel: () -> JComponent)
    fun addCommand(id: String, title: String, keyStroke: KeyStroke? = null, action: () -> Unit)
}

fun defaultPluginsFolder(): Path {
    val location = OrchestraDesktopApp::class.java.protectionDomain.codeSource?.location?.toURI()
    val binary = location?.let(Path::of)
    val base = when {
        binary == null -> Path.of(".")
        Files.isRegularFile(binary) -> binary.parent ?: Path.of(".")
        else -> binary
    }
    return base.resolve("plugins").toAbsolutePath().normalize()
}

fun loadDesktopPlugins(folder: Path): List<OrchestraDesktopPlugin> {
    val urls = pluginJarUrls(folder)
    if (urls.isEmpty()) return emptyList()
    val classLoader = URLClassLoader(urls, OrchestraDesktopPlugin::class.java.classLoader)
    return ServiceLoader.load(OrchestraDesktopPlugin::class.java, classLoader).toList()
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

private fun NodeLayout.rect(): Rectangle = Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())
private fun NodeLayout.center(): Point = Point((x + width / 2).toInt(), (y + height / 2).toInt())

fun launchDesktopApp(pluginsFolder: Path? = null) {
    SwingUtilities.invokeLater {
        OrchestraDesktopApp(pluginsFolder = pluginsFolder ?: defaultPluginsFolder()).show()
    }
}
