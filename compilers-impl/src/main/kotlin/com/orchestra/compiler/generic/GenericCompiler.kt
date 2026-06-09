package com.orchestra.compiler.generic

import com.orchestra.compiler.api.ANY_LANGUAGE_ID
import com.orchestra.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.orchestra.compiler.api.CompilationResult
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerPlugin
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.NodeCompilerContext
import com.orchestra.compiler.api.StructuredCompiler
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodeText
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.core.model.effectiveLanguageId
import com.orchestra.core.model.effectiveTechnologyId
import com.orchestra.core.model.getElementById
import com.orchestra.core.model.getElementsByIds
import com.orchestra.core.validation.DocumentValidator

open class GenericCompiler : StructuredCompiler() {
    override val id: String = "generic-flow-design"
    override val displayName: String = "Generic Flow-Design Compiler"
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID)
    override val supportedTechnologyIds: Set<String> = setOf("generic")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(ANY_LANGUAGE_ID, "generic"))

    private var activeOverrides: TemplateOverrides = TemplateOverrides(emptyMap())

    override fun supports(document: InflowDocument): Boolean =
        document.nodes.values.any { node ->
            node.stereotype(document) in setOf(NodeStereotype.StaticFile, NodeStereotype.CompilerTemplate)
        }

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun beforeCompile(document: InflowDocument, options: CompilerOptions) {
        activeOverrides = TemplateOverrides.from(document)
    }

    override fun afterCompile(document: InflowDocument, options: CompilerOptions) {
        activeOverrides = TemplateOverrides(emptyMap())
    }

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        document.nodes.values
            .filter { it.stereotype(document) == NodeStereotype.StaticFile }
            .flatMap { staticFilePath(it)?.let(::listOf) ?: staticFileList(it) }

    protected open fun templateOverrideFor(key: String): String? =
        null

    override fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        context.node.isTemplateDefinition(context.document)

    override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        val node = context.node
        if (node.stereotype(context.document) != NodeStereotype.StaticFile) return null
        return GeneratedFile(
            path = staticFilePath(node) ?: node.name.removePrefix("@").ifBlank { "static.txt" },
            content = node.text.declaration.ifBlank { node.text.specification },
            originNodeId = node.id,
            reason = "Literal static file encoded in the flow design",
            elementKind = GeneratedElementKind.StaticFile,
        )
    }

    override fun declarationFor(context: NodeCompilerContext): String {
        if (context.node.isTemplateDefinition(context.document)) return ""
        val template = templateTextFor(context) ?: return ""
        return renderTemplate(context.document, context.node, template, context.options)
    }

    override fun instantiationFor(context: NodeCompilerContext): String =
        context.node.text.instantiation

    override fun primaryFileFor(context: NodeCompilerContext, declaration: String): GeneratedFile? {
        if (declaration.isBlank()) return null
        val kind = when {
            context.node.isLink -> GeneratedElementKind.Link
            context.node.children.isNotEmpty() -> GeneratedElementKind.CompositeEntity
            else -> GeneratedElementKind.TerminalEntity
        }
        return GeneratedFile(
            path = context.primaryPath(),
            content = declaration.trimEnd(),
            originNodeId = context.node.id,
            reason = "Generated from flow compiler template",
            elementKind = kind,
        )
    }

    private fun templateTextFor(context: NodeCompilerContext): String? {
        val keys = templateKeysFor(context.document, context.node)
        keys.forEach { key ->
            templateOverrideFor(key)?.let { return it }
        }
        return activeOverrides.templateTextFor(context.document, context.node)
    }

    private fun renderTemplate(document: InflowDocument, node: Node, template: String, options: CompilerOptions): String {
        val values = templateValues(document, node, options)
        return template.replacePlaceholders(values)
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
        val stereotype: LinkStereotype,
    ) {
        fun argumentText(): String =
            if (typeName.isBlank()) variableName else "$variableName:$typeName"
    }

    private fun linkDescriptors(document: InflowDocument, linkIds: List<com.orchestra.core.model.NodeId>): List<TemplateLinkDescriptor> =
        linkIds.mapNotNull { id ->
            val node = document.nodes[id] ?: return@mapNotNull null
            val link = node.link ?: return@mapNotNull null
            TemplateLinkDescriptor(
                variableName = node.name,
                typeName = link.typeName,
                typeDefinition = link.payloadDefinition,
                stereotype = LinkClassifier.classify(document, node),
            )
        }

    private fun effectiveTechnology(document: InflowDocument, node: Node): TechnologyMetadata =
        node.technology.copy(
            languageId = document.effectiveLanguageId(node.id),
            technologyId = document.effectiveTechnologyId(node.id),
        )
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
            generatedProject = ClassifiedFilesystemLayoutStrategy.layout(document, options.projectName ?: document.name, listOf(file), options),
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
        val staticFiles = overrides[NodeStereotype.StaticFile.name]
            ?.let(::staticFileListLiteral)
            ?: "emptyList()"
        val templateCases = overrides
            .filterKeys { it != NodeStereotype.StaticFile.name }
            .entries
            .joinToString("\n") { (key, node) ->
                "            \"${key.escapeKotlinString()}\" -> ${templateText(node).kotlinTripleQuoted()}.trimIndent()"
            }
            .ifBlank { "            else -> null" }
        val templateWhen = if (templateCases.contains("else -> null")) {
            templateCases
        } else {
            "$templateCases\n            else -> null"
        }
        return """
package $packageName

import com.orchestra.compiler.api.ANY_LANGUAGE_ID
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.core.model.InflowDocument

class $className : GenericCompiler() {
    override val id: String = "${safeCompilerId(root.name)}"
    override val displayName: String = "${root.name.removePrefix("@").ifBlank { className }}"
    override val supportedLanguageIds: Set<String> = setOf("${technology.languageId.ifBlank { ANY_LANGUAGE_ID }}")
    override val supportedTechnologyIds: Set<String> = setOf("${technology.technologyId.ifBlank { "generic" }}")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(supportedLanguageIds.first(), supportedTechnologyIds.first()))

    override fun supports(document: InflowDocument): Boolean = true
    override fun validate(document: InflowDocument) = emptyList<com.orchestra.core.diagnostics.Diagnostic>()

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        $staticFiles

    override fun templateOverrideFor(key: String): String? =
        when (key) {
$templateWhen
        }
}
""".trimStart()
    }

    private fun staticFileListLiteral(node: Node): String {
        val paths = staticFileList(node)
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

private class TemplateOverrides(private val byName: Map<String, String>) {
    fun templateTextFor(document: InflowDocument, node: Node): String? {
        templateKeysFor(document, node).forEach { key ->
            byName[key]?.let { return it }
        }
        return null
    }

    companion object {
        fun from(document: InflowDocument): TemplateOverrides =
            TemplateOverrides(
                document.nodes.values
                    .filter { it.isTemplateDefinition(document) && staticFilePathForOverride(it) == null }
                    .associate { overrideKey(it) to templateText(it) },
            )
    }
}

private fun templateKeysFor(document: InflowDocument, node: Node): List<String> {
    val stereotype = stereotypeForTemplateContext(document, node).name
    return listOf(stereotype, node.kind.name, fallbackKey(node.kind)).distinct()
}

private fun fallbackKey(kind: NodeKind): String =
    when (kind) {
        NodeKind.Node -> NodeKind.Node.name
        NodeKind.Processor -> NodeKind.Processor.name
        NodeKind.Link -> NodeKind.Link.name
        NodeKind.Group -> NodeKind.Group.name
        NodeKind.Note -> NodeKind.Note.name
    }

private fun Node.isTemplateDefinition(document: InflowDocument): Boolean =
    stereotype(document) in setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile)

