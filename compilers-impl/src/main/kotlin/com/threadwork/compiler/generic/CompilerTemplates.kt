package com.threadwork.compiler.generic

import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.CompiledNodeArtifact
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTypeInformation
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.LayoutStrategy
import com.threadwork.compiler.api.NodeCompilerContext
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.api.SourceSetLayoutStrategy
import com.threadwork.compiler.api.StructuredCompiler
import com.threadwork.compiler.api.defaultTypeInformation
import com.threadwork.compiler.api.ANY_LANGUAGE_ID
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.CompilerCodeSymbol
import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.projectName
import com.threadwork.core.model.linkTypeDisplayName
import com.threadwork.core.model.typeDisplayName
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.validation.DocumentValidator
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import java.io.StringWriter
import java.nio.file.Path

object CompilerTemplateRoles {
    const val NodeHoistedDeclaration = "node.hoisted-declaration"
    const val NodeForwardDeclaration = "node.forward-declaration"
    const val NodeDeclaration = "node.declaration"
    const val NodeInstantiation = "node.instantiation"
    const val ProcessorHoistedDeclaration = "processor.hoisted-declaration"
    const val ProcessorForwardDeclaration = "processor.forward-declaration"
    const val ProcessorDeclaration = "processor.declaration"
    const val ProcessorInstantiation = "processor.instantiation"
    const val LinkHoistedDeclaration = "link.hoisted-declaration"
    const val LinkForwardDeclaration = "link.forward-declaration"
    const val LinkDeclaration = "link.declaration"
    const val LinkInstantiation = "link.instantiation"
    const val GroupHoistedDeclaration = "group.hoisted-declaration"
    const val GroupForwardDeclaration = "group.forward-declaration"
    const val GroupDeclaration = "group.declaration"
    const val GroupInstantiation = "group.instantiation"
    const val TypeHoistedDeclaration = "type.hoisted-declaration"
    const val TypeForwardDeclaration = "type.forward-declaration"
    const val TypeDeclaration = "type.declaration"
    const val TypeInstantiation = "type.instantiation"
    const val NoteHoistedDeclaration = "note.hoisted-declaration"
    const val NoteForwardDeclaration = "note.forward-declaration"
    const val NoteDeclaration = "note.declaration"
    const val NoteInstantiation = "note.instantiation"
    const val CompositeHoistedDeclaration = "composite.hoisted-declaration"
    const val CompositeForwardDeclaration = "composite.forward-declaration"
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

    fun stereotypeHoistedDeclaration(stereotype: NodeStereotype): String =
        "${stereotype.name.toTemplateToken()}.hoisted-declaration"

