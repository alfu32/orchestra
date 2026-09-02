package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.core.diagnostics.Diagnostic

internal data class EmbeddedDiagnostic(
    val diagnostic: Diagnostic,
    val generatedPath: String,
    val generatedLine: Int?,
    val generatedColumn: Int?,
)

/**
 * In-process compiler diagnostics for generated source. Providers are kept
 * language-specific so bundled runtimes such as TinyCC, QuickJS, or PHP can be
 * added without changing the editor scheduling path.
 */
internal interface EmbeddedSourceValidator {
    val languageIds: Set<String>
    val sourceFileExtensions: Set<String>

    fun validate(file: GeneratedFile): List<Diagnostic>

    fun validateDetailed(file: GeneratedFile): List<EmbeddedDiagnostic> =
        validate(file).map { diagnostic ->
            EmbeddedDiagnostic(
                diagnostic = diagnostic,
                generatedPath = file.path,
                generatedLine = file.sourceMap.entries.firstOrNull {
                    it.nodeId == diagnostic.nodeId &&
                        it.textSection == diagnostic.textSection &&
                        it.sourceLine == diagnostic.line
                }?.generatedLine,
                generatedColumn = null,
            )
        }
}
