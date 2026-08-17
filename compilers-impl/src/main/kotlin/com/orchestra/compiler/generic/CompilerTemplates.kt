package com.orchestra.compiler.generic

import com.orchestra.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.orchestra.compiler.api.CompiledNodeArtifact
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.LayoutStrategy
import com.orchestra.compiler.api.NodeCompilerContext
import com.orchestra.compiler.api.SingleFileLayoutStrategy
import com.orchestra.compiler.api.SourceSetLayoutStrategy
import com.orchestra.compiler.api.StructuredCompiler
import com.orchestra.compiler.api.ANY_LANGUAGE_ID
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.core.model.effectiveLanguageId
import com.orchestra.core.model.effectiveTechnologyId
import com.orchestra.core.model.getElementById
import com.orchestra.core.model.projectName
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.validation.DocumentValidator
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import java.io.StringWriter
import java.nio.file.Path

object CompilerTemplateRoles {
    const val NodeDeclaration = "node.declaration"
    const val NodeInstantiation = "node.instantiation"
    const val ProcessorDeclaration = "processor.declaration"
    const val ProcessorInstantiation = "processor.instantiation"
    const val LinkDeclaration = "link.declaration"
    const val LinkInstantiation = "link.instantiation"
    const val GroupDeclaration = "group.declaration"
    const val GroupInstantiation = "group.instantiation"
    const val NoteDeclaration = "note.declaration"
    const val NoteInstantiation = "note.instantiation"
    const val CompositeDeclaration = "composite.declaration"
    const val CompositeInstantiation = "composite.instantiation"
    const val CompositeSingleFile = "composite.single-file"
    const val CompositeDirectFileSystem = "composite.direct-file-system"
    const val CompositeClassifiedFileSystem = "composite.classified-file-system"
    const val CompositeSourceSet = "composite.source-set"
    const val CompositeFileBased = "composite.file-based"
    const val ChildImport = "child.import"
    const val RuntimeSupport = "runtime.support"
    const val PrimaryFilePath = "primary-file.path"
    const val StaticFilePath = "static-file.path"
    const val ProjectName = "project.name"

    fun stereotypeDeclaration(stereotype: NodeStereotype): String =
        "${stereotype.name.toTemplateToken()}.declaration"

    fun stereotypeInstantiation(stereotype: NodeStereotype): String =
        "${stereotype.name.toTemplateToken()}.instantiation"

    fun compositeFor(layoutStrategy: LayoutStrategy): String =
        when (layoutStrategy.id) {
            SingleFileLayoutStrategy.id -> CompositeSingleFile
            DirectFileSystemHomorphismLayoutStrategy.id -> CompositeDirectFileSystem
            ClassifiedFilesystemLayoutStrategy.id -> CompositeClassifiedFileSystem
            SourceSetLayoutStrategy.id -> CompositeSourceSet
            else -> CompositeFileBased
        }

    val overrideNodeNames: Map<String, String> = linkedMapOf(
        "@NodeDeclaration" to NodeDeclaration,
        "@NodeInstantiation" to NodeInstantiation,
        "@ProcessorDeclaration" to ProcessorDeclaration,
        "@ProcessorInstantiation" to ProcessorInstantiation,
        "@LinkDeclaration" to LinkDeclaration,
        "@LinkInstantiation" to LinkInstantiation,
        "@GroupDeclaration" to GroupDeclaration,
        "@GroupInstantiation" to GroupInstantiation,
        "@NoteDeclaration" to NoteDeclaration,
        "@NoteInstantiation" to NoteInstantiation,
        "@CompositeDeclaration" to CompositeDeclaration,
        "@CompositeInstantiation" to CompositeInstantiation,
        "@CompositeSingleFile" to CompositeSingleFile,
        "@CompositeDirectFileSystem" to CompositeDirectFileSystem,
        "@CompositeClassifiedFileSystem" to CompositeClassifiedFileSystem,
        "@CompositeSourceSet" to CompositeSourceSet,
        "@CompositeFileBased" to CompositeFileBased,
        "@ChildImport" to ChildImport,
        "@RuntimeSupport" to RuntimeSupport,
        "@PrimaryFilePath" to PrimaryFilePath,
        "@StaticFilePath" to StaticFilePath,
        "@ProjectName" to ProjectName,
    )