    fun stereotypeForwardDeclaration(stereotype: NodeStereotype): String =
        "${stereotype.name.toTemplateToken()}.forward-declaration"

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
        "@NodeHoistedDeclaration" to NodeHoistedDeclaration,
        "@NodeForwardDeclaration" to NodeForwardDeclaration,
        "@NodeDeclaration" to NodeDeclaration,
        "@NodeInstantiation" to NodeInstantiation,
        "@ProcessorHoistedDeclaration" to ProcessorHoistedDeclaration,
        "@ProcessorForwardDeclaration" to ProcessorForwardDeclaration,
        "@ProcessorDeclaration" to ProcessorDeclaration,
        "@ProcessorInstantiation" to ProcessorInstantiation,
        "@LinkHoistedDeclaration" to LinkHoistedDeclaration,
        "@LinkForwardDeclaration" to LinkForwardDeclaration,
        "@LinkDeclaration" to LinkDeclaration,
        "@LinkInstantiation" to LinkInstantiation,
        "@GroupHoistedDeclaration" to GroupHoistedDeclaration,
        "@GroupForwardDeclaration" to GroupForwardDeclaration,
        "@GroupDeclaration" to GroupDeclaration,
        "@GroupInstantiation" to GroupInstantiation,
        "@TypeHoistedDeclaration" to TypeHoistedDeclaration,
        "@TypeForwardDeclaration" to TypeForwardDeclaration,
        "@TypeDeclaration" to TypeDeclaration,
        "@TypeInstantiation" to TypeInstantiation,
        "@NoteHoistedDeclaration" to NoteHoistedDeclaration,
        "@NoteForwardDeclaration" to NoteForwardDeclaration,
        "@NoteDeclaration" to NoteDeclaration,
        "@NoteInstantiation" to NoteInstantiation,
        "@CompositeHoistedDeclaration" to CompositeHoistedDeclaration,
        "@CompositeForwardDeclaration" to CompositeForwardDeclaration,
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
            put("${normalizedName}hoisteddeclaration", stereotypeHoistedDeclaration(stereotype))
            put("${normalizedName}forwarddeclaration", stereotypeForwardDeclaration(stereotype))
            put("${normalizedName}declaration", stereotypeDeclaration(stereotype))
            put("${normalizedName}instantiation", stereotypeInstantiation(stereotype))
        }
    }

    val all: Set<String> = buildSet {
        addAll(overrideNodeNames.values)
        NodeStereotype.entries.forEach { stereotype ->
            add(stereotypeHoistedDeclaration(stereotype))
            add(stereotypeForwardDeclaration(stereotype))
            add(stereotypeDeclaration(stereotype))
            add(stereotypeInstantiation(stereotype))
        }
    }

    val suggestedOverrideNodeNames: List<String> = buildList {
        addAll(overrideNodeNames.keys)
        NodeStereotype.entries.forEach { stereotype ->
            add("@${stereotype.name}HoistedDeclaration")
            add("@${stereotype.name}ForwardDeclaration")
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

    override val supportedLayoutStrategyIds: Set<String> = setOf(
        SingleFileLayoutStrategy.id,
        DirectFileSystemHomorphismLayoutStrategy.id,
        ClassifiedFilesystemLayoutStrategy.id,
        SourceSetLayoutStrategy.id,
    )

    protected abstract fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet

    protected open val templateDefaultLayoutStrategy: LayoutStrategy =
        ClassifiedFilesystemLayoutStrategy

    protected open fun beforeTemplateCompile(document: ThreadworkDocument, options: CompilerOptions) = Unit

    protected open fun afterTemplateCompile(document: ThreadworkDocument, options: CompilerOptions) = Unit

    final override fun beforeCompile(document: ThreadworkDocument, options: CompilerOptions) {
        activeTemplateSet.set(templatesFor(document, options))
        beforeTemplateCompile(document, options)
    }

    final override fun afterCompile(document: ThreadworkDocument, options: CompilerOptions) {
        try {
            afterTemplateCompile(document, options)
        } finally {
            activeTemplateSet.remove()
        }
    }

    final override fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        activeTemplateSet.get()?.defaultLayoutStrategy ?: templateDefaultLayoutStrategy

    override fun getStaticFiles(document: ThreadworkDocument, options: CompilerOptions): List<String> =
        templatesFor(document, options).staticFileNames.toList()

    override fun generatedEntitySymbols(document: ThreadworkDocument, node: Node): List<CompilerCodeSymbol> {
        if (node.isLink || node.stereotype(document) == NodeStereotype.ServiceLibrary) return emptyList()
        return when (document.effectiveLanguageId(node.id).lowercase()) {
            "kotlin" -> {
                val symbol = indexedNodeSymbol(document, node)
                listOf(
                    generatedFunction("init_$symbol", "generated child initialization function"),
                    generatedFunction("run_$symbol", "generated child execution function"),
                )
            }

            "javascript", "typescript", "php" -> listOf(
                generatedFunction(
                    safeIdentifier(node.name, preserveCase = true),
                    "generated child execution function",
                ),
            )

            else -> emptyList()
        }
    }

    final override fun projectFiles(
        document: ThreadworkDocument,
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

    final override fun normalizedProjectName(document: ThreadworkDocument, options: CompilerOptions): String {
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
                "ownHoistedDeclaration" to hoistedDeclarationFor(context),
                "ownForwardDeclaration" to forwardDeclarationFor(context),
                "childHoistedDeclarations" to context.childHoistedDeclarations,
                "linkHoistedDeclarations" to context.linkHoistedDeclarations,
                "descendantHoistedDeclarations" to context.descendantHoistedDeclarations,
                "hoistedDeclarations" to hoistedDeclarationBlock(context),
                "childForwardDeclarations" to context.childForwardDeclarations,
                "linkForwardDeclarations" to context.linkForwardDeclarations,
                "descendantForwardDeclarations" to context.descendantForwardDeclarations,
                "forwardDeclarations" to forwardDeclarationBlock(context),
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

    override fun forwardDeclarationFor(context: NodeCompilerContext): String =
        forwardDeclarationTemplateFor(context)?.let { render(it, context) }.orEmpty()

    override fun hoistedDeclarationFor(context: NodeCompilerContext): String =
        hoistedDeclarationTemplateFor(context)?.let { render(it, context) }.orEmpty()

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
                context.node.kind == NodeKind.Type -> GeneratedElementKind.Type
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

    override fun typeInformation(
        document: ThreadworkDocument,
        node: Node,
        typeName: String,
    ): CompilerTypeInformation? {
        val modelInfo = defaultTypeInformation(document, node, typeName) ?: return null
        val typeNode = document.nodes.values.firstOrNull {
            it.kind == NodeKind.Type && it.name.equals(typeName.trim(), ignoreCase = true)
        } ?: return modelInfo
        val generatedDeclaration = compile(
            document,
            CompilerOptions(
                projectName = document.projectName(),
                scopeNodeIds = setOf(typeNode.id),
                includeScopeAncestors = false,
                allowCompilerDelegation = false,
            ),
        ).generatedProject?.files
            ?.firstOrNull { it.originNodeId == typeNode.id }
            ?.content
            ?.trim()
            .orEmpty()
        return if (generatedDeclaration.isBlank()) {
            modelInfo
        } else {
            modelInfo.copy(declaration = generatedDeclaration)
        }
    }

    private fun templateFor(context: NodeCompilerContext, declaration: Boolean): String? {
        val stereotype = stereotypeForTemplateContext(context.document, context.node)
        val suffix = if (declaration) "declaration" else "instantiation"
        val kindRole = when (context.node.kind) {
            NodeKind.Node -> if (declaration) CompilerTemplateRoles.NodeDeclaration else CompilerTemplateRoles.NodeInstantiation
            NodeKind.Processor -> if (declaration) CompilerTemplateRoles.ProcessorDeclaration else CompilerTemplateRoles.ProcessorInstantiation
            NodeKind.Link -> if (declaration) CompilerTemplateRoles.LinkDeclaration else CompilerTemplateRoles.LinkInstantiation
            NodeKind.Group -> if (declaration) CompilerTemplateRoles.GroupDeclaration else CompilerTemplateRoles.GroupInstantiation
            NodeKind.Type -> if (declaration) CompilerTemplateRoles.TypeDeclaration else CompilerTemplateRoles.TypeInstantiation
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

    private fun forwardDeclarationTemplateFor(context: NodeCompilerContext): String? {
        val stereotype = stereotypeForTemplateContext(context.document, context.node)
        val kindRole = when (context.node.kind) {
            NodeKind.Node -> CompilerTemplateRoles.NodeForwardDeclaration
            NodeKind.Processor -> CompilerTemplateRoles.ProcessorForwardDeclaration
            NodeKind.Link -> CompilerTemplateRoles.LinkForwardDeclaration
            NodeKind.Group -> CompilerTemplateRoles.GroupForwardDeclaration
            NodeKind.Type -> CompilerTemplateRoles.TypeForwardDeclaration
            NodeKind.Note -> CompilerTemplateRoles.NoteForwardDeclaration
        }
        val compositeRole = CompilerTemplateRoles.CompositeForwardDeclaration
            .takeIf { context.node.children.isNotEmpty() && !context.node.isLink }
        val layoutToken = context.effectiveLayoutStrategy.id
        return requireTemplateSet().template(
            "${stereotype.name.toTemplateToken()}.forward-declaration.$layoutToken",
            CompilerTemplateRoles.stereotypeForwardDeclaration(stereotype),
            compositeRole?.let { "$it.$layoutToken" }.orEmpty(),
            compositeRole.orEmpty(),
            "$kindRole.$layoutToken",
            kindRole,
            CompilerTemplateRoles.NodeForwardDeclaration,
        )
    }

    private fun hoistedDeclarationTemplateFor(context: NodeCompilerContext): String? {
        val stereotype = stereotypeForTemplateContext(context.document, context.node)
        val kindRole = when (context.node.kind) {
            NodeKind.Node -> CompilerTemplateRoles.NodeHoistedDeclaration
            NodeKind.Processor -> CompilerTemplateRoles.ProcessorHoistedDeclaration
            NodeKind.Link -> CompilerTemplateRoles.LinkHoistedDeclaration
            NodeKind.Group -> CompilerTemplateRoles.GroupHoistedDeclaration
            NodeKind.Type -> CompilerTemplateRoles.TypeHoistedDeclaration
            NodeKind.Note -> CompilerTemplateRoles.NoteHoistedDeclaration
        }
        val compositeRole = CompilerTemplateRoles.CompositeHoistedDeclaration
            .takeIf { context.node.children.isNotEmpty() && !context.node.isLink }
        val layoutToken = context.effectiveLayoutStrategy.id
        return requireTemplateSet().template(
            "${stereotype.name.toTemplateToken()}.hoisted-declaration.$layoutToken",
            CompilerTemplateRoles.stereotypeHoistedDeclaration(stereotype),
            compositeRole?.let { "$it.$layoutToken" }.orEmpty(),
            compositeRole.orEmpty(),
            "$kindRole.$layoutToken",
            kindRole,
            CompilerTemplateRoles.NodeHoistedDeclaration,
        )
    }

    private fun forwardDeclarationBlock(context: NodeCompilerContext): String =
        (context.descendantForwardDeclarationLines + forwardDeclarationFor(context).trim())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")

    private fun hoistedDeclarationBlock(context: NodeCompilerContext): String =
        (context.descendantHoistedDeclarationLines + hoistedDeclarationFor(context).trim())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n\n")

    private fun childImports(context: NodeCompilerContext): String {
        val artifacts = if (context.isSingleFileLayout) context.externalChildArtifacts else context.childArtifacts
        return artifacts.joinToString("\n") { child -> importForChild(context, child) }.trim()
    }

    private fun nodeTemplateContext(context: NodeCompilerContext): Map<String, Any?> {
        val node = context.node
        val document = context.document
        val technology = effectiveTechnology(document, node)
        val incoming = linkDescriptors(document, node.incomingLinks, context.compiledArtifacts)
        val outgoing = linkDescriptors(document, node.outgoingLinks, context.compiledArtifacts)
        val capabilities = incoming.filter(::isCapabilityDescriptor)
        val incomingDataLinks = incoming.filterNot(::isCapabilityDescriptor)
        val outgoingDataLinks = outgoing.filterNot(::isCapabilityDescriptor)
        val nodeView = nodeView(document, node)
        val typeFields = typeFieldViews(document, node)
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
            "incomingDataLinks" to incomingDataLinks,
            "outgoingDataLinks" to outgoingDataLinks,
            "capabilityLinks" to capabilities,
            "libraryCapabilityLinks" to capabilities.filter { it["isLibraryCapability"] == true },
            "sourceCapabilityLinks" to capabilities.filter { it["isSourceCapability"] == true },
            "runnableCapabilityLinks" to capabilities.filter { it["isRunnableCapability"] == true },
            "dependencyInjectionLinks" to capabilities,
            "ports" to node.ports.map { port ->
                mapOf("id" to port.id, "name" to port.name, "direction" to port.direction.name, "dataType" to port.dataType, "metadata" to port.metadata)
            },
            "typeFields" to typeFields,
            "declaredTypes" to document.nodes.values.filter { it.kind == NodeKind.Type }.map { nodeView(document, it) },
            "language" to technology.languageId,
            "stereotype" to stereotypeForTemplateContext(document, node).name,
            "declaration" to node.text.declaration,
            "instantiation" to node.text.instantiation,
            "specification" to node.text.specification,
            "tests" to node.text.tests,
            "usageInstructions" to node.text.aiInstructions,
            "incomingLinkVariables" to incomingDataLinks.joinToString(",") { it["variableName"].toString() },
            "outgoingLinkVariables" to outgoingDataLinks.joinToString(",") { it["variableName"].toString() },
            "incomingLinkTypes" to incomingDataLinks.joinToString(",") { it["typeName"].toString() },
            "outgoingLinkTypes" to outgoingDataLinks.joinToString(",") { it["typeName"].toString() },
            "incomingArguments" to incomingDataLinks.joinToString(", ") { it["argument"].toString() },
            "outgoingArguments" to outgoingDataLinks.joinToString(", ") { it["argument"].toString() },
            "incomingTypeDefinitions" to incomingDataLinks.joinToString("\n\n") { it["typeDefinition"].toString() },
            "outgoingTypeDefinitions" to outgoingDataLinks.joinToString("\n\n") { it["typeDefinition"].toString() },
            "dependencyInjectionArguments" to capabilities.joinToString(", ") { it["argument"].toString() },
            "childDeclarations" to context.childDeclarations,
            "inlineChildDeclarations" to context.inlineChildDeclarations,
            "inlineChildDeclarationsWithoutPhpTag" to context.inlineChildDeclarations.replace("<?php", "").trim(),
            "childHoistedDeclarations" to context.childHoistedDeclarations,
            "linkHoistedDeclarations" to context.linkHoistedDeclarations,
            "descendantHoistedDeclarations" to context.descendantHoistedDeclarations,
            "childForwardDeclarations" to context.childForwardDeclarations,
            "linkForwardDeclarations" to context.linkForwardDeclarations,
            "descendantForwardDeclarations" to context.descendantForwardDeclarations,
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
        ) + linkContext(document, node, context.compiledArtifacts)
    }

    private fun projectTemplateContext(
        document: ThreadworkDocument,
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

    private fun projectLayoutStrategy(document: ThreadworkDocument, options: CompilerOptions): LayoutStrategy {
        val scopeNodeId = options.scopeNodeIds.singleOrNull() ?: document.rootNodeId
        return layoutStrategy(document, scopeNodeId, options)
    }

    private fun requireTemplateSet(): CompilerTemplateSet =
        checkNotNull(activeTemplateSet.get()) { "Compiler template set is only available during compilation." }
}

private fun generatedFunction(name: String, detail: String): CompilerCodeSymbol = CompilerCodeSymbol(
    name = name,
    kind = CompilerCodeSymbolKind.GeneratedFunction,
    detail = detail,
    documentation = "Compiler-generated callable for a direct composite child.",
)

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

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet =
    templateSet
}

private fun typeFieldViews(document: ThreadworkDocument, node: Node): List<Map<String, Any?>> =
    node.typeDefinition?.fields.orEmpty().map { field ->
        val typeName = document.typeDisplayName(field.typeId)
        mapOf(
            "name" to field.name,
            "symbol" to safeIdentifier(field.name, preserveCase = true),
            "typeId" to field.typeId,
            "typeName" to typeName,
            "typeSymbol" to document.getElementById(field.typeId)
                ?.let { safeIdentifier(it.name, preserveCase = true) }
                .orEmpty()
                .ifBlank { typeName },
            "isReference" to field.isReference,
        )
    }

private fun nodeView(document: ThreadworkDocument, node: Node): Map<String, Any?> = linkedMapOf(
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
    "typeFields" to typeFieldViews(document, node),
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
            "typeDefinitionId" to link.typeDefinitionId,
            "typeName" to document.linkTypeDisplayName(node),
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

private fun artifactView(context: NodeCompilerContext, artifact: CompiledNodeArtifact): Map<String, Any?> {
    val incoming = linkDescriptors(context.document, artifact.node.incomingLinks, context.compiledArtifacts)
    val outgoing = linkDescriptors(context.document, artifact.node.outgoingLinks, context.compiledArtifacts)
    return mapOf(
        "node" to nodeView(context.document, artifact.node),
        "hoistedDeclaration" to artifact.hoistedDeclarationText,
        "hoistedDeclarations" to artifact.hoistedDeclarations,
        "forwardDeclaration" to artifact.forwardDeclarationText,
        "forwardDeclarations" to artifact.forwardDeclarations,
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
        "incomingDataLinks" to incoming.filterNot(::isCapabilityDescriptor),
        "outgoingDataLinks" to outgoing.filterNot(::isCapabilityDescriptor),
        "capabilityLinks" to incoming.filter(::isCapabilityDescriptor),
        "libraryCapabilityLinks" to incoming.filter { it["isLibraryCapability"] == true },
        "sourceCapabilityLinks" to incoming.filter { it["isSourceCapability"] == true },
        "runnableCapabilityLinks" to incoming.filter { it["isRunnableCapability"] == true },
        "dependencyInjectionLinks" to incoming.filter(::isCapabilityDescriptor),
        "link" to linkContext(context.document, artifact.node, context.compiledArtifacts)["link"],
    )
}

private fun linkDescriptors(
    document: ThreadworkDocument,
    ids: Iterable<com.threadwork.core.model.NodeId>,
    compiledArtifacts: Map<com.threadwork.core.model.NodeId, CompiledNodeArtifact> = emptyMap(),
): List<Map<String, Any?>> =
    ids.mapNotNull { id ->
        val node = document.getElementById(id) ?: return@mapNotNull null
        val link = node.link ?: return@mapNotNull null
        val typeName = document.linkTypeDisplayName(node)
        val declaredType = document.getElementById(link.typeDefinitionId)
        val symbol = safeIdentifier(node.name, preserveCase = true)
        val sourceNode = document.getElementById(link.sourceNodeId)
        val targetNode = document.getElementById(link.targetNodeId)
        val sourceCompilationProduct = compiledArtifacts[link.sourceNodeId]?.compiledProductText.orEmpty()
            .ifBlank { sourceNode?.text?.declaration.orEmpty() }
        val dependencyIndex = dependencyLinkIndex(document, node, sourceNode)
        val allocationIndex = dataLinkIndex(document, node, sourceNode)
        val allocationSymbol = "$symbol${allocationIndex.coerceAtLeast(1)}"
        val dependencySymbol = "${symbol}${dependencyIndex.coerceAtLeast(1)}"
        val capabilityTypeSymbol = dependencySymbol.replaceFirstChar { it.uppercase() } + "Capability"
        val stereotype = LinkClassifier.classify(document, node)
        val sourceCapability = stereotype == LinkStereotype.SourceCapability
        val runnableCapability = stereotype == LinkStereotype.RunnableCapability
        val libraryCapability = stereotype in setOf(LinkStereotype.UsageImport, LinkStereotype.DependencyInjection)
        mapOf(
            "id" to node.id.value,
            "name" to node.name,
            "variableName" to node.name,
            "symbol" to symbol,
            "argumentSymbol" to symbol,
            "allocationSymbol" to allocationSymbol,
            "transportSymbol" to "transport_$allocationSymbol",
            "aPortSymbol" to "${allocationSymbol}_a_port",
            "bPortSymbol" to "${allocationSymbol}_b_port",
            "typeName" to typeName,
            "typeSymbol" to declaredType?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty()
                .ifBlank { typeName },
            "typeDefinitionId" to link.typeDefinitionId,
            "typeFields" to declaredType?.let { typeFieldViews(document, it) }.orEmpty(),
            "typeDefinition" to link.payloadDefinition,
            "payloadDefinition" to link.payloadDefinition,
            "argument" to if (typeName.isBlank()) symbol else "$symbol:$typeName",
            "stereotype" to stereotype.name,
            "interactionKind" to link.interactionKind,
            "isCapability" to (sourceCapability || runnableCapability || libraryCapability),
            "isLibraryCapability" to libraryCapability,
            "isSourceCapability" to sourceCapability,
            "isRunnableCapability" to runnableCapability,
            "capabilityMethod" to when {
                sourceCapability -> "getSource"
                runnableCapability -> "getRunnable"
                else -> ""
            },
            "sourceNodeId" to link.sourceNodeId.value,
            "sourceNodeName" to sourceNode?.name.orEmpty(),
            "sourceNodeSymbol" to sourceNode?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty(),
            "sourceNodeType" to sourceNode?.name.orEmpty(),
            "sourceNodeTypeSymbol" to sourceNode?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty(),
            "sourceNodeInstantiation" to sourceNode?.text?.instantiation.orEmpty(),
            "sourceNodeDeclaration" to sourceCompilationProduct,
            "sourceNodeDeclarationDoubleQuoted" to sourceCompilationProduct.escapeDoubleQuotedLiteral(),
            "sourceNodeDeclarationDollarEscaped" to sourceCompilationProduct.escapeDollarDoubleQuotedLiteral(),
            "sourceRunSymbol" to sourceNode?.let { "run_${indexedNodeSymbol(document, it)}" }.orEmpty(),
            "libraryInstantiation" to sourceNode?.text?.instantiation.orEmpty(),
            "dependencyIndex" to dependencyIndex,
            "dependencySymbol" to dependencySymbol,
            "capabilityTypeSymbol" to capabilityTypeSymbol,
            "targetNodeId" to link.targetNodeId.value,
            "targetNodeName" to targetNode?.name.orEmpty(),
            "targetNodeSymbol" to targetNode?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty(),
            "sourcePortName" to link.sourcePortName,
            "targetPortName" to link.targetPortName,
        )
    }

private fun linkContext(
    document: ThreadworkDocument,
    node: Node,
    compiledArtifacts: Map<com.threadwork.core.model.NodeId, CompiledNodeArtifact> = emptyMap(),
): Map<String, Any?> {
    val link = node.link ?: return emptyMap()
    val sourceNode = document.getElementById(link.sourceNodeId)
    val targetNode = document.getElementById(link.targetNodeId)
    val sourceCompilationProduct = compiledArtifacts[link.sourceNodeId]?.compiledProductText.orEmpty()
        .ifBlank { sourceNode?.text?.declaration.orEmpty() }
    val sourceName = sourceNode?.name ?: link.sourceNodeId.value
    val targetName = targetNode?.name ?: link.targetNodeId.value
    val sourceReference = "${sanitizeReference(sourceName)}.${sanitizeReference(link.sourcePortName)}"
    val targetReference = "${sanitizeReference(targetName)}.${sanitizeReference(link.targetPortName)}"
    val symbol = safeIdentifier(node.name, preserveCase = true)
    val dependencyIndex = dependencyLinkIndex(document, node, sourceNode)
    val allocationIndex = dataLinkIndex(document, node, sourceNode)
    val allocationSymbol = "$symbol${allocationIndex.coerceAtLeast(1)}"
    val dependencySymbol = "${symbol}${dependencyIndex.coerceAtLeast(1)}"
    val capabilityTypeSymbol = dependencySymbol.replaceFirstChar { it.uppercase() } + "Capability"
    val stereotype = LinkClassifier.classify(document, node)
    val sourceCapability = stereotype == LinkStereotype.SourceCapability
    val runnableCapability = stereotype == LinkStereotype.RunnableCapability
    val libraryCapability = stereotype in setOf(LinkStereotype.UsageImport, LinkStereotype.DependencyInjection)
    return mapOf(
        "link" to mapOf(
            "id" to node.id.value,
            "name" to node.name,
            "variableName" to node.name,
            "symbol" to symbol,
            "argumentSymbol" to symbol,
            "allocationSymbol" to allocationSymbol,
            "transportSymbol" to "transport_$allocationSymbol",
            "aPortSymbol" to "${allocationSymbol}_a_port",
            "bPortSymbol" to "${allocationSymbol}_b_port",
            "typeDefinitionId" to link.typeDefinitionId,
            "typeName" to document.linkTypeDisplayName(node),
            "typeSymbol" to link.typeDefinitionId.takeIf(String::isNotBlank)
                ?.let(document::getElementById)
                ?.let { safeIdentifier(it.name, preserveCase = true) }
                .orEmpty()
                .ifBlank { document.linkTypeDisplayName(node) },
            "typeFields" to link.typeDefinitionId.takeIf(String::isNotBlank)
                ?.let(document::getElementById)
                ?.let { typeFieldViews(document, it) }
                .orEmpty(),
            "typeDefinition" to link.payloadDefinition,
            "sourceNodeName" to sourceNode?.name.orEmpty(),
            "sourceNodeSymbol" to sourceNode?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty(),
            "sourceNodeType" to sourceNode?.name.orEmpty(),
            "sourceNodeTypeSymbol" to sourceNode?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty(),
            "sourceNodeInstantiation" to sourceNode?.text?.instantiation.orEmpty(),
            "sourceNodeDeclaration" to sourceCompilationProduct,
            "sourceNodeDeclarationDoubleQuoted" to sourceCompilationProduct.escapeDoubleQuotedLiteral(),
            "sourceNodeDeclarationDollarEscaped" to sourceCompilationProduct.escapeDollarDoubleQuotedLiteral(),
            "sourceRunSymbol" to sourceNode?.let { "run_${indexedNodeSymbol(document, it)}" }.orEmpty(),
            "libraryInstantiation" to sourceNode?.text?.instantiation.orEmpty(),
            "dependencyIndex" to dependencyIndex,
            "dependencySymbol" to dependencySymbol,
            "capabilityTypeSymbol" to capabilityTypeSymbol,
            "sourceNodeId" to link.sourceNodeId.value,
            "targetNodeId" to link.targetNodeId.value,
            "targetNodeName" to targetNode?.name.orEmpty(),
            "targetNodeSymbol" to targetNode?.let { safeIdentifier(it.name, preserveCase = true) }.orEmpty(),
            "sourcePortName" to link.sourcePortName,
            "targetPortName" to link.targetPortName,
            "transportKind" to link.transportKind,
            "interactionKind" to link.interactionKind,
            "stereotype" to stereotype.name,
            "isCapability" to (sourceCapability || runnableCapability || libraryCapability),
            "isLibraryCapability" to libraryCapability,
            "isSourceCapability" to sourceCapability,
            "isRunnableCapability" to runnableCapability,
            "capabilityMethod" to when {
                sourceCapability -> "getSource"
                runnableCapability -> "getRunnable"
                else -> ""
            },
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

private fun isCapabilityDescriptor(descriptor: Map<String, Any?>): Boolean =
    descriptor["stereotype"] in setOf(
        LinkStereotype.UsageImport.name,
        LinkStereotype.DependencyInjection.name,
        LinkStereotype.SourceCapability.name,
        LinkStereotype.RunnableCapability.name,
    )

private fun dependencyLinkIndex(document: ThreadworkDocument, linkNode: Node, sourceNode: Node?): Int {
    val dependencies = sourceNode?.outgoingLinks.orEmpty().mapNotNull(document::getElementById)
        .filter { candidate ->
            candidate.link != null && LinkClassifier.classify(document, candidate) in setOf(
                LinkStereotype.UsageImport,
                LinkStereotype.DependencyInjection,
                LinkStereotype.SourceCapability,
                LinkStereotype.RunnableCapability,
            )
        }
    return dependencies.indexOfFirst { it.id == linkNode.id }.takeIf { it >= 0 }?.plus(1) ?: 1
}

/**
 * The generated double-buffer allocation belongs to the source node and needs
 * a stable disambiguator. Processor-local arguments intentionally remain
 * unindexed because they are scoped to one generated function.
 */
private fun dataLinkIndex(document: ThreadworkDocument, linkNode: Node, sourceNode: Node?): Int {
    val dataLinks = sourceNode?.outgoingLinks.orEmpty().mapNotNull(document::getElementById)
        .filter { candidate ->
            candidate.link != null && LinkClassifier.classify(document, candidate) !in setOf(
                LinkStereotype.UsageImport,
                LinkStereotype.DependencyInjection,
                LinkStereotype.SourceCapability,
                LinkStereotype.RunnableCapability,
            )
        }
    return dataLinks.indexOfFirst { it.id == linkNode.id }.takeIf { it >= 0 }?.plus(1) ?: 1
}

private fun compileScopeIds(document: ThreadworkDocument, requested: Set<com.threadwork.core.model.NodeId>): Set<com.threadwork.core.model.NodeId> {
    if (requested.isEmpty()) return document.nodes.keys
    val result = linkedSetOf<com.threadwork.core.model.NodeId>()
    fun include(id: com.threadwork.core.model.NodeId) {
        val node = document.getElementById(id) ?: return
        if (result.add(id) && !node.isLink) node.children.forEach(::include)
    }
    requested.forEach(::include)
    return result
}

private fun executableScopeRoots(
    document: ThreadworkDocument,
    scopeIds: Set<com.threadwork.core.model.NodeId>,
): List<Node> {
    if (document.rootNodeId in scopeIds) return listOfNotNull(document.getElementById(document.rootNodeId))
    return scopeIds.mapNotNull(document::getElementById)
        .filterNot { it.isLink }
        .filter { it.parentId !in scopeIds }
        .ifEmpty { listOfNotNull(document.getElementById(document.rootNodeId)) }
}

private fun indexedNodeSymbol(document: ThreadworkDocument, node: Node): String {
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
    val modelName = value.trim()
    val sanitized = if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(modelName)) {
        modelName
    } else {
        modelName.replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
    }
    val fallback = sanitized.ifBlank { "node" }.let { if (preserveCase) it else it.lowercase() }
    return if (fallback.first().isDigit()) "_$fallback" else fallback
}

private fun safePathSegment(value: String): String =
    value.trim().replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_').ifBlank { "project" }

private fun safePackageName(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9_.-]+"), "-").trim('-').ifBlank { "project" }

private fun sanitizeReference(value: String): String = value.trim().replace("\"", "\\\"")

private fun String.escapeDoubleQuoted(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private fun String.escapeDoubleQuotedLiteral(): String =
    escapeDoubleQuoted()
        .replace("\r", "\\r")
        .replace("\n", "\\n")

private fun String.escapeDollarDoubleQuotedLiteral(): String =
    escapeDoubleQuotedLiteral().replace("$", "\\$")

private fun String.escapeSingleQuoted(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun String.indentNonBlank(prefix: String): String =
    trimEnd().lines().filter { it.isNotBlank() }.joinToString("\n") { "$prefix$it" }

private fun String.toBlockComment(): String =
    lines().joinToString(prefix = "/**\n", postfix = "\n */") { " * $it" }

private fun effectiveTechnology(document: ThreadworkDocument, node: Node): TechnologyMetadata =
    node.technology.copy(
        languageId = document.effectiveLanguageId(node.id),
        technologyId = document.effectiveTechnologyId(node.id),
    )

internal fun stereotypeForTemplateContext(document: ThreadworkDocument, node: Node): NodeStereotype =
    if (!node.isLink) {
        node.stereotype(document)
    } else {
        when (LinkClassifier.classify(document, node)) {
            LinkStereotype.Transport -> NodeStereotype.Transport
            LinkStereotype.ErrorPipe -> NodeStereotype.ErrorPipe
            LinkStereotype.UsageImport,
            LinkStereotype.DependencyInjection -> NodeStereotype.DependencyInjection
            LinkStereotype.SourceCapability,
            LinkStereotype.RunnableCapability -> NodeStereotype.DependencyInjection
        }
    }

internal fun String.toTemplateToken(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

private fun String.normalizedTemplateName(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "")

private fun String.normalizeLegacyPlaceholders(): String =
    replace(Regex("\\$\\{([A-Za-z0-9_.]+)}")) { match -> "{{ ${match.groupValues[1]} }}" }
