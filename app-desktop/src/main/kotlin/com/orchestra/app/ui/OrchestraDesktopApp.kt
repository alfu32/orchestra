package com.orchestra.app.ui

import com.orchestra.app.editor.SwingCodeEditorAdapter
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodeLayout
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.NodeText
import com.orchestra.core.model.PortDirection
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.storage.DocumentRepository
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.KotlinxJsonDocumentStore
import com.orchestra.storage.newDocument
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Stroke
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OrchestraDesktopApp(
    private val repository: DocumentRepository = InMemoryDocumentRepository(newDocument("Untitled Orchestra")),
    private val store: KotlinxJsonDocumentStore = KotlinxJsonDocumentStore(),
) {
    private val frame = JFrame("Orchestra")
    private val selection = linkedSetOf<NodeId>()
    private val canvas = GraphCanvas(repository, selection, ::onSelectionChanged, ::refreshAll)
    private val tree = JTree()
    private val inspector = InspectorPanel(repository, ::refreshAll)
    private val editorTabs = NodeEditorTabs(repository, ::refreshAll)
    private var currentFile: Path? = null

    fun show() {
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.jMenuBar = menuBar()
        frame.contentPane = layout()
        frame.minimumSize = Dimension(1100, 720)
        frame.setSize(1400, 900)
        frame.setLocationRelativeTo(null)
        refreshAll()
        frame.isVisible = true
    }

    private fun layout(): JComponent {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(button("Node") { canvas.mode = CanvasMode.CreateNode })
            add(button("Child") { canvas.createChildNodeInSelection() })
            add(button("Link") { canvas.mode = CanvasMode.CreateLink })
            add(button("Reparent") { canvas.mode = CanvasMode.Reparent })
            add(button("Pan") { canvas.mode = CanvasMode.Pan })
            add(button("Select") { canvas.mode = CanvasMode.Select })
            add(button("Sheet") { canvas.toggleSheet() })
            add(button("Delete") { deleteSelection() })
            add(button("Copy") { canvas.copySelection() })
            add(button("Paste") { canvas.pasteSelection() })
        }

        tree.addTreeSelectionListener {
            val id = (tree.lastSelectedPathComponent as? TreeNodeRef)?.id ?: return@addTreeSelectionListener
            selection.clear()
            selection += id
            onSelectionChanged()
        }

        val left = JPanel(BorderLayout()).apply {
            add(JLabel("Hierarchy"), BorderLayout.NORTH)
            add(JScrollPane(tree), BorderLayout.CENTER)
        }
        val right = JPanel(BorderLayout()).apply {
            add(JLabel("Inspector"), BorderLayout.NORTH)
            add(inspector, BorderLayout.CENTER)
        }
        val middle = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(canvas), right).apply {
            resizeWeight = 1.0
        }
        val top = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, middle).apply {
            resizeWeight = 0.18
        }
        val main = JSplitPane(JSplitPane.VERTICAL_SPLIT, top, editorTabs).apply {
            resizeWeight = 0.68
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(main, BorderLayout.CENTER)
        }
    }

    private fun menuBar() = JMenuBar().apply {
        add(JMenu("File").apply {
            add(item("New") {
                repository.replaceDocument(newDocument("Untitled Orchestra"))
                selection.clear()
                currentFile = null
                refreshAll()
            })
            add(item("Open...") { openFile() })
            add(item("Save") { saveFile() })
            add(item("Save As...") { saveAsFile() })
        })
        add(JMenu("Graph").apply {
            add(item("Create Node") { canvas.mode = CanvasMode.CreateNode })
            add(item("Create Child Node") { canvas.createChildNodeInSelection() })
            add(item("Create Link") { canvas.mode = CanvasMode.CreateLink })
            add(item("Reparent Selection") { canvas.mode = CanvasMode.Reparent })
            add(item("Pan") { canvas.mode = CanvasMode.Pan })
            add(item("Delete Selection") { deleteSelection() })
        })
    }

    private fun openFile() {
        val path = askPath("Open .inflow.json") ?: return
        repository.replaceDocument(store.load(path))
        currentFile = path
        selection.clear()
        refreshAll()
    }

    private fun saveFile() {
        val path = currentFile ?: askPath("Save .inflow.json") ?: return
        store.save(repository.getDocument(), path)
        currentFile = path
        repository.clearDirty()
    }

    private fun saveAsFile() {
        val path = askPath("Save .inflow.json") ?: return
        store.save(repository.getDocument(), path)
        currentFile = path
        repository.clearDirty()
    }

    private fun askPath(title: String): Path? {
        val value = JOptionPane.showInputDialog(frame, title, currentFile?.toString() ?: "build/document.inflow.json")
        return value?.takeIf { it.isNotBlank() }?.let(Path::of)
    }

    private fun deleteSelection() {
        selection.toList().filter { it != repository.getDocument().rootNodeId }.forEach(repository::deleteNode)
        selection.clear()
        refreshAll()
    }

    private fun onSelectionChanged() {
        inspector.bind(selection.firstOrNull())
        editorTabs.bind(selection.toList())
        canvas.repaint()
    }

    private fun refreshAll() {
        refreshTree()
        canvas.refreshBoundsFromChildren()
        canvas.repaint()
        onSelectionChanged()
    }

    private fun refreshTree() {
        val document = repository.getDocument()
        tree.model = DefaultTreeModel(treeNode(document, document.rootNodeId))
    }

    private fun treeNode(document: InflowDocument, id: NodeId): TreeNodeRef {
        val node = repository.requireNode(id)
        return TreeNodeRef(id, node.name).apply {
            node.children.filter { document.nodes[it]?.isLink != true }.forEach { add(treeNode(document, it)) }
        }
    }

    private fun button(label: String, action: () -> Unit) = JButton(label).apply { addActionListener { action() } }
    private fun item(label: String, action: () -> Unit) = JMenuItem(label).apply { addActionListener { action() } }
}