    val explicitOverrideNames: Map<String, String> = buildMap {
        overrideNodeNames.forEach { (name, role) ->
            put(name.removePrefix("@").normalizedTemplateName(), role)
        }
        NodeStereotype.entries.forEach { stereotype ->
            val normalizedName = stereotype.name.normalizedTemplateName()
            put("${normalizedName}declaration", stereotypeDeclaration(stereotype))
            put("${normalizedName}instantiation", stereotypeInstantiation(stereotype))
        }
    }

    val all: Set<String> = buildSet {
        addAll(overrideNodeNames.values)
        NodeStereotype.entries.forEach { stereotype ->
            add(stereotypeDeclaration(stereotype))
            add(stereotypeInstantiation(stereotype))
        }
    }

    val suggestedOverrideNodeNames: List<String> = buildList {
        addAll(overrideNodeNames.keys)
        NodeStereotype.entries.forEach { stereotype ->
            add("@${stereotype.name}Declaration")
            add("@${stereotype.name}Instantiation")
        }
    }.distinct().sorted()
}

data class TemplateGeneratedFile(
    val pathTemplate: String,
    val contentTemplate: String,
    val reason: String = "Generated from compiler project-file template",
    val layoutStrategyIds: Set<String> = emptySet(),
    val elementKind: GeneratedElementKind = GeneratedElementKind.ProjectLayout,
)

data class CompilerTemplateSet(
    val templates: Map<String, String>,
    val projectFiles: List<TemplateGeneratedFile> = emptyList(),
    val staticFileNames: Set<String> = emptySet(),
    val fileExtension: String = "",
    val defaultLayoutStrategy: LayoutStrategy = ClassifiedFilesystemLayoutStrategy,
    val emitLinkFiles: Boolean = true,
    val skipCompilerTemplates: Boolean = false,
) {
    fun template(vararg roles: String): String? =
        roles.firstNotNullOfOrNull { role -> templates[role] }

    fun overlay(overrides: CompilerTemplateSet): CompilerTemplateSet =
        copy(
            templates = templates + overrides.templates,
            projectFiles = projectFiles + overrides.projectFiles,
            staticFileNames = staticFileNames + overrides.staticFileNames,
            fileExtension = overrides.fileExtension.ifBlank { fileExtension },
            defaultLayoutStrategy = overrides.defaultLayoutStrategy,
            emitLinkFiles = overrides.emitLinkFiles,
            skipCompilerTemplates = overrides.skipCompilerTemplates,
        )
}

class CompilerTemplateRenderer {
    private val engine = PebbleEngine.Builder()
        .loader(StringLoader())
        .strictVariables(false)
        .newLineTrimming(false)
        .autoEscaping(false)
        .cacheActive(true)
        .build()

    fun render(template: String, context: Map<String, Any?>): String {
        if (template.isBlank()) return ""
        val writer = StringWriter()
        engine.getTemplate(template.normalizeLegacyPlaceholders()).evaluate(writer, context)
        return writer.toString()
    }
}

/**
 * Batteries-included compiler whose language-specific behavior is supplied entirely by templates.
 */
abstract class TemplateSetCompiler : StructuredCompiler() {
    private val renderer = CompilerTemplateRenderer()
    private val activeTemplateSet = ThreadLocal<CompilerTemplateSet?>()

    final override val supportedLayoutStrategyIds: Set<String> = setOf(
        SingleFileLayoutStrategy.id,
        DirectFileSystemHomorphismLayoutStrategy.id,
        ClassifiedFilesystemLayoutStrategy.id,
        SourceSetLayoutStrategy.id,
    )

    protected abstract fun templatesFor(document: InflowDocument, options: CompilerOptions): CompilerTemplateSet

    protected open val templateDefaultLayoutStrategy: LayoutStrategy =
        ClassifiedFilesystemLayoutStrategy

    protected open fun beforeTemplateCompile(document: InflowDocument, options: CompilerOptions) = Unit

    protected open fun afterTemplateCompile(document: InflowDocument, options: CompilerOptions) = Unit

    final override fun beforeCompile(document: InflowDocument, options: CompilerOptions) {
        activeTemplateSet.set(templatesFor(document, options))
        beforeTemplateCompile(document, options)
    }

    final override fun afterCompile(document: InflowDocument, options: CompilerOptions) {
        try {
            afterTemplateCompile(document, options)
        } finally {
            activeTemplateSet.remove()
        }
    }

