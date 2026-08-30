package com.threadwork.compiler.documentation

import com.threadwork.compiler.api.CompilationResult
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.GeneratedProject
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.VOID_TECHNOLOGY_ID
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.linkTypeDisplayName
import com.threadwork.core.model.projectName
import com.threadwork.core.model.typeDisplayName

/** Preserves the two-file documentation API while delegating each document to its own compiler. */
class DocumentationCompiler : CompilerPlugin {
    override val id: String = "documentation"
    override val displayName: String = "Documentation Compiler"
    private val specificationCompiler = SpecificationDocumentationCompiler()
    private val componentCompiler = ComponentDocumentationCompiler()

    override fun supports(document: ThreadworkDocument): Boolean =
        specificationCompiler.supports(document) && componentCompiler.supports(document)

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        specificationCompiler.validate(document) + componentCompiler.validate(document)

    override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult {
        val specification = specificationCompiler.compile(document, options)
        val components = componentCompiler.compile(document, options)
        val files = specification.generatedProject?.files.orEmpty() + components.generatedProject?.files.orEmpty()
        val scope = DocumentationScope.resolve(document, options)
        return CompilationResult(
            generatedProject = GeneratedProject(scope.fileBaseName, files),
            diagnostics = specification.diagnostics + components.diagnostics,
            success = specification.success && components.success,
        )
    }

    fun componentFiches(document: ThreadworkDocument, nodeIds: Collection<NodeId>): String =
        componentCompiler.componentFiches(document, nodeIds)
}

/** Generates the detailed per-component annex and selected-node fiches. */
class ComponentDocumentationCompiler : CompilerPlugin {
    override val id: String = "documentation-components"
    override val displayName: String = "Component Annex Documentation Compiler"

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> = emptyList()

    override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult {
        val scope = DocumentationScope.resolve(document, options)
        val file = GeneratedFile(
            path = "${scope.fileBaseName}.COMPONENTS.md",
            content = componentDocumentation(document, scope),
            originNodeId = scope.anchorNodeId,
            reason = "Composed components breakdown",
            elementKind = GeneratedElementKind.Documentation,
        )
        return CompilationResult(
            generatedProject = GeneratedProject(scope.fileBaseName, listOf(file)),
            diagnostics = emptyList(),
            success = true,
        )
    }

    /** Builds the complete Markdown dossier for explicitly selected processing nodes only. */
    fun componentFiches(document: ThreadworkDocument, nodeIds: Collection<NodeId>): String {
        val nodes = nodeIds
            .distinct()
            .mapNotNull(document.nodes::get)
            .filter { !it.isLink && it.kind !in setOf(NodeKind.Note, NodeKind.Type) }
        if (nodes.isEmpty()) return ""
        return buildString {
            appendLine("# Selected Component Fiches")
            nodes.forEachIndexed { index, node ->
                if (index > 0) {
                    appendLine(PAGE_BREAK_MARKER)
                    appendLine()
                }
                appendComponentFiche(document, node)
            }
        }.trimEnd() + "\n"
    }

    private fun componentDocumentation(document: ThreadworkDocument, scope: DocumentationScope): String =
        buildString {
            appendLine("# ${scope.title} Annexes ; Components Breakdown")
            appendLine()
            appendLine("Generated from Threadwork component specifications for `${scope.displayPath}`.")
            appendLine()
            appendTableOfContents(scope.processingNodes.map { it.name.ifBlank { it.id.value } })
            scope.processingNodes.forEach { node ->
                appendLine(PAGE_BREAK_MARKER)
                appendLine()
                appendComponentFiche(document, node)
            }
        }.trimEnd() + "\n"

    private fun StringBuilder.appendComponentFiche(document: ThreadworkDocument, node: Node) {
        val inputs = dataInputs(document, node)
        val outputs = dataOutputs(document, node)
        appendLine("## ${node.name.ifBlank { node.id.value }}")
        appendLine()
        appendLine("- **Path:** `${nodePath(document, node)}`")
        appendLine("- **Role:** `${node.stereotype(document).name}`")
        appendLine()
        appendTechnology(document, node)
        appendCompositeOverview(document, node)
        appendSpecification(node.text.specification, node.text.specificationLanguageId)
        appendLinkTable("Inputs", inputs, document, incoming = true)
        appendComponentLinkDocumentation(document, inputs, "Incoming Link Contracts")
        appendLinkTable("Outputs", outputs, document, incoming = false)
        appendComponentLinkDocumentation(document, outputs, "Outgoing Link Contracts")
        appendUsedTypes(document, node)
        appendComponentDependencies(document, node)
        appendUsageInstructions(node)
        appendTestData(node)
    }

