package com.orchestra.compiler.generic

import com.orchestra.compiler.api.ANY_LANGUAGE_ID
import com.orchestra.compiler.api.CompilationResult
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerPlugin
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodeText
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.core.model.getElementById
import com.orchestra.core.model.getElementsByIds
import com.orchestra.core.model.effectiveLanguageId
import com.orchestra.core.model.effectiveTechnologyId
import com.orchestra.core.validation.DocumentValidator

open class GenericCompiler : CompilerPlugin {
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
        val overrides = TemplateOverrides.from(document)
        val files = mutableListOf<GeneratedFile>()

        document.nodes.values
            .filter { it.id in scopeIds }
            .sortedBy { it.id.value }
            .forEach { node ->
                when {
                    node.stereotype(document) == NodeStereotype.StaticFile && staticFilePath(node) != null -> {
                        files += staticFileFor(node)
                    }
                    node.isTemplateDefinition(document) -> Unit
                    node.isLink -> {
                        overrides.templateForLink(document, node)?.let { template ->
                            files += generatedFileFor(document, node, template, GeneratedElementKind.Link, options)
                        }
                    }
                    else -> {
                        overrides.templateForNode(document, node)?.let { template ->
                            val kind = if (node.children.isEmpty()) GeneratedElementKind.TerminalEntity else GeneratedElementKind.CompositeEntity
                            files += generatedFileFor(document, node, template, kind, options)
                        }
                    }
                }
            }

