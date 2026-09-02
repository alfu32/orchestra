package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.NodeId
import org.tinycc.TinyCC
import java.nio.file.Files
import kotlin.math.max

/** Maps TinyCC's in-memory source diagnostics back through a generated-file source map. */
internal object TinyCcSourceValidator : EmbeddedSourceValidator {
    override val languageIds: Set<String> = setOf("c")
    override val sourceFileExtensions: Set<String> = setOf("c")
    private val diagnosticPattern = Regex(
        """:\s*(\d+)(?::\s*(\d+))?:\s*(?:(warning|error|fatal error):\s*)?(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private const val diagnosticsPerNode = 2
    private const val minimumRecoveryPasses = 16

    private data class ValidationPass(
        val exitCode: Int,
        val messages: List<String>,
    )

    override fun validate(file: GeneratedFile): List<Diagnostic> {
        return validateDetailed(file).map(EmbeddedDiagnostic::diagnostic)
    }

    override fun validateDetailed(file: GeneratedFile): List<EmbeddedDiagnostic> {
        val output = Files.createTempFile("threadwork-tinycc-", sharedLibrarySuffix())
        return try {
            val diagnostics = linkedMapOf<String, EmbeddedDiagnostic>()
            var validationContent = file.content
            val suppressedLines = linkedSetOf<Int>()
            val recordedErrorsByNode = mutableMapOf<NodeId?, Int>()

            repeat(recoveryPassLimit(file)) {
                val pass = compilePass(validationContent, output)
                val passDiagnostics = pass.messages.flatMap { message -> mapDiagnostic(file, message) }
                passDiagnostics.forEach { diagnostic ->
                    if (diagnostic.diagnostic.severity != DiagnosticSeverity.Error) {
                        diagnostics.putIfAbsent(diagnosticKey(diagnostic), diagnostic)
                        return@forEach
                    }
                    val nodeId = diagnostic.diagnostic.nodeId
                    val count = recordedErrorsByNode[nodeId] ?: 0
                    if (count < diagnosticsPerNode && diagnostics.putIfAbsent(diagnosticKey(diagnostic), diagnostic) == null) {
                        recordedErrorsByNode[nodeId] = count + 1
                    }
                }
                if (pass.exitCode == 0) return diagnostics.values.toList()

                val newlySuppressed = passDiagnostics
                    .filter { it.diagnostic.severity == DiagnosticSeverity.Error }
                    .mapNotNull(EmbeddedDiagnostic::generatedLine)
                    .toMutableSet()
                recordedErrorsByNode
                    .filterValues { it >= diagnosticsPerNode }
                    .keys
                    .filterNotNull()
                    .forEach { exhaustedNodeId ->
                        file.sourceMap.entries
                            .asSequence()
                            .filter { it.nodeId == exhaustedNodeId }
                            .mapTo(newlySuppressed) { it.generatedLine }
                    }
                newlySuppressed.removeAll(suppressedLines)
                suppressedLines += newlySuppressed
                if (newlySuppressed.isEmpty()) {
                    if (diagnostics.isEmpty()) diagnostics["fallback"] = fallbackDiagnostic(file, pass.messages)
                    return diagnostics.values.toList()
                }
                validationContent = suppressGeneratedLines(validationContent, newlySuppressed)
            }
            if (diagnostics.isEmpty()) listOf(fallbackDiagnostic(file, emptyList())) else diagnostics.values.toList()
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun compilePass(content: String, output: java.nio.file.Path): ValidationPass {
        val messages = mutableListOf<String>()
        val exitCode = runCatching {
            TinyCC.compile(
                content,
                TinyCC.OutputType.DYNAMIC_LIBRARY,
                output,
                "",
            ) { message -> messages += message }
        }.getOrElse { error ->
            messages += (error.message ?: "TinyCC could not validate generated C source.")
            -1
        }
        return ValidationPass(exitCode, messages)
    }

    private fun suppressGeneratedLines(content: String, generatedLines: Iterable<Int>): String {
        val suppressed = generatedLines.toSet()
        return content.lines().mapIndexed { index, line ->
            if (index + 1 in suppressed) "; /* Threadwork validator recovery */" else line
        }.joinToString("\n")
    }

    private fun recoveryPassLimit(file: GeneratedFile): Int {
        val mappedNodeCount = file.sourceMap.entries.map { it.nodeId }.toSet().size
        return max(minimumRecoveryPasses, mappedNodeCount * diagnosticsPerNode * 2)
    }

    private fun diagnosticKey(diagnostic: EmbeddedDiagnostic): String = listOf(
        diagnostic.generatedPath,
        diagnostic.generatedLine,
        diagnostic.generatedColumn,
        diagnostic.diagnostic.message,
    ).joinToString("|")

    private fun mapDiagnostic(file: GeneratedFile, message: String): List<EmbeddedDiagnostic> =
        message.lineSequence().mapNotNull { line ->
            val match = diagnosticPattern.find(line.trim()) ?: return@mapNotNull null
            val generatedLine = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val generatedColumn = match.groupValues[2].toIntOrNull()
            val location = file.sourceMap.locate(generatedLine, generatedColumn)
            val severity = when (match.groupValues[3].lowercase()) {
                "warning" -> DiagnosticSeverity.Warning
                else -> DiagnosticSeverity.Error
            }
            EmbeddedDiagnostic(
                diagnostic = Diagnostic(
                    severity = severity,
                    message = match.groupValues[4].ifBlank { line.trim() },
                    nodeId = location?.nodeId,
                    textSection = location?.textSection,
                    line = location?.line,
                    column = location?.column,
                    sourcePluginId = "tinycc",
                ),
                generatedPath = file.path,
                generatedLine = generatedLine,
                generatedColumn = generatedColumn,
            )
        }.toList()

    private fun fallbackDiagnostic(file: GeneratedFile, messages: List<String>): EmbeddedDiagnostic {
        val location = file.sourceMap.entries.firstOrNull()
        return EmbeddedDiagnostic(
            diagnostic = Diagnostic(
                severity = DiagnosticSeverity.Error,
                message = messages.joinToString(" ").ifBlank { "TinyCC could not validate generated C source." },
                nodeId = location?.nodeId,
                textSection = location?.textSection,
                line = location?.sourceLine,
                sourcePluginId = "tinycc",
            ),
            generatedPath = file.path,
            generatedLine = location?.generatedLine,
            generatedColumn = null,
        )
    }

    private fun sharedLibrarySuffix(): String =
        when {
            System.getProperty("os.name").contains("win", ignoreCase = true) -> ".dll"
            System.getProperty("os.name").contains("mac", ignoreCase = true) -> ".dylib"
            else -> ".so"
        }
}
