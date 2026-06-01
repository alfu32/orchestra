package com.orchestra.app.editor

import com.orchestra.completion.CompletionRequest
import com.orchestra.completion.CompletionSuggestion
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.TechnologyMetadata
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min

class GridCodeEditorAdapter : JPanel(), CodeEditorAdapter {
    private data class BufferPosition(val line: Int, val column: Int) : Comparable<BufferPosition> {
        override fun compareTo(other: BufferPosition): Int =
            compareValuesBy(this, other, BufferPosition::line, BufferPosition::column)
    }

    private data class Snapshot(
        val lines: List<String>,
        val caret: BufferPosition,
        val anchor: BufferPosition?,
        val scrollLine: Int,
        val scrollColumn: Int,
    )

    private val editorFont = Font(Font.MONOSPACED, Font.PLAIN, 14)
    private val lines = mutableListOf("")
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var caret = BufferPosition(0, 0)
    private var selectionAnchor: BufferPosition? = null
    private var scrollLine = 0
    private var scrollColumn = 0
    private var readOnly = false
    private var languageId = ""
    private var technology = TechnologyMetadata()
    private var completionContext = EditorCompletionContext(null, NodeTextSection.Source)
    private var diagnostics: List<Diagnostic> = emptyList()
    private var completionItems: List<CompletionSuggestion> = emptyList()
    private var completionIndex = 0
    private var cursorVisible = true
    private val menuMask = runCatching { Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx }
        .getOrDefault(InputEvent.CTRL_DOWN_MASK)