    private fun StringBuilder.appendCompositeOverview(document: ThreadworkDocument, node: Node) {
        if (!node.isComposite) return
        appendDirectChildren(document, node)
        appendLine("#### Flow Diagram (Mermaid)")
        appendLine()
        appendLine("```mermaid")
        appendLine("flowchart LR")

        val children = node.children
            .mapNotNull(document.nodes::get)
            .filterNot(Node::isLink)
            .sortedWith(compareBy<Node>({ it.name.lowercase() }, { it.id.value }))
        val childIds = children.mapTo(linkedSetOf()) { it.id }
        val mermaidIds = children.associate { it.id to mermaidNodeId(it.id) }
        children.forEach { child ->
            val label = mermaidLabel(child, document)
            appendLine("  ${mermaidIds.getValue(child.id)}[\"$label\"]")
        }

        document.nodes.values
            .asSequence()
            .filter { it.isLink && it.parentId == node.id }
            .mapNotNull { linkNode ->
                val link = linkNode.link ?: return@mapNotNull null
                val source = mermaidIds[link.sourceNodeId] ?: return@mapNotNull null
                val target = mermaidIds[link.targetNodeId] ?: return@mapNotNull null
                Triple(source, target, linkNode.name.ifBlank { linkNode.id.value })
            }
            .sortedWith(compareBy<Triple<String, String, String>>({ it.third.lowercase() }, { it.first }, { it.second }))
            .forEach { (source, target, name) ->
                appendLine("  $source -->|${mermaidArrowLabel(name)}| $target")
            }
        if (children.isEmpty()) {
            appendLine("  %% No direct child components.")
        } else if (document.nodes.values.none { linkNode ->
                if (!linkNode.isLink || linkNode.parentId != node.id) return@none false
                val link = linkNode.link ?: return@none false
                link.sourceNodeId in childIds && link.targetNodeId in childIds
            }) {
            appendLine("  %% No direct links between these components.")
        }
        appendLine("```")
        appendLine()
    }

    private fun StringBuilder.appendTableOfContents(entries: List<String>) {
        appendLine("## Contents")
        appendLine()
        if (entries.isEmpty()) {
            appendLine("_No components are present in this scope._")
        } else {
            entries.forEach { entry -> appendLine("- ${entry.trim()}") }
        }
        appendLine()
    }

    private fun StringBuilder.appendSpecification(specification: String, languageId: String) {
        if (specification.isBlank()) return
        appendLine("#### Specification")
        appendLine()
        appendRichText(specification, languageId)
    }

    private fun StringBuilder.appendTechnology(document: ThreadworkDocument, node: Node) {
        val directLanguageId = node.technology.languageId.trim()
        val directTechnologyId = node.technology.technologyId.trim()
            .takeUnless { it == VOID_TECHNOLOGY_ID }
            .orEmpty()
        val languageId = directLanguageId.ifBlank { document.effectiveLanguageId(node.id) }
        val technologyId = directTechnologyId.ifBlank { document.effectiveTechnologyId(node.id) }
        val compilerId = node.technology.compilerId.trim()
        appendLine("- **Language:** ${markdownTechnologyValue(languageId, directLanguageId.isBlank())}")
        appendLine("- **Technology:** ${markdownTechnologyValue(technologyId, directTechnologyId.isBlank())}")
        if (compilerId.isNotBlank()) appendLine("- **Direct compiler:** `${escapeInlineCode(compilerId)}`")
        appendLine()
    }