    final override fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        activeTemplateSet.get()?.defaultLayoutStrategy ?: templateDefaultLayoutStrategy

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        templatesFor(document, options).staticFileNames.toList()

    final override fun projectFiles(
        document: InflowDocument,
        options: CompilerOptions,
        projectName: String,
    ): List<GeneratedFile> {
        val context = projectTemplateContext(document, options, projectName)
        val layoutId = projectLayoutStrategy(document, options).id
        return requireTemplateSet().projectFiles
            .filter { it.layoutStrategyIds.isEmpty() || layoutId in it.layoutStrategyIds }
            .map { file ->
                GeneratedFile(
                    path = renderer.render(file.pathTemplate, context).trim(),
                    content = renderer.render(file.contentTemplate, context).trimEnd(),
                    originNodeId = null,
                    reason = file.reason,
                    elementKind = file.elementKind,
                )
            }
            .filter { it.path.isNotBlank() }
    }

    final override fun normalizedProjectName(document: InflowDocument, options: CompilerOptions): String {
        val rawName = options.projectName?.takeIf { it.isNotBlank() } ?: document.projectName()
        val template = requireTemplateSet().template(CompilerTemplateRoles.ProjectName) ?: return rawName
        return renderer.render(
            template,
            mapOf(
                "projectName" to rawName,
                "safeProjectName" to safePathSegment(rawName),
                "safePackageName" to safePackageName(rawName),
                "safeIdentifier" to safeIdentifier(rawName),
            ),
        ).trim().ifBlank { rawName }
    }

    override fun fileExtension(context: NodeCompilerContext): String =
        requireTemplateSet().fileExtension.ifBlank { super.fileExtension(context) }

    override fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        requireTemplateSet().skipCompilerTemplates &&
            context.node.stereotype(context.document) == NodeStereotype.CompilerTemplate