        return CompilationResult(
            generatedProject = layoutStrategy(options).layout(document, projectName, files.distinctBy { it.path }, options),
            diagnostics = diagnostics,
            success = true,
        )
    }

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        document.nodes.values
            .filter { it.stereotype(document) == NodeStereotype.StaticFile }
            .flatMap { staticFilePath(it)?.let(::listOf) ?: staticFileList(it) }

    private fun staticFileFor(node: Node): GeneratedFile =
        GeneratedFile(
            path = staticFilePath(node) ?: generatedPath(node, NodeStereotype.StaticFile, fallbackExtension = "txt"),
            content = node.text.declaration.ifBlank { node.text.specification },
            originNodeId = node.id,
            reason = "Literal static file encoded in the flow design",
            elementKind = GeneratedElementKind.StaticFile,
        )

    private fun generatedFileFor(
        document: InflowDocument,
        node: Node,
        template: Node,
        kind: GeneratedElementKind,
        options: CompilerOptions,
    ): GeneratedFile {
        val stereotype = stereotypeForTemplateContext(document, node)
        return GeneratedFile(
            path = generatedPath(node, stereotype, fallbackExtension = extensionFor(document, node)),
            content = renderTemplate(document, node, template, options),
            originNodeId = node.id,
            reason = "Generated from ${template.name} override",
            elementKind = kind,
        )
    }

    private fun renderTemplate(document: InflowDocument, node: Node, template: Node, options: CompilerOptions): String {
        val values = templateValues(document, node, options)
        return templateText(template).replacePlaceholders(values)
    }

    private fun templateValues(document: InflowDocument, node: Node, options: CompilerOptions): Map<String, String> {
        val link = node.link
        val source = link?.sourceNodeId?.let(document::getElementById)
        val target = link?.targetNodeId?.let(document::getElementById)
        val stereotype = stereotypeForTemplateContext(document, node)
        val technology = effectiveTechnology(document, node)
        val incomingDescriptors = linkDescriptors(document, node.incomingLinks)
        val outgoingDescriptors = linkDescriptors(document, node.outgoingLinks)
        val dependencyInjectionDescriptors = incomingDescriptors
            .filter { descriptor -> descriptor.stereotype == LinkStereotype.DependencyInjection }
        val values = linkedMapOf<String, String>(
            "id" to node.id.value,
            "name" to node.name,
            "options.projectName" to (options.projectName ?: document.name),
            "node.id" to node.id.value,
            "node.name" to node.name,
            "node.kind.name" to node.kind.name,
            "node.kind.ordinal" to node.kind.ordinal.toString(),
            "node.kind" to node.kind.name,
            "node.parentId" to node.parentId?.value.orEmpty(),
            "node.stereotype" to stereotype.name,
            "node.children.size" to node.children.size.toString(),
            "node.incomingLinks" to node.incomingLinks.joinToString(",") { it.value },
            "node.isComposite" to node.children.isNotEmpty().toString(),
            "node.isLink" to node.isLink.toString(),
            "node.isTerminal" to node.children.isEmpty().toString(),
            "node.layout" to node.layout.toString(),
            "node.link.sourceNodeId" to link?.sourceNodeId?.value.orEmpty(),
            "node.link.targetNodeId" to link?.targetNodeId?.value.orEmpty(),
            "node.link.sourcePortName" to link?.sourcePortName.orEmpty(),
            "node.link.targetPortName" to link?.targetPortName.orEmpty(),
            "node.metadata.size" to node.metadata.size.toString(),
            "node.metadata" to node.metadata.entries.joinToString(",") { "${it.key}=${it.value}" },
            "node.outgoingLinks" to node.outgoingLinks.joinToString(",") { it.value },
            "node.pluginData" to node.pluginData.entries.joinToString(",") { "${it.key}=${it.value}" },
            "node.ports" to node.ports.joinToString(",") { it.name },
            "node.text.instantiationLanguageId" to node.text.instantiationLanguageId,
            "node.text.instantiation" to node.text.instantiation,
            "node.text.declarationLanguageId" to node.text.declarationLanguageId,
            "node.text.declaration" to node.text.declaration,
            "node.text.specificationLanguageId" to node.text.specificationLanguageId,
            "node.text.specification" to node.text.specification,
            "node.text.aiInstructionsLanguageId" to node.text.aiInstructionsLanguageId,
            "node.text.aiInstructions" to node.text.aiInstructions,
            "node.text.testsLanguageId" to node.text.testsLanguageId,
            "node.text.tests" to node.text.tests,
            "stereotype" to stereotype.name,
            "language" to technology.languageId,
            "technology" to technology.technologyId,
            "technology.languageId" to technology.languageId,
            "technology.technologyId" to technology.technologyId,
            "technology.compilerId" to technology.compilerId,
            "technology.fileExtension" to technology.fileExtension,
            "declaration" to node.text.declaration,
            "instantiation" to node.text.instantiation,
            "specification" to node.text.specification,
            "tests" to node.text.tests,
            "usageInstructions" to node.text.aiInstructions,
            "text.declaration" to node.text.declaration,
            "text.instantiation" to node.text.instantiation,
            "text.specification" to node.text.specification,
            "text.tests" to node.text.tests,
            "text.usageInstructions" to node.text.aiInstructions,
            "children" to document.getElementsByIds(node.children).values.joinToString(",") { it.name },
            "incomingLinks" to document.getElementsByIds(node.incomingLinks).values.joinToString(",") { it.name },
            "outgoingLinks" to document.getElementsByIds(node.outgoingLinks).values.joinToString(",") { it.name },
            "incomingLinkVariables" to incomingDescriptors.joinToString(",") { it.variableName },
            "outgoingLinkVariables" to outgoingDescriptors.joinToString(",") { it.variableName },
            "incomingLinkTypes" to incomingDescriptors.joinToString(",") { it.typeName },
            "outgoingLinkTypes" to outgoingDescriptors.joinToString(",") { it.typeName },
            "incomingArguments" to incomingDescriptors.joinToString(", ") { it.argumentText() },
            "outgoingArguments" to outgoingDescriptors.joinToString(", ") { it.argumentText() },
            "incomingTypeDefinitions" to incomingDescriptors.joinToString("\n\n") { it.typeDefinition },
            "outgoingTypeDefinitions" to outgoingDescriptors.joinToString("\n\n") { it.typeDefinition },
            "dependencyInjectionLinks" to dependencyInjectionDescriptors.joinToString(",") { it.variableName },
            "dependencyInjectionArguments" to dependencyInjectionDescriptors.joinToString(", ") { it.argumentText() },
            "ports" to node.ports.joinToString(",") { it.name },
        )
        node.metadata.forEach { (key, value) ->
            values["metadata.$key"] = value
        }
        if (link != null) {
            values += mapOf(
                "link.sourceNodeId" to link.sourceNodeId.value,
                "link.targetNodeId" to link.targetNodeId.value,
                "link.sourcePortName" to link.sourcePortName,
                "link.targetPortName" to link.targetPortName,
                "link.transportKind" to link.transportKind,
                "link.variableName" to node.name,
                "link.typeName" to link.typeName,
                "link.typeDefinition" to link.payloadDefinition,
                "link.payloadDefinition" to link.payloadDefinition,
                "sourceNode.name" to source?.name.orEmpty(),
            "targetNode.name" to target?.name.orEmpty(),
        )
        }
        return values
    }

    private data class TemplateLinkDescriptor(
        val variableName: String,
        val typeName: String,
        val typeDefinition: String,
        val sourceNodeName: String,
        val sourcePortName: String,
        val targetNodeName: String,
        val targetPortName: String,
        val stereotype: LinkStereotype,
    ) {
        fun argumentText(): String =
            if (typeName.isBlank()) variableName else "$variableName:$typeName"
    }

    private fun linkDescriptors(document: InflowDocument, linkIds: List<NodeId>): List<TemplateLinkDescriptor> =
        linkIds.mapNotNull { id ->
            val node = document.nodes[id] ?: return@mapNotNull null
            val link = node.link ?: return@mapNotNull null
            val source = document.nodes[link.sourceNodeId]
            val target = document.nodes[link.targetNodeId]
            TemplateLinkDescriptor(
                variableName = node.name,
                typeName = link.typeName,
                typeDefinition = link.payloadDefinition,
                sourceNodeName = source?.name.orEmpty(),
                sourcePortName = link.sourcePortName,
                targetNodeName = target?.name.orEmpty(),
                targetPortName = link.targetPortName,
                stereotype = LinkClassifier.classify(document, node),
            )
        }

    private fun generatedPath(node: Node, stereotype: NodeStereotype, fallbackExtension: String): String {
        node.metadata["path"]?.takeIf { it.isNotBlank() }?.let { return it }
        node.metadata["file"]?.takeIf { it.isNotBlank() }?.let { return it }
        val directory = when {
            node.isLink -> "links"
            stereotype in setOf(NodeStereotype.CompositeWorker, NodeStereotype.CompositeErrorHandler, NodeStereotype.TestSuite) -> "composites"
            stereotype == NodeStereotype.ServiceLibrary -> "libraries"
            else -> "nodes"
        }
        val extension = fallbackExtension.trim().trimStart('.').ifBlank { "txt" }
        return "$directory/${safeFileName(node.name)}.$extension"
    }

    private fun extensionFor(document: InflowDocument, node: Node): String =
        node.technology.fileExtension.ifBlank {
            when (document.effectiveLanguageId(node.id)) {
                "markdown" -> "md"
                "kotlin" -> "kt"
                "javascript" -> "js"
                "typescript" -> "ts"
                "json" -> "json"
                else -> "txt"
            }
        }

    private fun staticFilePath(node: Node): String? =
        node.metadata["path"]?.takeIf { it.isNotBlank() }
            ?: node.metadata["file"]?.takeIf { it.isNotBlank() }

    private fun staticFileList(node: Node): List<String> =
        templateText(node)
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

    private fun effectiveTechnology(document: InflowDocument, node: Node): TechnologyMetadata =
        node.technology.copy(
            languageId = document.effectiveLanguageId(node.id),
            technologyId = document.effectiveTechnologyId(node.id),
        )

    private fun stereotypeForTemplateContext(document: InflowDocument, node: Node): NodeStereotype =
        if (!node.isLink) {
            node.stereotype(document)
        } else {
            when (LinkClassifier.classify(document, node)) {
                LinkStereotype.Transport -> NodeStereotype.Transport
                LinkStereotype.ErrorPipe -> NodeStereotype.ErrorPipe
                LinkStereotype.UsageImport,
                LinkStereotype.DependencyInjection -> NodeStereotype.DependencyInjection
            }
        }
}

