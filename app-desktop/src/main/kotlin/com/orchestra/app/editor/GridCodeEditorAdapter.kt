package com.orchestra.app.editor

import com.orchestra.completion.CompletionRequest
import com.orchestra.completion.CompletionSuggestion
import com.orchestra.app.fonts.OrchestraFonts
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
        val cursors: List<CaretState>,
        val scrollLine: Int,
        val scrollColumn: Int,
    )

    private data class CaretState(
        var caret: BufferPosition,
        var anchor: BufferPosition? = null,
    )

    private data class TextEdit(
        val stateIndex: Int,
        val startOffset: Int,
        val endOffset: Int,
        val replacement: String,
    )

    private data class EditPlan(
        val stateIndex: Int,
        val startOffset: Int,
        val endOffset: Int,
        val replacement: String,
        val resultingCaretOffset: Int,
    )

    private var editorFont = OrchestraFonts.codeFont(14f)
    private val lines = mutableListOf("")
    private val cursors = mutableListOf(CaretState(BufferPosition(0, 0)))
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
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

    private var caret: BufferPosition
        get() = cursors.first().caret
        set(value) {
            cursors.first().caret = value
        }

    private var selectionAnchor: BufferPosition?
        get() = cursors.first().anchor
        set(value) {
            cursors.first().anchor = value
        }

    override var onTextChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((EditorCursor) -> Unit)? = null
    override var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)? = null
    var onUndoRequested: (() -> Unit)? = null
    var onRedoRequested: (() -> Unit)? = null

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
                if (e.isAltDown) {
                    toggleCursor(pos)
                } else if (e.isShiftDown) {
                    cursorStates().forEach { state ->
                        if (state.anchor == null) state.anchor = state.caret
                        state.caret = pos
                    }
                } else {
                    cursors.clear()
                    cursors += CaretState(pos)
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
                val pos = positionAt(e.point)
                cursorStates().forEach { state ->
                    if (state.anchor == null) state.anchor = state.caret
                    state.caret = pos
                }
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
        cursors.clear()
        cursors += CaretState(BufferPosition(0, 0))
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

    fun commandUndo() = undo()

    fun commandRedo() = redo()

    fun commandCopy(): Boolean = copySelection()

    fun commandCut(): Boolean = cutSelection()

    fun commandPaste() = pasteClipboard()

    fun setEditorFont(font: Font) {
        editorFont = font
        revalidate()
        repaint()
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
                    return
                }
                KeyEvent.VK_C -> {
                    copySelection()
                    e.consume()
                    return
                }
                KeyEvent.VK_X -> {
                    cutSelection()
                    e.consume()
                    return
                }
                KeyEvent.VK_V -> {
                    pasteClipboard()
                    e.consume()
                    return
                }
                KeyEvent.VK_Z -> {
                    if (e.isShiftDown) {
                        onRedoRequested?.invoke() ?: redo()
                    } else {
                        onUndoRequested?.invoke() ?: undo()
                    }
                    e.consume()
                    return
                }
                KeyEvent.VK_Y -> {
                    onRedoRequested?.invoke() ?: redo()
                    e.consume()
                    return
                }
                KeyEvent.VK_SPACE -> {
                    requestCompletions()
                    e.consume()
                    return
                }
            }
        }

        if (e.isAltDown && e.keyCode == KeyEvent.VK_UP) {
            addAdjacentCursors(-1)
            e.consume()
            return
        }
        if (e.isAltDown && e.keyCode == KeyEvent.VK_DOWN) {
            addAdjacentCursors(1)
            e.consume()
            return
        }

        when (e.keyCode) {
            KeyEvent.VK_LEFT -> moveHorizontal(-1, shift, e.isControlDown)
            KeyEvent.VK_RIGHT -> moveHorizontal(1, shift, e.isControlDown)
            KeyEvent.VK_UP -> moveVertical(-1, shift)
            KeyEvent.VK_DOWN -> moveVertical(1, shift)
            KeyEvent.VK_HOME -> moveToLineBoundary(start = true, expand = shift)
            KeyEvent.VK_END -> moveToLineBoundary(start = false, expand = shift)
            KeyEvent.VK_PAGE_UP -> moveVertical(-visibleRowCount(), shift)
            KeyEvent.VK_PAGE_DOWN -> moveVertical(visibleRowCount(), shift)
            KeyEvent.VK_BACK_SPACE -> {
                if (e.isControlDown) edit { deleteWordBackward() } else edit { deleteBackward() }
            }
            KeyEvent.VK_DELETE -> {
                if (e.isControlDown) edit { deleteWordForward() } else edit { deleteForward() }
            }
            KeyEvent.VK_ENTER -> edit { insertTextAtCursors("\n") }
            KeyEvent.VK_TAB -> edit { insertTextAtCursors("    ") }
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
        edit { insertTextAtCursors(char.toString()) }
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
        cursors.clear()
        cursors += CaretState(caret = caret, anchor = start)
        edit { insertTextAtCursors(suggestion.insertText) }
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

    private fun insertTextAtCursors(value: String) {
        applyMultiTextEdit(value) { state ->
            val range = selectionRange(state)
            if (range != null) {
                val start = offsetFor(range.first)
                val end = offsetFor(range.second)
                EditPlan(cursorIndex(state), start, end, value, start + value.length)
            } else {
                val offset = offsetFor(state.caret)
                EditPlan(cursorIndex(state), offset, offset, value, offset + value.length)
            }
        }
    }

    private fun deleteBackward() {
        applyDeletion { state ->
            val range = selectionRange(state)
            if (range != null) {
                EditPlan(cursorIndex(state), offsetFor(range.first), offsetFor(range.second), "", offsetFor(range.first))
            } else if (state.caret.column > 0) {
                val end = offsetFor(state.caret)
                EditPlan(cursorIndex(state), end - 1, end, "", end - 1)
            } else if (state.caret.line > 0) {
                val currentStart = lineStartOffset(state.caret.line)
                EditPlan(cursorIndex(state), currentStart - 1, currentStart, "", currentStart - 1)
            } else {
                null
            }
        }
    }

    private fun deleteForward() {
        applyDeletion { state ->
            val range = selectionRange(state)
            if (range != null) {
                EditPlan(cursorIndex(state), offsetFor(range.first), offsetFor(range.second), "", offsetFor(range.first))
            } else if (state.caret.column < lines[state.caret.line].length) {
                val start = offsetFor(state.caret)
                EditPlan(cursorIndex(state), start, start + 1, "", start)
            } else if (state.caret.line < lines.lastIndex) {
                val start = offsetFor(state.caret)
                EditPlan(cursorIndex(state), start, start + 1, "", start)
            } else {
                null
            }
        }
    }

    private fun deleteWordBackward() {
        applyDeletion { state ->
            val range = selectionRange(state)
            if (range != null) {
                EditPlan(cursorIndex(state), offsetFor(range.first), offsetFor(range.second), "", offsetFor(range.first))
            } else {
                val caretOffset = offsetFor(state.caret)
                val start = wordBoundaryLeft(state.caret)
                if (start == state.caret) null else EditPlan(cursorIndex(state), offsetFor(start), caretOffset, "", offsetFor(start))
            }
        }
    }

    private fun deleteWordForward() {
        applyDeletion { state ->
            val range = selectionRange(state)
            if (range != null) {
                EditPlan(cursorIndex(state), offsetFor(range.first), offsetFor(range.second), "", offsetFor(range.first))
            } else {
                val caretOffset = offsetFor(state.caret)
                val end = wordBoundaryRight(state.caret)
                if (end == state.caret) null else EditPlan(cursorIndex(state), caretOffset, offsetFor(end), "", caretOffset)
            }
        }
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
        edit { deleteSelections() }
        return true
    }

    private fun pasteClipboard() {
        val text = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull() ?: return
        edit { insertTextAtCursors(text) }
    }

    private fun selectAll() {
        cursors.clear()
        cursors += CaretState(caret = BufferPosition(lines.lastIndex, lines.last().length), anchor = BufferPosition(0, 0))
        notifyCursor()
        repaint()
    }

    private fun moveHorizontal(delta: Int, expand: Boolean, word: Boolean) {
        cursorStates().forEach { state ->
            val target = if (word) nextWordPosition(state.caret, delta) else stepHorizontal(state.caret, delta)
            if (expand && state.anchor == null) state.anchor = state.caret
            if (!expand) state.anchor = null
            state.caret = clamp(target)
        }
        ensureCaretVisible()
        notifyCursor()
        repaint()
    }

    private fun moveVertical(delta: Int, expand: Boolean) {
        cursorStates().forEach { state ->
            val line = (state.caret.line + delta).coerceIn(0, lines.lastIndex)
            val column = state.caret.column.coerceAtMost(lines[line].length)
            if (expand && state.anchor == null) state.anchor = state.caret
            if (!expand) state.anchor = null
            state.caret = BufferPosition(line, column)
        }
        ensureCaretVisible()
        notifyCursor()
        repaint()
    }

    private fun moveToLineBoundary(start: Boolean, expand: Boolean) {
        cursorStates().forEach { state ->
            if (expand && state.anchor == null) state.anchor = state.caret
            if (!expand) state.anchor = null
            state.caret = clamp(BufferPosition(state.caret.line, if (start) 0 else lines[state.caret.line].length))
        }
        ensureCaretVisible()
        notifyCursor()
        repaint()
    }

    private fun stepHorizontal(position: BufferPosition, delta: Int): BufferPosition =
        if (delta < 0) {
            when {
                position.column > 0 -> BufferPosition(position.line, position.column - 1)
                position.line > 0 -> BufferPosition(position.line - 1, lines[position.line - 1].length)
                else -> position
            }
        } else {
            when {
                position.column < lines[position.line].length -> BufferPosition(position.line, position.column + 1)
                position.line < lines.lastIndex -> BufferPosition(position.line + 1, 0)
                else -> position
            }
        }

    private fun nextWordPosition(position: BufferPosition, delta: Int): BufferPosition =
        if (delta < 0) wordBoundaryLeft(position) else wordBoundaryRight(position)

    private fun wordBoundaryLeft(position: BufferPosition): BufferPosition {
        var current = position
        if (current.column == 0 && current.line == 0) return current
        if (current.column == 0) current = BufferPosition(current.line - 1, lines[current.line - 1].length)
        while (current.column > 0 && lines[current.line][current.column - 1].isWhitespace()) {
            current = BufferPosition(current.line, current.column - 1)
        }
        while (current.column > 0 && !lines[current.line][current.column - 1].isWhitespace()) {
            current = BufferPosition(current.line, current.column - 1)
        }
        return current
    }

    private fun wordBoundaryRight(position: BufferPosition): BufferPosition {
        var current = position
        if (current.column == lines[current.line].length && current.line == lines.lastIndex) return current
        while (current.column < lines[current.line].length && lines[current.line][current.column].isWhitespace()) {
            current = BufferPosition(current.line, current.column + 1)
        }
        while (current.column < lines[current.line].length && !lines[current.line][current.column].isWhitespace()) {
            current = BufferPosition(current.line, current.column + 1)
        }
        if (current.column == lines[current.line].length && current.line < lines.lastIndex) return BufferPosition(current.line + 1, 0)
        return current
    }

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
        selectionRanges().forEach { range ->
            if (lineIndex !in range.first.line..range.second.line) return@forEach
            val start = if (lineIndex == range.first.line) range.first.column else 0
            val end = if (lineIndex == range.second.line) range.second.column else lines[lineIndex].length
            val visibleStart = start.coerceAtLeast(scrollColumn)
            val visibleEnd = end.coerceAtMost(scrollColumn + visibleCols)
            if (visibleEnd < visibleStart) return@forEach
            g2.color = Color(0x3a5f8a)
            g2.fillRect(
                gutterWidth + (visibleStart - scrollColumn) * charWidth,
                row * lineHeight,
                max(1, (visibleEnd - visibleStart) * charWidth),
                lineHeight,
            )
        }
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
        if (!hasFocus() || !cursorVisible) return
        g2.color = Color(0xf2f2f2)
        cursorStates().forEach { state ->
            val row = state.caret.line - scrollLine
            if (row !in 0 until visibleRows) return@forEach
            val col = state.caret.column - scrollColumn
            if (col < 0) return@forEach
            val x = gutterWidth + col * charWidth
            val y = row * lineHeight + 2
            g2.fillRect(x, y, 2, metrics.height - 4)
        }
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
        val range = selectionRanges().firstOrNull { lineIndex in it.first.line..it.second.line } ?: return null
        val start = if (lineIndex == range.first.line) range.first.column else 0
        val end = if (lineIndex == range.second.line) range.second.column else lines[lineIndex].length
        return start..end
    }

    private fun selectionRange(): Pair<BufferPosition, BufferPosition>? {
        val anchor = selectionAnchor ?: return null
        if (anchor == caret) return null
        return if (anchor < caret) anchor to caret else caret to anchor
    }

    private fun selectionRange(state: CaretState): Pair<BufferPosition, BufferPosition>? {
        val anchor = state.anchor ?: return null
        if (anchor == state.caret) return null
        return if (anchor < state.caret) anchor to state.caret else state.caret to anchor
    }

    private fun selectionRanges(): List<Pair<BufferPosition, BufferPosition>> =
        cursorStates().mapNotNull { selectionRange(it) }

    private fun selectionText(): String {
        val ranges = selectionRanges()
        if (ranges.isEmpty()) return ""
        return ranges.joinToString("\n") { range ->
            if (range.first.line == range.second.line) {
                lines[range.first.line].substring(range.first.column, range.second.column)
            } else {
                val selected = mutableListOf<String>()
                selected += lines[range.first.line].substring(range.first.column)
                for (line in (range.first.line + 1) until range.second.line) selected += lines[line]
                selected += lines[range.second.line].substring(0, range.second.column)
                selected.joinToString("\n")
            }
        }
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
        Snapshot(lines.toList(), cursors.map { CaretState(it.caret, it.anchor) }, scrollLine, scrollColumn)

    private fun restore(snapshot: Snapshot) {
        lines.clear()
        lines += snapshot.lines
        cursors.clear()
        cursors += snapshot.cursors.map { CaretState(it.caret, it.anchor) }
        if (cursors.isEmpty()) cursors += CaretState(BufferPosition(0, 0))
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

    private fun cursorStates(): MutableList<CaretState> = cursors

    private fun cursorIndex(state: CaretState): Int = cursors.indexOfFirst { it === state }.coerceAtLeast(0)

    private fun toggleCursor(position: BufferPosition): Boolean {
        val existingIndex = cursors.indexOfFirst { it.caret == position && it.anchor == null }
        return if (existingIndex >= 0) {
            cursors.removeAt(existingIndex)
            if (cursors.isEmpty()) cursors += CaretState(position)
            true
        } else {
            cursors += CaretState(position)
            false
        }
    }

    private fun addAdjacentCursors(delta: Int) {
        val additions = cursorStates().mapNotNull { state ->
            val targetLine = (state.caret.line + delta).coerceIn(0, lines.lastIndex)
            val targetColumn = state.caret.column.coerceAtMost(lines[targetLine].length)
            val target = BufferPosition(targetLine, targetColumn)
            if (cursors.any { it.caret == target && it.anchor == null }) null else CaretState(target)
        }
        if (additions.isNotEmpty()) {
            cursors += additions
            normalizeCursors()
            ensureCaretVisible()
            notifyCursor()
            repaint()
        }
    }

    private fun deleteSelections(): Boolean {
        val plans = cursorStates().mapIndexedNotNull { index, state ->
            selectionRange(state)?.let { range ->
                EditPlan(index, offsetFor(range.first), offsetFor(range.second), "", offsetFor(range.first))
            }
        }
        if (plans.isEmpty()) return false
        applyPlans(plans)
        return true
    }

    private fun deleteBackwardSelectionAware(): Boolean = deleteSelections()

    private fun applyDeletion(planBuilder: (CaretState) -> EditPlan?) {
        val plans = cursorStates().mapIndexedNotNull { index, state ->
            planBuilder(state)?.copy(stateIndex = index)
        }
        if (plans.isEmpty()) return
        applyPlans(plans)
    }

    private fun applyMultiTextEdit(value: String, planBuilder: (CaretState) -> EditPlan?) {
        val plans = cursorStates().mapIndexedNotNull { index, state ->
            planBuilder(state)?.copy(stateIndex = index)
        }
        if (plans.isEmpty()) return
        applyPlans(plans)
    }

    private fun applyPlans(plans: List<EditPlan>) {
        if (plans.isEmpty()) return
        val originalText = getText()
        val sortedPlans = plans.sortedWith(compareByDescending<EditPlan> { it.startOffset }.thenByDescending { it.stateIndex })
        val builder = StringBuilder(originalText)
        sortedPlans.forEach { plan ->
            builder.replace(plan.startOffset, plan.endOffset, plan.replacement)
        }
        val finalText = builder.toString()
        lines.clear()
        lines += splitLines(finalText)
        if (lines.isEmpty()) lines += ""
        val deltasBefore = sortedPlans.associateWith { plan ->
            sortedPlans.filter { it.startOffset < plan.startOffset }.sumOf { it.replacement.length - (it.endOffset - it.startOffset) }
        }
        val updated = cursors.mapIndexed { index, state ->
            val plan = sortedPlans.firstOrNull { it.stateIndex == index }
            if (plan != null) {
                val finalOffset = plan.resultingCaretOffset + deltasBefore.getValue(plan)
                CaretState(positionFromOffset(finalText, finalOffset))
            } else {
                CaretState(state.caret, state.anchor)
            }
        }
        cursors.clear()
        cursors += updated
        normalizeCursors()
        ensureCaretVisible()
        notifyTextChanged()
    }

    private fun normalizeCursors() {
        val unique = linkedMapOf<Pair<BufferPosition, BufferPosition?>, CaretState>()
        cursors.forEach { state ->
            unique[state.caret to state.anchor] = state
        }
        cursors.clear()
        cursors += unique.values
        if (cursors.isEmpty()) cursors += CaretState(BufferPosition(0, 0))
    }

    private fun positionFromOffset(text: String, offset: Int): BufferPosition {
        val clamped = offset.coerceIn(0, text.length)
        var line = 0
        var column = 0
        for (index in 0 until clamped) {
            if (text[index] == '\n') {
                line++
                column = 0
            } else {
                column++
            }
        }
        return BufferPosition(line, column)
    }

    private fun lineStartOffset(line: Int): Int {
        var offset = 0
        for (index in 0 until line.coerceAtMost(lines.lastIndex)) {
            offset += lines[index].length + 1
        }
        return offset
    }
}