    override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        val templateSet = requireTemplateSet()
        val node = context.node
        if (node.stereotype(context.document) != NodeStereotype.StaticFile && node.name !in templateSet.staticFileNames) {
            return null
        }
        val explicitPath = node.metadata["path"]?.takeIf { it.isNotBlank() }
            ?: node.metadata["file"]?.takeIf { it.isNotBlank() }
        val pathTemplate = templateSet.template(CompilerTemplateRoles.StaticFilePath)
        val path = pathTemplate?.let {
            render(it, context, mapOf("explicitPath" to explicitPath.orEmpty()))
        }?.trim().orEmpty().ifBlank { explicitPath ?: node.name.removePrefix("@").ifBlank { "static.txt" } }
        return GeneratedFile(
            path = path,
            content = node.text.declaration.ifBlank { node.text.specification },
            originNodeId = node.id,
            reason = "Literal static file",
            elementKind = if (node.stereotype(context.document) == NodeStereotype.StaticFile) {
                GeneratedElementKind.StaticFile
            } else {
                GeneratedElementKind.MagicFile
            },
        )
    }

    override fun declarationFor(context: NodeCompilerContext): String {
        val ownTemplate = templateFor(context, declaration = true)
        val ownDeclaration = ownTemplate?.let { render(it, context) }.orEmpty()
        if (context.node.children.isEmpty() || context.node.isLink) return ownDeclaration

        val assemblyTemplate = requireTemplateSet().template(
            CompilerTemplateRoles.compositeFor(context.effectiveLayoutStrategy),
            CompilerTemplateRoles.CompositeFileBased.takeUnless { context.isSingleFileLayout }.orEmpty(),
        ) ?: return ownDeclaration
        return render(
            assemblyTemplate,
            context,
            mapOf(
                "ownDeclaration" to ownDeclaration,
                "runtimeSupport" to renderRole(CompilerTemplateRoles.RuntimeSupport, context),
                "childImports" to childImports(context),
                "inlineChildDeclarations" to context.inlineChildDeclarations,
                "childDeclarations" to context.childDeclarations,
                "childInstantiations" to context.childInstantiations,
                "linkDeclarations" to context.linkDeclarations,
                "linkInstantiations" to context.linkInstantiations,
            ),
        )
    }

    override fun instantiationFor(context: NodeCompilerContext): String {
        val template = templateFor(context, declaration = false)
            ?: return context.node.text.instantiation
        return render(template, context)
    }

    override fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String {
        val template = requireTemplateSet().template(CompilerTemplateRoles.ChildImport) ?: return ""
        return render(template, context, mapOf("child" to artifactView(context, child)))
    }

    override fun primaryFileFor(context: NodeCompilerContext, declaration: String): GeneratedFile? {
        if (declaration.isBlank() || (context.node.isLink && !requireTemplateSet().emitLinkFiles)) return null
        val file = GeneratedFile(
            path = context.primaryPath(),
            content = declaration.trimEnd(),
            originNodeId = context.node.id,
            reason = "Generated from compiler template '${templateFor(context, declaration = true)?.take(40).orEmpty()}'",
            elementKind = when {
                context.node.isLink -> GeneratedElementKind.Link
                context.node.children.isNotEmpty() -> GeneratedElementKind.CompositeEntity
                else -> GeneratedElementKind.TerminalEntity
            },
        )
        val pathTemplate = requireTemplateSet().template(
            "${CompilerTemplateRoles.PrimaryFilePath}.${context.effectiveLayoutStrategy.id}",
            CompilerTemplateRoles.PrimaryFilePath,
        ) ?: return file
        val path = render(pathTemplate, context, mapOf("defaultPath" to file.path)).trim()
        return if (path.isBlank()) file else file.copy(path = path)
    }

    protected fun renderRole(role: String, context: NodeCompilerContext): String =
        requireTemplateSet().template(role)?.let { render(it, context) }.orEmpty()

    protected fun render(
        template: String,
        context: NodeCompilerContext,
        additions: Map<String, Any?> = emptyMap(),
    ): String =
        renderer.render(template, nodeTemplateContext(context) + additions)

    private fun templateFor(context: NodeCompilerContext, declaration: Boolean): String? {
        val stereotype = stereotypeForTemplateContext(context.document, context.node)
        val suffix = if (declaration) "declaration" else "instantiation"
        val kindRole = when (context.node.kind) {
            NodeKind.Node -> if (declaration) CompilerTemplateRoles.NodeDeclaration else CompilerTemplateRoles.NodeInstantiation
            NodeKind.Processor -> if (declaration) CompilerTemplateRoles.ProcessorDeclaration else CompilerTemplateRoles.ProcessorInstantiation
            NodeKind.Link -> if (declaration) CompilerTemplateRoles.LinkDeclaration else CompilerTemplateRoles.LinkInstantiation
            NodeKind.Group -> if (declaration) CompilerTemplateRoles.GroupDeclaration else CompilerTemplateRoles.GroupInstantiation
            NodeKind.Note -> if (declaration) CompilerTemplateRoles.NoteDeclaration else CompilerTemplateRoles.NoteInstantiation
        }
        val compositeRole = if (context.node.children.isNotEmpty() && !context.node.isLink) {
            if (declaration) CompilerTemplateRoles.CompositeDeclaration else CompilerTemplateRoles.CompositeInstantiation
        } else {
            null
        }
        val layoutToken = context.effectiveLayoutStrategy.id
        return requireTemplateSet().template(
            "${stereotype.name.toTemplateToken()}.$suffix.$layoutToken",
            "${stereotype.name.toTemplateToken()}.$suffix",
            compositeRole?.let { "$it.$layoutToken" }.orEmpty(),
            compositeRole.orEmpty(),
            "$kindRole.$layoutToken",
            kindRole,
            if (declaration) CompilerTemplateRoles.NodeDeclaration else CompilerTemplateRoles.NodeInstantiation,
        )
    }

    private fun childImports(context: NodeCompilerContext): String {
        val artifacts = if (context.isSingleFileLayout) context.externalChildArtifacts else context.childArtifacts
        return artifacts.joinToString("\n") { child -> importForChild(context, child) }.trim()
    }

    private fun nodeTemplateContext(context: NodeCompilerContext): Map<String, Any?> {
        val node = context.node
        val document = context.document
        val technology = effectiveTechnology(document, node)
        val incoming = linkDescriptors(document, node.incomingLinks)
        val outgoing = linkDescriptors(document, node.outgoingLinks)
        val dependencies = incoming.filter { it["stereotype"] == LinkStereotype.DependencyInjection.name }
        val nodeView = nodeView(document, node)
        val symbol = safeIdentifier(node.name, preserveCase = true)
        val kotlinSymbol = indexedNodeSymbol(document, node)
        val isCompilationRoot = node.id == document.rootNodeId || node.id in context.options.scopeNodeIds
        return linkedMapOf(
            "id" to node.id.value,
            "name" to node.name,
            "document" to mapOf("id" to document.id, "name" to document.projectName(), "rootNodeId" to document.rootNodeId.value),
            "options" to mapOf("projectName" to context.projectName),
            "node" to nodeView,
            "self" to nodeView,
            "metadata" to node.metadata,
            "text" to textView(node),
            "technology" to technologyView(technology),
            "layout" to layoutView(node),
            "children" to node.children.mapNotNull(document::getElementById).map { nodeView(document, it) },
            "parent" to node.parentId?.let(document::getElementById)?.let { nodeView(document, it) },
            "childArtifacts" to context.childArtifacts.map { artifactView(context, it) },
            "linkArtifacts" to context.linkArtifacts.map { artifactView(context, it) },
            "inlineChildArtifacts" to context.inlineChildArtifacts.map { artifactView(context, it) },
            "externalChildArtifacts" to context.externalChildArtifacts.map { artifactView(context, it) },
            "incomingLinks" to incoming,
            "outgoingLinks" to outgoing,
            "dependencyInjectionLinks" to dependencies,
            "ports" to node.ports.map { port ->
                mapOf("id" to port.id, "name" to port.name, "direction" to port.direction.name, "dataType" to port.dataType, "metadata" to port.metadata)
            },
            "language" to technology.languageId,
            "stereotype" to stereotypeForTemplateContext(document, node).name,
            "declaration" to node.text.declaration,
            "instantiation" to node.text.instantiation,
            "specification" to node.text.specification,
            "tests" to node.text.tests,
            "usageInstructions" to node.text.aiInstructions,
            "incomingLinkVariables" to incoming.joinToString(",") { it["variableName"].toString() },
            "outgoingLinkVariables" to outgoing.joinToString(",") { it["variableName"].toString() },
            "incomingLinkTypes" to incoming.joinToString(",") { it["typeName"].toString() },
            "outgoingLinkTypes" to outgoing.joinToString(",") { it["typeName"].toString() },
            "incomingArguments" to incoming.joinToString(", ") { it["argument"].toString() },
            "outgoingArguments" to outgoing.joinToString(", ") { it["argument"].toString() },
            "incomingTypeDefinitions" to incoming.joinToString("\n\n") { it["typeDefinition"].toString() },
            "outgoingTypeDefinitions" to outgoing.joinToString("\n\n") { it["typeDefinition"].toString() },
            "dependencyInjectionArguments" to dependencies.joinToString(", ") { it["argument"].toString() },
            "childDeclarations" to context.childDeclarations,
            "inlineChildDeclarations" to context.inlineChildDeclarations,
            "inlineChildDeclarationsWithoutPhpTag" to context.inlineChildDeclarations.replace("<?php", "").trim(),
            "childInstantiations" to context.childInstantiations,
            "linkDeclarations" to context.linkDeclarations,
            "linkInstantiations" to context.linkInstantiations,
            "primaryPath" to context.primaryPath(),
            "layoutStrategy" to mapOf("id" to context.effectiveLayoutStrategy.id, "displayName" to context.effectiveLayoutStrategy.displayName),
            "symbol" to symbol,
            "safeName" to symbol,
            "runSymbol" to "run_$kotlinSymbol",
            "initializerSymbol" to "init_$kotlinSymbol",
            "classFileSymbol" to kotlinSymbol.removePrefix("_").replaceFirstChar { it.uppercase() },
            "isCompilationRoot" to isCompilationRoot,
            "declarationIndent2" to node.text.declaration.indentNonBlank("  "),
            "declarationIndent4" to node.text.declaration.indentNonBlank("    "),
            "instantiationIndent2" to node.text.instantiation.indentNonBlank("  "),
            "instantiationIndent4" to node.text.instantiation.indentNonBlank("    "),
            "safeProjectName" to safePathSegment(context.projectName),
            "safePackageName" to safePackageName(context.projectName),
            "projectIdentifier" to safeIdentifier(context.projectName),
        ) + linkContext(document, node)
    }

    private fun projectTemplateContext(
        document: InflowDocument,
        options: CompilerOptions,
        projectName: String,
    ): Map<String, Any?> {
        val root = document.getElementById(document.rootNodeId)
        val layoutStrategy = projectLayoutStrategy(document, options)
        val scopeRoots = executableScopeRoots(document, compileScopeIds(document, options.scopeNodeIds))
        val singleFileSourceName = options.scopeNodeIds.singleOrNull()
            ?.let(document::getElementById)
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: projectName
        val singleFileBaseName = safeIdentifier(singleFileSourceName)
        return mapOf(
            "document" to mapOf(
                "id" to document.id,
                "name" to document.projectName(),
                "rootNodeId" to document.rootNodeId.value,
                "nodes" to document.nodes.values.map { nodeView(document, it) },
            ),
            "root" to root?.let { nodeView(document, it) },
            "options" to mapOf("projectName" to projectName),
            "projectName" to projectName,
            "safeProjectName" to safePathSegment(projectName),
            "safePackageName" to safePackageName(projectName),
            "projectIdentifier" to safeIdentifier(projectName),
            "layoutStrategy" to mapOf("id" to layoutStrategy.id, "displayName" to layoutStrategy.displayName),
            "scopeRoots" to scopeRoots.map { nodeView(document, it) },
            "singleFileBaseName" to singleFileBaseName,
            "singleFileClassName" to singleFileBaseName.replaceFirstChar { it.uppercase() } + "Kt",
            "mainClassName" to if (layoutStrategy.id == SingleFileLayoutStrategy.id) {
                "generated.${singleFileBaseName.replaceFirstChar { it.uppercase() }}Kt"
            } else {
                "generated.MainKt"
            },
        )
    }

    private fun projectLayoutStrategy(document: InflowDocument, options: CompilerOptions): LayoutStrategy {
        val scopeNodeId = options.scopeNodeIds.singleOrNull() ?: document.rootNodeId
        return layoutStrategy(document, scopeNodeId, options)
    }

    private fun requireTemplateSet(): CompilerTemplateSet =
        checkNotNull(activeTemplateSet.get()) { "Compiler template set is only available during compilation." }
}

