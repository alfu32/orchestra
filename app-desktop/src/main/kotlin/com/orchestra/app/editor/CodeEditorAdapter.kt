package com.orchestra.app.editor

import com.orchestra.completion.CompletionRequest
import com.orchestra.completion.CompletionSuggestion
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.TechnologyMetadata
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

data class EditorCursor(
    val offset: Int,
    val line: Int,
    val column: Int,
)

data class EditorCompletionContext(
    val nodeId: String?,
    val textSection: NodeTextSection,
)

interface CodeEditorAdapter {
    fun setText(text: String)
    fun getText(): String
    fun setLanguage(languageId: String)
    fun setTechnology(technology: TechnologyMetadata)
    fun setReadOnly(readOnly: Boolean)
    fun setDiagnostics(diagnostics: List<Diagnostic>)
    fun setCompletionContext(context: EditorCompletionContext)
    fun focus()

    var onTextChanged: ((String) -> Unit)?
    var onCursorChanged: ((EditorCursor) -> Unit)?
    var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)?
}

class SwingCodeEditorAdapter : JPanel(BorderLayout()), CodeEditorAdapter {
    private val textArea = JTextArea()
    private var applyingText = false

    override var onTextChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((EditorCursor) -> Unit)? = null
    override var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)? = null

    init {
        textArea.tabSize = 4
        textArea.lineWrap = false
        add(JScrollPane(textArea), BorderLayout.CENTER)
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = notifyTextChanged()
            override fun removeUpdate(e: DocumentEvent) = notifyTextChanged()
            override fun changedUpdate(e: DocumentEvent) = notifyTextChanged()
        })
        textArea.addCaretListener {
            onCursorChanged?.invoke(cursor())
        }
    }

    override fun setText(text: String) {
        if (textArea.text == text) return
        applyingText = true
        textArea.text = text
        textArea.caretPosition = 0
        applyingText = false
    }

    override fun getText(): String = textArea.text

    override fun setLanguage(languageId: String) {
        textArea.toolTipText = languageId.ifBlank { "plain text" }
    }

    override fun setTechnology(technology: TechnologyMetadata) {
        setLanguage(technology.languageId)
    }

    override fun setReadOnly(readOnly: Boolean) {
        textArea.isEditable = !readOnly
    }

    override fun setDiagnostics(diagnostics: List<Diagnostic>) {
        textArea.toolTipText = diagnostics.joinToString("\n") { "${it.severity}: ${it.message}" }.ifBlank { textArea.toolTipText }
    }

    override fun setCompletionContext(context: EditorCompletionContext) {
        // Swing fallback keeps completion dispatch in the host service.
    }

    override fun focus() {
        textArea.requestFocusInWindow()
    }

    private fun notifyTextChanged() {
        if (!applyingText) onTextChanged?.invoke(textArea.text)
    }

    private fun cursor(): EditorCursor {
        val offset = textArea.caretPosition
        val line = textArea.getLineOfOffset(offset)
        return EditorCursor(offset, line + 1, offset - textArea.getLineStartOffset(line) + 1)
    }
}

class CodeMirrorWebViewAdapterUnsupported : CodeEditorAdapter {
    override var onTextChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((EditorCursor) -> Unit)? = null
    override var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)? = null

    override fun setText(text: String) = unsupported()
    override fun getText(): String = unsupported()
    override fun setLanguage(languageId: String) = unsupported()
    override fun setTechnology(technology: TechnologyMetadata) = unsupported()
    override fun setReadOnly(readOnly: Boolean) = unsupported()
    override fun setDiagnostics(diagnostics: List<Diagnostic>) = unsupported()
    override fun setCompletionContext(context: EditorCompletionContext) = unsupported()
    override fun focus() = unsupported()

    private fun unsupported(): Nothing = error(
        "CodeMirror WebView runtime is not on the classpath. Use SwingCodeEditorAdapter or add a JavaFX/JCEF adapter.",
    )
}