    private fun StringBuilder.appendLinkTable(
        heading: String,
        links: List<Node>,
        document: ThreadworkDocument,
        incoming: Boolean,
    ) {
        if (links.isEmpty()) return
        appendLine("#### $heading")
        appendLine()
        appendLine("| Name | Type | ${if (incoming) "Source" else "Target"} | Port |")
        appendLine("| --- | --- | --- | --- |")
        links.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            val endpointId = if (incoming) link.sourceNodeId else link.targetNodeId
            val endpointPort = if (incoming) link.sourcePortName else link.targetPortName
            val ownPort = if (incoming) link.targetPortName else link.sourcePortName
            appendLine(
                "| ${tableCell(linkNode.name)} | ${tableCell(document.linkTypeDisplayName(linkNode).ifBlank { "Unspecified" })} | " +
                    "${tableCell(document.nodes[endpointId]?.name ?: endpointId.value)} | ${tableCell(ownPort.ifBlank { endpointPort })} |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendComponentDependencies(document: ThreadworkDocument, node: Node) {
        val dependencies = dependencyInputs(document, node)
        if (dependencies.isEmpty()) return
        appendLine("#### Used Libraries and Capabilities")
        appendLine()
        dependencies.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            val library = document.nodes[link.sourceNodeId]
            appendLine("##### ${linkNode.name.ifBlank { linkNode.id.value }}")
            appendLine()
            appendLine("- **Library:** `${library?.name ?: link.sourceNodeId.value}`")
            appendLine("- **Library path:** `${library?.let { nodePath(document, it) } ?: link.sourceNodeId.value}`")
            appendLine("- **Classification:** `${LinkClassifier.classify(document, linkNode).name}`")
            appendLine("- **Interaction:** `${escapeInlineCode(link.interactionKind)}`")
            if (library != null) appendTechnology(document, library)
            if (library?.text?.specification?.isNotBlank() == true) {
                appendLine("**Library specification**")
                appendLine()
                appendRichText(library.text.specification, library.text.specificationLanguageId)
            }
            if (library?.text?.aiInstructions?.isNotBlank() == true) {
                appendLine("**Library usage instructions**")
                appendLine()
                appendRichText(library.text.aiInstructions, library.text.aiInstructionsLanguageId)
            }
        }
    }

