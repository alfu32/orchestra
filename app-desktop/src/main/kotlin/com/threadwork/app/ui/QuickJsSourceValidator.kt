package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import kotlinx.serialization.json.JsonPrimitive
import org.quickjs.QuickJS

/** Uses QuickJS to parse generated JavaScript without executing the generated program. */
internal object QuickJsSourceValidator : EmbeddedSourceValidator {
    override val languageIds: Set<String> = setOf("javascript")
    override val sourceFileExtensions: Set<String> = setOf("js")
    private val locationPattern = Regex("(?:.*:)?(\\d+)(?::(\\d+))?:\\s*(.*)$")

    override fun validate(file: GeneratedFile): List<Diagnostic> =
        validateDetailed(file).map(EmbeddedDiagnostic::diagnostic)

    override fun validateDetailed(file: GeneratedFile): List<EmbeddedDiagnostic> {
        val sourceLiteral = JsonPrimitive(file.content).toString()
        return runCatching<List<EmbeddedDiagnostic>> {
            QuickJS().use { quickJs ->
                quickJs.evaluate("new Function($sourceLiteral);", file.path)
            }
            emptyList()
        }.getOrElse { error ->
            mapError(file, error.message ?: "QuickJS could not parse generated JavaScript.")
        }
    }

    private fun mapError(file: GeneratedFile, message: String): List<EmbeddedDiagnostic> {
        val locationMatch = message.lineSequence()
            .mapNotNull(locationPattern::find)
            .firstOrNull()
        val generatedLine = locationMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val generatedColumn = locationMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
        val location = generatedLine?.let { file.sourceMap.locate(it, generatedColumn) }
        val diagnosticMessage = message.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() && !it.startsWith("at ") }
            ?: message
        return listOf(
            EmbeddedDiagnostic(
                diagnostic = Diagnostic(
                    severity = DiagnosticSeverity.Error,
                    message = diagnosticMessage,
                    nodeId = location?.nodeId,
                    textSection = location?.textSection,
                    line = location?.line,
                    column = location?.column,
                    sourcePluginId = "quickjs",
                ),
                generatedPath = file.path,
                generatedLine = generatedLine,
                generatedColumn = generatedColumn,
            ),
        )
    }
}
