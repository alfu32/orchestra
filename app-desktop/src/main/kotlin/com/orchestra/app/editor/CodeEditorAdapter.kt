package com.orchestra.app.editor

import com.orchestra.completion.CompletionRequest
import com.orchestra.completion.CompletionSuggestion
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.TechnologyMetadata

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
        "CodeMirror WebView runtime is not on the classpath. Use GridCodeEditorAdapter or add a JavaFX/JCEF adapter.",
    )
}