    override var onTextChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((EditorCursor) -> Unit)? = null
    override var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)? = null

    init {
        isFocusable = true
        background = Color(0x1e1e1e)
        foreground = Color(0xd4d4d4)
        preferredSize = Dimension(900, 520)
        toolTipText = "plain text"

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) = handleKeyPressed(e)
            override fun keyTyped(e: KeyEvent) = handleKeyTyped(e)
        })
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                val pos = positionAt(e.point)
                if (e.isShiftDown) {
                    if (selectionAnchor == null) selectionAnchor = caret
                    caret = pos
                } else {
                    caret = pos
                    selectionAnchor = null
                }
                hideCompletions()
                notifyCursor()
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                notifyCursor()
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (selectionAnchor == null) selectionAnchor = caret
                caret = positionAt(e.point)
                ensureCaretVisible()
                notifyCursor()
                repaint()
            }
        })
        addMouseWheelListener { e: MouseWheelEvent ->
            if (e.isShiftDown) {
                scrollColumn = (scrollColumn + e.preciseWheelRotation.toInt() * 4).coerceAtLeast(0)
            } else {
                scrollLine = (scrollLine + (e.preciseWheelRotation * 3).toInt()).coerceIn(0, maxScrollLine())
            }
            repaint()
        }
        Timer(530) {
            cursorVisible = !cursorVisible
            if (hasFocus()) repaint()
        }.start()
    }

    override fun setText(text: String) {
        if (getText() == text) return
        lines.clear()
        lines += splitLines(text)
        if (lines.isEmpty()) lines += ""
        caret = BufferPosition(0, 0)
        selectionAnchor = null
        scrollLine = 0
        scrollColumn = 0
        undoStack.clear()
        redoStack.clear()
        hideCompletions()
        notifyCursor()
        repaint()
    }

    override fun getText(): String = lines.joinToString("\n")

    override fun setLanguage(languageId: String) {
        this.languageId = RegexSyntaxHighlighter.normalizeLanguage(languageId)
        updateToolTip()
        repaint()
    }

    override fun setTechnology(technology: TechnologyMetadata) {
        this.technology = technology
        setLanguage(technology.languageId)
    }

    override fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        repaint()
    }

    override fun setDiagnostics(diagnostics: List<Diagnostic>) {
        this.diagnostics = diagnostics
        updateToolTip()
        repaint()
    }

    override fun setCompletionContext(context: EditorCompletionContext) {
        completionContext = context
    }

    override fun focus() {
        requestFocusInWindow()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.font = editorFont
        val metrics = g2.fontMetrics
        val lineHeight = metrics.height
        val charWidth = max(1, metrics.charWidth('M'))
        val gutterWidth = gutterWidth(metrics, charWidth)
        val visibleRows = max(1, height / lineHeight)
        val visibleCols = max(1, (width - gutterWidth - charWidth) / charWidth)
        scrollLine = scrollLine.coerceIn(0, maxScrollLine())

        g2.color = Color(0x1e1e1e)
        g2.fillRect(0, 0, width, height)
        drawGutter(g2, metrics, lineHeight, charWidth, gutterWidth, visibleRows)
        for (row in 0 until visibleRows) {
            val lineIndex = scrollLine + row
            if (lineIndex >= lines.size) break
            val baseline = row * lineHeight + metrics.ascent
            drawLineBackground(g2, row, lineHeight, gutterWidth)
            drawSelection(g2, lineIndex, row, lineHeight, charWidth, gutterWidth, visibleCols)
            drawHighlightedLine(g2, lines[lineIndex], lineIndex, gutterWidth, baseline, charWidth, visibleCols)
            drawDiagnostics(g2, lineIndex, row, lineHeight, gutterWidth, charWidth, visibleCols)
        }
        drawCaret(g2, metrics, lineHeight, charWidth, gutterWidth, visibleRows)
        drawCompletionPopup(g2, metrics, lineHeight, charWidth, gutterWidth, visibleRows)
        drawEditorStatus(g2, metrics)
    }

    private fun handleKeyPressed(e: KeyEvent) {
        cursorVisible = true
        if (completionItems.isNotEmpty() && handleCompletionKey(e)) return
        val menu = (e.modifiersEx and menuMask) != 0
        val shift = e.isShiftDown

        if (menu) {
            when (e.keyCode) {
                KeyEvent.VK_A -> {
                    selectAll()
                    e.consume()
                }
                KeyEvent.VK_C -> {
                    copySelection()
                    e.consume()
                }
                KeyEvent.VK_X -> {
                    cutSelection()
                    e.consume()
                }
                KeyEvent.VK_V -> {
                    pasteClipboard()
                    e.consume()
                }
                KeyEvent.VK_Z -> {
                    if (e.isShiftDown) redo() else undo()
                    e.consume()
                }
                KeyEvent.VK_Y -> {
                    redo()
                    e.consume()
                }
                KeyEvent.VK_SPACE -> {
                    requestCompletions()
                    e.consume()
                }
            }
            return
        }

        when (e.keyCode) {
            KeyEvent.VK_LEFT -> moveHorizontal(-1, shift, e.isControlDown)
            KeyEvent.VK_RIGHT -> moveHorizontal(1, shift, e.isControlDown)
            KeyEvent.VK_UP -> moveVertical(-1, shift)
            KeyEvent.VK_DOWN -> moveVertical(1, shift)
            KeyEvent.VK_HOME -> moveTo(BufferPosition(caret.line, 0), shift)
            KeyEvent.VK_END -> moveTo(BufferPosition(caret.line, lines[caret.line].length), shift)
            KeyEvent.VK_PAGE_UP -> moveVertical(-visibleRowCount(), shift)
            KeyEvent.VK_PAGE_DOWN -> moveVertical(visibleRowCount(), shift)
            KeyEvent.VK_BACK_SPACE -> edit { deleteBackspace() }
            KeyEvent.VK_DELETE -> edit { deleteForward() }
            KeyEvent.VK_ENTER -> edit { insertTextAtCaret("\n") }
            KeyEvent.VK_TAB -> edit { insertTextAtCaret("    ") }
            KeyEvent.VK_ESCAPE -> hideCompletions()
            else -> return
        }
        e.consume()
    }

    private fun handleKeyTyped(e: KeyEvent) {
        if (readOnly || e.isControlDown || e.isMetaDown || e.isAltDown) return
        val char = e.keyChar
        if (char == KeyEvent.CHAR_UNDEFINED || char < ' ' || char == '\u007f') return
        cursorVisible = true
        edit { insertTextAtCaret(char.toString()) }
        e.consume()
    }

    private fun handleCompletionKey(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.VK_ESCAPE -> hideCompletions()
            KeyEvent.VK_UP -> completionIndex = (completionIndex - 1).coerceAtLeast(0)
            KeyEvent.VK_DOWN -> completionIndex = (completionIndex + 1).coerceAtMost(completionItems.lastIndex)
            KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> applyCompletion(completionItems[completionIndex])
            else -> return false
        }
        repaint()
        e.consume()
        return true
    }

    private fun requestCompletions() {
        val nodeId = completionContext.nodeId?.let(::NodeId) ?: return
        val line = lines[caret.line]
        val prefix = currentPrefix()
        val request = CompletionRequest(
            nodeId = nodeId,
            textSection = completionContext.textSection,
            languageId = languageId,
            technologyId = technology.technologyId,
            cursorOffset = offsetFor(caret),
            fullText = getText(),
            currentLine = line,
            prefix = prefix,
        )
        completionItems = onCompletionRequested?.invoke(request).orEmpty().take(12)
        completionIndex = 0
        repaint()
    }

    private fun applyCompletion(suggestion: CompletionSuggestion) {
        val prefix = currentPrefix()
        val start = BufferPosition(caret.line, (caret.column - prefix.length).coerceAtLeast(0))
        selectionAnchor = start
        edit { insertTextAtCaret(suggestion.insertText) }
        hideCompletions()
    }

    private fun edit(operation: () -> Unit) {
        if (readOnly) return
        undoStack.addLast(snapshot())
        while (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        operation()
        hideCompletions()
        notifyTextChanged()
    }

    private fun insertTextAtCaret(value: String) {
        deleteSelectionIfAny()
        val pieces = splitLines(value)
        val current = lines[caret.line]
        val before = current.substring(0, caret.column)
        val after = current.substring(caret.column)
        if (pieces.size == 1) {
            lines[caret.line] = before + pieces.first() + after
            caret = BufferPosition(caret.line, caret.column + pieces.first().length)
        } else {
            lines[caret.line] = before + pieces.first()
            val insertAt = caret.line + 1
            pieces.drop(1).dropLast(1).forEachIndexed { index, line ->
                lines.add(insertAt + index, line)
            }
            val last = pieces.last()
            lines.add(insertAt + pieces.size - 2, last + after)
            caret = BufferPosition(insertAt + pieces.size - 2, last.length)
        }
        selectionAnchor = null
    }

    private fun deleteBackspace() {
        if (deleteSelectionIfAny()) return
        if (caret.column > 0) {
            val line = lines[caret.line]
            lines[caret.line] = line.removeRange(caret.column - 1, caret.column)
            caret = BufferPosition(caret.line, caret.column - 1)
        } else if (caret.line > 0) {
            val previousLength = lines[caret.line - 1].length
            lines[caret.line - 1] += lines.removeAt(caret.line)
            caret = BufferPosition(caret.line - 1, previousLength)
        }
    }

    private fun deleteForward() {
        if (deleteSelectionIfAny()) return
        val line = lines[caret.line]
        if (caret.column < line.length) {
            lines[caret.line] = line.removeRange(caret.column, caret.column + 1)
        } else if (caret.line < lines.lastIndex) {
            lines[caret.line] += lines.removeAt(caret.line + 1)
        }
    }

    private fun deleteSelectionIfAny(): Boolean {
        val range = selectionRange() ?: return false
        if (range.first.line == range.second.line) {
            val line = lines[range.first.line]
            lines[range.first.line] = line.removeRange(range.first.column, range.second.column)
        } else {
            val firstPrefix = lines[range.first.line].substring(0, range.first.column)
            val lastSuffix = lines[range.second.line].substring(range.second.column)
            repeat(range.second.line - range.first.line) {
                lines.removeAt(range.first.line + 1)
            }
            lines[range.first.line] = firstPrefix + lastSuffix
        }
        caret = range.first
        selectionAnchor = null
        return true
    }

    private fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(this.snapshot())
        restore(snapshot)
        notifyTextChanged()
    }

    private fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(this.snapshot())
        restore(snapshot)
        notifyTextChanged()
    }

    private fun copySelection(): Boolean {
        val text = selectionText()
        if (text.isEmpty()) return false
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
        return true
    }

    private fun cutSelection(): Boolean {
        if (!copySelection()) return false
        edit { deleteSelectionIfAny() }
        return true
    }

    private fun pasteClipboard() {
        val text = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull() ?: return
        edit { insertTextAtCaret(text) }
    }

    private fun selectAll() {
        selectionAnchor = BufferPosition(0, 0)
        caret = BufferPosition(lines.lastIndex, lines.last().length)
        notifyCursor()
        repaint()
    }

    private fun moveHorizontal(delta: Int, expand: Boolean, word: Boolean) {
        val target = if (word) nextWordPosition(delta) else stepHorizontal(delta)
        moveTo(target, expand)
    }

    private fun moveVertical(delta: Int, expand: Boolean) {
        val line = (caret.line + delta).coerceIn(0, lines.lastIndex)
        moveTo(BufferPosition(line, caret.column.coerceAtMost(lines[line].length)), expand)
    }

    private fun moveTo(position: BufferPosition, expand: Boolean) {
        if (expand && selectionAnchor == null) selectionAnchor = caret
        if (!expand) selectionAnchor = null
        caret = clamp(position)
        ensureCaretVisible()
        notifyCursor()
        repaint()
    }

    private fun stepHorizontal(delta: Int): BufferPosition =
        if (delta < 0) {
            when {
                caret.column > 0 -> BufferPosition(caret.line, caret.column - 1)
                caret.line > 0 -> BufferPosition(caret.line - 1, lines[caret.line - 1].length)
                else -> caret
            }
        } else {
            when {
                caret.column < lines[caret.line].length -> BufferPosition(caret.line, caret.column + 1)
                caret.line < lines.lastIndex -> BufferPosition(caret.line + 1, 0)
                else -> caret
            }
        }

    private fun nextWordPosition(delta: Int): BufferPosition {
        var pos = caret
        var previous = pos
        do {
            previous = pos
            pos = if (delta < 0) stepHorizontal(-1) else stepHorizontal(1)
            caret = pos
        } while (pos != previous && currentChar(pos)?.let { it.isLetterOrDigit() || it == '_' } == false)
        caret = previous
        return pos
    }

    private fun currentChar(pos: BufferPosition): Char? =
        lines.getOrNull(pos.line)?.getOrNull((pos.column - 1).coerceAtLeast(0))

    private fun drawGutter(
        g2: Graphics2D,
        metrics: FontMetrics,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visibleRows: Int,
    ) {
        g2.color = Color(0x252526)
        g2.fillRect(0, 0, gutterWidth, height)
        g2.color = Color(0x858585)
        for (row in 0 until visibleRows) {
            val lineIndex = scrollLine + row
            if (lineIndex >= lines.size) break
            val label = (lineIndex + 1).toString().padStart((lines.size + 1).toString().length)
            g2.drawString(label, charWidth, row * lineHeight + metrics.ascent)
        }
        g2.color = Color(0x3c3c3c)
        g2.drawLine(gutterWidth - 1, 0, gutterWidth - 1, height)
    }

    private fun drawLineBackground(g2: Graphics2D, row: Int, lineHeight: Int, gutterWidth: Int) {
        if (scrollLine + row == caret.line) {
            g2.color = Color(0x282828)
            g2.fillRect(gutterWidth, row * lineHeight, width - gutterWidth, lineHeight)
        }
    }

    private fun drawHighlightedLine(
        g2: Graphics2D,
        line: String,
        lineIndex: Int,
        gutterWidth: Int,
        baseline: Int,
        charWidth: Int,
        visibleCols: Int,
    ) {
        val start = scrollColumn.coerceAtMost(line.length)
        val end = (scrollColumn + visibleCols).coerceAtMost(line.length)
        if (start >= end) return
        val tokens = RegexSyntaxHighlighter.highlightLine(languageId, line)
        var cursor = start

        fun drawSegment(segmentStart: Int, segmentEnd: Int, color: Color) {
            if (segmentEnd <= segmentStart) return
            g2.color = color
            val x = gutterWidth + (segmentStart - scrollColumn) * charWidth
            g2.drawString(line.substring(segmentStart, segmentEnd), x, baseline)
        }

        tokens.forEach { token ->
            val tokenStart = token.start.coerceAtLeast(start)
            val tokenEnd = token.endExclusive.coerceAtMost(end)
            if (tokenEnd <= tokenStart) return@forEach
            if (cursor < tokenStart) drawSegment(cursor, tokenStart, RegexSyntaxHighlighter.Default)
            drawSegment(tokenStart, tokenEnd, token.color)
            cursor = tokenEnd.coerceAtLeast(cursor)
        }
        if (cursor < end) drawSegment(cursor, end, RegexSyntaxHighlighter.Default)
        if (diagnostics.any { it.line == lineIndex + 1 }) {
            g2.color = Color(0xff6b68)
            g2.fillRect(gutterWidth, baseline + 3, max(1, (end - start) * charWidth), 2)
        }
    }

    private fun drawSelection(
        g2: Graphics2D,
        lineIndex: Int,
        row: Int,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visibleCols: Int,
    ) {
        val selected = selectedColumns(lineIndex) ?: return
        val start = selected.first.coerceAtLeast(scrollColumn)
        val end = selected.last.coerceAtMost(scrollColumn + visibleCols)
        if (end < start) return
        g2.color = Color(0x3a5f8a)
        g2.fillRect(gutterWidth + (start - scrollColumn) * charWidth, row * lineHeight, max(1, (end - start) * charWidth), lineHeight)
    }

    private fun drawDiagnostics(
        g2: Graphics2D,
        lineIndex: Int,
        row: Int,
        lineHeight: Int,
        gutterWidth: Int,
        charWidth: Int,
        visibleCols: Int,
    ) {
        val diagnostic = diagnostics.firstOrNull { it.line == lineIndex + 1 } ?: return
        g2.color = when (diagnostic.severity) {
            DiagnosticSeverity.Error -> Color(0xff5555)
            DiagnosticSeverity.Warning -> Color(0xd7ba7d)
            DiagnosticSeverity.Info -> Color(0x75beff)
        }
        val column = (diagnostic.column ?: 1).coerceAtLeast(1) - 1
        if (column in scrollColumn..(scrollColumn + visibleCols)) {
            val x = gutterWidth + (column - scrollColumn) * charWidth
            val y = row * lineHeight + lineHeight - 3
            g2.fillRect(x, y, charWidth.coerceAtLeast(4), 2)
        }
    }

    private fun drawCaret(
        g2: Graphics2D,
        metrics: FontMetrics,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visibleRows: Int,
    ) {
        if (!hasFocus() || !cursorVisible || selectionRange() != null) return
        val row = caret.line - scrollLine
        if (row !in 0 until visibleRows) return
        val col = caret.column - scrollColumn
        if (col < 0) return
        val x = gutterWidth + col * charWidth
        val y = row * lineHeight + 2
        g2.color = Color(0xf2f2f2)
        g2.fillRect(x, y, 2, metrics.height - 4)
    }

    private fun drawCompletionPopup(
        g2: Graphics2D,
        metrics: FontMetrics,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visibleRows: Int,
    ) {
        if (completionItems.isEmpty()) return
        val row = (caret.line - scrollLine + 1).coerceIn(0, visibleRows - 1)
        val col = (caret.column - scrollColumn).coerceAtLeast(0)
        val labelColumnWidth = completionItems.maxOf { metrics.stringWidth(it.label.take(40)) }.coerceIn(120, 260)
        val detailColumnWidth = completionItems.maxOf { metrics.stringWidth(it.detail.take(52)) }.coerceIn(120, 320)
        val desiredWidth = labelColumnWidth + detailColumnWidth + 36
        val popupWidth = min(max(320, desiredWidth), max(320, width - gutterWidth - 16))
        val popupHeight = completionItems.size * lineHeight + 6
        val x = (gutterWidth + col * charWidth).coerceIn(0, max(0, width - popupWidth - 4))
        val y = (row * lineHeight).coerceIn(0, max(0, height - popupHeight - 4))
        val detailX = x + 14 + labelColumnWidth + 16
        g2.color = Color(0x252526)
        g2.fillRect(x, y, popupWidth, popupHeight)
        g2.color = Color(0x5f5f5f)
        g2.drawRect(x, y, popupWidth, popupHeight)
        completionItems.forEachIndexed { index, item ->
            val rowY = y + 3 + index * lineHeight
            if (index == completionIndex) {
                g2.color = Color(0x094771)
                g2.fillRect(x + 1, rowY, popupWidth - 2, lineHeight)
            }
            g2.color = Color(0xd4d4d4)
            g2.drawString(item.label.take(40), x + 8, rowY + metrics.ascent)
            if (item.detail.isNotBlank()) {
                g2.color = Color(0x9cdcfe)
                g2.drawString(item.detail.take(52), detailX, rowY + metrics.ascent)
            }
        }
    }

    private fun drawEditorStatus(g2: Graphics2D, metrics: FontMetrics) {
        val text = "${languageId.ifBlank { "plain" }}  ${caret.line + 1}:${caret.column + 1}${if (readOnly) "  read-only" else ""}"
        g2.color = Color(0x858585)
        g2.drawString(text, width - metrics.stringWidth(text) - 10, height - 8)
    }

    private fun positionAt(point: Point): BufferPosition {
        val metrics = getFontMetrics(editorFont)
        val charWidth = max(1, metrics.charWidth('M'))
        val lineHeight = metrics.height.coerceAtLeast(1)
        val gutter = gutterWidth(metrics, charWidth)
        val line = (scrollLine + point.y / lineHeight).coerceIn(0, lines.lastIndex)
        val column = (scrollColumn + ((point.x - gutter).coerceAtLeast(0) / charWidth)).coerceIn(0, lines[line].length)
        return BufferPosition(line, column)
    }

    private fun visibleRowCount(): Int =
        max(1, height / getFontMetrics(editorFont).height.coerceAtLeast(1))

    private fun gutterWidth(metrics: FontMetrics, charWidth: Int): Int =
        (lines.size + 1).toString().length * charWidth + charWidth * 3

    private fun selectedColumns(lineIndex: Int): IntRange? {
        val range = selectionRange() ?: return null
        if (lineIndex !in range.first.line..range.second.line) return null
        val start = if (lineIndex == range.first.line) range.first.column else 0
        val end = if (lineIndex == range.second.line) range.second.column else lines[lineIndex].length
        return start..end
    }

    private fun selectionRange(): Pair<BufferPosition, BufferPosition>? {
        val anchor = selectionAnchor ?: return null
        if (anchor == caret) return null
        return if (anchor < caret) anchor to caret else caret to anchor
    }

    private fun selectionText(): String {
        val range = selectionRange() ?: return ""
        if (range.first.line == range.second.line) {
            return lines[range.first.line].substring(range.first.column, range.second.column)
        }
        val selected = mutableListOf<String>()
        selected += lines[range.first.line].substring(range.first.column)
        for (line in (range.first.line + 1) until range.second.line) selected += lines[line]
        selected += lines[range.second.line].substring(0, range.second.column)
        return selected.joinToString("\n")
    }

    private fun currentPrefix(): String {
        val text = lines[caret.line].take(caret.column)
        return text.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    private fun offsetFor(position: BufferPosition): Int {
        var offset = 0
        for (line in 0 until position.line) offset += lines[line].length + 1
        return offset + position.column
    }

    private fun notifyTextChanged() {
        ensureCaretVisible()
        onTextChanged?.invoke(getText())
        notifyCursor()
        repaint()
    }

    private fun notifyCursor() {
        onCursorChanged?.invoke(EditorCursor(offsetFor(caret), caret.line + 1, caret.column + 1))
    }

    private fun ensureCaretVisible() {
        val visibleRows = visibleRowCount()
        if (caret.line < scrollLine) scrollLine = caret.line
        if (caret.line >= scrollLine + visibleRows) scrollLine = caret.line - visibleRows + 1
        val metrics = getFontMetrics(editorFont)
        val charWidth = max(1, metrics.charWidth('M'))
        val visibleCols = max(1, (width - gutterWidth(metrics, charWidth) - charWidth) / charWidth)
        if (caret.column < scrollColumn) scrollColumn = caret.column
        if (caret.column >= scrollColumn + visibleCols) scrollColumn = caret.column - visibleCols + 1
        scrollLine = scrollLine.coerceIn(0, maxScrollLine())
        scrollColumn = scrollColumn.coerceAtLeast(0)
    }

    private fun maxScrollLine(): Int = (lines.size - visibleRowCount()).coerceAtLeast(0)

    private fun clamp(position: BufferPosition): BufferPosition {
        val line = position.line.coerceIn(0, lines.lastIndex)
        return BufferPosition(line, position.column.coerceIn(0, lines[line].length))
    }

    private fun snapshot(): Snapshot =
        Snapshot(lines.toList(), caret, selectionAnchor, scrollLine, scrollColumn)

    private fun restore(snapshot: Snapshot) {
        lines.clear()
        lines += snapshot.lines
        caret = snapshot.caret
        selectionAnchor = snapshot.anchor
        scrollLine = snapshot.scrollLine
        scrollColumn = snapshot.scrollColumn
        hideCompletions()
    }

    private fun hideCompletions() {
        completionItems = emptyList()
        completionIndex = 0
    }

    private fun updateToolTip() {
        val language = languageId.ifBlank { "plain text" }
        val diagnosticText = diagnostics.joinToString("\n") { "${it.severity}: ${it.message}" }
        toolTipText = if (diagnosticText.isBlank()) language else "$language\n$diagnosticText"
    }

    private fun splitLines(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val result = mutableListOf<String>()
        var start = 0
        while (true) {
            val next = normalized.indexOf('\n', start)
            if (next < 0) {
                result += normalized.substring(start)
                break
            }
            result += normalized.substring(start, next)
            start = next + 1
        }
        return result.ifEmpty { listOf("") }
    }
}