private class TreeNodeRef(val id: NodeId, private val label: String) : DefaultMutableTreeNode(label) {
    override fun toString(): String = label
}

enum class CanvasMode {
    Select,
    CreateNode,
    CreateLink,
    Reparent,
    Pan,
}

class GraphCanvas(
    private val repository: DocumentRepository,
    private val selection: LinkedHashSet<NodeId>,
    private val onSelectionChanged: () -> Unit,
    private val refreshAll: () -> Unit,
) : JPanel() {
    var mode: CanvasMode = CanvasMode.Select
    private var dragStart: Point? = null
    private var panDragStart: Point? = null
    private var selectionRect: Rectangle? = null
    private var linkSource: NodeId? = null
    private var clipboard: List<Node> = emptyList()
    private var zoom = 1.0
    private var panX = 0.0
    private var panY = 0.0
    private var showSheet = false

    private data class PortAnchor(val point: Point, val xDirection: Int)
    private data class LinkRoute(val source: Point, val target: Point, val points: List<Point>)

    init {
        background = Color(0xf7f7f7)
        preferredSize = Dimension(2400, 1800)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = handlePressed(e)
            override fun mouseDragged(e: MouseEvent) = handleDragged(e)
            override fun mouseReleased(e: MouseEvent) = handleReleased(e)
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

    fun toggleSheet() {
        showSheet = !showSheet
        repaint()
    }

    fun createChildNodeInSelection() {
        val root = repository.getDocument().rootNodeId
        val parentId = selection.firstOrNull()
            ?.let(repository::getNode)
            ?.takeIf { !it.isLink }
            ?.id
            ?: root
        val parent = repository.requireNode(parentId)
        val offset = parent.children.size * 28
        val layout = if (parentId == root) {
            NodeLayout(120.0 + offset, 120.0 + offset, 180.0, 90.0)
        } else {
            NodeLayout(parent.layout.x + 48 + offset, parent.layout.y + 72 + offset, 180.0, 90.0)
        }
        val node = repository.createNode(parentId, "New Child", NodeKind.Processor)
        repository.updateNodeLayout(node.id, layout)
        selection.clear()
        selection += node.id
        mode = CanvasMode.Select
        refreshAll()
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
        document.nodes.values.filter { it.children.isNotEmpty() }.sortedByDescending { depthOf(it) }.forEach { parent ->
            val boxes = parent.children.mapNotNull(document.nodes::get).filter { !it.isLink }.map { it.layout }
            if (boxes.isNotEmpty()) {
                parent.layout = NodeLayout(
                    x = boxes.minOf { it.x } - 32,
                    y = boxes.minOf { it.y } - 48,
                    width = boxes.maxOf { it.x + it.width } - boxes.minOf { it.x } + 64,
                    height = boxes.maxOf { it.y + it.height } - boxes.minOf { it.y } + 96,
                )
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
        g2.scale(zoom, zoom)
        g2.translate(panX, panY)
        if (showSheet) drawIsoSheet(g2)
        drawGrid(g2)
        val document = repository.getDocument()
        document.nodes.values.filter { it.isLink }.forEach { drawLink(g2, it) }
        document.nodes.values.filter { !it.isLink && it.id != document.rootNodeId }.sortedBy { it.children.isEmpty() }.forEach { drawNode(g2, it) }
        selectionRect?.let {
            g2.color = Color(0x3366cc55, true)
            g2.fill(it)
            g2.color = Color(0x3366cc)
            g2.draw(it)
        }
    }

    private fun drawGrid(g2: Graphics2D) {
        g2.color = Color(0xe2e2e2)
        val step = 40
        val minX = floor(-panX / step).toInt() * step
        val minY = floor(-panY / step).toInt() * step
        val maxX = ceil((width / zoom - panX) / step).toInt() * step
        val maxY = ceil((height / zoom - panY) / step).toInt() * step
        for (x in minX..maxX step step) {
            g2.color = if (x % 200 == 0) Color(0xd1d1df) else Color(0xe2e2e2)
            g2.drawLine(x, minY, x, maxY)
        }
        for (y in minY..maxY step step) {
            g2.color = if (y % 200 == 0) Color(0xd1d1df) else Color(0xe2e2e2)
            g2.drawLine(minX, y, maxX, y)
        }
    }

    private fun drawIsoSheet(g2: Graphics2D) {
        val sheet = Rectangle(-80, -80, 1600, 1130)
        g2.color = Color.WHITE
        g2.fill(sheet)
        g2.color = Color(0x999999)
        g2.stroke = BasicStroke(1f)
        g2.draw(sheet)

        val margin = 28
        val drawing = Rectangle(sheet.x + margin, sheet.y + margin, sheet.width - margin * 2, sheet.height - margin * 2)
        g2.draw(drawing)

        val titleBlock = Rectangle(drawing.x + drawing.width - 440, drawing.y + drawing.height - 120, 440, 120)
        g2.draw(titleBlock)
        repeat(4) { row ->
            val y = titleBlock.y + (row + 1) * titleBlock.height / 5
            g2.drawLine(titleBlock.x, y, titleBlock.x + titleBlock.width, y)
        }
        g2.drawLine(titleBlock.x + 120, titleBlock.y, titleBlock.x + 120, titleBlock.y + titleBlock.height)
        g2.drawLine(titleBlock.x + 300, titleBlock.y, titleBlock.x + 300, titleBlock.y + titleBlock.height)
        g2.color = Color(0x444444)
        g2.font = g2.font.deriveFont(12f)
        g2.drawString("orchestra", titleBlock.x + 12, titleBlock.y + 22)
        g2.drawString(repository.getDocument().name, titleBlock.x + 132, titleBlock.y + 22)

        val partsList = Rectangle(drawing.x + drawing.width - 220, drawing.y + 36, 190, 360)
        g2.color = Color(0xb0b0b0)
        g2.draw(partsList)
        repeat(18) { row ->
            val y = partsList.y + row * 20
            g2.drawLine(partsList.x, y, partsList.x + partsList.width, y)
        }
        g2.drawLine(partsList.x + 42, partsList.y, partsList.x + 42, partsList.y + partsList.height)
        g2.drawLine(partsList.x + 128, partsList.y, partsList.x + 128, partsList.y + partsList.height)
    }

    private fun drawNode(g2: Graphics2D, node: Node) {
        val r = node.layout.rect()
        val selected = node.id in selection
        val stereotype = node.stereotype()
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
        drawStateDot(g2, node, r)
        g2.stroke = previousStroke
    }

    private fun drawLink(g2: Graphics2D, node: Node) {
        val link = node.link ?: return
        val route = routeLink(node) ?: return
        val previousStroke = g2.stroke
        val selected = node.id in selection
        val stereotype = LinkClassifier.classify(repository.getDocument(), node)
        g2.color = linkColor(stereotype, selected)
        g2.stroke = linkStroke(stereotype, selected)
        route.points.zipWithNext().forEach { (a, b) -> g2.drawLine(a.x, a.y, b.x, b.y) }
        g2.fillRect(route.target.x - 5, route.target.y - 5, 10, 10)
        drawPortLabel(g2, link.sourcePortName.ifBlank { "out" }, route.source, true)
        drawPortLabel(g2, link.targetPortName.ifBlank { "in" }, route.target, false)
        if (selected) {
            g2.color = Color(0x1565c0)
            g2.fillRect(route.source.x - 5, route.source.y - 5, 10, 10)
            g2.fillRect(route.target.x - 5, route.target.y - 5, 10, 10)
        }
        g2.stroke = previousStroke
    }

    private fun routeLink(linkNode: Node): LinkRoute? {
        val link = linkNode.link ?: return null
        val sourceNode = repository.getNode(link.sourceNodeId) ?: return null
        val targetNode = repository.getNode(link.targetNodeId) ?: return null
        val source = portAnchor(sourceNode, linkNode, outgoing = true) ?: return null
        val target = portAnchor(targetNode, linkNode, outgoing = false) ?: return null
        val sourceStub = Point(source.point.x + 28 * source.xDirection, source.point.y)
        val targetStub = Point(target.point.x + 28 * target.xDirection, target.point.y)
        val midX = (sourceStub.x + targetStub.x) / 2
        val points = compact(
            listOf(
                source.point,
                sourceStub,
                Point(midX, sourceStub.y),
                Point(midX, targetStub.y),
                targetStub,
                target.point,
            ),
        )
        return LinkRoute(source.point, target.point, points)
    }

    private fun portAnchor(node: Node, linkNode: Node, outgoing: Boolean): PortAnchor? {
        val link = linkNode.link ?: return null
        val document = repository.getDocument()
        val r = node.layout.rect()
        val center = node.layout.center()
        val linkIds = if (outgoing) node.outgoingLinks else node.incomingLinks
        val sorted = linkIds
            .mapNotNull(document.nodes::get)
            .sortedBy { connectedAngle(center, it, outgoing) }
        val index = sorted.indexOfFirst { it.id == linkNode.id }.takeIf { it >= 0 } ?: 0
        val count = max(1, sorted.size)
        val otherId = if (outgoing) link.targetNodeId else link.sourceNodeId
        val otherCenter = document.nodes[otherId]?.layout?.center() ?: center
        val side = if (otherCenter.x >= center.x) 1 else -1
        val x = if (side > 0) r.x + r.width else r.x
        val y = r.y + ((index + 1) * r.height / (count + 1))
        return PortAnchor(Point(x, y), side)
    }

    private fun connectedAngle(center: Point, linkNode: Node, outgoing: Boolean): Double {
        val link = linkNode.link ?: return 0.0
        val otherId = if (outgoing) link.targetNodeId else link.sourceNodeId
        val otherCenter = repository.getNode(otherId)?.layout?.center() ?: center
        return atan2((otherCenter.y - center.y).toDouble(), (otherCenter.x - center.x).toDouble())
    }

    private fun compact(points: List<Point>): List<Point> =
        points.fold(mutableListOf()) { acc, point ->
            if (acc.lastOrNull() != point) acc += point
            acc
        }

    private fun drawPortLabel(g2: Graphics2D, label: String, anchor: Point, outgoing: Boolean) {
        val text = label.take(24)
        val metrics = g2.fontMetrics
        val width = metrics.stringWidth(text) + 8
        val height = metrics.height + 2
        val x = if (outgoing) anchor.x + 8 else anchor.x - width - 8
        val y = anchor.y - height / 2
        g2.color = Color(0xfdfdfd)
        g2.fillRect(x, y, width, height)
        g2.color = Color(0x3344cc)
        g2.stroke = BasicStroke(1f)
        g2.drawRect(x, y, width, height)
        g2.color = Color(0x222222)
        g2.font = g2.font.deriveFont(9f)
        g2.drawString(text, x + 4, y + height - 5)
    }

    private fun drawStateDot(g2: Graphics2D, node: Node, r: Rectangle) {
        val state = node.metadata["state"]?.trim()?.lowercase()
        val color = when (state) {
            "done", "complete", "completed" -> Color(0x1db954)
            "blocked", "error", "failed" -> Color(0xff0000)
            "todo", "pending" -> Color(0xffd800)
            "active", "doing", "in-progress", "running" -> Color(0x00a6d6)
            else -> when (node.stereotype()) {
                NodeStereotype.ErrorHandler,
                NodeStereotype.CompositeErrorHandler -> Color(0xffd800)
                NodeStereotype.ServiceLibrary -> Color(0x3333cc)
                else -> Color(0xff0000)
            }
        }
        g2.color = color
        g2.fillOval(r.x - 8, r.y - 8, 18, 18)
    }

    private fun handlePressed(e: MouseEvent) {
        if (mode == CanvasMode.Pan || SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
            panDragStart = e.point
            return
        }
        val point = modelPoint(e.point)
        dragStart = point
        val hit = hitNode(point)
        val hitLink = if (hit == null) hitLink(point) else null
        when (mode) {
            CanvasMode.CreateNode -> {
                val parent = hit?.takeIf { repository.getNode(it)?.isLink != true }
                val node = repository.createNode(parent, "New Node", NodeKind.Processor)
                repository.updateNodeLayout(node.id, NodeLayout(point.x.toDouble(), point.y.toDouble(), 180.0, 90.0))
                selection.clear()
                selection += node.id
                mode = CanvasMode.Select
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
                        mode = CanvasMode.Select
                    }
                    onSelectionChanged()
                }
            }
            CanvasMode.Select -> {
                if (hit != null) {
                    if (!e.isShiftDown) selection.clear()
                    selection += hit
                    onSelectionChanged()
                } else if (hitLink != null) {
                    if (!e.isShiftDown) selection.clear()
                    selection += hitLink
                    onSelectionChanged()
                } else {
                    selection.clear()
                    selectionRect = Rectangle(point)
                    onSelectionChanged()
                }
            }
            CanvasMode.Reparent -> {
                if (selection.isEmpty()) {
                    hit?.let {
                        selection.clear()
                        selection += it
                        onSelectionChanged()
                    }
                } else {
                    val targetParent = hit?.takeUnless { it in selection } ?: repository.getDocument().rootNodeId
                    reparentSelection(targetParent)
                    mode = CanvasMode.Select
                    refreshAll()
                }
            }
            CanvasMode.Pan -> Unit
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
        val start = dragStart ?: return
        val point = modelPoint(e.point)
        if (selectionRect != null) {
            selectionRect = Rectangle(min(start.x, point.x), min(start.y, point.y), kotlin.math.abs(point.x - start.x), kotlin.math.abs(point.y - start.y))
        } else if (selection.isNotEmpty() && mode == CanvasMode.Select) {
            val dx = point.x - start.x
            val dy = point.y - start.y
            selection.mapNotNull(repository::getNode).filter { !it.isLink }.forEach {
                it.layout.x += dx
                it.layout.y += dy
            }
            repository.markDirty()
            dragStart = point
        }
        repaint()
    }

    private fun handleReleased(e: MouseEvent) {
        if (panDragStart != null) {
            panDragStart = null
            if (mode == CanvasMode.Pan) mode = CanvasMode.Select
            return
        }
        selectionRect?.let { rect ->
            selection.clear()
            repository.getDocument().nodes.values
                .filter { !it.isLink && it.id != repository.getDocument().rootNodeId && it.layout.rect().intersects(rect) }
                .forEach { selection += it.id }
            selectionRect = null
        }
        if (selection.isNotEmpty()) updateParentAfterDrag()
        dragStart = null
        refreshAll()
    }

    private fun createLink(sourceId: NodeId, targetId: NodeId) {
        ensureDefaultPort(sourceId, PortDirection.Output, "out")
        ensureDefaultPort(targetId, PortDirection.Input, "in")
        val source = repository.requireNode(sourceId)
        val target = repository.requireNode(targetId)
        val link = repository.createLink(repository.getDocument().rootNodeId, "${source.name} -> ${target.name}", sourceId, "out", targetId, "in")
        link.link?.transportKind = when {
            source.stereotype() == NodeStereotype.ServiceLibrary -> "usage"
            target.stereotype() in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> "error"
            else -> "packet"
        }
        selection.clear()
        selection += link.id
        repository.markDirty()
    }

    private fun ensureDefaultPort(nodeId: NodeId, direction: PortDirection, name: String) {
        val node = repository.requireNode(nodeId)
        if (node.ports.none { it.direction == direction && it.name == name }) {
            repository.addPort(nodeId, NodePort("${direction.name.lowercase()}_$name", name, direction))
        }
    }

    private fun updateParentAfterDrag() {
        selection.mapNotNull(repository::getNode).filter { !it.isLink }.forEach { moved ->
            val center = moved.layout.center()
            val newParent = repository.getDocument().nodes.values
                .filter { it.id != moved.id && it.id != repository.getDocument().rootNodeId && !it.isLink && it.layout.rect().contains(center) }
                .minByOrNull { it.layout.width * it.layout.height }
                ?.id
                ?: repository.getDocument().rootNodeId
            if (moved.parentId != newParent && moved.id != repository.getDocument().rootNodeId) {
                runCatching { repository.moveNode(moved.id, newParent) }
            }
        }
    }

    private fun reparentSelection(targetParent: NodeId) {
        val root = repository.getDocument().rootNodeId
        val parent = targetParent.takeIf { it !in selection } ?: root
        selection.mapNotNull(repository::getNode).filter { !it.isLink && it.id != root }.forEach {
            runCatching { repository.moveNode(it.id, parent) }
        }
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
                routeLink(link)?.points?.zipWithNext()?.any { (a, b) -> distanceToSegment(point, a, b) <= 8.0 } == true
            }
            ?.id

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
        node.stereotype() == NodeStereotype.ServiceLibrary -> Color(0xf6f7ff)
        node.stereotype() in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> Color(0xfffbfb)
        node.stereotype() in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> Color(0xf8fff8)
        else -> Color.WHITE
    }

    private fun strokeFor(node: Node, selected: Boolean): Color = when {
        selected -> Color(0x3366cc)
        node.stereotype() in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> Color(0xcc3333)
        node.stereotype() in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> Color(0x33aa33)
        node.stereotype() == NodeStereotype.ServiceLibrary -> Color(0x3333cc)
        else -> Color(0x222222)
    }

    private fun nodeStroke(node: Node, selected: Boolean): Stroke = when {
        selected -> BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
        node.children.isNotEmpty() -> BasicStroke(2.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, floatArrayOf(24f, 8f, 4f, 8f), 0f)
        node.stereotype() == NodeStereotype.ServiceLibrary -> BasicStroke(2.2f)
        node.stereotype() in setOf(NodeStereotype.ErrorHandler, NodeStereotype.CompositeErrorHandler) -> BasicStroke(2.2f)
        node.stereotype() in setOf(NodeStereotype.Test, NodeStereotype.TestSuite) -> BasicStroke(2.2f)
        else -> BasicStroke(2f)
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
) : JPanel(BorderLayout()) {
    private var nodeId: NodeId? = null
    private var binding = false
    private val nameField = JTextField()
    private val language = JTextField()
    private val technology = JTextField()
    private val state = JTextField()
    private val linkTransportKind = JTextField()
    private val payloadDefinition = JTextArea(5, 24)
    private val metadata = JTextArea(5, 24)

    init {
        listOf(nameField, language, technology, state, linkTransportKind).forEach(::applyOnCommit)
        listOf(metadata, payloadDefinition).forEach(::applyOnFocusLost)
        val form = JPanel(java.awt.GridLayout(0, 1, 4, 4)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(JLabel("Name")); add(nameField)
            add(JLabel("Language")); add(language)
            add(JLabel("Technology")); add(technology)
            add(JLabel("State")); add(state)
            add(JLabel("Link transport kind")); add(linkTransportKind)
            add(JLabel("Link payload definition")); add(JScrollPane(payloadDefinition))
            add(JLabel("Metadata key=value")); add(JScrollPane(metadata))
            add(JButton("Apply").apply { addActionListener { apply() } })
        }
        add(form, BorderLayout.NORTH)
    }

    fun bind(id: NodeId?) {
        if (nodeId == id && isEditing()) return
        binding = true
        nodeId = id
        val node = id?.let(repository::getNode)
        nameField.text = node?.name.orEmpty()
        language.text = node?.technology?.languageId.orEmpty()
        technology.text = node?.technology?.technologyId.orEmpty()
        state.text = node?.metadata?.get("state").orEmpty()
        linkTransportKind.text = node?.link?.transportKind.orEmpty()
        payloadDefinition.text = node?.link?.payloadDefinition.orEmpty()
        metadata.text = node?.metadata?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty()
        binding = false
    }

    private fun applyOnCommit(field: JTextField) {
        field.addActionListener { apply() }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = apply()
        })
    }

    private fun applyOnFocusLost(area: JTextArea) {
        area.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = apply()
        })
    }

    private fun isEditing(): Boolean =
        nameField.hasFocus() ||
            language.hasFocus() ||
            technology.hasFocus() ||
            state.hasFocus() ||
            linkTransportKind.hasFocus() ||
            payloadDefinition.hasFocus() ||
            metadata.hasFocus()

    private fun parseMetadata(): MutableMap<String, String> {
        val next = mutableMapOf<String, String>()
        metadata.text.lines().filter { "=" in it }.forEach {
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim()
            if (key.isNotBlank()) next[key] = value
        }
        if (state.text.isNotBlank()) next["state"] = state.text
        return next
    }

    private fun apply() {
        if (binding) return
        val id = nodeId ?: return
        val node = repository.requireNode(id)
        repository.renameNode(id, nameField.text)
        repository.updateNodeTechnology(id, node.technology.copy(languageId = language.text, technologyId = technology.text))
        node.metadata.clear()
        node.metadata.putAll(parseMetadata())
        node.link?.let {
            it.transportKind = linkTransportKind.text.ifBlank { "packet" }
            it.payloadDefinition = payloadDefinition.text
        }
        repository.markDirty()
        refreshAll()
    }
}

