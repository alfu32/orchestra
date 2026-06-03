package com.orchestra.compiler.generic

import com.orchestra.compiler.api.CompilationResult
import com.orchestra.compiler.api.ANY_LANGUAGE_ID
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerPlugin
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.validation.DocumentValidator

class GenericCompiler : CompilerPlugin {
    override val id: String = "generic-flow-design"
    override val displayName: String = "Generic Flow-Design Compiler"
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID)
    override val supportedTechnologyIds: Set<String> = setOf("generic")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(ANY_LANGUAGE_ID, "generic"))

    override fun supports(document: InflowDocument): Boolean =
        document.nodes.values.any { node ->
            node.stereotype(document) in setOf(NodeStereotype.StaticFile, NodeStereotype.CompilerTemplate)
        }

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun compile(document: InflowDocument, options: CompilerOptions): CompilationResult {
        val diagnostics = validate(document)
        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }

        val projectName = options.projectName ?: document.name
        val scopeIds = compileScopeIds(document, options.scopeNodeIds)
        val files = document.nodes.values
            .filter { it.id in scopeIds }
            .sortedBy { it.id.value }
            .flatMap { node -> generatedFilesFor(document, node, options) }

        return CompilationResult(
            generatedProject = layoutStrategy(options).layout(projectName, files, options),
            diagnostics = diagnostics,
            success = true,
        )
    }

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        document.nodes.values
            .filter { it.stereotype(document) == NodeStereotype.StaticFile }
            .mapNotNull(::staticFilePath)

    override fun getStaticFile(document: InflowDocument, node: Node, options: CompilerOptions): String =
        node.text.source.ifBlank { node.text.specification }

    override fun getCompilerTemplate(document: InflowDocument, node: Node, options: CompilerOptions): String =
        node.text.source.ifBlank { node.text.specification }

    private fun generatedFilesFor(document: InflowDocument, node: Node, options: CompilerOptions): List<GeneratedFile> =
        when (node.stereotype(document)) {
            NodeStereotype.StaticFile -> staticFilePath(node)?.let { path ->
                listOf(
                    GeneratedFile(
                        path = path,
                        content = getStaticFile(document, node, options),
                        originNodeId = node.id,
                        reason = "Literal static file encoded in the flow design",
                        elementKind = GeneratedElementKind.StaticFile,
                    ),
                )
            } ?: emptyList()
            NodeStereotype.CompilerTemplate -> listOf(
                GeneratedFile(
                    path = compilerTemplatePath(node),
                    content = getCompilerTemplate(document, node, options),
                    originNodeId = node.id,
                    reason = "Compiler template override encoded in the flow design",
                    elementKind = GeneratedElementKind.CompilerTemplate,
                ),
            )
            else -> emptyList()
        }

    private fun staticFilePath(node: Node): String? =
        node.metadata["path"]
            ?: node.metadata["file"]
            ?: node.technology.fileExtension.takeIf { it.isNotBlank() }?.let { "${node.name.removePrefix("@")}.${it.trimStart('.')}" }

    private fun compilerTemplatePath(node: Node): String =
        node.metadata["path"]
            ?: "compiler-overrides/${node.name.removePrefix("@").ifBlank { "template" }}.template"
}

private fun compileScopeIds(document: InflowDocument, requested: Set<NodeId>): Set<NodeId> {
    if (requested.isEmpty()) return document.nodes.keys
    val result = linkedSetOf<NodeId>()
    fun include(id: NodeId) {
        val node = document.nodes[id] ?: return
        if (result.add(id) && !node.isLink) node.children.forEach(::include)
    }
    requested.forEach(::include)
    return result
}
