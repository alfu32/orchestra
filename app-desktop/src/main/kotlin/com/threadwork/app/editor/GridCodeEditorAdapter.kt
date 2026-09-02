package com.threadwork.app.editor

import com.threadwork.completion.CompletionRequest
import com.threadwork.completion.CompletionSuggestion
import com.threadwork.completion.DeclarationSymbol
import com.threadwork.app.fonts.ThreadworkFonts
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.TechnologyMetadata
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
        val scrollVisualRow: Int,
    )

    /** A rendered portion of one logical buffer line. */
    private data class VisualRow(
        val lineIndex: Int,
        val startColumn: Int,
        val endColumn: Int,
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

    private var editorFont = ThreadworkFonts.codeFont(14f)
    private val lines = mutableListOf("")
    private val cursors = mutableListOf(CaretState(BufferPosition(0, 0)))
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var scrollVisualRow = 0
    private var readOnly = false
    private var languageId = ""
    private var technology = TechnologyMetadata()
    private var completionContext = EditorCompletionContext(null, NodeTextSection.Declaration)
    private var diagnostics: List<Diagnostic> = emptyList()
    private var declarationSymbols: List<DeclarationSymbol> = emptyList()
    private val declarationSymbolsTimer = Timer(320) { refreshDeclarationSymbolsNow() }.apply {
        isRepeats = false
    }
    private var semanticIdentifierColors: Map<String, Color> = emptyMap()
    private var pinnedHeader: String = ""
    private var completionItems: List<CompletionSuggestion> = emptyList()
    private var completionIndex = 0
    private var completionScrollOffset = 0
    private var hoverPoint: Point? = null
    private var hoverPosition: BufferPosition? = null
    private var hoverInfo: EditorHoverInfo? = null
    private val typeHoverTimer = Timer(1700) { resolveHoverInfo() }.apply { isRepeats = false }
    private var cursorVisible = true
    private val menuMask = runCatching { Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx }
        .getOrDefault(InputEvent.CTRL_DOWN_MASK)

    private var caret: BufferPosition
        get() = primaryCursor().caret
        set(value) {
            primaryCursor().caret = value
        }

    private var selectionAnchor: BufferPosition?
        get() = primaryCursor().anchor
        set(value) {
            primaryCursor().anchor = value
        }

    override var onTextChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((EditorCursor) -> Unit)? = null
    override var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)? = null
    override var onDeclarationSymbolsRequested: ((CompletionRequest) -> List<DeclarationSymbol>)? = null
        set(value) {
            field = value
            refreshDeclarationSymbols()
        }
    override var onHoverInfoRequested: ((EditorHoverRequest) -> EditorHoverInfo?)? = null
    var onUndoRequested: (() -> Unit)? = null
    var onRedoRequested: (() -> Unit)? = null

    init {
        isFocusable = true
        focusTraversalKeysEnabled = false
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
                clearHover()
                completionIndexAt(e.point)?.let { index ->
                    completionIndex = index
                    applyCompletion(completionItems[index])
                    e.consume()
                    return
                }
                val pos = positionAt(e.point)
                if (e.isAltDown) {
                    toggleCursor(pos)
                } else if (e.clickCount >= 2) {
                    selectWordAt(pos)
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

            override fun mouseMoved(e: MouseEvent) {
                if (completionItems.isNotEmpty()) return
                updateDiagnosticTooltip(e.point)
                val position = positionAt(e.point)
                if (position == hoverPosition && hoverPoint == e.point) return
                hoverPosition = position
                hoverPoint = e.point
                hoverInfo = null
                typeHoverTimer.restart()
                repaint()
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                clearHover()
                updateToolTip()
            }
        })
        addMouseWheelListener { e: MouseWheelEvent ->
            if (completionItems.isNotEmpty()) {
                scrollCompletions((e.preciseWheelRotation * 3).toInt())
                e.consume()
                return@addMouseWheelListener
            }
            scrollVisualRow = (scrollVisualRow + (e.preciseWheelRotation * 3).toInt())
                .coerceIn(0, maxScrollVisualRow())
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
        scrollVisualRow = 0
        undoStack.clear()
        redoStack.clear()
        hideCompletions()
        clearHover()
        refreshDeclarationSymbols()
        notifyCursor()
        repaint()
    }

    override fun getText(): String = lines.joinToString("\n")

    override fun setLanguage(languageId: String) {
        this.languageId = RegexSyntaxHighlighter.normalizeLanguage(languageId)
        refreshDeclarationSymbols()
        updateToolTip()
        clearHover()
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
        refreshDeclarationSymbols()
        clearHover()
    }

    override fun setSemanticIdentifierColors(colors: Map<String, Color>) {
        semanticIdentifierColors = colors.filterKeys(String::isNotBlank)
        repaint()
    }

    override fun setPinnedHeader(text: String?) {
        val next = text.orEmpty().lineSequence().joinToString(" ") { it.trim() }.trim()
        if (pinnedHeader == next) return
        pinnedHeader = next
        revalidate()
        repaint()
    }

    override fun focus() {
        requestFocusInWindow()
    }

    /** Bring a diagnostic line into view without changing the caret or selection. */
    fun revealDiagnostic(line: Int?) {
        val targetLine = line?.minus(1) ?: return
        if (targetLine !in lines.indices) return
        val metrics = getFontMetrics(editorFont)
        val charWidth = max(1, metrics.charWidth('M'))
        val rows = visualRows(metrics, charWidth, gutterWidth(metrics, charWidth))
        val visualIndex = rows.indexOfFirst { it.lineIndex == targetLine }
        if (visualIndex < 0) return
        scrollVisualRow = visualIndex.coerceIn(0, maxScrollVisualRow(rows))
        repaint()
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
        val bodyTop = pinnedHeaderHeight(metrics)
        val visibleRows = max(1, (height - bodyTop) / lineHeight)
        val visualRows = visualRows(metrics, charWidth, gutterWidth)
        scrollVisualRow = scrollVisualRow.coerceIn(0, maxScrollVisualRow(visualRows))

        g2.color = Color(0x1e1e1e)
        g2.fillRect(0, 0, width, height)
        if (pinnedHeader.isNotBlank()) {
            drawPinnedHeader(g2, metrics, lineHeight, charWidth, gutterWidth)
        }
        val bodyGraphics = g2.create(0, bodyTop, width, (height - bodyTop).coerceAtLeast(1)) as Graphics2D
        drawGutter(bodyGraphics, metrics, lineHeight, charWidth, gutterWidth, visibleRows, visualRows)
        for (row in 0 until visibleRows) {
            val visualRow = visualRows.getOrNull(scrollVisualRow + row) ?: break
            val baseline = row * lineHeight + metrics.ascent
            drawLineBackground(bodyGraphics, visualRow, row, lineHeight, charWidth, gutterWidth, visualRows)
            drawSelection(bodyGraphics, visualRow, row, lineHeight, charWidth, gutterWidth)
            drawHighlightedLine(bodyGraphics, lines[visualRow.lineIndex], visualRow, gutterWidth, baseline, charWidth)
            drawDiagnostics(bodyGraphics, visualRow, row, lineHeight, gutterWidth, charWidth)
        }
        drawCaret(bodyGraphics, metrics, lineHeight, charWidth, gutterWidth, visibleRows, visualRows)
        drawCompletionPopup(bodyGraphics, metrics, lineHeight, charWidth, gutterWidth, visibleRows, visualRows)
        drawHoverPopup(bodyGraphics, metrics, lineHeight)
        bodyGraphics.dispose()
        drawEditorStatus(g2, metrics)
    }

    private fun drawPinnedHeader(
        g2: Graphics2D,
        metrics: FontMetrics,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
    ) {
        g2.color = Color(0x252526)
        g2.fillRect(0, 0, gutterWidth, lineHeight)
        g2.color = Color(0x3c3c3c)
        g2.drawLine(gutterWidth - 1, 0, gutterWidth - 1, lineHeight)
        drawHighlightedText(g2, pinnedHeader, gutterWidth, metrics.ascent)
        if (scrollVisualRow > 0) {
            g2.color = Color(0x5a5a5a)
            g2.drawLine(0, lineHeight - 1, width, lineHeight - 1)
        }
    }

    private fun drawHighlightedText(g2: Graphics2D, text: String, x: Int, baseline: Int) {
        val tokens = RegexSyntaxHighlighter.highlightLine(
            languageId,
            text,
            declarationSymbols,
            semanticIdentifierColors,
        )
        var cursor = 0
        var drawX = x
        fun draw(segment: String, color: Color) {
            if (segment.isEmpty()) return
            g2.color = color
            g2.drawString(segment, drawX, baseline)
            drawX += g2.fontMetrics.stringWidth(segment)
        }
        tokens.forEach { token ->
            if (cursor < token.start) draw(text.substring(cursor, token.start), RegexSyntaxHighlighter.Default)
            draw(text.substring(token.start, token.endExclusive), token.color)
            cursor = token.endExclusive
        }
        if (cursor < text.length) draw(text.substring(cursor), RegexSyntaxHighlighter.Default)
    }

    private fun handleKeyPressed(e: KeyEvent) {
        cursorVisible = true
        clearHover()
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
            KeyEvent.VK_TAB -> {
                if (selectionRanges().isNotEmpty()) {
                    edit {
                        if (shift) dedentSelectedLines() else indentSelectedLines()
                    }
                } else if (shift) {
                    edit { dedentCurrentLine() }
                } else {
                    edit { insertTextAtCursors(" ".repeat(ThreadworkEditorSettings.indentSpaces)) }
                }
            }
            KeyEvent.VK_BACK_SPACE -> {
                if (e.isControlDown) edit { deleteWordBackward() } else edit { deleteBackward() }
            }
            KeyEvent.VK_DELETE -> {
                if (e.isControlDown) edit { deleteWordForward() } else edit { deleteForward() }
            }
            KeyEvent.VK_ENTER -> edit { insertTextAtCursors("\n") }
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
        clearHover()
        edit { insertTextAtCursors(char.toString()) }
        e.consume()
    }

    private fun handleCompletionKey(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.VK_ESCAPE -> hideCompletions()
            KeyEvent.VK_UP -> {
                completionIndex = (completionIndex - 1).coerceAtLeast(0)
                ensureCompletionVisible()
            }
            KeyEvent.VK_DOWN -> {
                completionIndex = (completionIndex + 1).coerceAtMost(completionItems.lastIndex)
                ensureCompletionVisible()
            }
            KeyEvent.VK_PAGE_UP -> {
                completionIndex = (completionIndex - visibleCompletionRows()).coerceAtLeast(0)
                ensureCompletionVisible()
            }
            KeyEvent.VK_PAGE_DOWN -> {
                completionIndex = (completionIndex + visibleCompletionRows()).coerceAtMost(completionItems.lastIndex)
                ensureCompletionVisible()
            }
            KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> applyCompletion(completionItems[completionIndex])
            else -> return false
        }
        repaint()
        e.consume()
        return true
    }

    private fun requestCompletions() {
        val request = currentCompletionRequest() ?: return
        completionItems = onCompletionRequested?.invoke(request).orEmpty()
        completionIndex = 0
        completionScrollOffset = 0
        repaint()
    }

    private fun refreshDeclarationSymbols() {
        declarationSymbolsTimer.stop()
        refreshDeclarationSymbolsNow()
    }

    private fun scheduleDeclarationSymbolsRefresh() {
        declarationSymbolsTimer.restart()
    }

    private fun refreshDeclarationSymbolsNow() {
        declarationSymbols = currentCompletionRequest()
            ?.let { request -> onDeclarationSymbolsRequested?.invoke(request) }
            .orEmpty()
        repaint()
    }

    private fun currentCompletionRequest(): CompletionRequest? {
        val nodeId = completionContext.nodeId?.let(::NodeId) ?: return null
        val currentCaret = caret
        val line = lines.getOrElse(currentCaret.line) { "" }
        return CompletionRequest(
            nodeId = nodeId,
            textSection = completionContext.textSection,
            languageId = languageId,
            technologyId = technology.technologyId,
            cursorOffset = offsetFor(currentCaret),
            fullText = getText(),
            currentLine = line,
            prefix = currentPrefix(),
        )
    }

    private fun applyCompletion(suggestion: CompletionSuggestion) {
        val currentCaret = caret
        val prefix = currentPrefix()
        val start = BufferPosition(currentCaret.line, (currentCaret.column - prefix.length).coerceAtLeast(0))
        cursors.clear()
        cursors += CaretState(caret = currentCaret, anchor = start)
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
        scheduleDeclarationSymbolsRefresh()
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
        visualRows: List<VisualRow>,
    ) {
        g2.color = Color(0x252526)
        g2.fillRect(0, 0, gutterWidth, bodyHeight())
        g2.color = Color(0x858585)
        for (row in 0 until visibleRows) {
            val visualRow = visualRows.getOrNull(scrollVisualRow + row) ?: break
            if (visualRow.startColumn != 0) continue
            diagnostics.firstOrNull { it.line == visualRow.lineIndex + 1 }?.let { diagnostic ->
                g2.color = when (diagnostic.severity) {
                    DiagnosticSeverity.Error -> Color(0xff5555)
                    DiagnosticSeverity.Warning -> Color(0xd7ba7d)
                    DiagnosticSeverity.Info -> Color(0x75beff)
                }
                val markerSize = (lineHeight / 2).coerceAtLeast(6)
                g2.fillOval(4, row * lineHeight + (lineHeight - markerSize) / 2, markerSize, markerSize)
                g2.color = Color(0x858585)
            }
            val label = (visualRow.lineIndex + 1).toString().padStart((lines.size + 1).toString().length)
            g2.drawString(label, charWidth, row * lineHeight + metrics.ascent)
        }
        g2.color = Color(0x3c3c3c)
        g2.drawLine(gutterWidth - 1, 0, gutterWidth - 1, bodyHeight())
    }

    private fun drawLineBackground(
        g2: Graphics2D,
        visualRow: VisualRow,
        row: Int,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visualRows: List<VisualRow>,
    ) {
        diagnostics.firstOrNull { it.line == visualRow.lineIndex + 1 }?.let { diagnostic ->
            val startX = if (diagnostic.column == null) {
                gutterWidth
            } else {
                val diagnosticColumn = (diagnostic.column ?: return@let).coerceAtLeast(1) - 1
                if (diagnosticColumn >= visualRow.endColumn) return@let
                val startColumn = max(diagnosticColumn, visualRow.startColumn)
                gutterWidth + (startColumn - visualRow.startColumn) * charWidth
            }
            if (startX < width) {
                g2.color = when (diagnostic.severity) {
                    DiagnosticSeverity.Error -> Color(0x24ff5555, true)
                    DiagnosticSeverity.Warning -> Color(0x24d7ba7d, true)
                    DiagnosticSeverity.Info -> Color(0x2475beff, true)
                }
                g2.fillRect(startX, row * lineHeight, width - startX, lineHeight)
            }
        }
        if (visualRows.indexOfCaret(caret) == scrollVisualRow + row) {
            g2.color = Color(0x50282828, true)
            g2.fillRect(gutterWidth, row * lineHeight, width - gutterWidth, lineHeight)
        }
    }

    private fun drawHighlightedLine(
        g2: Graphics2D,
        line: String,
        visualRow: VisualRow,
        gutterWidth: Int,
        baseline: Int,
        charWidth: Int,
    ) {
        val start = visualRow.startColumn.coerceAtMost(line.length)
        val end = visualRow.endColumn.coerceAtMost(line.length)
        if (start >= end) return
        val tokens = RegexSyntaxHighlighter.highlightLine(
            languageId,
            line,
            declarationSymbols,
            semanticIdentifierColors,
        )
        var cursor = start

        fun drawSegment(segmentStart: Int, segmentEnd: Int, color: Color) {
            if (segmentEnd <= segmentStart) return
            g2.color = color
            val x = gutterWidth + (segmentStart - start) * charWidth
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
        if (diagnostics.any { it.line == visualRow.lineIndex + 1 }) {
            g2.color = Color(0xff6b68)
            g2.fillRect(gutterWidth, baseline + 3, max(1, (end - start) * charWidth), 2)
        }
    }

    private fun drawSelection(
        g2: Graphics2D,
        visualRow: VisualRow,
        row: Int,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
    ) {
        selectionRanges().forEach { range ->
            val lineIndex = visualRow.lineIndex
            if (lineIndex !in range.first.line..range.second.line) return@forEach
            val start = if (lineIndex == range.first.line) range.first.column else 0
            val end = if (lineIndex == range.second.line) range.second.column else lines[lineIndex].length
            val visibleStart = start.coerceAtLeast(visualRow.startColumn)
            val visibleEnd = end.coerceAtMost(visualRow.endColumn)
            if (visibleEnd <= visibleStart) return@forEach
            g2.color = Color(0x3a5f8a)
            g2.fillRect(
                gutterWidth + (visibleStart - visualRow.startColumn) * charWidth,
                row * lineHeight,
                max(1, (visibleEnd - visibleStart) * charWidth),
                lineHeight,
            )
        }
    }

    private fun drawDiagnostics(
        g2: Graphics2D,
        visualRow: VisualRow,
        row: Int,
        lineHeight: Int,
        gutterWidth: Int,
        charWidth: Int,
    ) {
        val lineIndex = visualRow.lineIndex
        val diagnostic = diagnostics.firstOrNull { it.line == lineIndex + 1 } ?: return
        g2.color = when (diagnostic.severity) {
            DiagnosticSeverity.Error -> Color(0xff5555)
            DiagnosticSeverity.Warning -> Color(0xd7ba7d)
            DiagnosticSeverity.Info -> Color(0x75beff)
        }
        val column = (diagnostic.column ?: 1).coerceAtLeast(1) - 1
        if (column in visualRow.startColumn until visualRow.endColumn) {
            val x = gutterWidth + (column - visualRow.startColumn) * charWidth
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
        visualRows: List<VisualRow>,
    ) {
        if (!hasFocus() || !cursorVisible) return
        g2.color = Color(0xf2f2f2)
        cursorStates().forEach { state ->
            val visualIndex = visualRows.indexOfCaret(state.caret)
            val row = visualIndex - scrollVisualRow
            if (row !in 0 until visibleRows) return@forEach
            val segment = visualRows[visualIndex]
            val col = state.caret.column - segment.startColumn
            val x = gutterWidth + col * charWidth
            val y = row * lineHeight + 2
            g2.fillRect(x, y, 2, metrics.height - 4)
        }
    }

    private fun resolveHoverInfo() {
        val point = hoverPoint ?: return
        val position = hoverPosition ?: return
        val nodeId = completionContext.nodeId ?: return
        if (position != positionAt(point)) return
        val symbol = symbolAt(position)
        if (symbol.isBlank()) return
        hoverInfo = onHoverInfoRequested?.invoke(
            EditorHoverRequest(
                nodeId = nodeId,
                textSection = completionContext.textSection,
                languageId = languageId,
                technologyId = technology.technologyId,
                symbol = symbol,
            ),
        )
        repaint()
    }

    private fun clearHover() {
        typeHoverTimer.stop()
        if (hoverPoint == null && hoverPosition == null && hoverInfo == null) return
        hoverPoint = null
        hoverPosition = null
        hoverInfo = null
        repaint()
    }

    private fun symbolAt(position: BufferPosition): String {
        val line = lines.getOrNull(position.line).orEmpty()
        if (line.isEmpty()) return ""
        val probe = when {
            position.column in line.indices && line[position.column].isWordChar() -> position.column
            position.column > 0 && line[position.column - 1].isWordChar() -> position.column - 1
            else -> return ""
        }
        var start = probe
        var end = probe + 1
        while (start > 0 && line[start - 1].isWordChar()) start--
        while (end < line.length && line[end].isWordChar()) end++
        return line.substring(start, end)
    }

    private fun drawHoverPopup(g2: Graphics2D, metrics: FontMetrics, lineHeight: Int) {
        val info = hoverInfo ?: return
        val point = hoverPoint ?: return
        val maxContentWidth = max(220, min(width - 24, 620))
        val maxChars = max(24, maxContentWidth / max(1, metrics.charWidth('M')))
        val bodyLines = info.body.lines().flatMap { line -> wrapForHover(line, maxChars) }.ifEmpty { listOf("") }
        val allLines = listOf(info.title) + bodyLines
        val popupWidth = allLines.maxOf { metrics.stringWidth(it) }
            .plus(20)
            .coerceIn(220, maxContentWidth)
        val popupHeight = (bodyLines.size + 1) * lineHeight + 14
        val x = point.x.coerceIn(4, max(4, width - popupWidth - 4))
        val bodyPointY = (point.y - pinnedHeaderHeight(metrics)).coerceAtLeast(0)
        val y = (bodyPointY + lineHeight).coerceIn(4, max(4, bodyHeight() - popupHeight - 4))
        g2.color = Color(0x252526)
        g2.fillRoundRect(x, y, popupWidth, popupHeight, 6, 6)
        g2.color = Color(0x5f5f5f)
        g2.drawRoundRect(x, y, popupWidth, popupHeight, 6, 6)
        g2.color = Color(0x9cdcfe)
        g2.drawString(info.title, x + 10, y + metrics.ascent + 5)
        g2.color = Color(0xd4d4d4)
        bodyLines.forEachIndexed { index, line ->
            g2.drawString(line, x + 10, y + (index + 2) * lineHeight + 5)
        }
    }

    private fun wrapForHover(value: String, maxChars: Int): List<String> {
        if (value.length <= maxChars) return listOf(value)
        val words = value.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val next = if (current.isBlank()) word else "$current $word"
            if (next.length > maxChars && current.isNotBlank()) {
                lines += current
                current = word
            } else {
                current = next
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun drawCompletionPopup(
        g2: Graphics2D,
        metrics: FontMetrics,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visibleRows: Int,
        visualRows: List<VisualRow>,
    ) {
        if (completionItems.isEmpty()) return
        val geometry = completionPopupGeometry(metrics, lineHeight, charWidth, gutterWidth, visibleRows, visualRows) ?: return
        val x = geometry.x
        val y = geometry.y
        val popupWidth = geometry.width
        val popupHeight = geometry.height
        val labelColumnWidth = geometry.labelColumnWidth
        val detailX = x + 14 + labelColumnWidth + 16
        val visibleItems = completionItems
            .drop(completionScrollOffset)
            .take(geometry.visibleItemCount)
        g2.color = Color(0x252526)
        g2.fillRect(x, y, popupWidth, popupHeight)
        g2.color = Color(0x5f5f5f)
        g2.drawRect(x, y, popupWidth, popupHeight)
        visibleItems.forEachIndexed { visibleIndex, item ->
            val index = completionScrollOffset + visibleIndex
            val rowY = y + 3 + visibleIndex * lineHeight
            if (index == completionIndex) {
                g2.color = Color(0x094771)
                g2.fillRect(x + 1, rowY, popupWidth - 2, lineHeight)
            }
            g2.color = semanticColorFor(item.label) ?: Color(0xd4d4d4)
            g2.drawString(item.label.take(72), x + 8, rowY + metrics.ascent)
            if (item.detail.isNotBlank()) {
                g2.color = Color(0x9cdcfe)
                g2.drawString(item.detail.take(84), detailX, rowY + metrics.ascent)
            }
        }
        drawCompletionScrollbar(g2, geometry)
    }

    private fun semanticColorFor(label: String): Color? =
        semanticIdentifierColors.entries.firstOrNull { (name, _) ->
            label == name || label.startsWith("$name.") || label.startsWith("$name(")
        }?.value

    private fun drawCompletionScrollbar(g2: Graphics2D, geometry: CompletionPopupGeometry) {
        if (completionItems.size <= geometry.visibleItemCount) return
        val trackX = geometry.x + geometry.width - 7
        val trackY = geometry.y + 3
        val trackHeight = geometry.visibleItemCount * geometry.lineHeight
        val thumbHeight = max(12, trackHeight * geometry.visibleItemCount / completionItems.size)
        val maxOffset = maxCompletionScrollOffset()
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(1)
        val thumbY = trackY + if (maxOffset == 0) 0 else thumbTravel * completionScrollOffset / maxOffset
        g2.color = Color(0x3c3c3c)
        g2.fillRect(trackX, trackY, 4, trackHeight)
        g2.color = Color(0x858585)
        g2.fillRect(trackX, thumbY, 4, thumbHeight)
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
        val visualRows = visualRows(metrics, charWidth, gutter)
        val bodyY = (point.y - pinnedHeaderHeight(metrics)).coerceAtLeast(0)
        val visualIndex = (scrollVisualRow + bodyY / lineHeight).coerceIn(0, visualRows.lastIndex)
        val visualRow = visualRows[visualIndex]
        val column = (visualRow.startColumn + ((point.x - gutter).coerceAtLeast(0) / charWidth))
            .coerceIn(visualRow.startColumn, visualRow.endColumn)
        return BufferPosition(visualRow.lineIndex, column)
    }

    private fun visibleRowCount(): Int =
        max(
            1,
            (height - pinnedHeaderHeight(getFontMetrics(editorFont))) /
                getFontMetrics(editorFont).height.coerceAtLeast(1),
        )

    private fun pinnedHeaderHeight(metrics: FontMetrics): Int =
        if (pinnedHeader.isBlank()) 0 else metrics.height

    private fun bodyHeight(): Int =
        (height - pinnedHeaderHeight(getFontMetrics(editorFont))).coerceAtLeast(1)

    private fun gutterWidth(metrics: FontMetrics, charWidth: Int): Int =
        (lines.size + 1).toString().length * charWidth + charWidth * 3

    private fun visualRows(metrics: FontMetrics, charWidth: Int, gutterWidth: Int): List<VisualRow> {
        val wrapColumns = max(1, (width - gutterWidth - charWidth) / charWidth)
        return buildList {
            lines.forEachIndexed { lineIndex, line ->
                if (line.isEmpty()) {
                    add(VisualRow(lineIndex, 0, 0))
                } else {
                    var start = 0
                    while (start < line.length) {
                        val end = min(line.length, start + wrapColumns)
                        add(VisualRow(lineIndex, start, end))
                        start = end
                    }
                }
            }
        }
    }

    private fun List<VisualRow>.indexOfCaret(position: BufferPosition): Int {
        val lastForLine = indexOfLast { it.lineIndex == position.line }
        return indexOfFirst { row ->
            row.lineIndex == position.line && position.column in row.startColumn until row.endColumn
        }.takeIf { it >= 0 }
            ?: lastForLine.takeIf { it >= 0 }
            ?: 0
    }

    private fun maxScrollVisualRow(rows: List<VisualRow> = visualRowsForCurrentViewport()): Int =
        (rows.size - visibleRowCount()).coerceAtLeast(0)

    private fun visualRowsForCurrentViewport(): List<VisualRow> {
        val metrics = getFontMetrics(editorFont)
        val charWidth = max(1, metrics.charWidth('M'))
        return visualRows(metrics, charWidth, gutterWidth(metrics, charWidth))
    }

    private fun selectedColumns(lineIndex: Int): IntRange? {
        val range = selectionRanges().firstOrNull { lineIndex in it.first.line..it.second.line } ?: return null
        val start = if (lineIndex == range.first.line) range.first.column else 0
        val end = if (lineIndex == range.second.line) range.second.column else lines[lineIndex].length
        return start..end
    }

    private fun primaryCursor(): CaretState {
        if (cursors.isEmpty()) cursors += CaretState(BufferPosition(0, 0))
        return cursors.first()
    }

    private fun selectWordAt(position: BufferPosition) {
        val line = lines[position.line]
        if (line.isEmpty()) {
            cursors.clear()
            cursors += CaretState(position)
            return
        }
        val index = position.column.coerceIn(0, line.length - 1)
        val probe = if (!line[index].isWordChar() && index > 0 && line[index - 1].isWordChar()) index - 1 else index
        if (!line[probe].isWordChar()) {
            cursors.clear()
            cursors += CaretState(position)
            return
        }
        var start = probe
        var end = probe + 1
        while (start > 0 && line[start - 1].isWordChar()) start--
        while (end < line.length && line[end].isWordChar()) end++
        cursors.clear()
        cursors += CaretState(BufferPosition(position.line, end), BufferPosition(position.line, start))
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
        return text.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == '$' }
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
        val metrics = getFontMetrics(editorFont)
        val charWidth = max(1, metrics.charWidth('M'))
        val visualRows = visualRows(metrics, charWidth, gutterWidth(metrics, charWidth))
        val caretVisualRow = visualRows.indexOfCaret(caret)
        if (caretVisualRow < scrollVisualRow) scrollVisualRow = caretVisualRow
        if (caretVisualRow >= scrollVisualRow + visibleRows) scrollVisualRow = caretVisualRow - visibleRows + 1
        scrollVisualRow = scrollVisualRow.coerceIn(0, maxScrollVisualRow(visualRows))
    }

    private fun clamp(position: BufferPosition): BufferPosition {
        val line = position.line.coerceIn(0, lines.lastIndex)
        return BufferPosition(line, position.column.coerceIn(0, lines[line].length))
    }

    private fun snapshot(): Snapshot =
        Snapshot(lines.toList(), cursors.map { CaretState(it.caret, it.anchor) }, scrollVisualRow)

    private fun restore(snapshot: Snapshot) {
        lines.clear()
        lines += snapshot.lines
        cursors.clear()
        cursors += snapshot.cursors.map { CaretState(it.caret, it.anchor) }
        if (cursors.isEmpty()) cursors += CaretState(BufferPosition(0, 0))
        scrollVisualRow = snapshot.scrollVisualRow
        hideCompletions()
    }

    private fun hideCompletions() {
        completionItems = emptyList()
        completionIndex = 0
        completionScrollOffset = 0
    }

    private fun updateToolTip() {
        val language = languageId.ifBlank { "plain text" }
        val diagnosticText = diagnostics.joinToString("\n") { "${it.severity}: ${it.message}" }
        toolTipText = if (diagnosticText.isBlank()) language else "$language\n$diagnosticText"
    }

    private fun updateDiagnosticTooltip(point: Point) {
        val metrics = getFontMetrics(editorFont)
        val lineHeight = metrics.height
        val bodyY = point.y - pinnedHeaderHeight(metrics)
        if (bodyY < 0) {
            updateToolTip()
            return
        }
        val charWidth = max(1, metrics.charWidth('M'))
        val gutterWidth = gutterWidth(metrics, charWidth)
        if (point.x >= gutterWidth) {
            updateToolTip()
            return
        }
        val rows = visualRows(metrics, charWidth, gutterWidth)
        val row = bodyY / lineHeight
        val visualRow = rows.getOrNull(scrollVisualRow + row)
        val rowDiagnostics = visualRow?.let { visibleRow ->
            if (visibleRow.startColumn == 0) {
                diagnostics.filter { it.line == visibleRow.lineIndex + 1 }
            } else {
                emptyList()
            }
        }.orEmpty()
        if (rowDiagnostics.isEmpty()) {
            updateToolTip()
        } else {
            toolTipText = rowDiagnostics.joinToString("\n") { "${it.severity}: ${it.message}" }
        }
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

    private fun indentSelectedLines() {
        val selectedLines = selectionRanges()
            .flatMap { range -> (range.first.line..range.second.line).asSequence() }
            .toSet()
        if (selectedLines.isEmpty()) return
        val indent = " ".repeat(ThreadworkEditorSettings.indentSpaces)
        selectedLines.forEach { lineIndex ->
            lines[lineIndex] = indent + lines[lineIndex]
        }
        cursors.replaceAll { state ->
            CaretState(
                caret = if (state.caret.line in selectedLines) {
                    state.caret.copy(column = state.caret.column + indent.length)
                } else {
                    state.caret
                },
                anchor = state.anchor?.let { anchor ->
                    if (anchor.line in selectedLines) anchor.copy(column = anchor.column + indent.length) else anchor
                },
            )
        }
        normalizeCursors()
        ensureCaretVisible()
    }

    private fun dedentSelectedLines() {
        val selectedLines = selectionRanges()
            .flatMap { range -> (range.first.line..range.second.line).asSequence() }
            .toSet()
        if (selectedLines.isEmpty()) return
        val removedByLine = mutableMapOf<Int, Int>()
        selectedLines.forEach { lineIndex ->
            val line = lines[lineIndex]
            val removed = when {
                line.startsWith("\t") -> 1
                else -> line.takeWhile { it == ' ' }.take(ThreadworkEditorSettings.indentSpaces).length
            }
            removedByLine[lineIndex] = removed
            if (removed > 0) {
                lines[lineIndex] = line.drop(removed)
            }
        }
        cursors.replaceAll { state ->
            CaretState(
                caret = if (state.caret.line in selectedLines) {
                    val removed = removedByLine[state.caret.line] ?: 0
                    state.caret.copy(column = (state.caret.column - removed).coerceAtLeast(0))
                } else {
                    state.caret
                },
                anchor = state.anchor?.let { anchor ->
                    if (anchor.line in selectedLines) {
                        val removed = removedByLine[anchor.line] ?: 0
                        anchor.copy(column = (anchor.column - removed).coerceAtLeast(0))
                    } else {
                        anchor
                    }
                },
            )
        }
        normalizeCursors()
        ensureCaretVisible()
    }

    private fun dedentCurrentLine() {
        val lineIndex = caret.line
        val line = lines[lineIndex]
        val removed = when {
            line.startsWith("\t") -> 1
            else -> line.takeWhile { it == ' ' }.take(ThreadworkEditorSettings.indentSpaces).length
        }
        if (removed <= 0) return
        lines[lineIndex] = line.drop(removed)
        cursors.replaceAll { state ->
            if (state.caret.line == lineIndex) {
                CaretState(
                    caret = state.caret.copy(column = (state.caret.column - removed).coerceAtLeast(0)),
                    anchor = state.anchor?.let { anchor ->
                        if (anchor.line == lineIndex) anchor.copy(column = (anchor.column - removed).coerceAtLeast(0)) else anchor
                    },
                )
            } else {
                state
            }
        }
        normalizeCursors()
        ensureCaretVisible()
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

    private data class CompletionPopupGeometry(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val labelColumnWidth: Int,
        val lineHeight: Int,
        val visibleItemCount: Int,
    )

    private fun completionPopupGeometry(
        metrics: FontMetrics,
        lineHeight: Int,
        charWidth: Int,
        gutterWidth: Int,
        visibleRows: Int,
        visualRows: List<VisualRow> = visualRowsForCurrentViewport(),
    ): CompletionPopupGeometry? {
        if (completionItems.isEmpty()) return null
        val caretVisualRow = visualRows.indexOfCaret(caret)
        val segment = visualRows[caretVisualRow]
        val row = (caretVisualRow - scrollVisualRow + 1).coerceIn(0, visibleRows - 1)
        val col = (caret.column - segment.startColumn).coerceAtLeast(0)
        val labelColumnWidth = completionItems.maxOf { metrics.stringWidth(it.label.take(72)) }.coerceIn(150, 420)
        val detailColumnWidth = completionItems.maxOf { metrics.stringWidth(it.detail.take(84)) }.coerceIn(120, 440)
        val desiredWidth = labelColumnWidth + detailColumnWidth + 36
        val popupWidth = min(max(320, desiredWidth), max(320, width - gutterWidth - 16))
        val visibleItemCount = visibleCompletionRows(lineHeight)
        val popupHeight = visibleItemCount * lineHeight + 6
        val x = (gutterWidth + col * charWidth).coerceIn(0, max(0, width - popupWidth - 4))
        val y = (row * lineHeight).coerceIn(0, max(0, bodyHeight() - popupHeight - 4))
        return CompletionPopupGeometry(x, y, popupWidth, popupHeight, labelColumnWidth, lineHeight, visibleItemCount)
    }

    private fun completionIndexAt(point: Point): Int? {
        if (completionItems.isEmpty()) return null
        val metrics = getFontMetrics(editorFont)
        val charWidth = max(1, metrics.charWidth('M'))
        val lineHeight = metrics.height.coerceAtLeast(1)
        val gutter = gutterWidth(metrics, charWidth)
        val geometry = completionPopupGeometry(metrics, lineHeight, charWidth, gutter, visibleRowCount()) ?: return null
        val bodyPoint = Point(point.x, point.y - pinnedHeaderHeight(metrics))
        if (bodyPoint.x !in geometry.x..(geometry.x + geometry.width)) return null
        if (bodyPoint.y !in geometry.y..(geometry.y + geometry.height)) return null
        val index = completionScrollOffset + (bodyPoint.y - geometry.y - 3) / geometry.lineHeight
        return index.takeIf { it in completionItems.indices }
    }

    private fun visibleCompletionRows(lineHeight: Int = getFontMetrics(editorFont).height.coerceAtLeast(1)): Int {
        if (completionItems.isEmpty()) return 0
        val maxByHeight = ((bodyHeight() - 12) / lineHeight).coerceAtLeast(3)
        return min(completionItems.size, min(20, maxByHeight))
    }

    private fun maxCompletionScrollOffset(): Int =
        (completionItems.size - visibleCompletionRows()).coerceAtLeast(0)

    private fun ensureCompletionVisible() {
        val visibleRows = visibleCompletionRows()
        if (visibleRows <= 0) return
        if (completionIndex < completionScrollOffset) completionScrollOffset = completionIndex
        if (completionIndex >= completionScrollOffset + visibleRows) {
            completionScrollOffset = completionIndex - visibleRows + 1
        }
        completionScrollOffset = completionScrollOffset.coerceIn(0, maxCompletionScrollOffset())
    }

    private fun scrollCompletions(delta: Int) {
        if (delta == 0) return
        completionScrollOffset = (completionScrollOffset + delta).coerceIn(0, maxCompletionScrollOffset())
        completionIndex = completionIndex.coerceIn(
            completionScrollOffset,
            min(completionItems.lastIndex, completionScrollOffset + visibleCompletionRows() - 1),
        )
        repaint()
    }
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'
