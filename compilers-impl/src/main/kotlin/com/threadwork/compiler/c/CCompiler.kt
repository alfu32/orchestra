package com.threadwork.compiler.c

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.NodeCompilerContext
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.generic.CompilerTemplateSet
import com.threadwork.compiler.generic.CompilerTemplateSetLoader
import com.threadwork.compiler.generic.TemplateSetCompiler
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.VOID_LAYOUT_STRATEGY_ID
import com.threadwork.core.model.effectiveLayoutStrategyId
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

    override fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet =
        TEMPLATES

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
        super.shouldSkipNode(context) || context.node.stereotype(context.document) == NodeStereotype.StaticFile

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
        val librarySource = super.hoistedDeclarationFor(context)
            .takeIf { node.stereotype(context.document) == NodeStereotype.ServiceLibrary }
            .orEmpty()
        return (wireTypes + librarySource)
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
                if (childTechnology != C_TECHNOLOGY_ID) {
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
            .filter { it.stereotype(document) != NodeStereotype.ServiceLibrary }
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
        val C_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/c/compiler.properties")
        val activeTypeDeclarations = ThreadLocal<Map<String, NodeId>?>()
    }
}
