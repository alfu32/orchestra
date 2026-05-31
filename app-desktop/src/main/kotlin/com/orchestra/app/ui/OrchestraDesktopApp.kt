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
    private var dragAllowsReparent = false
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
        val links = document.nodes.values.filter { it.isLink }
        links.filterNot(::isDependencyAnnotation).forEach { drawLink(g2, it) }
        document.nodes.values.filter { !it.isLink && it.id != document.rootNodeId }.sortedBy { it.children.isEmpty() }.forEach { drawNode(g2, it) }
        drawDependencyAnnotations(g2, links.filter(::isDependencyAnnotation))
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
        drawNodePorts(g2, node)
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
        drawArrowAlongRoute(g2, route.points)
        drawPortLabel(g2, link.sourcePortName.ifBlank { "out" }, route.source, true)
        drawPortLabel(g2, link.targetPortName.ifBlank { "in" }, route.target, false)
        if (selected) {
            g2.color = Color(0x1565c0)
            drawPortMarker(g2, route.source, sourceDirection(route.source, route.points.getOrNull(1)), outgoing = true)
            drawPortMarker(g2, route.target, targetDirection(route.target, route.points.getOrNull(route.points.lastIndex - 1)), outgoing = false)
        }
        g2.stroke = previousStroke
    }

    private fun drawNodePorts(g2: Graphics2D, node: Node) {
        normalConnectedLinks(node, outgoing = true).forEach { linkNode ->
            val anchor = portAnchor(node, linkNode, outgoing = true) ?: return@forEach
            g2.color = linkColor(LinkClassifier.classify(repository.getDocument(), linkNode), linkNode.id in selection)
            drawPortMarker(g2, anchor.point, anchor.xDirection, outgoing = true)
        }
        normalConnectedLinks(node, outgoing = false).forEach { linkNode ->
            val anchor = portAnchor(node, linkNode, outgoing = false) ?: return@forEach
            g2.color = linkColor(LinkClassifier.classify(repository.getDocument(), linkNode), linkNode.id in selection)
            drawPortMarker(g2, anchor.point, anchor.xDirection, outgoing = false)
        }
    }

    private fun drawPortMarker(g2: Graphics2D, point: Point, side: Int, outgoing: Boolean) {
        val x = point.x
        val y = point.y
        if (outgoing) {
            g2.fillRect(x - 4, y - 7, 8, 14)
            g2.drawLine(x, y, x + side * 18, y)
            val tip = Point(x + side * 18, y)
            fillTriangle(g2, tip, side, 7)
        } else {
            val tip = Point(x, y)
            fillTriangle(g2, tip, -side, 8)
            g2.drawLine(x - side * 18, y, x, y)
        }
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

    private fun fillTriangle(g2: Graphics2D, tip: Point, horizontalDirection: Int, size: Int) {
        val baseX = tip.x - horizontalDirection * size
        g2.fillPolygon(
            intArrayOf(tip.x, baseX, baseX),
            intArrayOf(tip.y, tip.y - size, tip.y + size),
            3,
        )
    }

    private fun sourceDirection(source: Point, next: Point?): Int =
        next?.let { if (it.x >= source.x) 1 else -1 } ?: 1

    private fun targetDirection(target: Point, previous: Point?): Int =
        previous?.let { if (it.x >= target.x) 1 else -1 } ?: -1

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
        linkNode.link ?: return null
        val r = node.layout.rect()
        val center = node.layout.center()
        val side = linkSide(node, linkNode, outgoing)
        val sorted = normalLinksOnSide(node, side)
            .sortedBy { connectedAngle(center, it, it.link?.sourceNodeId == node.id) }
        val index = sorted.indexOfFirst { it.id == linkNode.id }.takeIf { it >= 0 } ?: 0
        val count = max(1, sorted.size)
        val x = if (side > 0) r.x + r.width else r.x
        val y = r.y + ((index + 1) * r.height / (count + 1))
        return PortAnchor(Point(x, y), side)
    }

    private fun normalConnectedLinks(node: Node, outgoing: Boolean): List<Node> {
        val document = repository.getDocument()
        val ids = if (outgoing) node.outgoingLinks else node.incomingLinks
        return ids.mapNotNull(document.nodes::get).filterNot(::isDependencyAnnotation)
    }

    private fun normalLinksOnSide(node: Node, side: Int): List<Node> {
        val document = repository.getDocument()
        val ids = node.outgoingLinks + node.incomingLinks
        return ids.mapNotNull(document.nodes::get)
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
                val parent = selection.firstOrNull()
                    ?.let(repository::getNode)
                    ?.takeIf { !it.isLink }
                    ?.id
                    ?: hit?.takeIf { repository.getNode(it)?.isLink != true }
                    ?: repository.getDocument().rootNodeId
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
                    if (hit !in selection) {
                        if (!e.isShiftDown) selection.clear()
                        selection += hit
                    }
                    dragAllowsReparent = e.isControlDown
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
            selectedMoveRoots().forEach { moveNodeAndDescendants(it, dx.toDouble(), dy.toDouble()) }
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
        if (selection.isNotEmpty() && dragAllowsReparent) updateParentAfterDrag(modelPoint(e.point))
        dragAllowsReparent = false
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