open class StringTemplateCompiler(
    override val id: String,
    override val displayName: String,
    private val templateSet: CompilerTemplateSet,
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID),
    override val supportedTechnologyIds: Set<String> = setOf("generic"),
    override val providedTechnologies: List<CompilerTechnology> =
        supportedLanguageIds.flatMap { language -> supportedTechnologyIds.map { CompilerTechnology(language, it) } },
) : TemplateSetCompiler() {
    override val magicFileNames: Set<String> = templateSet.staticFileNames
    override val templateDefaultLayoutStrategy: LayoutStrategy = templateSet.defaultLayoutStrategy

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun templatesFor(document: InflowDocument, options: CompilerOptions): CompilerTemplateSet =
        templateSet
}

private fun nodeView(document: InflowDocument, node: Node): Map<String, Any?> = linkedMapOf(
    "id" to node.id.value,
    "name" to node.name,
    "symbol" to safeIdentifier(node.name, preserveCase = true),
    "runSymbol" to "run_${indexedNodeSymbol(document, node)}",
    "initializerSymbol" to "init_${indexedNodeSymbol(document, node)}",
    "classFileSymbol" to indexedNodeSymbol(document, node).removePrefix("_").replaceFirstChar { it.uppercase() },
    "kind" to mapOf("name" to node.kind.name, "ordinal" to node.kind.ordinal),
    "parentId" to node.parentId?.value,
    "stereotype" to stereotypeForTemplateContext(document, node).name,
    "children" to node.children.map { it.value },
    "incomingLinks" to node.incomingLinks.map { it.value },
    "outgoingLinks" to node.outgoingLinks.map { it.value },
    "ports" to node.ports.map { port ->
        mapOf(
            "id" to port.id,
            "name" to port.name,
            "direction" to port.direction.name,
            "dataType" to port.dataType,
            "metadata" to port.metadata,
        )
    },
    "isComposite" to node.isComposite,
    "isLink" to node.isLink,
    "isTerminal" to node.isTerminal,
    "metadata" to node.metadata,
    "pluginData" to node.pluginData,
    "text" to textView(node),
    "technology" to technologyView(effectiveTechnology(document, node)),
    "layout" to layoutView(node),
    "link" to node.link?.let { link ->
        mapOf(
            "sourceNodeId" to link.sourceNodeId.value,
            "targetNodeId" to link.targetNodeId.value,
            "sourcePortName" to link.sourcePortName,
            "targetPortName" to link.targetPortName,
            "transportKind" to link.transportKind,
            "typeName" to link.typeName,
            "payloadDefinition" to link.payloadDefinition,
        )
    },
    "metadataComment" to "name=${node.name}\nkind=${node.kind.name}\nstereotype=${stereotypeForTemplateContext(document, node)}",
    "declarationBlockComment" to node.text.declaration.ifBlank { node.text.specification }.toBlockComment(),
)