private class NodeEditorTabs(
    private val repository: DocumentRepository,
    private val refreshAll: () -> Unit,
) : JTabbedPane() {
    fun bind(ids: List<NodeId>) {
        removeAll()
        ids.mapNotNull(repository::getNode).forEach { node ->
            addTab(node.name, NodeTextEditor(repository, node.id, refreshAll))
        }
    }
}

private class NodeTextEditor(
    private val repository: DocumentRepository,
    private val nodeId: NodeId,
    private val refreshAll: () -> Unit,
) : JTabbedPane() {
    init {
        addTextTab("Source", { it.source }, { text, value -> text.copy(source = value) })
        addTextTab("Specification", { it.specification }, { text, value -> text.copy(specification = value) })
        addTextTab("Tests", { it.tests }, { text, value -> text.copy(tests = value) })
        addTextTab("AI Instructions", { it.aiInstructions }, { text, value -> text.copy(aiInstructions = value) })
    }

    private fun addTextTab(label: String, getter: (NodeText) -> String, setter: (NodeText, String) -> NodeText) {
        val editor = SwingCodeEditorAdapter()
        val node = repository.requireNode(nodeId)
        editor.setTechnology(node.technology)
        editor.setText(getter(node.text))
        val timer = Timer(250) {
            val current = repository.requireNode(nodeId)
            repository.updateNodeText(nodeId, setter(current.text, editor.getText()))
            refreshAll()
        }
        timer.isRepeats = false
        editor.onTextChanged = { timer.restart() }
        addTab(label, editor)
    }
}

interface PluginUiProvider {
    val id: String
    val displayName: String
    fun createPanel(repository: DocumentRepository, selectedNodeIds: List<NodeId>): JComponent
}

private fun NodeLayout.rect(): Rectangle = Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())
private fun NodeLayout.center(): Point = Point((x + width / 2).toInt(), (y + height / 2).toInt())

fun launchDesktopApp() {
    SwingUtilities.invokeLater {
        OrchestraDesktopApp().show()
    }
}
