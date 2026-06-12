package com.orchestra.compiler.api

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
import com.orchestra.core.model.VOID_LAYOUT_STRATEGY_ID
import com.orchestra.core.model.effectiveLanguageId
import com.orchestra.core.model.effectiveLayoutStrategyId
import com.orchestra.core.model.effectiveTechnologyId
import com.orchestra.core.model.getElementById
import java.nio.file.Path

enum class GeneratedElementKind {
    TerminalEntity,
    CompositeEntity,
    Link,
    MagicFile,
    StaticFile,
    CompilerTemplate,
    Runtime,
    ProjectLayout,
}

const val ANY_LANGUAGE_ID = "any"

data class CompilerTechnology(
    val languageId: String,
    val technologyId: String,
)

interface CompilerPlugin : FsStorage {
    val id: String
    val displayName: String
    val supportedLanguageIds: Set<String> get() = emptySet()
    val supportedTechnologyIds: Set<String> get() = emptySet()
    val providedTechnologies: List<CompilerTechnology>
        get() = supportedLanguageIds.flatMap { languageId ->
            supportedTechnologyIds.map { technologyId -> CompilerTechnology(languageId, technologyId) }
        }
    val magicFileNames: Set<String> get() = emptySet()

    fun supports(document: InflowDocument): Boolean
    fun validate(document: InflowDocument): List<Diagnostic>

    fun linkStereotype(document: InflowDocument, linkNode: Node): LinkStereotype =
        LinkClassifier.classify(document, linkNode)

    fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        magicFileNames.toList()

    fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        ClassifiedFilesystemLayoutStrategy

    fun layoutStrategy(document: InflowDocument, nodeId: NodeId, options: CompilerOptions): LayoutStrategy {
        val resolvedStrategyId = document.effectiveLayoutStrategyId(nodeId)
        return if (resolvedStrategyId == VOID_LAYOUT_STRATEGY_ID) {
            layoutStrategy(options)
        } else {
            layoutStrategyById(resolvedStrategyId)
        }
    }

    fun layoutStrategy(document: InflowDocument, options: CompilerOptions): LayoutStrategy {
        val scopeNodeId = options.scopeNodeIds.singleOrNull() ?: document.rootNodeId
        return layoutStrategy(document, scopeNodeId, options)
    }

    fun compile(document: InflowDocument, options: CompilerOptions = CompilerOptions()): CompilationResult

    override fun store(document: InflowDocument, node: Node): List<VirtualFile> =
        compile(
            document,
            CompilerOptions(
                projectName = document.name,
                scopeNodeIds = setOf(node.id),
                includeScopeAncestors = false,
            ),
        ).generatedProject?.toVirtualFiles().orEmpty()

    override fun restore(document: InflowDocument, chunk: List<VirtualFile>): InflowDocument =
        document
}

data class CompilerOptions(
    val projectName: String? = null,
    val scopeNodeIds: Set<NodeId> = emptySet(),
    val compilerPlugins: List<CompilerPlugin> = emptyList(),
    val includeScopeAncestors: Boolean = true,
    val allowCompilerDelegation: Boolean = true,
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
        toVirtualFiles().writeTo(directory)
    }
}

data class GeneratedFile(
    val path: String,
    val content: String,
    val originNodeId: NodeId?,
    val reason: String,
    val elementKind: GeneratedElementKind = GeneratedElementKind.TerminalEntity,
)

data class CompiledNodeArtifact(
    val node: Node,
    val layoutStrategy: LayoutStrategy,
    val declarationText: String,
    val instantiationText: String,
    val primaryFile: GeneratedFile?,
    val files: List<GeneratedFile>,
) {
    val isComposite: Boolean get() = node.children.isNotEmpty() && !node.isLink
}

data class NodeCompilerContext(
    val compiler: CompilerPlugin,
    val document: InflowDocument,
    val node: Node,
    val options: CompilerOptions,
    val projectName: String,
    val layoutStrategy: LayoutStrategy,
    val extension: String,
    val childArtifacts: List<CompiledNodeArtifact>,
    val linkArtifacts: List<CompiledNodeArtifact>,
) {
    val isSingleFileLayout: Boolean get() = layoutStrategy.id == SingleFileLayoutStrategy.id
    val childDeclarations: String get() = childArtifacts.joinToString("\n\n") { it.declarationText }.trim()
    val childInstantiations: String get() = childArtifacts.joinToString("\n") { it.instantiationText }.trim()
    val linkDeclarations: String get() = linkArtifacts.joinToString("\n\n") { it.declarationText }.trim()
    val linkInstantiations: String get() = linkArtifacts.joinToString("\n") { it.instantiationText }.trim()

    fun primaryPath(): String =
        layoutStrategy.primaryPathFor(document, node, projectName, extension, options)
}