    private fun StringBuilder.appendComponentLinkDocumentation(
        document: ThreadworkDocument,
        links: List<Node>,
        heading: String,
    ) {
        if (links.isEmpty()) return
        appendLine("#### $heading")
        appendLine()
        links.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            val type = document.nodes[NodeId(link.typeDefinitionId)]?.takeIf(Node::isType)
            appendLine("##### ${linkDisplayName(document, linkNode)}")
            appendLine()
            appendLine("- **Link ID:** `${escapeInlineCode(linkNode.id.value)}`")
            appendLine("- **From:** `${endpoint(document, link.sourceNodeId, link.sourcePortName)}`")
            appendLine("- **To:** `${endpoint(document, link.targetNodeId, link.targetPortName)}`")
            appendLine("- **Transport:** `${escapeInlineCode(link.transportKind)}`")
            appendLine("- **Interaction:** `${escapeInlineCode(link.interactionKind)}`")
            appendLine("- **Classification:** `${LinkClassifier.classify(document, linkNode).name}`")
            appendLine()
            if (type != null) {
                appendLine("**Shared type:** `${escapeInlineCode(type.name.ifBlank { type.id.value })}`")
                appendLine()
                val fields = type.typeDefinition?.fields.orEmpty()
                if (fields.isEmpty()) {
                    appendLine("_The shared type declares no fields._")
                    appendLine()
                } else {
                    appendLine("| Field | Type | Reference |")
                    appendLine("| --- | --- | --- |")
                    fields.forEach { field ->
                        appendLine("| ${tableCell(field.name)} | ${tableCell(document.typeDisplayName(field.typeId))} | ${if (field.isReference) "Yes" else "No"} |")
                    }
                    appendLine()
                }
            } else if (link.payloadDefinition.isNotBlank()) {
                appendCodeBlock(link.payloadDefinition.trim(), linkNode.text.declarationLanguageId)
            }
            if (linkNode.text.specification.isNotBlank()) {
                appendLine("**Link specification**")
                appendLine()
                appendRichText(linkNode.text.specification, linkNode.text.specificationLanguageId)
            }
        }
    }

    private fun StringBuilder.appendUsedTypes(document: ThreadworkDocument, node: Node) {
        val links = (dataInputs(document, node) + dataOutputs(document, node)).distinctBy { it.id }
        val documentedLinks = links.filter { linkNode ->
            val link = linkNode.link ?: return@filter false
            document.nodes[NodeId(link.typeDefinitionId)]?.isType == true || link.payloadDefinition.isNotBlank()
        }
        if (documentedLinks.isEmpty()) return
        appendLine("#### Used Types")
        appendLine()
        val describedTypeIds = mutableSetOf<String>()
        documentedLinks.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            val typeId = link.typeDefinitionId.trim()
            val type = document.nodes[NodeId(typeId)]?.takeIf(Node::isType)
            when {
                type != null && describedTypeIds.add(type.id.value) -> {
                    appendLine("##### ${type.name.ifBlank { type.id.value }}")
                    appendLine()
                    val fields = type.typeDefinition?.fields.orEmpty()
                    if (fields.isEmpty()) {
                        appendLine("_The shared type declares no fields._")
                        appendLine()
                    } else {
                        appendLine("| Field | Type | Reference |")
                        appendLine("| --- | --- | --- |")
                        fields.forEach { field ->
                            appendLine("| ${tableCell(field.name)} | ${tableCell(document.typeDisplayName(field.typeId))} | ${if (field.isReference) "Yes" else "No"} |")
                        }
                        appendLine()
                    }
                }
                typeId.isBlank() && link.payloadDefinition.isNotBlank() && describedTypeIds.add("payload:${linkNode.id.value}") -> {
                    appendLine("##### ${linkNode.name.ifBlank { linkNode.id.value }} payload")
                    appendLine()
                    appendCodeBlock(link.payloadDefinition.trim(), linkNode.text.declarationLanguageId)
                }
            }
        }
    }

    private fun StringBuilder.appendDirectChildren(document: ThreadworkDocument, node: Node) {
        val children = node.children
            .mapNotNull(document.nodes::get)
            .filterNot(Node::isLink)
            .sortedWith(compareBy<Node>({ it.name.lowercase() }, { it.id.value }))
        if (children.isEmpty()) return
        appendLine("#### Direct Children")
        appendLine()
        appendLine("| Name | Stereotype |")
        appendLine("| --- | --- |")
        children.forEach { child ->
                appendLine("| ${tableCell(child.name.ifBlank { child.id.value })} | ${tableCell(child.stereotype(document).name)} |")
            }
        appendLine()
    }

    private fun mermaidNodeId(id: NodeId): String =
        "node_${id.value.replace(Regex("[^A-Za-z0-9_]"), "_")}"

    private fun mermaidLabel(node: Node, document: ThreadworkDocument): String =
        "${node.name.ifBlank { node.id.value }} (${node.stereotype(document).name})"
            .replace('"', '\'')
            .replace(Regex("\\s+"), " ")

    private fun mermaidArrowLabel(value: String): String =
        value.replace('|', '/').replace(Regex("\\s+"), " ").trim()

    private fun StringBuilder.appendUsageInstructions(node: Node) {
        if (node.text.aiInstructions.isBlank()) return
        appendLine("#### Usage Instructions")
        appendLine()
        appendRichText(node.text.aiInstructions, node.text.aiInstructionsLanguageId)
    }

    private fun StringBuilder.appendTestData(node: Node) {
        if (node.text.tests.isBlank()) return
        appendLine("#### Test Data")
        appendLine()
        appendRichText(node.text.tests, node.text.testsLanguageId)
    }

    private fun StringBuilder.appendCodeBlock(content: String, languageId: String) {
        val longestRun = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        val language = languageId.trim().takeUnless { it == "plain" }.orEmpty()
        appendLine("$fence$language")
        appendLine(content)
        appendLine(fence)
        appendLine()
    }

    private fun StringBuilder.appendRichText(content: String, languageId: String) {
        if (languageId.isBlank() || languageId.equals("markdown", ignoreCase = true)) {
            appendLine(content.trim())
            appendLine()
        } else {
            appendCodeBlock(content.trim(), languageId)
        }
    }

    private fun dataInputs(document: ThreadworkDocument, node: Node): List<Node> =
        relatedLinks(document, node.incomingLinks).filter { LinkClassifier.classify(document, it) !in dependencyKinds }

    private fun dataOutputs(document: ThreadworkDocument, node: Node): List<Node> =
        relatedLinks(document, node.outgoingLinks).filter { LinkClassifier.classify(document, it) !in dependencyKinds }

    private fun dependencyInputs(document: ThreadworkDocument, node: Node): List<Node> =
        relatedLinks(document, node.incomingLinks).filter { LinkClassifier.classify(document, it) in dependencyKinds }

    private fun relatedLinks(document: ThreadworkDocument, ids: List<NodeId>): List<Node> =
        ids.mapNotNull(document.nodes::get).filter(Node::isLink).sortedBy { it.name.lowercase() }

    private fun linkDisplayName(document: ThreadworkDocument, linkNode: Node): String {
        val typeName = document.linkTypeDisplayName(linkNode)
        return if (typeName.isBlank()) linkNode.name else "${linkNode.name}:$typeName"
    }

    private fun endpoint(document: ThreadworkDocument, nodeId: NodeId, portName: String): String {
        val nodeName = document.nodes[nodeId]?.name ?: nodeId.value
        return listOf(nodeName, portName).filter(String::isNotBlank).joinToString(".")
    }

    private fun nodePath(document: ThreadworkDocument, node: Node): String {
        val names = mutableListOf<String>()
        val visited = mutableSetOf<NodeId>()
        var current: Node? = node
        while (current != null && visited.add(current.id)) {
            names += current.name.ifBlank { current.id.value }
            current = current.parentId?.let(document.nodes::get)
        }
        return names.asReversed().joinToString("/", prefix = "/")
    }

    private fun tableCell(value: String): String =
        value.replace("\\", "\\\\").replace("|", "\\|").replace(Regex("\\s*\\R\\s*"), " ").trim()

    private fun markdownTechnologyValue(value: String, inferred: Boolean): String =
        when {
            value.isBlank() -> "_Not specified._"
            inferred -> "Inferred (`${escapeInlineCode(value)}`)"
            else -> "`${escapeInlineCode(value)}`"
        }

    private fun escapeInlineCode(value: String): String = value.replace("`", "\\`")

    private val dependencyKinds = setOf(
        LinkStereotype.UsageImport,
        LinkStereotype.DependencyInjection,
        LinkStereotype.SourceCapability,
        LinkStereotype.RunnableCapability,
    )

    private companion object {
        const val PAGE_BREAK_MARKER = "<!-- threadwork:page-break -->"
    }
}