class CompilerCompiler : CompilerPlugin {
    override val id: String = "compiler-compiler"
    override val displayName: String = "Compiler Compiler"
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID)
    override val supportedTechnologyIds: Set<String> = setOf("compiler-compiler")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(ANY_LANGUAGE_ID, "compiler-compiler"))

    override fun supports(document: InflowDocument): Boolean =
        compilerRoot(document) != null

    override fun validate(document: InflowDocument): List<Diagnostic> {
        val diagnostics = DocumentValidator.validate(document).toMutableList()
        val compilerNodes = document.nodes.values.filter { it.name.equals("@Compiler", ignoreCase = true) }
        if (compilerNodes.size > 1) {
            diagnostics += Diagnostic(DiagnosticSeverity.Error, "CompilerCompiler expects exactly one @Compiler node.")
        }
        return diagnostics
    }

    override fun compile(document: InflowDocument, options: CompilerOptions): CompilationResult {
        val diagnostics = validate(document)
        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }
        val root = compilerRoot(document)
            ?: return CompilationResult(null, diagnostics + Diagnostic(DiagnosticSeverity.Error, "No @Compiler node found."), success = false)
        val className = compilerClassName(root)
        val packageName = root.metadata["package"].orEmpty().ifBlank { "generated.compiler" }
        val technology = effectiveCompilerTechnology(document, root)
        val overrides = root.children.mapNotNull(document::getElementById)
            .filter { it.stereotype(document) in setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile) }
            .associateBy { overrideKey(it) }
        val source = compilerClassSource(packageName, className, root, technology, overrides)
        val file = GeneratedFile(
            path = "src/main/kotlin/${packageName.replace('.', '/')}/$className.kt",
            content = source,
            originNodeId = root.id,
            reason = "Compiler class generated from @Compiler and child overrides",
            elementKind = GeneratedElementKind.CompilerTemplate,
        )
        return CompilationResult(
            generatedProject = layoutStrategy(options).layout(document, options.projectName ?: document.name, listOf(file), options),
            diagnostics = diagnostics,
            success = true,
        )
    }

    private fun compilerClassSource(
        packageName: String,
        className: String,
        root: Node,
        technology: TechnologyMetadata,
        overrides: Map<String, Node>,
    ): String {
        fun method(kind: NodeKind, declaration: Boolean): String {
            val section = if (declaration) "declaration" else "instantiation"
            val body = overrides[kind.name]
                ?.let { node ->
                    when (section) {
                        "declaration" -> node.text.declaration
                        else -> node.text.instantiation
                    }
                }
                .orEmpty()
                .kotlinTripleQuoted()
            return """
    override fun get${kind.name}${if (declaration) "Declaration" else "Instantiation"}(document: InflowDocument, node: Node, options: CompilerOptions): String =
        $body.trimIndent()
""".trimEnd()
        }
        val staticFiles = overrides[NodeStereotype.StaticFile.name]
            ?.let(::staticFileListLiteral)
            ?: "emptyList()"
        return """
package $packageName

import com.orchestra.compiler.api.ANY_LANGUAGE_ID
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.compiler.generic.compileWithMethodDispatch
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node

class $className : GenericCompiler() {
    override val id: String = "${safeCompilerId(root.name)}"
    override val displayName: String = "${root.name.removePrefix("@").ifBlank { className }}"
    override val supportedLanguageIds: Set<String> = setOf("${technology.languageId.ifBlank { ANY_LANGUAGE_ID }}")
    override val supportedTechnologyIds: Set<String> = setOf("${technology.technologyId.ifBlank { "generic" }}")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(supportedLanguageIds.first(), supportedTechnologyIds.first()))

    override fun supports(document: InflowDocument): Boolean = true
    override fun validate(document: InflowDocument) = emptyList<com.orchestra.core.diagnostics.Diagnostic>()
    override fun compile(document: InflowDocument, options: CompilerOptions) =
        compileWithMethodDispatch(this, document, options)

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        $staticFiles

${NodeKind.entries.joinToString("\n\n") { kind -> listOf(method(kind, true), method(kind, false)).joinToString("\n\n") }}
}
""".trimStart()
    }

    private fun staticFileListLiteral(node: Node): String {
        val paths = templateText(node)
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
        return if (paths.isEmpty()) {
            "emptyList()"
        } else {
            paths.joinToString(prefix = "listOf(", postfix = ")") { "\"${it.escapeKotlinString()}\"" }
        }
    }

    private fun compilerRoot(document: InflowDocument): Node? =
        document.nodes.values.singleOrNull { it.name.equals("@Compiler", ignoreCase = true) }

    private fun compilerClassName(node: Node): String =
        node.metadata["className"]
            ?: node.metadata["class"]
            ?: "${node.name.removePrefix("@").toPascalCase().ifBlank { "Generated" }}Compiler"

    private fun effectiveCompilerTechnology(document: InflowDocument, node: Node): TechnologyMetadata =
        node.technology.copy(
            languageId = document.effectiveLanguageId(node.id),
            technologyId = node.technology.technologyId.trim().ifBlank { safeCompilerId(document.name) },
        )
}