abstract class StructuredCompiler : CompilerPlugin {
    final override fun compile(document: InflowDocument, options: CompilerOptions): CompilationResult {
        val diagnostics = validate(document).toMutableList()
        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }

        beforeCompile(document, options)
        val projectName = normalizedProjectName(document, options)
        val scopeIds = compileScopeIds(document, options.scopeNodeIds, options.includeScopeAncestors)
        val roots = compilationRoots(document, scopeIds)
        val files = mutableListOf<GeneratedFile>()
        files += projectFiles(document, options, projectName)

        roots.forEach { root ->
            compileNode(document, root, options, projectName, scopeIds, diagnostics, linkedSetOf())
                ?.let { files += it.files }
        }
        afterCompile(document, options)

        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }
        return CompilationResult(
            generatedProject = finalizeProject(document, projectName, files.distinctBy { it.path }, options),
            diagnostics = diagnostics,
            success = true,
        )
    }

    protected open fun beforeCompile(document: InflowDocument, options: CompilerOptions) {
    }

    protected open fun afterCompile(document: InflowDocument, options: CompilerOptions) {
    }

    protected open fun normalizedProjectName(document: InflowDocument, options: CompilerOptions): String =
        options.projectName?.takeIf { it.isNotBlank() } ?: document.name.ifBlank { "project" }

    protected open fun projectFiles(document: InflowDocument, options: CompilerOptions, projectName: String): List<GeneratedFile> =
        emptyList()

    protected open fun finalizeProject(
        document: InflowDocument,
        projectName: String,
        files: List<GeneratedFile>,
        options: CompilerOptions,
    ): GeneratedProject =
        GeneratedProject(projectName, files)

    protected open fun fileExtension(context: NodeCompilerContext): String =
        context.node.technology.fileExtension.ifBlank {
            when (context.document.effectiveLanguageId(context.node.id)) {
                "markdown" -> "md"
                "kotlin" -> "kt"
                "javascript" -> "js"
                "typescript" -> "ts"
                "php" -> "php"
                "json" -> "json"
                else -> "txt"
            }
        }.trim().trimStart('.').ifBlank { "txt" }

    protected open fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        false

    protected open fun staticFileFor(context: NodeCompilerContext): GeneratedFile? =
        null

    protected open fun declarationFor(context: NodeCompilerContext): String =
        when (context.node.kind) {
            NodeKind.Node -> getNodeDeclaration(context)
            NodeKind.Processor -> getProcessorDeclaration(context)
            NodeKind.Link -> getLinkDeclaration(context)
            NodeKind.Group -> getGroupDeclaration(context)
            NodeKind.Note -> getNoteDeclaration(context)
        }

    protected open fun instantiationFor(context: NodeCompilerContext): String =
        when (context.node.kind) {
            NodeKind.Node -> getNodeInstantiation(context)
            NodeKind.Processor -> getProcessorInstantiation(context)
            NodeKind.Link -> getLinkInstantiation(context)
            NodeKind.Group -> getGroupInstantiation(context)
            NodeKind.Note -> getNoteInstantiation(context)
        }

    protected open fun getNodeDeclaration(context: NodeCompilerContext): String =
        defaultDeclaration(context)

    protected open fun getNodeInstantiation(context: NodeCompilerContext): String =
        context.node.text.instantiation

    protected open fun getProcessorDeclaration(context: NodeCompilerContext): String =
        if (context.node.children.isNotEmpty()) defaultCompositeDeclaration(context) else defaultDeclaration(context)

    protected open fun getProcessorInstantiation(context: NodeCompilerContext): String =
        context.node.text.instantiation

    protected open fun getLinkDeclaration(context: NodeCompilerContext): String =
        context.node.link?.payloadDefinition?.ifBlank { context.node.text.declaration } ?: context.node.text.declaration

    protected open fun getLinkInstantiation(context: NodeCompilerContext): String =
        context.node.text.instantiation

    protected open fun getGroupDeclaration(context: NodeCompilerContext): String =
        defaultCompositeDeclaration(context)

    protected open fun getGroupInstantiation(context: NodeCompilerContext): String =
        context.node.text.instantiation

    protected open fun getNoteDeclaration(context: NodeCompilerContext): String =
        defaultDeclaration(context)

    protected open fun getNoteInstantiation(context: NodeCompilerContext): String =
        context.node.text.instantiation

    protected open fun defaultDeclaration(context: NodeCompilerContext): String =
        listOf(context.node.text.instantiation, context.node.text.declaration)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    protected open fun defaultCompositeDeclaration(context: NodeCompilerContext): String {
        val childBlock = if (context.isSingleFileLayout) context.childDeclarations else childImportsFor(context)
        return listOf(
            childBlock,
            context.linkDeclarations,
            context.node.text.instantiation,
            context.node.text.declaration,
            context.childInstantiations,
            context.linkInstantiations,
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    protected open fun childImportsFor(context: NodeCompilerContext): String =
        context.childArtifacts.joinToString("\n") { importForChild(context, it) }.trim()

    protected open fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String =
        ""

    protected open fun shouldEmitPrimaryFile(context: NodeCompilerContext, declaration: String): Boolean =
        declaration.isNotBlank() && !context.node.isLink

    protected open fun primaryFileFor(context: NodeCompilerContext, declaration: String): GeneratedFile? {
        if (!shouldEmitPrimaryFile(context, declaration)) return null
        return GeneratedFile(
            path = context.primaryPath(),
            content = declaration.trimEnd(),
            originNodeId = context.node.id,
            reason = if (context.node.children.isNotEmpty()) "Composite node declaration" else "Terminal node declaration",
            elementKind = if (context.node.children.isNotEmpty()) GeneratedElementKind.CompositeEntity else GeneratedElementKind.TerminalEntity,
        )
    }

    private fun compileNode(
        document: InflowDocument,
        node: Node,
        options: CompilerOptions,
        projectName: String,
        scopeIds: Set<NodeId>,
        diagnostics: MutableList<Diagnostic>,
        stack: LinkedHashSet<NodeId>,
    ): CompiledNodeArtifact? {
        if (node.id !in scopeIds) return null
        if (!stack.add(node.id)) {
            diagnostics += Diagnostic(DiagnosticSeverity.Error, "Cycle detected while compiling '${node.name}'.", node.id, sourcePluginId = id)
            return null
        }

        findDelegateCompiler(document, node, options)?.let { delegate ->
            val result = delegate.compile(
                document,
                options.copy(
                    scopeNodeIds = setOf(node.id),
                    includeScopeAncestors = false,
                ),
            )
            diagnostics += result.diagnostics
            stack.remove(node.id)
            val files = result.generatedProject?.files.orEmpty()
            return if (result.success && result.generatedProject != null) {
                CompiledNodeArtifact(
                    node = node,
                    layoutStrategy = layoutStrategy(document, node.id, options),
                    declarationText = "",
                    instantiationText = "",
                    primaryFile = null,
                    files = files,
                )
            } else {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.Error,
                    "Delegated compiler '${delegate.id}' failed for '${node.name}'.",
                    node.id,
                    sourcePluginId = id,
                )
                null
            }
        }

        val childNodes = node.children.mapNotNull(document::getElementById).filter { it.id in scopeIds }
        val childArtifacts = childNodes
            .filterNot { it.isLink }
            .mapNotNull { compileNode(document, it, options, projectName, scopeIds, diagnostics, stack) }
        val linkArtifacts = childNodes
            .filter { it.isLink }
            .mapNotNull { compileNode(document, it, options, projectName, scopeIds, diagnostics, stack) }
        val strategy = layoutStrategy(document, node.id, options)
        val baseContext = NodeCompilerContext(
            compiler = this,
            document = document,
            node = node,
            options = options,
            projectName = projectName,
            layoutStrategy = strategy,
            extension = "txt",
            childArtifacts = childArtifacts,
            linkArtifacts = linkArtifacts,
        )
        val context = baseContext.copy(extension = fileExtension(baseContext))
        val artifact = staticFileFor(context)?.let { file ->
            CompiledNodeArtifact(node, strategy, file.content, "", file, listOf(file))
        } ?: when {
            shouldSkipNode(context) -> CompiledNodeArtifact(node, strategy, "", "", null, emptyList())
            else -> regularArtifact(context)
        }
        stack.remove(node.id)
        return artifact
    }

    private fun regularArtifact(context: NodeCompilerContext): CompiledNodeArtifact {
        val declaration = declarationFor(context).trimEnd()
        val instantiation = instantiationFor(context).trimEnd()
        val primary = primaryFileFor(context, declaration)
        val inheritedSingleFile = context.layoutStrategy.id == SingleFileLayoutStrategy.id
        val childFiles = (context.childArtifacts + context.linkArtifacts).flatMap { artifact ->
            if (inheritedSingleFile && artifact.layoutStrategy.id == SingleFileLayoutStrategy.id) {
                emptyList()
            } else {
                artifact.files
            }
        }
        val ownFiles = listOfNotNull(primary)
        return CompiledNodeArtifact(
            node = context.node,
            layoutStrategy = context.layoutStrategy,
            declarationText = declaration,
            instantiationText = instantiation,
            primaryFile = primary,
            files = ownFiles + childFiles,
        )
    }

    private fun compileScopeIds(document: InflowDocument, requested: Set<NodeId>, includeAncestors: Boolean): Set<NodeId> {
        if (requested.isEmpty()) return document.nodes.keys
        val result = linkedSetOf<NodeId>()
        fun includeAncestors(id: NodeId) {
            var currentParentId = document.getElementById(id)?.parentId
            while (currentParentId != null) {
                val parent = document.getElementById(currentParentId) ?: break
                result += parent.id
                currentParentId = parent.parentId
            }
        }
        fun include(id: NodeId) {
            val node = document.getElementById(id) ?: return
            if (result.add(id) && !node.isLink) node.children.forEach(::include)
        }
        if (includeAncestors) requested.forEach(::includeAncestors)
        requested.forEach(::include)
        val selectedNodes = result.mapNotNull(document::getElementById).filterNot { it.isLink }.map { it.id }.toSet()
        document.nodes.values.filter { it.isLink }.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            if (link.sourceNodeId in selectedNodes && link.targetNodeId in selectedNodes) result += linkNode.id
        }
        return result
    }

    private fun compilationRoots(document: InflowDocument, scopeIds: Set<NodeId>): List<Node> {
        if (scopeIds.contains(document.rootNodeId)) return listOfNotNull(document.getElementById(document.rootNodeId))
        return scopeIds
            .mapNotNull(document::getElementById)
            .filterNot { it.isLink }
            .filter { it.parentId !in scopeIds }
            .ifEmpty { listOfNotNull(document.getElementById(document.rootNodeId)) }
    }

    private fun findDelegateCompiler(document: InflowDocument, node: Node, options: CompilerOptions): CompilerPlugin? {
        if (!options.allowCompilerDelegation || options.compilerPlugins.isEmpty()) return null
        val technologyId = document.effectiveTechnologyId(node.id).trim()
        val languageId = document.effectiveLanguageId(node.id).trim()
        if (technologyId.isBlank() || compilerSupports(this, technologyId, languageId)) return null
        return options.compilerPlugins
            .asSequence()
            .filter { it.id != id }
            .filter { compiler -> runCatching { compiler.supports(document) }.getOrDefault(false) }
            .filter { compiler -> compilerSupports(compiler, technologyId, languageId) }
            .sortedWith(compareByDescending<CompilerPlugin> { compiler ->
                compiler.providedTechnologies.any { it.technologyId == technologyId && (it.languageId == languageId || it.languageId == ANY_LANGUAGE_ID) }
            }.thenBy { it.id })
            .firstOrNull()
    }

    private fun compilerSupports(compiler: CompilerPlugin, technologyId: String, languageId: String): Boolean =
        technologyId in compiler.supportedTechnologyIds ||
            compiler.providedTechnologies.any { technology ->
                technology.technologyId == technologyId &&
                    (languageId.isBlank() || technology.languageId == languageId || technology.languageId == ANY_LANGUAGE_ID)
            }
}

