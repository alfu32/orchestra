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
        """:\s*(\d+)(?::\s*(\d+))?:\s*(?:(warning|error|fatal error):\s*)?(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    override fun validate(file: GeneratedFile): List<Diagnostic> {
        if (file.sourceMap.entries.isEmpty()) return emptyList()
        val messages = mutableListOf<String>()
        val output = Files.createTempFile("threadwork-tinycc-", sharedLibrarySuffix())
        return try {
            val exitCode = runCatching {
                TinyCC.compile(
                    file.content,
                    TinyCC.OutputType.DYNAMIC_LIBRARY,
                    output,
                    "",
                ) { message -> messages += message }
            }.getOrElse { error ->
                messages += (error.message ?: "TinyCC could not validate generated C source.")
                -1
            }
            val diagnostics = messages.flatMap { message -> mapDiagnostic(file, message) }
            if (exitCode == 0 || diagnostics.isNotEmpty()) diagnostics else listOf(fallbackDiagnostic(file, messages))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun mapDiagnostic(file: GeneratedFile, message: String): List<Diagnostic> =
        message.lineSequence().mapNotNull { line ->
            val match = diagnosticPattern.find(line.trim()) ?: return@mapNotNull null
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

    private fun fallbackDiagnostic(file: GeneratedFile, messages: List<String>): Diagnostic {
        val location = file.sourceMap.entries.first()
        return Diagnostic(
            severity = DiagnosticSeverity.Error,
            message = messages.joinToString(" ").ifBlank { "TinyCC could not validate generated C source." },
            nodeId = location.nodeId,
            textSection = location.textSection,
            line = location.sourceLine,
            sourcePluginId = "tinycc",
        )
    }

    private fun sharedLibrarySuffix(): String =
        when {
            System.getProperty("os.name").contains("win", ignoreCase = true) -> ".dll"
            System.getProperty("os.name").contains("mac", ignoreCase = true) -> ".dylib"
            else -> ".so"
        }
}
