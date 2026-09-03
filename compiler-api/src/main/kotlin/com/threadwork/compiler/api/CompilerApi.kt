package com.threadwork.compiler.api

import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.VOID_LAYOUT_STRATEGY_ID
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveLayoutStrategyId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.projectName
import java.nio.file.Path

enum class GeneratedElementKind {
    TerminalEntity,
    CompositeEntity,
    Link,
    Type,
    MagicFile,
    StaticFile,
    CompilerTemplate,
    Runtime,
    ProjectLayout,
    Documentation,
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
    val supportedLayoutStrategyIds: Set<String> get() = emptySet()

    fun supports(document: ThreadworkDocument): Boolean
    fun validate(document: ThreadworkDocument): List<Diagnostic>

    fun linkStereotype(document: ThreadworkDocument, linkNode: Node): LinkStereotype =
        LinkClassifier.classify(document, linkNode)

    fun getStaticFiles(document: ThreadworkDocument, options: CompilerOptions): List<String> =
        magicFileNames.toList()

    /**
     * Names, types, and runtime operations that this compiler exposes to a
     * processing-node declaration.  Implementations with a richer generated
     * runtime may override this to expose their exact compiled artefacts.
     */
    fun codeIntelligence(document: ThreadworkDocument, node: Node): CompilerCodeIntelligence =
        defaultCodeIntelligence(document, node)

    /**
     * Callable or otherwise directly addressable names emitted for [node].
     * Composite editors use these names to refer to their direct children
     * without guessing compiler-specific identifier conventions.
     */
    fun generatedEntitySymbols(document: ThreadworkDocument, node: Node): List<CompilerCodeSymbol> = emptyList()

    /** Exact generated function header enclosing an editable source section, when applicable. */
    fun generatedFunctionHeader(
        document: ThreadworkDocument,
        node: Node,
        section: NodeTextSection,
    ): String = ""

    /** Detailed generated representation of a type for editor hover help. */
    fun typeInformation(
        document: ThreadworkDocument,
        node: Node,
        typeName: String,
    ): CompilerTypeInformation? = defaultTypeInformation(document, node, typeName)

    fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        ClassifiedFilesystemLayoutStrategy

    fun layoutStrategy(document: ThreadworkDocument, nodeId: NodeId, options: CompilerOptions): LayoutStrategy {
        val resolvedStrategyId = document.effectiveLayoutStrategyId(nodeId)
        return if (resolvedStrategyId == VOID_LAYOUT_STRATEGY_ID) {
            layoutStrategy(options)
        } else {
            layoutStrategyById(resolvedStrategyId)
        }
    }

    fun layoutStrategy(document: ThreadworkDocument, options: CompilerOptions): LayoutStrategy {
        val scopeNodeId = options.scopeNodeIds.singleOrNull() ?: document.rootNodeId
        return layoutStrategy(document, scopeNodeId, options)
    }

    fun compile(document: ThreadworkDocument, options: CompilerOptions = CompilerOptions()): CompilationResult

    fun store(document: ThreadworkDocument, node: Node, options: CompilerOptions): List<VirtualFile> =
        compile(
            document,
            options.copy(
                projectName = options.projectName ?: document.projectName(),
                scopeNodeIds = setOf(node.id),
                includeScopeAncestors = false,
            ),
        ).generatedProject?.toVirtualFiles().orEmpty()

    override fun store(document: ThreadworkDocument, node: Node): List<VirtualFile> =
        store(document, node, CompilerOptions(projectName = document.projectName()))

    override fun restore(document: ThreadworkDocument, chunk: List<VirtualFile>): ThreadworkDocument =
        document
}