interface LayoutStrategy {
    val id: String
    val displayName: String

    fun primaryPathFor(
        document: InflowDocument,
        node: Node,
        projectName: String,
        extension: String,
        options: CompilerOptions,
    ): String

    fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(projectName, files)
}

object ClassifiedFilesystemLayoutStrategy : LayoutStrategy {
    override val id: String = "classified-filesystem"
    override val displayName: String = "Classified filesystem layout"

    override fun primaryPathFor(
        document: InflowDocument,
        node: Node,
        projectName: String,
        extension: String,
        options: CompilerOptions,
    ): String {
        node.explicitPath()?.let { return it }
        val stereotype = node.stereotype(document)
        val directory = when {
            node.isLink -> "links"
            stereotype in setOf(NodeStereotype.CompositeWorker, NodeStereotype.CompositeErrorHandler, NodeStereotype.TestSuite) -> "composites"
            stereotype == NodeStereotype.ServiceLibrary -> "libraries"
            else -> "nodes"
        }
        return "$directory/${node.safeNodeSegment()}.${extension.safeExtension()}"
    }
}

object DirectFileSystemHomorphismLayoutStrategy : LayoutStrategy {
    override val id: String = "direct-file-system-homomorphism"
    override val displayName: String = "Direct file-system homomorphism"