private fun overrideKey(node: Node): String =
    node.name.trim().removePrefix("@").ifBlank { NodeKind.Node.name }.normalizeCompilerOverrideKey()

private fun String.normalizeCompilerOverrideKey(): String =
    when (lowercase()) {
        "node" -> NodeKind.Node.name
        "link" -> NodeKind.Link.name
        "inputport" -> NodeStereotype.InputPort.name
        "outputport" -> NodeStereotype.OutputPort.name
        "transport" -> NodeStereotype.Transport.name
        "errorpipe" -> NodeStereotype.ErrorPipe.name
        "group" -> NodeKind.Group.name
        "compositeworker" -> NodeStereotype.CompositeWorker.name
        "compositeerrorhandler" -> NodeStereotype.CompositeErrorHandler.name
        "testsuite" -> NodeStereotype.TestSuite.name
        "note", "compilertemplate" -> NodeKind.Note.name
        "staticfile" -> NodeStereotype.StaticFile.name
        "processor", "processingunit" -> NodeKind.Processor.name
        "generator" -> NodeStereotype.Generator.name
        "transformer" -> NodeStereotype.Transformer.name
        "sink" -> NodeStereotype.Sink.name
        "script" -> NodeStereotype.Script.name
        "errorhandler" -> NodeStereotype.ErrorHandler.name
        "servicelibrary" -> NodeStereotype.ServiceLibrary.name
        "dependencyinjection" -> NodeStereotype.DependencyInjection.name
        "test" -> NodeStereotype.Test.name
        else -> this
    }

private fun templateText(node: Node): String {
    val parts = listOf(node.text.instantiation, node.text.declaration).filter { it.isNotBlank() }
    return parts.joinToString("\n").ifBlank { node.text.specification }
}

private fun staticFilePath(node: Node): String? =
    node.metadata["path"]?.takeIf { it.isNotBlank() }
        ?: node.metadata["file"]?.takeIf { it.isNotBlank() }

private fun staticFilePathForOverride(node: Node): String? =
    staticFilePath(node)

private fun staticFileList(node: Node): List<String> =
    templateText(node)
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }

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

private fun String.replacePlaceholders(values: Map<String, String>): String {
    var rendered = this
    values.forEach { (key, value) ->
        rendered = rendered.replace("\${$key}", value)
        rendered = rendered.replace("{{$key}}", value)
    }
    return rendered
}

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