private class TemplateOverrides(private val byName: Map<String, Node>) {
    fun templateForNode(document: InflowDocument, node: Node): Node? {
        return byName[node.kind.name] ?: fallbackTemplate(node.kind)
    }

    fun templateForLink(document: InflowDocument, linkNode: Node): Node? {
        return byName[NodeKind.Link.name] ?: byName[NodeKind.Node.name]
    }

    private fun fallbackTemplate(kind: NodeKind): Node? =
        when (kind) {
            NodeKind.Node -> byName[NodeKind.Node.name]
            NodeKind.Processor -> byName[NodeKind.Processor.name] ?: byName[NodeKind.Node.name]
            NodeKind.Link -> byName[NodeKind.Link.name] ?: byName[NodeKind.Node.name]
            NodeKind.Group -> byName[NodeKind.Group.name] ?: byName[NodeKind.Node.name]
            NodeKind.Note -> byName[NodeKind.Note.name] ?: byName[NodeKind.Node.name]
        }

    companion object {
        fun from(document: InflowDocument): TemplateOverrides =
            TemplateOverrides(
                document.nodes.values
                    .filter { it.isTemplateDefinition(document) && staticFilePathForOverride(it) == null }
                    .associateBy { overrideKey(it) },
            )
    }
}

private fun Node.isTemplateDefinition(document: InflowDocument): Boolean =
    stereotype(document) in setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile)