    override fun primaryPathFor(
        document: InflowDocument,
        node: Node,
        projectName: String,
        extension: String,
        options: CompilerOptions,
    ): String {
        node.explicitPath()?.let { return it }
        val rootPrefix = projectName.safeLayoutSegment()
        val segments = node.directLayoutSegments(document, rootPrefix)
        return (segments + "${node.safeNodeSegment()}.${extension.safeExtension()}").joinToString("/")
    }

    override fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(projectName, files.filterNot { file -> file.originNodeId?.let(document::getElementById)?.isLink == true })
}

object SingleFileLayoutStrategy : LayoutStrategy {
    override val id: String = "single-file"
    override val displayName: String = "Single file layout"

    override fun primaryPathFor(
        document: InflowDocument,
        node: Node,
        projectName: String,
        extension: String,
        options: CompilerOptions,
    ): String {
        val nameSource = options.scopeNodeIds.singleOrNull()
            ?.let(document::getElementById)
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: if (node.id == document.rootNodeId) projectName else node.name
        return "${nameSource.safeLayoutSegment()}.${extension.safeExtension()}"
    }

    override fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject {
        val nameSource = options.scopeNodeIds.singleOrNull()
            ?.let(document::getElementById)
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: projectName.trim().ifBlank { document.name.ifBlank { "project" } }
        val extension = files.singleFileExtension()
        val content = files
            .joinToString(separator = "\n\n") { it.content.trimEnd() }
            .trimEnd()
        val merged = GeneratedFile(
            path = "${nameSource.safeLayoutSegment()}.$extension",
            content = content,
            originNodeId = null,
            reason = "Merged ${files.size} generated files using single-file layout",
            elementKind = GeneratedElementKind.ProjectLayout,
        )
        return GeneratedProject(projectName, listOf(merged))
    }
}

