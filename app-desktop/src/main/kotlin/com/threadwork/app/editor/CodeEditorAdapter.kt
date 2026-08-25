package com.threadwork.app.editor

import com.threadwork.completion.CompletionRequest
import com.threadwork.completion.CompletionSuggestion
import com.threadwork.completion.DeclarationSymbol
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.TechnologyMetadata

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
    var onDeclarationSymbolsRequested: ((CompletionRequest) -> List<DeclarationSymbol>)?
}

class CodeMirrorWebViewAdapterUnsupported : CodeEditorAdapter {
    override var onTextChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((EditorCursor) -> Unit)? = null
    override var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)? = null
    override var onDeclarationSymbolsRequested: ((CompletionRequest) -> List<DeclarationSymbol>)? = null

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