data class CompilerOptions(
    val projectName: String? = null,
    val scopeNodeIds: Set<NodeId> = emptySet(),
    val compilerPlugins: List<CompilerPlugin> = emptyList(),
    val includeScopeAncestors: Boolean = true,
    val includeScopeDescendants: Boolean = true,
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

/**
 * A location in generated output that can be traced back to editable model text.
 * Line and column values are one-based, matching compiler diagnostics.
 */
data class GeneratedSourceMapEntry(
    val generatedLine: Int,
    val nodeId: NodeId,
    val textSection: NodeTextSection,
    val sourceLine: Int,
    val generatedColumnOffset: Int = 0,
)

data class GeneratedSourceLocation(
    val nodeId: NodeId,
    val textSection: NodeTextSection,
    val line: Int,
    val column: Int?,
)

data class GeneratedSourceMap(
    val entries: List<GeneratedSourceMapEntry> = emptyList(),
) {
    fun locate(generatedLine: Int, generatedColumn: Int? = null): GeneratedSourceLocation? {
        val entry = entries.firstOrNull { it.generatedLine == generatedLine } ?: return null
        val sourceColumn = generatedColumn?.let { column ->
            (column - entry.generatedColumnOffset).coerceAtLeast(1)
        }
        return GeneratedSourceLocation(entry.nodeId, entry.textSection, entry.sourceLine, sourceColumn)
    }
}

data class GeneratedFile(
    val path: String,
    val content: String,
    val originNodeId: NodeId?,
    val reason: String,
    val elementKind: GeneratedElementKind = GeneratedElementKind.TerminalEntity,
    val sourceMap: GeneratedSourceMap = GeneratedSourceMap(),
    val binaryContent: ByteArray? = null,
)

data class CompiledNodeArtifact(
    val node: Node,
    val layoutStrategy: LayoutStrategy,
    val declarationText: String,
    val instantiationText: String,
    val primaryFile: GeneratedFile?,
    val files: List<GeneratedFile>,
    /** Prototype text generated for this entity only. */
    val forwardDeclarationText: String = "",
    /** Order-preserving prototype blocks for this entity and its compiled subtree. */
    val forwardDeclarations: List<String> = emptyList(),
    /** Declaration text, such as a wire type, that must precede every executable definition. */
    val hoistedDeclarationText: String = "",
    /** Order-preserving declarations required before code for this entity and its compiled subtree. */
    val hoistedDeclarations: List<String> = emptyList(),
    /** Compiler-produced source represented by this artifact, including delegated compiler output. */
    val compiledProductText: String = declarationText,
) {
    val isComposite: Boolean get() = node.children.isNotEmpty() && !node.isLink
}

data class NodeCompilerContext(
    val compiler: CompilerPlugin,
    val document: ThreadworkDocument,
    val node: Node,
    val options: CompilerOptions,
    val projectName: String,
    val layoutStrategy: LayoutStrategy,
    val extension: String,
    val childArtifacts: List<CompiledNodeArtifact>,
    val linkArtifacts: List<CompiledNodeArtifact>,
    val compiledArtifacts: Map<NodeId, CompiledNodeArtifact> = emptyMap(),
) {
    val effectiveLayoutStrategy: LayoutStrategy
        get() {
            val resolvedStrategyId = document.effectiveLayoutStrategyId(node.id)
            return if (resolvedStrategyId == VOID_LAYOUT_STRATEGY_ID) {
                layoutStrategy
            } else {
                layoutStrategyById(resolvedStrategyId)
            }
        }
    val isSingleFileLayout: Boolean get() = effectiveLayoutStrategy.id == SingleFileLayoutStrategy.id
    val childDeclarations: String get() = childArtifacts.joinToString("\n\n") { it.declarationText }.trim()
    val inlineChildArtifacts: List<CompiledNodeArtifact>
        get() = childArtifacts.filter { it.layoutStrategy.id == SingleFileLayoutStrategy.id }
    val externalChildArtifacts: List<CompiledNodeArtifact>
        get() = childArtifacts.filterNot { it.layoutStrategy.id == SingleFileLayoutStrategy.id }
    val inlineChildDeclarations: String
        get() = inlineChildArtifacts.joinToString("\n\n") { it.declarationText }.trim()
    val childInstantiations: String get() = childArtifacts.joinToString("\n") { it.instantiationText }.trim()
    val linkDeclarations: String get() = linkArtifacts.joinToString("\n\n") { it.declarationText }.trim()
    val linkInstantiations: String get() = linkArtifacts.joinToString("\n") { it.instantiationText }.trim()
    val childForwardDeclarationLines: List<String>
        get() = childArtifacts.flatMap { it.forwardDeclarations }.filter(String::isNotBlank).distinct()
    val linkForwardDeclarationLines: List<String>
        get() = linkArtifacts.flatMap { it.forwardDeclarations }.filter(String::isNotBlank).distinct()
    val descendantForwardDeclarationLines: List<String>
        get() = (childForwardDeclarationLines + linkForwardDeclarationLines).distinct()
    val childForwardDeclarations: String get() = childForwardDeclarationLines.joinToString("\n")
    val linkForwardDeclarations: String get() = linkForwardDeclarationLines.joinToString("\n")
    val descendantForwardDeclarations: String get() = descendantForwardDeclarationLines.joinToString("\n")
    val childHoistedDeclarationLines: List<String>
        get() = childArtifacts.flatMap { it.hoistedDeclarations }.filter(String::isNotBlank).distinct()
    val linkHoistedDeclarationLines: List<String>
        get() = linkArtifacts.flatMap { it.hoistedDeclarations }.filter(String::isNotBlank).distinct()
    val descendantHoistedDeclarationLines: List<String>
        get() = (childHoistedDeclarationLines + linkHoistedDeclarationLines).distinct()
    val childHoistedDeclarations: String get() = childHoistedDeclarationLines.joinToString("\n\n")
    val linkHoistedDeclarations: String get() = linkHoistedDeclarationLines.joinToString("\n\n")
    val descendantHoistedDeclarations: String get() = descendantHoistedDeclarationLines.joinToString("\n\n")

    fun primaryPath(): String =
        layoutStrategy.primaryPathFor(document, node, projectName, extension, options)
}

interface LayoutCompilerVariant {
    val layoutStrategy: LayoutStrategy

    fun projectFiles(
        document: ThreadworkDocument,
        options: CompilerOptions,
        projectName: String,
    ): List<GeneratedFile> = emptyList()

    fun fileExtension(context: NodeCompilerContext): String

    fun shouldSkipNode(context: NodeCompilerContext): Boolean = false

    fun staticFileFor(context: NodeCompilerContext): GeneratedFile? = null

    fun declarationFor(context: NodeCompilerContext): String

    /** Generates declarations that must be visible before this entity's full definition. */
    fun forwardDeclarationFor(context: NodeCompilerContext): String = ""

    /** Generates complete declarations that must be hoisted before executable definitions. */
    fun hoistedDeclarationFor(context: NodeCompilerContext): String = ""

    fun instantiationFor(context: NodeCompilerContext): String =
        context.node.text.instantiation

    fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String =
        ""

    fun primaryFileFor(context: NodeCompilerContext, declaration: String): GeneratedFile? {
        if (declaration.isBlank() || context.node.isLink) return null
        return GeneratedFile(
            path = context.primaryPath(),
            content = declaration.trimEnd(),
            originNodeId = context.node.id,
            reason = if (context.node.children.isNotEmpty()) "Composite node declaration" else "Terminal node declaration",
            elementKind = if (context.node.children.isNotEmpty()) GeneratedElementKind.CompositeEntity else GeneratedElementKind.TerminalEntity,
        )
    }
}

/**
 * Public compiler facade that delegates node generation to one implementation per layout strategy.
 * Only the facade is registered with the runtime; variants remain internal implementation details.
 */
abstract class LayoutCompositeCompiler : StructuredCompiler() {
    protected abstract val layoutVariants: List<LayoutCompilerVariant>
    protected abstract val defaultLayoutStrategyId: String

    private fun variantsById(): Map<String, LayoutCompilerVariant> =
        layoutVariants.associateBy { it.layoutStrategy.id }

    final override val supportedLayoutStrategyIds: Set<String>
        get() = layoutVariants.mapTo(linkedSetOf()) { it.layoutStrategy.id }

    protected fun layoutVariantFor(strategyId: String): LayoutCompilerVariant =
        variantsById()[strategyId]
            ?: variantsById()[defaultLayoutStrategyId]
            ?: error("Compiler '$id' has no layout variant for '$strategyId' and no default variant '$defaultLayoutStrategyId'.")

    protected fun layoutVariantFor(context: NodeCompilerContext): LayoutCompilerVariant =
        layoutVariantFor(context.effectiveLayoutStrategy.id)

    final override fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        layoutVariantFor(defaultLayoutStrategyId).layoutStrategy

    final override fun projectFiles(
        document: ThreadworkDocument,
        options: CompilerOptions,
        projectName: String,
    ): List<GeneratedFile> {
        val scopeNodeId = options.scopeNodeIds.singleOrNull() ?: document.rootNodeId
        val strategyId = document.effectiveLayoutStrategyId(scopeNodeId)
            .takeUnless { it == VOID_LAYOUT_STRATEGY_ID }
            ?: defaultLayoutStrategyId
        return layoutVariantFor(strategyId).projectFiles(document, options, projectName)
    }

    final override fun fileExtension(context: NodeCompilerContext): String =
        layoutVariantFor(context).fileExtension(context)

    final override fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        layoutVariantFor(context).shouldSkipNode(context)

    final override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? =
        layoutVariantFor(context).staticFileFor(context)

    final override fun declarationFor(context: NodeCompilerContext): String =
        layoutVariantFor(context).declarationFor(context)

    final override fun forwardDeclarationFor(context: NodeCompilerContext): String =
        layoutVariantFor(context).forwardDeclarationFor(context)

    final override fun hoistedDeclarationFor(context: NodeCompilerContext): String =
        layoutVariantFor(context).hoistedDeclarationFor(context)

    final override fun instantiationFor(context: NodeCompilerContext): String =
        layoutVariantFor(context).instantiationFor(context)

    final override fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String =
        layoutVariantFor(context).importForChild(context, child)

    final override fun primaryFileFor(context: NodeCompilerContext, declaration: String): GeneratedFile? =
        layoutVariantFor(context).primaryFileFor(context, declaration)
}

abstract class StructuredCompiler : CompilerPlugin {
    final override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult {
        val diagnostics = validate(document).toMutableList()
        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }

        beforeCompile(document, options)
        val projectName = normalizedProjectName(document, options)
        val scopeIds = compileScopeIds(
            document,
            options.scopeNodeIds,
            options.includeScopeAncestors,
            options.includeScopeDescendants,
        )
        val roots = compilationRoots(document, scopeIds)
        val files = mutableListOf<GeneratedFile>()
        val compiledArtifacts = linkedMapOf<NodeId, CompiledNodeArtifact>()
        files += projectFiles(document, options, projectName)

        roots.forEach { root ->
            compileNode(document, root, options, projectName, scopeIds, diagnostics, linkedSetOf(), compiledArtifacts)
                ?.let { files += it.files }
        }
        afterCompile(document, options)

        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }
        val generatedProject = finalizeProject(document, projectName, files.distinctBy { it.path }, options)
        return CompilationResult(
            generatedProject = generatedProject.withSourceMaps(document),
            diagnostics = diagnostics,
            success = true,
        )
    }

    protected open fun beforeCompile(document: ThreadworkDocument, options: CompilerOptions) {
    }

    protected open fun afterCompile(document: ThreadworkDocument, options: CompilerOptions) {
    }

    protected open fun normalizedProjectName(document: ThreadworkDocument, options: CompilerOptions): String =
        options.projectName?.takeIf { it.isNotBlank() } ?: document.projectName()

    protected open fun projectFiles(document: ThreadworkDocument, options: CompilerOptions, projectName: String): List<GeneratedFile> =
        emptyList()

    protected open fun finalizeProject(
        document: ThreadworkDocument,
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
            NodeKind.Type -> getTypeDeclaration(context)
            NodeKind.Note -> getNoteDeclaration(context)
        }

    protected open fun forwardDeclarationFor(context: NodeCompilerContext): String = ""

    protected open fun hoistedDeclarationFor(context: NodeCompilerContext): String = ""

    protected open fun instantiationFor(context: NodeCompilerContext): String =
        when (context.node.kind) {
            NodeKind.Node -> getNodeInstantiation(context)
            NodeKind.Processor -> getProcessorInstantiation(context)
            NodeKind.Link -> getLinkInstantiation(context)
            NodeKind.Group -> getGroupInstantiation(context)
            NodeKind.Type -> getTypeInstantiation(context)
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

    protected open fun getTypeDeclaration(context: NodeCompilerContext): String =
        context.node.text.declaration

    protected open fun getTypeInstantiation(context: NodeCompilerContext): String = ""

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
        document: ThreadworkDocument,
        node: Node,
        options: CompilerOptions,
        projectName: String,
        scopeIds: Set<NodeId>,
        diagnostics: MutableList<Diagnostic>,
        stack: LinkedHashSet<NodeId>,
        compiledArtifacts: MutableMap<NodeId, CompiledNodeArtifact>,
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
                    scopeNodeIds = delegatedScopeNodeIds(document, node, options),
                    includeScopeAncestors = false,
                ),
            )
            diagnostics += result.diagnostics
            stack.remove(node.id)
            val files = result.generatedProject?.files.orEmpty().let { generatedFiles ->
                // A delegated single-file compiler owns its file at project scope,
                // so there is no origin node to retain. Associate that artifact with
                // the requested node so enclosing filesystem compilers can place it.
                if (generatedFiles.size == 1 && generatedFiles.single().originNodeId == null) {
                    generatedFiles.map { file -> file.copy(originNodeId = node.id) }
                } else {
                    generatedFiles
                }
            }
            return if (result.success && result.generatedProject != null) {
                val providerFiles = files.filter { it.originNodeId == node.id }.ifEmpty { files }
                CompiledNodeArtifact(
                    node = node,
                    layoutStrategy = layoutStrategy(document, node.id, options),
                    declarationText = "",
                    instantiationText = "",
                    primaryFile = null,
                    files = files,
                    compiledProductText = providerFiles.joinToString("\n\n") { it.content.trimEnd() }.trimEnd(),
                ).also { compiledArtifacts[node.id] = it }
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
            .mapNotNull { compileNode(document, it, options, projectName, scopeIds, diagnostics, stack, compiledArtifacts) }
        val linkArtifacts = childNodes
            .filter { it.isLink }
            .mapNotNull { compileNode(document, it, options, projectName, scopeIds, diagnostics, stack, compiledArtifacts) }
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
            compiledArtifacts = compiledArtifacts,
        )
        val context = baseContext.copy(extension = fileExtension(baseContext))
        val artifact = staticFileFor(context)?.let { file ->
            CompiledNodeArtifact(node, strategy, file.content, "", file, listOf(file))
        } ?: when {
            shouldSkipNode(context) -> CompiledNodeArtifact(node, strategy, "", "", null, emptyList())
            else -> regularArtifact(context)
        }
        stack.remove(node.id)
        compiledArtifacts[node.id] = artifact
        return artifact
    }

    private fun regularArtifact(context: NodeCompilerContext): CompiledNodeArtifact {
        val ownForwardDeclaration = forwardDeclarationFor(context).trim()
        val forwardDeclarations = (
            context.descendantForwardDeclarationLines + ownForwardDeclaration
        ).filter(String::isNotBlank).distinct()
        val ownHoistedDeclaration = hoistedDeclarationFor(context).trim()
        val hoistedDeclarations = (
            context.descendantHoistedDeclarationLines + ownHoistedDeclaration
        ).filter(String::isNotBlank).distinct()
        val declaration = declarationFor(context).trimEnd()
        val instantiation = instantiationFor(context).trimEnd()
        val primary = primaryFileFor(context, declaration)
        val inheritedSingleFile = context.isSingleFileLayout
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
            forwardDeclarationText = ownForwardDeclaration,
            forwardDeclarations = forwardDeclarations,
            hoistedDeclarationText = ownHoistedDeclaration,
            hoistedDeclarations = hoistedDeclarations,
        )
    }

    private fun delegatedScopeNodeIds(
        document: ThreadworkDocument,
        delegateRoot: Node,
        options: CompilerOptions,
    ): Set<NodeId> {
        if (options.scopeNodeIds.isEmpty()) return setOf(delegateRoot.id)
        return options.scopeNodeIds
            .filter { nodeId -> isInSubtree(document, nodeId, delegateRoot.id) }
            .toSet()
            .plus(delegateRoot.id)
    }

    private fun isInSubtree(document: ThreadworkDocument, nodeId: NodeId, ancestorId: NodeId): Boolean {
        var currentId: NodeId? = nodeId
        while (currentId != null) {
            if (currentId == ancestorId) return true
            currentId = document.getElementById(currentId)?.parentId
        }
        return false
    }

    private fun compileScopeIds(
        document: ThreadworkDocument,
        requested: Set<NodeId>,
        includeAncestors: Boolean,
        includeDescendants: Boolean,
    ): Set<NodeId> {
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
            if (result.add(id) && includeDescendants && !node.isLink) node.children.forEach(::include)
        }
        if (includeAncestors) requested.forEach(::includeAncestors)
        requested.forEach(::include)
        var changed: Boolean
        do {
            val selectedNodes = result.mapNotNull(document::getElementById)
                .filterNot { it.isLink }
                .mapTo(linkedSetOf()) { it.id }
            val sizeBefore = result.size
            document.nodes.values.filter { it.isLink }.forEach { linkNode ->
                val link = linkNode.link ?: return@forEach
                when {
                    link.sourceNodeId in selectedNodes && link.targetNodeId in selectedNodes -> result += linkNode.id
                    link.targetNodeId in selectedNodes && LinkClassifier.isCapability(document, linkNode) -> {
                        include(link.sourceNodeId)
                        if (includeAncestors) includeAncestors(link.sourceNodeId)
                        result += linkNode.id
                    }
                }
            }
            changed = result.size != sizeBefore
        } while (changed)
        val pendingTypeIds = ArrayDeque(
            result.mapNotNull(document::getElementById)
                .mapNotNull { it.link?.typeDefinitionId?.takeIf(String::isNotBlank) }
                .map(::NodeId),
        )
        while (pendingTypeIds.isNotEmpty()) {
            val typeId = pendingTypeIds.removeFirst()
            val typeNode = document.getElementById(typeId) ?: continue
            if (typeNode.kind != NodeKind.Type || !result.add(typeId)) continue
            typeNode.typeDefinition?.fields.orEmpty()
                .map { NodeId(it.typeId) }
                .filter { referencedId -> document.getElementById(referencedId)?.kind == NodeKind.Type }
                .forEach(pendingTypeIds::addLast)
            if (includeAncestors) includeAncestors(typeId)
        }
        return result
    }

    private fun compilationRoots(document: ThreadworkDocument, scopeIds: Set<NodeId>): List<Node> {
        if (scopeIds.contains(document.rootNodeId)) return listOfNotNull(document.getElementById(document.rootNodeId))
        return scopeIds
            .mapNotNull(document::getElementById)
            .filterNot { it.isLink }
            .filter { it.parentId !in scopeIds }
            .ifEmpty { listOfNotNull(document.getElementById(document.rootNodeId)) }
    }

    private fun findDelegateCompiler(document: ThreadworkDocument, node: Node, options: CompilerOptions): CompilerPlugin? {
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

private fun GeneratedProject.withSourceMaps(document: ThreadworkDocument): GeneratedProject =
    copy(files = files.map { file ->
        if (file.sourceMap.entries.isNotEmpty()) file else file.copy(sourceMap = sourceMapFor(document, file.content))
    })

private data class SourceTextBlock(
    val nodeId: NodeId,
    val section: NodeTextSection,
    val lines: List<SourceTextLine>,
)

/** A nonblank editable source line and its one-based editor line number. */
private data class SourceTextLine(
    val sourceLine: Int,
    val content: String,
)

private fun sourceMapFor(document: ThreadworkDocument, generatedContent: String): GeneratedSourceMap {
    val generatedLines = generatedContent.lines()
    val usedGeneratedLines = mutableSetOf<Int>()
    val entries = mutableListOf<GeneratedSourceMapEntry>()
    sourceTextBlocks(document).forEach { block ->
        val start = findSourceBlock(generatedLines, block.lines.map(SourceTextLine::content), usedGeneratedLines) ?: return@forEach
        block.lines.forEachIndexed { index, sourceLine ->
            val generatedIndex = start + index
            usedGeneratedLines += generatedIndex
            entries += GeneratedSourceMapEntry(
                generatedLine = generatedIndex + 1,
                nodeId = block.nodeId,
                textSection = block.section,
                sourceLine = sourceLine.sourceLine,
                generatedColumnOffset = generatedLines[generatedIndex].leadingWhitespaceCount() - sourceLine.content.leadingWhitespaceCount(),
            )
        }
    }
    return GeneratedSourceMap(entries)
}

private fun sourceTextBlocks(document: ThreadworkDocument): List<SourceTextBlock> =
    document.nodes.values.flatMap { node ->
        listOf(
            NodeTextSection.Declaration to node.text.declaration,
            NodeTextSection.Instantiation to node.text.instantiation,
        ).mapNotNull { (section, text) ->
            val allLines = text.lines()
            val nonBlankLines = allLines.mapIndexedNotNull { index, line ->
                line.takeIf(String::isNotBlank)?.let { SourceTextLine(index + 1, it) }
            }
            if (nonBlankLines.isEmpty()) null else SourceTextBlock(
                nodeId = node.id,
                section = section,
                lines = nonBlankLines,
            )
        }
    }

private fun findSourceBlock(
    generatedLines: List<String>,
    sourceLines: List<String>,
    usedGeneratedLines: Set<Int>,
): Int? {
    if (sourceLines.isEmpty()) return null
    return generatedLines.indices.firstOrNull { start ->
        start + sourceLines.size <= generatedLines.size &&
            (start until start + sourceLines.size).none { it in usedGeneratedLines } &&
            sourceLines.indices.all { index ->
                generatedLines[start + index].trim() == sourceLines[index].trim()
            }
    }
}

private fun String.leadingWhitespaceCount(): Int = takeWhile(Char::isWhitespace).length

interface LayoutStrategy {
    val id: String
    val displayName: String

    fun primaryPathFor(
        document: ThreadworkDocument,
        node: Node,
        projectName: String,
        extension: String,
        options: CompilerOptions,
    ): String

    fun layout(document: ThreadworkDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(projectName, files)
}

object ClassifiedFilesystemLayoutStrategy : LayoutStrategy {
    override val id: String = "classified-filesystem"
    override val displayName: String = "Classified filesystem layout"

    override fun primaryPathFor(
        document: ThreadworkDocument,
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
        document: ThreadworkDocument,
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

    override fun layout(document: ThreadworkDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(projectName, files.filterNot { file -> file.originNodeId?.let(document::getElementById)?.isLink == true })
}

object SingleFileLayoutStrategy : LayoutStrategy {
    override val id: String = "single-file"
    override val displayName: String = "Single file layout"

    override fun primaryPathFor(
        document: ThreadworkDocument,
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

    override fun layout(document: ThreadworkDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject {
        val nameSource = options.scopeNodeIds.singleOrNull()
            ?.let(document::getElementById)
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: projectName.trim().ifBlank { document.projectName() }
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
        document: ThreadworkDocument,
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

private fun Node.directLayoutSegments(document: ThreadworkDocument, rootPrefix: String): List<String> {
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