private fun textView(node: Node): Map<String, Any?> = mapOf(
    "declaration" to node.text.declaration,
    "declarationLanguageId" to node.text.declarationLanguageId,
    "instantiation" to node.text.instantiation,
    "instantiationLanguageId" to node.text.instantiationLanguageId,
    "specification" to node.text.specification,
    "specificationLanguageId" to node.text.specificationLanguageId,
    "tests" to node.text.tests,
    "testsLanguageId" to node.text.testsLanguageId,
    "usageInstructions" to node.text.aiInstructions,
    "usageInstructionsLanguageId" to node.text.aiInstructionsLanguageId,
)

private fun technologyView(technology: TechnologyMetadata): Map<String, Any?> = mapOf(
    "languageId" to technology.languageId,
    "technologyId" to technology.technologyId,
    "compilerId" to technology.compilerId,
    "fileExtension" to technology.fileExtension,
    "contentType" to technology.contentType,
)

private fun layoutView(node: Node): Map<String, Any?> = mapOf(
    "x" to node.layout.x,
    "y" to node.layout.y,
    "width" to node.layout.width,
    "height" to node.layout.height,
)

private fun artifactView(context: NodeCompilerContext, artifact: CompiledNodeArtifact): Map<String, Any?> = mapOf(
    "node" to nodeView(context.document, artifact.node),
    "declaration" to artifact.declarationText,
    "instantiation" to artifact.instantiationText,
    "path" to artifact.primaryFile?.path.orEmpty(),
    "layoutStrategyId" to artifact.layoutStrategy.id,
    "symbol" to safeIdentifier(artifact.node.name, preserveCase = true),
    "runSymbol" to "run_${indexedNodeSymbol(context.document, artifact.node)}",
    "initializerSymbol" to "init_${indexedNodeSymbol(context.document, artifact.node)}",
    "relativePath" to artifact.primaryFile?.let { relativePath(context.primaryPath(), it.path) }.orEmpty(),
    "relativeModulePath" to artifact.primaryFile?.let { relativeModulePath(context.primaryPath(), it.path, context.extension) }.orEmpty(),
    "isInline" to (artifact.layoutStrategy.id == SingleFileLayoutStrategy.id),
)

