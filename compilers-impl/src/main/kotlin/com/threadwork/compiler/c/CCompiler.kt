package com.threadwork.compiler.c

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.CompilerCodeIntelligence
import com.threadwork.compiler.api.CompilerCodeSymbol
import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.compiler.api.NodeCompilerContext
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.api.compilerArgumentName
import com.threadwork.compiler.api.defaultCodeIntelligence
import com.threadwork.compiler.generic.CompilerTemplateSet
import com.threadwork.compiler.generic.CompilerTemplateSetLoader
import com.threadwork.compiler.generic.TemplateSetCompiler
import com.threadwork.compiler.generic.compilerTemplateOverrides
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.VOID_LAYOUT_STRATEGY_ID
import com.threadwork.core.model.effectiveLayoutStrategyId
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.getElementById
import com.threadwork.core.validation.DocumentValidator

class CCompiler : TemplateSetCompiler() {
    override val id: String = "c-compiler"
    override val displayName: String = "C17 Compiler"
    override val supportedLanguageIds: Set<String> = setOf("c")
    override val supportedTechnologyIds: Set<String> = setOf("c-native")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("c", "c-native"))
    override val supportedLayoutStrategyIds: Set<String> = setOf(SingleFileLayoutStrategy.id)
    override val magicFileNames: Set<String> = TEMPLATES.staticFileNames

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        DocumentValidator.validate(document) + validateCModel(document)

    override fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet {
        val overrides = compilerTemplateOverrides(document)
        return TEMPLATES.overlay(
            CompilerTemplateSet(
                templates = overrides.templates,
                projectFiles = overrides.projectFiles,
            ),
        )
    }

    override fun beforeTemplateCompile(document: ThreadworkDocument, options: CompilerOptions) {
        val scope = compilationScope(document, options)
        activeTypeDeclarations.set(
            document.nodes.values
                .asSequence()
                .filter { it.id in scope && it.isLink }
                .filter { document.effectiveTechnologyId(it.id) == C_TECHNOLOGY_ID }
                .filter { !it.link?.typeName.isNullOrBlank() && !it.link?.payloadDefinition.isNullOrBlank() }
                .groupBy { it.link!!.typeName.trim() }
                .mapValues { (_, links) -> links.minBy { it.id.value }.id },
        )
    }

    override fun afterTemplateCompile(document: ThreadworkDocument, options: CompilerOptions) {
        activeTypeDeclarations.remove()
    }

    override fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        super.shouldSkipNode(context) ||
            context.node.stereotype(context.document) == NodeStereotype.StaticFile ||
            isForeignStandaloneFile(context)

    private fun isForeignStandaloneFile(context: NodeCompilerContext): Boolean =
        isForeignStandaloneFile(context.document, context.node, context.layoutStrategy.id)

    private fun isForeignStandaloneFile(
        document: ThreadworkDocument,
        node: Node,
        layoutId: String = document.effectiveLayoutStrategyId(node.id),
    ): Boolean {
        val technologyId = document.effectiveTechnologyId(node.id).trim()
        // These nodes are owned by the aggregate compiler.  They are directory
        // boundaries as well as literal-file boundaries, so their descendants
        // must never be assembled into the C source file.
        if (technologyId in setOf("file-export", "multi-tech")) return true
        if (node.isLink || node.children.isNotEmpty()) return false
        return layoutId == SingleFileLayoutStrategy.id &&
            document.effectiveLanguageId(node.id).trim().lowercase() !in setOf("", "c")
    }

    override fun codeIntelligence(document: ThreadworkDocument, node: Node): CompilerCodeIntelligence {
        val libraryLinkIds = node.incomingLinks.filter { linkId ->
            document.nodes[linkId]?.let { LinkClassifier.classify(document, it) } in LIBRARY_LINK_STEREOTYPES
        }.toSet()
        val defaults = defaultCodeIntelligence(document, node)
        val functions = libraryLinkIds.flatMap { linkId ->
            val linkNode = document.nodes[linkId] ?: return@flatMap emptyList()
            val sourceNode = linkNode.link?.sourceNodeId?.let(document.nodes::get) ?: return@flatMap emptyList()
            val prefix = compilerArgumentName(linkNode.name)
            CServiceFunctionDiscovery.discover(sourceNode.text.declaration).map { function ->
                CompilerCodeSymbol(
                    name = "${prefix}__${function.name}",
                    kind = CompilerCodeSymbolKind.LibraryFunction,
                    typeName = function.returnType,
                    detail = function.signature,
                    documentation = "Function '${function.name}' supplied by C service link '${linkNode.name}'.",
                    originNodeId = linkNode.id,
                )
            }
        }
        val runtimeSymbols = listOf(
            CompilerCodeSymbol(
                name = "threadwork_running",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "volatile sig_atomic_t",
                detail = "C runtime ingress flag",
                documentation = "Set to zero by SIGINT, SIGTERM, or the generated one-shot shutdown. C generator nodes return before producing new packets when this flag is zero.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_transit",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "unsigned long long",
                detail = "C runtime completed transport count",
                documentation = "Incremented after each non-empty modeled data-link transport completes.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_network_shutdown_begin",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "void threadwork_network_shutdown_begin(unsigned int idle_ticks)",
                detail = "begin network drain monitoring",
                documentation = "Begins a bounded idle-window check for residual modeled link traffic.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_network_has_recent_transit",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "int threadwork_network_has_recent_transit(void)",
                detail = "continue network drain while traffic is recent",
                documentation = "Returns non-zero while link transports are still active or the configured idle window has not elapsed.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_install_shutdown_signal_handlers",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "int threadwork_install_shutdown_signal_handlers(void)",
                detail = "install SIGINT and SIGTERM shutdown handling",
                documentation = "Installs the standard C runtime signal handlers that close generator ingress by setting threadwork_running to zero.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_shutdown_request",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "void threadwork_shutdown_request(void)",
                detail = "close generator ingress",
                documentation = "Requests a graceful shutdown by setting threadwork_running to zero while processors continue draining modeled links.",
            ),
        )
        return defaults.copy(
            symbols = (
                defaults.symbols.filterNot { it.originNodeId in libraryLinkIds } + functions + runtimeSymbols
                ).distinctBy { it.name to it.kind },
        )
    }

    override fun generatedEntitySymbols(document: ThreadworkDocument, node: Node): List<CompilerCodeSymbol> {
        if (node.isLink || node.stereotype(document) == NodeStereotype.ServiceLibrary) return emptyList()
        val symbol = indexedNodeSymbol(document, node)
        return listOf(
            CompilerCodeSymbol(
                name = "tw_init_$symbol",
                kind = CompilerCodeSymbolKind.GeneratedFunction,
                detail = "generated child initialization function",
                documentation = "Initialize direct child '${node.name}' in the C execution context.",
                originNodeId = node.id,
            ),
            CompilerCodeSymbol(
                name = "tw_run_$symbol",
                kind = CompilerCodeSymbolKind.GeneratedFunction,
                detail = "generated child execution function",
                documentation = "Run direct child '${node.name}' in the C execution context.",
                originNodeId = node.id,
            ),
        )
    }

    override fun generatedFunctionHeader(
        document: ThreadworkDocument,
        node: Node,
        section: NodeTextSection,
    ): String {
        if (node.isLink || node.stereotype(document) == NodeStereotype.ServiceLibrary) return ""
        val functionPrefix = when (section) {
            NodeTextSection.Declaration -> "run"
            NodeTextSection.Instantiation -> "init"
            else -> return ""
        }
        val arguments = mutableListOf("threadwork_context *context")
        node.incomingLinks.mapNotNull(document.nodes::get).forEach { linkNode ->
            when (LinkClassifier.classify(document, linkNode)) {
                LinkStereotype.UsageImport,
                LinkStereotype.DependencyInjection -> Unit
                LinkStereotype.SourceCapability -> {
                    val dependency = dependencySymbol(document, linkNode)
                    arguments += "${dependency.replaceFirstChar(Char::uppercase)}Capability *${compilerArgumentName(linkNode.name)}"
                }
                LinkStereotype.RunnableCapability -> Unit
                else -> arguments += "threadwork_buffer *${compilerArgumentName(linkNode.name)}"
            }
        }
        node.outgoingLinks.mapNotNull(document.nodes::get)
            .filterNot { LinkClassifier.isCapability(document, it) }
            .forEach { arguments += "threadwork_buffer *${compilerArgumentName(it.name)}" }
        return "static int tw_${functionPrefix}_${indexedNodeSymbol(document, node)}(${arguments.joinToString(", ")}) {"
    }

    override fun hoistedDeclarationFor(context: NodeCompilerContext): String {
        val node = context.node
        if (node.kind == NodeKind.Type) return super.hoistedDeclarationFor(context)
        if (node.isLink) {
            // Type declarations are deduplicated separately. Every data link still owns
            // a distinct pair of buffers and a distinct transport function.
            return super.hoistedDeclarationFor(context)
        }
        val wireTypes = (node.incomingLinks + node.outgoingLinks)
            .mapNotNull(context.document.nodes::get)
            .filter { context.document.effectiveTechnologyId(it.id) == C_TECHNOLOGY_ID }
            .filter { linkNode ->
                val typeName = linkNode.link?.typeName?.trim().orEmpty()
                typeName.isNotBlank() && linkNode.id == firstDeclarationNodeId(context.document, typeName)
            }
            .mapNotNull { it.link?.payloadDefinition?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        val isLibrary = node.stereotype(context.document) == NodeStereotype.ServiceLibrary
        val librarySource = super.hoistedDeclarationFor(context).takeIf { isLibrary }.orEmpty()
        val libraryAliases = if (isLibrary) libraryAliasesFor(context.document, node) else ""
        return (wireTypes + librarySource + libraryAliases)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n\n")
    }

    private fun validateCModel(document: ThreadworkDocument): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val cNodes = document.nodes.values.filter { document.effectiveTechnologyId(it.id) == C_TECHNOLOGY_ID }

        cNodes.forEach { node ->
            val layoutId = document.effectiveLayoutStrategyId(node.id)
            if (layoutId !in setOf(VOID_LAYOUT_STRATEGY_ID, SingleFileLayoutStrategy.id)) {
                diagnostics += error(
                    node.id,
                    "C compilation supports only the single-file layout; '${node.name}' resolves to '$layoutId'.",
                )
            }
        }

        cNodes.filter(Node::isLink).filter {
            LinkClassifier.classify(document, it) == LinkStereotype.RunnableCapability
        }.forEach { link ->
            diagnostics += error(
                link.id,
                "C17 cannot expose a type-safe runtime-compiled runnable capability; use src or an explicit toolchain adapter.",
            )
        }

        cNodes.filterNot(Node::isLink).forEach { parent ->
            parent.children.mapNotNull(document.nodes::get).filterNot(Node::isLink).forEach { child ->
                val childTechnology = document.effectiveTechnologyId(child.id)
                if (childTechnology != C_TECHNOLOGY_ID && !isForeignStandaloneFile(document, child)) {
                    diagnostics += error(
                        child.id,
                        "C composite '${parent.name}' directly contains '${child.name}' from technology " +
                            "'$childTechnology' without an FFI adapter.",
                    )
                }
            }
        }

        cNodes
            .filter { it.kind in setOf(NodeKind.Node, NodeKind.Processor, NodeKind.Group) }
            .filter {
                it.stereotype(document) !in setOf(
                    NodeStereotype.ServiceLibrary,
                    NodeStereotype.CompilerTemplate,
                )
            }
            .filter { node -> node.text.declaration.lineSequence().any(::isIncludeDirective) }
            .forEach { node ->
                diagnostics += error(
                    node.id,
                    "C include directives are only valid in service-library declarations; " +
                        "'${node.name}' is executable processing code.",
                )
            }

        val typedLinks = cNodes.filter(Node::isLink).filter { linkNode ->
            val link = linkNode.link
            val declaredType = link?.typeDefinitionId
                ?.takeIf(String::isNotBlank)
                ?.let { document.getElementById(it) }
            if (declaredType != null) {
                if (!C_IDENTIFIER.matches(declaredType.name.trim())) {
                    diagnostics += error(
                        linkNode.id,
                        "C type name '${declaredType.name}' is not a valid C identifier.",
                    )
                }
                return@filter false
            }
            val hasTypeName = !link?.typeName.isNullOrBlank()
            val hasDefinition = !link?.payloadDefinition.isNullOrBlank()
            if (hasTypeName != hasDefinition) {
                diagnostics += error(
                    linkNode.id,
                    "C link '${linkNode.name}' must define both typeName and payloadDefinition, or neither.",
                )
            }
            if (hasTypeName && !C_IDENTIFIER.matches(link!!.typeName.trim())) {
                diagnostics += error(
                    linkNode.id,
                    "C link type name '${link.typeName}' is not a valid C identifier.",
                )
            }
            hasTypeName && hasDefinition
        }

        typedLinks.groupBy { it.link!!.typeName.trim() }.forEach { (typeName, links) ->
            val definitions = links.map { normalizeDefinition(it.link!!.payloadDefinition) }.distinct()
            if (definitions.size > 1) {
                links.forEach { link ->
                    diagnostics += error(
                        link.id,
                        "C type '$typeName' has conflicting payload definitions.",
                    )
                }
            }
        }

        cNodes.filter(Node::isLink).forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            listOf(link.sourceNodeId, link.targetNodeId).forEach { endpointId ->
                val endpointTechnology = document.effectiveTechnologyId(endpointId)
                if (endpointTechnology !in setOf("", C_TECHNOLOGY_ID)) {
                    diagnostics += error(
                        linkNode.id,
                        "C link '${linkNode.name}' crosses into technology '$endpointTechnology' without an FFI adapter.",
                    )
                }
            }
        }
        return diagnostics
    }

    private fun firstDeclarationNodeId(document: ThreadworkDocument, typeName: String): NodeId? =
        activeTypeDeclarations.get()?.get(typeName)
            ?: document.nodes.values
                .asSequence()
                .filter(Node::isLink)
                .filter { document.effectiveTechnologyId(it.id) == C_TECHNOLOGY_ID }
                .filter { it.link?.typeName?.trim() == typeName && !it.link?.payloadDefinition.isNullOrBlank() }
                .minByOrNull { it.id.value }
                ?.id

    private fun libraryAliasesFor(document: ThreadworkDocument, libraryNode: Node): String {
        val functions = CServiceFunctionDiscovery.discover(libraryNode.text.declaration)
        if (functions.isEmpty()) return ""
        return libraryNode.outgoingLinks
            .mapNotNull(document.nodes::get)
            .filter { LinkClassifier.classify(document, it) in LIBRARY_LINK_STEREOTYPES }
            .flatMap { linkNode ->
                val prefix = compilerArgumentName(linkNode.name)
                functions.map { function -> function.pointerDeclaration("${prefix}__${function.name}") }
            }
            .distinct()
            .joinToString("\n")
    }

    private fun indexedNodeSymbol(document: ThreadworkDocument, node: Node): String {
        val nodes = document.nodes.values.filterNot(Node::isLink).sortedBy { it.id.value }
        val index = nodes.indexOfFirst { it.id == node.id }.takeIf { it >= 0 }?.plus(1) ?: 1
        val modelName = node.name.trim()
        val sanitized = if (C_IDENTIFIER.matches(modelName)) {
            modelName
        } else {
            modelName.replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
        }.ifBlank { "node" }.lowercase()
        val symbol = if (sanitized.first().isDigit()) "_$sanitized" else sanitized
        return "${symbol}_$index"
    }

    private fun dependencySymbol(document: ThreadworkDocument, linkNode: Node): String {
        val source = linkNode.link?.sourceNodeId?.let(document.nodes::get)
        val dependencies = source?.outgoingLinks.orEmpty().mapNotNull(document.nodes::get).filter {
            LinkClassifier.isCapability(document, it)
        }
        val index = dependencies.indexOfFirst { it.id == linkNode.id }.takeIf { it >= 0 }?.plus(1) ?: 1
        return "${compilerArgumentName(linkNode.name)}$index"
    }

    private fun compilationScope(document: ThreadworkDocument, options: CompilerOptions): Set<NodeId> {
        if (options.scopeNodeIds.isEmpty()) return document.nodes.keys
        val result = linkedSetOf<NodeId>()
        fun includeDescendants(nodeId: NodeId) {
            val node = document.nodes[nodeId] ?: return
            if (result.add(nodeId) && !node.isLink) node.children.forEach(::includeDescendants)
        }
        options.scopeNodeIds.forEach(::includeDescendants)
        if (options.includeScopeAncestors) {
            options.scopeNodeIds.forEach { nodeId ->
                var parentId = document.nodes[nodeId]?.parentId
                while (parentId != null) {
                    result += parentId
                    parentId = document.nodes[parentId]?.parentId
                }
            }
        }
        return result
    }

    private fun error(nodeId: NodeId, message: String): Diagnostic =
        Diagnostic(
            severity = DiagnosticSeverity.Error,
            message = message,
            nodeId = nodeId,
            sourcePluginId = id,
        )

    private fun normalizeDefinition(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun isIncludeDirective(line: String): Boolean =
        line.trimStart().matches(Regex("#\\s*include(?:\\s|<|\").*"))

    private companion object {
        const val C_TECHNOLOGY_ID = "c-native"
        val LIBRARY_LINK_STEREOTYPES = setOf(LinkStereotype.UsageImport, LinkStereotype.DependencyInjection)
        val C_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/c/compiler.properties")
        val activeTypeDeclarations = ThreadLocal<Map<String, NodeId>?>()
    }
}
