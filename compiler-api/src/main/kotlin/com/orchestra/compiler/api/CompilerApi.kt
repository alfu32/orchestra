package com.orchestra.compiler.api

import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.NodeId
import java.nio.file.Files
import java.nio.file.Path

interface CompilerPlugin {
    val id: String
    val displayName: String
    val supportedLanguageIds: Set<String> get() = emptySet()
    val supportedTechnologyIds: Set<String> get() = emptySet()

    fun supports(document: InflowDocument): Boolean
    fun validate(document: InflowDocument): List<Diagnostic>
    fun compile(document: InflowDocument, options: CompilerOptions = CompilerOptions()): CompilationResult
}

data class CompilerOptions(
    val projectName: String? = null,
)

data class CompilationResult(
    val generatedProject: GeneratedProject?,
    val diagnostics: List<Diagnostic>,
    val success: Boolean,
)

data class GeneratedProject(
    val name: String,
    val files: List<GeneratedFile>,
) {
    fun writeTo(directory: Path) {
        files.forEach { file ->
            val target = directory.resolve(file.path)
            target.parent?.let(Files::createDirectories)
            Files.writeString(target, file.content)
        }
    }
}

data class GeneratedFile(
    val path: String,
    val content: String,
    val originNodeId: NodeId?,
    val reason: String,
)