internal data class DocumentationScope(
    val title: String,
    val fileBaseName: String,
    val displayPath: String,
    val anchorNodeId: NodeId,
    val processingNodes: List<Node>,
    val types: List<Node>,
    val links: List<Node>,
    val notes: List<Node>,
) {
    companion object {
        fun resolve(document: ThreadworkDocument, options: CompilerOptions): DocumentationScope {
            val selectedIds = options.scopeNodeIds.filterTo(linkedSetOf()) { it in document.nodes }
            val requestedRoots = if (selectedIds.isEmpty()) {
                listOf(document.rootNodeId)
            } else {
                selectedIds.filterNot { nodeId -> hasSelectedAncestor(document, nodeId, selectedIds) }
            }
            val includedIds = linkedSetOf<NodeId>()
            requestedRoots.forEach { includeDescendants(document, it, includedIds) }
            val anchor = anchorNode(document, selectedIds, requestedRoots)
            val ordered = orderedNodes(document, requestedRoots, includedIds)
            val processingNodes = ordered.filter { !it.isLink && it.kind !in setOf(NodeKind.Note, NodeKind.Type) }
            val notes = ordered.filter { it.kind == NodeKind.Note }
            val links = document.nodes.values
                .filter(Node::isLink)
                .filter { linkNode ->
                    val link = linkNode.link
                    linkNode.id in includedIds ||
                        (link != null && (link.sourceNodeId in includedIds || link.targetNodeId in includedIds))
                }
                .sortedWith(compareBy<Node>({ nodeDepth(document, it) }, { it.name.lowercase() }, { it.id.value }))
            val referencedTypeIds = referencedTypeIds(document, links)
            val types = document.nodes.values
                .filter { it.kind == NodeKind.Type && (it.id in includedIds || it.id in referencedTypeIds) }
                .sortedWith(compareBy<Node>({ nodeDepth(document, it) }, { it.name.lowercase() }, { it.id.value }))
            val title = if (selectedIds.isEmpty()) {
                options.projectName?.trim().takeUnless(String?::isNullOrBlank) ?: document.projectName()
            } else {
                anchor.name.trim().ifBlank { document.projectName() }
            }
            return DocumentationScope(
                title = title,
                fileBaseName = safeFileBaseName(title),
                displayPath = nodePath(document, anchor),
                anchorNodeId = anchor.id,
                processingNodes = processingNodes,
                types = types,
                links = links,
                notes = notes,
            )
        }

        private fun anchorNode(
            document: ThreadworkDocument,
            selectedIds: Set<NodeId>,
            requestedRoots: List<NodeId>,
        ): Node {
            if (selectedIds.isEmpty()) return document.nodes.getValue(document.rootNodeId)
            val soleRoot = requestedRoots.singleOrNull()?.let(document.nodes::get)
            if (soleRoot != null && !soleRoot.isLink && soleRoot.children.isNotEmpty()) return soleRoot
            val parentIds = requestedRoots.mapNotNull { document.nodes[it]?.parentId }.distinct()
            return parentIds.singleOrNull()?.let(document.nodes::get)
                ?: soleRoot
                ?: document.nodes.getValue(document.rootNodeId)
        }

        private fun hasSelectedAncestor(document: ThreadworkDocument, nodeId: NodeId, selectedIds: Set<NodeId>): Boolean {
            val visited = mutableSetOf<NodeId>()
            var current = document.nodes[nodeId]?.parentId
            while (current != null && visited.add(current)) {
                if (current in selectedIds) return true
                current = document.nodes[current]?.parentId
            }
            return false
        }

        private fun includeDescendants(document: ThreadworkDocument, nodeId: NodeId, included: MutableSet<NodeId>) {
            if (!included.add(nodeId)) return
            document.nodes[nodeId]?.children?.forEach { includeDescendants(document, it, included) }
        }

        private fun referencedTypeIds(document: ThreadworkDocument, links: List<Node>): Set<NodeId> {
            val result = linkedSetOf<NodeId>()
            val pending = ArrayDeque(
                links.mapNotNull { it.link?.typeDefinitionId?.takeIf(String::isNotBlank) }.map(::NodeId),
            )
            while (pending.isNotEmpty()) {
                val typeId = pending.removeFirst()
                val type = document.nodes[typeId]?.takeIf(Node::isType) ?: continue
                if (!result.add(typeId)) continue
                type.typeDefinition?.fields.orEmpty()
                    .map { NodeId(it.typeId) }
                    .filter { document.nodes[it]?.isType == true }
                    .forEach(pending::addLast)
            }
            return result
        }

        private fun orderedNodes(
            document: ThreadworkDocument,
            roots: List<NodeId>,
            includedIds: Set<NodeId>,
        ): List<Node> {
            val result = mutableListOf<Node>()
            val visited = mutableSetOf<NodeId>()
            fun visit(nodeId: NodeId) {
                if (nodeId !in includedIds || !visited.add(nodeId)) return
                val node = document.nodes[nodeId] ?: return
                result += node
                node.children.forEach(::visit)
            }
            roots.forEach(::visit)
            includedIds.filterNot(visited::contains).sortedBy(NodeId::value).forEach(::visit)
            return result
        }

        private fun nodeDepth(document: ThreadworkDocument, node: Node): Int {
            var depth = 0
            val visited = mutableSetOf<NodeId>()
            var current = node.parentId
            while (current != null && visited.add(current)) {
                depth++
                current = document.nodes[current]?.parentId
            }
            return depth
        }

        private fun nodePath(document: ThreadworkDocument, node: Node): String {
            val names = mutableListOf<String>()
            val visited = mutableSetOf<NodeId>()
            var current: Node? = node
            while (current != null && visited.add(current.id)) {
                names += current.name.ifBlank { current.id.value }
                current = current.parentId?.let(document.nodes::get)
            }
            return names.asReversed().joinToString("/", prefix = "/")
        }

        private fun safeFileBaseName(name: String): String =
            name.trim()
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .trim('.', ' ')
                .ifBlank { "project" }
    }
}