object SourceSetLayoutStrategy : LayoutStrategy {
    override val id: String = "source-set"
    override val displayName: String = "Source set layout"

    override fun primaryPathFor(
        document: InflowDocument,
        node: Node,
        projectName: String,
        extension: String,
        options: CompilerOptions,
    ): String {
        node.explicitPath()?.let { return it }
        return "src/main/resources/${node.safeNodeSegment()}.${extension.safeExtension()}"
    }
}

private fun Node.explicitPath(): String? =
    metadata["path"]?.takeIf { it.isNotBlank() }
        ?: metadata["file"]?.takeIf { it.isNotBlank() }

private fun Node.directLayoutSegments(document: InflowDocument, rootPrefix: String): List<String> {
    val segments = mutableListOf(rootPrefix)
    var currentParentId = parentId
    val parents = mutableListOf<Node>()
    while (currentParentId != null) {
        val parent = document.getElementById(currentParentId) ?: break
        if (parent.id == document.rootNodeId) break
        parents += parent
        currentParentId = parent.parentId
    }
    parents.asReversed().filter { it.children.isNotEmpty() }.forEach { segments += it.safeNodeSegment() }
    return segments
}

private fun Node.safeNodeSegment(): String =
    name.trim()
        .replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        .trim('_')
        .ifBlank { id.value.take(12) }

private fun String.safeLayoutSegment(): String =
    trim()
        .replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        .trim('_')
        .ifBlank { "project" }

private fun String.safeExtension(): String =
    trim().trimStart('.').ifBlank { "txt" }

private fun List<GeneratedFile>.singleFileExtension(): String =
    map { file -> file.path.substringAfterLast('.', "txt").trim().ifBlank { "txt" } }
        .distinct()
        .singleOrNull()
        ?: "txt"

fun layoutStrategyById(id: String): LayoutStrategy =
    when (id) {
        ClassifiedFilesystemLayoutStrategy.id -> ClassifiedFilesystemLayoutStrategy
        DirectFileSystemHomorphismLayoutStrategy.id -> DirectFileSystemHomorphismLayoutStrategy
        SingleFileLayoutStrategy.id -> SingleFileLayoutStrategy
        SourceSetLayoutStrategy.id -> SourceSetLayoutStrategy
        else -> ClassifiedFilesystemLayoutStrategy
    }
