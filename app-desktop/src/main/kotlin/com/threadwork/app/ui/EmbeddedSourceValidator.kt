package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.core.diagnostics.Diagnostic

/**
 * In-process compiler diagnostics for generated source. Providers are kept
 * language-specific so bundled runtimes such as TinyCC, QuickJS, or PHP can be
 * added without changing the editor scheduling path.
 */
internal interface EmbeddedSourceValidator {
    val languageIds: Set<String>
    val sourceFileExtensions: Set<String>

    fun validate(file: GeneratedFile): List<Diagnostic>
}