private fun overrideKey(node: Node): String =
    node.name.trim().removePrefix("@").ifBlank { NodeKind.Node.name }.normalizeCompilerOverrideKey()

private fun String.normalizeCompilerOverrideKey(): String =
    when (lowercase()) {
        "node" -> NodeKind.Node.name
        "link", "inputport", "outputport", "transport", "errorpipe" -> NodeKind.Link.name
        "group", "compositeworker", "compositeerrorhandler", "testsuite" -> NodeKind.Group.name
        "note", "compilertemplate" -> NodeKind.Note.name
        "staticfile" -> "StaticFile"
        "processor", "generator", "transformer", "sink", "script", "errorhandler", "servicelibrary", "dependencyinjection", "test" -> NodeKind.Processor.name
        else -> this
    }

private fun templateText(node: Node): String {
        val parts = listOf(node.text.instantiation, node.text.declaration).filter { it.isNotBlank() }
        return parts.joinToString("\n").ifBlank { node.text.specification }
    }

private fun staticFilePathForOverride(node: Node): String? =
    node.metadata["path"]?.takeIf { it.isNotBlank() }
        ?: node.metadata["file"]?.takeIf { it.isNotBlank() }

private fun String.replacePlaceholders(values: Map<String, String>): String {
    var rendered = this
    values.forEach { (key, value) ->
        rendered = rendered.replace("\${$key}", value)
        rendered = rendered.replace("{{$key}}", value)
    }
    return rendered
}

private fun safeFileName(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "_")
        .trim('_')
        .ifBlank { "generated" }

private fun safeCompilerId(value: String): String =
    value.removePrefix("@")
        .lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-')
        .ifBlank { "generated-compiler" }

private fun String.toPascalCase(): String =
    split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

private fun String.kotlinTripleQuoted(): String =
    "\"\"\"\n${replace("\"\"\"", "\"\"\\\"").replace("$", "\${'$'}")}\n\"\"\""

private fun String.escapeKotlinString(): String =
    flatMap { char ->
        when (char) {
            '\\' -> "\\\\".toList()
            '"' -> "\\\"".toList()
            '\n' -> "\\n".toList()
            '\r' -> "\\r".toList()
            '\t' -> "\\t".toList()
            else -> listOf(char)
        }
    }.joinToString("")

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