private fun linkDescriptors(document: InflowDocument, ids: Iterable<com.orchestra.core.model.NodeId>): List<Map<String, Any?>> =
    ids.mapNotNull { id ->
        val node = document.getElementById(id) ?: return@mapNotNull null
        val link = node.link ?: return@mapNotNull null
        val typeName = link.typeName
        mapOf(
            "id" to node.id.value,
            "name" to node.name,
            "variableName" to node.name,
            "typeName" to typeName,
            "typeDefinition" to link.payloadDefinition,
            "payloadDefinition" to link.payloadDefinition,
            "argument" to if (typeName.isBlank()) node.name else "${node.name}:$typeName",
            "stereotype" to LinkClassifier.classify(document, node).name,
            "sourceNodeId" to link.sourceNodeId.value,
            "targetNodeId" to link.targetNodeId.value,
            "sourcePortName" to link.sourcePortName,
            "targetPortName" to link.targetPortName,
        )
    }

private fun linkContext(document: InflowDocument, node: Node): Map<String, Any?> {
    val link = node.link ?: return emptyMap()
    val sourceNode = document.getElementById(link.sourceNodeId)
    val targetNode = document.getElementById(link.targetNodeId)
    val sourceName = sourceNode?.name ?: link.sourceNodeId.value
    val targetName = targetNode?.name ?: link.targetNodeId.value
    val sourceReference = "${sanitizeReference(sourceName)}.${sanitizeReference(link.sourcePortName)}"
    val targetReference = "${sanitizeReference(targetName)}.${sanitizeReference(link.targetPortName)}"
    return mapOf(
        "link" to mapOf(
            "id" to node.id.value,
            "name" to node.name,
            "variableName" to node.name,
            "typeName" to link.typeName,
            "typeDefinition" to link.payloadDefinition,
            "sourceNodeId" to link.sourceNodeId.value,
            "targetNodeId" to link.targetNodeId.value,
            "sourcePortName" to link.sourcePortName,
            "targetPortName" to link.targetPortName,
            "transportKind" to link.transportKind,
            "sourceReference" to sourceReference,
            "targetReference" to targetReference,
            "escapedNameDoubleQuoted" to node.name.escapeDoubleQuoted(),
            "escapedNameSingleQuoted" to node.name.escapeSingleQuoted(),
            "escapedSourceReferenceDoubleQuoted" to sourceReference.escapeDoubleQuoted(),
            "escapedTargetReferenceDoubleQuoted" to targetReference.escapeDoubleQuoted(),
            "escapedSourceReferenceSingleQuoted" to sourceReference.escapeSingleQuoted(),
            "escapedTargetReferenceSingleQuoted" to targetReference.escapeSingleQuoted(),
        ),
        "sourceNode" to sourceNode?.let { nodeView(document, it) },
        "targetNode" to targetNode?.let { nodeView(document, it) },
    )
}

