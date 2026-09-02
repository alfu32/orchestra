package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import org.tinycc.TinyCC
import java.nio.file.Files

/** Maps TinyCC's in-memory source diagnostics back through a generated-file source map. */
internal object TinyCcSourceValidator : EmbeddedSourceValidator {
    override val languageIds: Set<String> = setOf("c")
    override val sourceFileExtensions: Set<String> = setOf("c")
    private val diagnosticPattern = Regex(
        """.*?:(\d+)(?::(\d+))?:\s*(?:(warning|error):\s*)?(.*)""",
        RegexOption.IGNORE_CASE,
    )

    override fun validate(file: GeneratedFile): List<Diagnostic> {
        if (file.sourceMap.entries.isEmpty()) return emptyList()
        val messages = mutableListOf<String>()
        val output = Files.createTempFile("threadwork-tinycc-", sharedLibrarySuffix())
        return try {
            TinyCC.compile(
                file.content,
                TinyCC.OutputType.DYNAMIC_LIBRARY,
                output,
                "",
            ) { message -> messages += message }
            messages.flatMap { message -> mapDiagnostic(file, message) }
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun mapDiagnostic(file: GeneratedFile, message: String): List<Diagnostic> =
        message.lineSequence().mapNotNull { line ->
            val match = diagnosticPattern.matchEntire(line.trim()) ?: return@mapNotNull null
            val generatedLine = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val generatedColumn = match.groupValues[2].toIntOrNull()
            val location = file.sourceMap.locate(generatedLine, generatedColumn) ?: return@mapNotNull null
            val severity = when (match.groupValues[3].lowercase()) {
                "warning" -> DiagnosticSeverity.Warning
                else -> DiagnosticSeverity.Error
            }
            Diagnostic(
                severity = severity,
                message = match.groupValues[4].ifBlank { line.trim() },
                nodeId = location.nodeId,
                textSection = location.textSection,
                line = location.line,
                column = location.column,
                sourcePluginId = "tinycc",
            )
        }.toList()

    private fun sharedLibrarySuffix(): String =
        when {
            System.getProperty("os.name").contains("win", ignoreCase = true) -> ".dll"
            System.getProperty("os.name").contains("mac", ignoreCase = true) -> ".dylib"
            else -> ".so"
        }
}