private fun compileScopeIds(document: InflowDocument, requested: Set<com.orchestra.core.model.NodeId>): Set<com.orchestra.core.model.NodeId> {
    if (requested.isEmpty()) return document.nodes.keys
    val result = linkedSetOf<com.orchestra.core.model.NodeId>()
    fun include(id: com.orchestra.core.model.NodeId) {
        val node = document.getElementById(id) ?: return
        if (result.add(id) && !node.isLink) node.children.forEach(::include)
    }
    requested.forEach(::include)
    return result
}

private fun executableScopeRoots(
    document: InflowDocument,
    scopeIds: Set<com.orchestra.core.model.NodeId>,
): List<Node> {
    if (document.rootNodeId in scopeIds) return listOfNotNull(document.getElementById(document.rootNodeId))
    return scopeIds.mapNotNull(document::getElementById)
        .filterNot { it.isLink }
        .filter { it.parentId !in scopeIds }
        .ifEmpty { listOfNotNull(document.getElementById(document.rootNodeId)) }
}

private fun indexedNodeSymbol(document: InflowDocument, node: Node): String {
    val nodes = document.nodes.values.filterNot { it.isLink }.sortedBy { it.id.value }
    val index = nodes.indexOfFirst { it.id == node.id }.takeIf { it >= 0 }?.plus(1) ?: 1
    return "${safeIdentifier(node.name)}_$index"
}

private fun relativePath(from: String, to: String): String {
    val fromParent = Path.of(from).parent ?: Path.of("")
    return fromParent.relativize(Path.of(to)).toString().replace('\\', '/')
}

private fun relativeModulePath(from: String, to: String, extension: String): String {
    val relative = relativePath(from, to).removeSuffix(".${extension.trimStart('.')}")
    return if (relative.startsWith('.')) relative else "./$relative"
}

private fun safeIdentifier(value: String, preserveCase: Boolean = false): String {
    val sanitized = value.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
    val fallback = sanitized.ifBlank { "node" }.let { if (preserveCase) it else it.lowercase() }
    return if (fallback.first().isDigit()) "_$fallback" else fallback
}

private fun safePathSegment(value: String): String =
    value.trim().replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_').ifBlank { "project" }

private fun safePackageName(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9_.-]+"), "-").trim('-').ifBlank { "project" }

private fun sanitizeReference(value: String): String = value.trim().replace("\"", "\\\"")

private fun String.escapeDoubleQuoted(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private fun String.escapeSingleQuoted(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun String.indentNonBlank(prefix: String): String =
    trimEnd().lines().filter { it.isNotBlank() }.joinToString("\n") { "$prefix$it" }

private fun String.toBlockComment(): String =
    lines().joinToString(prefix = "/**\n", postfix = "\n */") { " * $it" }

private fun effectiveTechnology(document: InflowDocument, node: Node): TechnologyMetadata =
    node.technology.copy(
        languageId = document.effectiveLanguageId(node.id),
        technologyId = document.effectiveTechnologyId(node.id),
    )

internal fun stereotypeForTemplateContext(document: InflowDocument, node: Node): NodeStereotype =
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

internal fun String.toTemplateToken(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

private fun String.normalizedTemplateName(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "")

private fun String.normalizeLegacyPlaceholders(): String =
    replace(Regex("\\$\\{([A-Za-z0-9_.]+)}")) { match -> "{{ ${match.groupValues[1]} }}" }
