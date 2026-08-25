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
import com.threadwork.core.model.projectName

/** Produces human-readable project documentation without participating in source compiler selection. */
class DocumentationCompiler : CompilerPlugin {
    override val id: String = "documentation"
    override val displayName: String = "Documentation Compiler"

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> = emptyList()

    override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult {
        val scope = DocumentationScope.resolve(document, options)
        val functional = functionalDocumentation(document, scope)
        val technical = technicalDocumentation(document, scope)
        val files = listOf(
            GeneratedFile(
                path = "${scope.fileBaseName}.SPEC.md",
                content = functional,
                originNodeId = scope.anchorNodeId,
                reason = "Composed functional specification",
                elementKind = GeneratedElementKind.Documentation,
            ),
            GeneratedFile(
                path = "${scope.fileBaseName}.TECH.md",
                content = technical,
                originNodeId = scope.anchorNodeId,
                reason = "Composed technical documentation",
                elementKind = GeneratedElementKind.Documentation,
            ),
        )
        return CompilationResult(
            generatedProject = GeneratedProject(scope.fileBaseName, files),
            diagnostics = emptyList(),
            success = true,
        )
    }

    private fun functionalDocumentation(document: ThreadworkDocument, scope: DocumentationScope): String =
        buildString {
            appendLine("# ${scope.title} Functional Specification")
            appendLine()
            appendLine("Generated from Threadwork specifications for `${scope.displayPath}`.")
            appendLine()
            appendLine("## Processing Nodes")
            appendLine()
            scope.processingNodes.forEach { node ->
                appendLine("### ${node.name.ifBlank { node.id.value }}")
                appendLine()
                appendLine("- **Path:** `${nodePath(document, node)}`")
                appendLine("- **Role:** `${node.stereotype(document).name}`")
                appendLine()
                appendSpecification(node.text.specification, node.text.specificationLanguageId)
                appendLinkTable("Inputs", dataInputs(document, node), document, incoming = true)
                appendLinkTable("Outputs", dataOutputs(document, node), document, incoming = false)
            }

            if (scope.links.isNotEmpty()) {
                appendLine("## Data Flows")
                appendLine()
                scope.links.forEach { linkNode ->
                    val link = linkNode.link ?: return@forEach
                    appendLine("### ${linkDisplayName(linkNode)}")
                    appendLine()
                    appendLine("- **Link ID:** `${escapeInlineCode(linkNode.id.value)}`")
                    appendLine("- **From:** `${endpoint(document, link.sourceNodeId, link.sourcePortName)}`")
                    appendLine("- **To:** `${endpoint(document, link.targetNodeId, link.targetPortName)}`")
                    appendLine("- **Classification:** `${LinkClassifier.classify(document, linkNode).name}`")
                    appendLine()
                    if (linkNode.text.specification.isNotBlank()) {
                        appendRichText(linkNode.text.specification, linkNode.text.specificationLanguageId)
                    }
                }
            }

            appendNotes(scope.notes, document)
        }.trimEnd() + "\n"

    private fun technicalDocumentation(document: ThreadworkDocument, scope: DocumentationScope): String =
        buildString {
            appendLine("# ${scope.title} Technical Documentation")
            appendLine()
            appendLine("Generated from Threadwork technical metadata and contracts for `${scope.displayPath}`.")
            appendLine()
            appendLine("## Processing Nodes")
            appendLine()
            scope.processingNodes.forEach { node ->
                appendLine("### ${node.name.ifBlank { node.id.value }}")
                appendLine()
                appendLine("- **Path:** `${nodePath(document, node)}`")
                appendLine("- **Role:** `${node.stereotype(document).name}`")
                appendDirectTechnology(node)
                appendLinkTable("Inputs", dataInputs(document, node), document, incoming = true)
                appendLinkTable("Outputs", dataOutputs(document, node), document, incoming = false)
                appendDependencies(document, node)
                appendLine("#### Running and Compiling Instructions")
                appendLine()
                if (node.text.aiInstructions.isBlank()) {
                    appendLine("_No instructions provided._")
                    appendLine()
                } else {
                    appendRichText(node.text.aiInstructions, node.text.aiInstructionsLanguageId)
                }
            }

            appendLine("## Data Contracts")
            appendLine()
            if (scope.links.isEmpty()) {
                appendLine("_No data contracts are present in this scope._")
                appendLine()
            } else {
                scope.links.forEach { linkNode -> appendContract(document, linkNode) }
            }
        }.trimEnd() + "\n"

    private fun StringBuilder.appendSpecification(specification: String, languageId: String) {
        appendLine("#### Specification")
        appendLine()
        if (specification.isBlank()) {
            appendLine("_No functional specification provided._")
            appendLine()
        } else {
            appendRichText(specification, languageId)
        }
    }

    private fun StringBuilder.appendDirectTechnology(node: Node) {
        val languageId = node.technology.languageId.trim()
        val technologyId = node.technology.technologyId.trim()
        val compilerId = node.technology.compilerId.trim()
        appendLine("- **Direct language:** ${markdownCodeOrUnspecified(languageId)}")
        appendLine(
            "- **Direct technology:** ${markdownCodeOrUnspecified(technologyId.takeUnless { it == VOID_TECHNOLOGY_ID }.orEmpty())}",
        )
        if (compilerId.isNotBlank()) appendLine("- **Direct compiler:** `${escapeInlineCode(compilerId)}`")
        appendLine()
    }

    private fun StringBuilder.appendLinkTable(
        heading: String,
        links: List<Node>,
        document: ThreadworkDocument,
        incoming: Boolean,
    ) {
        appendLine("#### $heading")
        appendLine()
        if (links.isEmpty()) {
            appendLine("_None._")
            appendLine()
            return
        }
        appendLine("| Name | Type | ${if (incoming) "Source" else "Target"} | Port |")
        appendLine("| --- | --- | --- | --- |")
        links.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            val endpointId = if (incoming) link.sourceNodeId else link.targetNodeId
            val endpointPort = if (incoming) link.sourcePortName else link.targetPortName
            val ownPort = if (incoming) link.targetPortName else link.sourcePortName
            appendLine(
                "| ${tableCell(linkNode.name)} | ${tableCell(link.typeName.ifBlank { "Unspecified" })} | " +
                    "${tableCell(document.nodes[endpointId]?.name ?: endpointId.value)} | ${tableCell(ownPort.ifBlank { endpointPort })} |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendDependencies(document: ThreadworkDocument, node: Node) {
        val dependencies = dependencyInputs(document, node)
        appendLine("#### Dependencies")
        appendLine()
        if (dependencies.isEmpty()) {
            appendLine("_None._")
            appendLine()
            return
        }
        appendLine("| Instance | Library | Classification |")
        appendLine("| --- | --- | --- |")
        dependencies.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            appendLine(
                "| ${tableCell(linkNode.name)} | ${tableCell(document.nodes[link.sourceNodeId]?.name ?: link.sourceNodeId.value)} | " +
                    "${LinkClassifier.classify(document, linkNode).name} |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendContract(document: ThreadworkDocument, linkNode: Node) {
        val link = linkNode.link ?: return
        appendLine("### ${linkDisplayName(linkNode)}")
        appendLine()
        appendLine("- **Link ID:** `${escapeInlineCode(linkNode.id.value)}`")
        appendLine("- **From:** `${endpoint(document, link.sourceNodeId, link.sourcePortName)}`")
        appendLine("- **To:** `${endpoint(document, link.targetNodeId, link.targetPortName)}`")
        appendLine("- **Transport:** `${escapeInlineCode(link.transportKind)}`")
        appendLine("- **Classification:** `${LinkClassifier.classify(document, linkNode).name}`")
        appendLine()
        if (link.payloadDefinition.isBlank()) {
            appendLine("_No payload type definition provided._")
            appendLine()
        } else {
            val languageId = linkNode.text.declarationLanguageId.trim().ifBlank {
                document.effectiveLanguageId(linkNode.id)
            }
            appendCodeBlock(link.payloadDefinition.trim(), languageId)
        }
        if (linkNode.text.specification.isNotBlank()) {
            appendLine("#### Contract Notes")
            appendLine()
            appendRichText(linkNode.text.specification, linkNode.text.specificationLanguageId)
        }
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

    private fun StringBuilder.appendNotes(notes: List<Node>, document: ThreadworkDocument) {
        if (notes.isEmpty()) return
        appendLine("## Notes")
        appendLine()
        notes.forEach { note ->
            appendLine("### ${note.name.ifBlank { note.id.value }}")
            appendLine()
            appendLine("_`${nodePath(document, note)}`_")
            appendLine()
            if (note.text.specification.isBlank()) {
                appendLine("_No note text provided._")
                appendLine()
            } else {
                appendRichText(note.text.specification, note.text.specificationLanguageId)
            }
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

    private fun linkDisplayName(linkNode: Node): String {
        val typeName = linkNode.link?.typeName.orEmpty().trim()
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

    private fun markdownCodeOrUnspecified(value: String): String =
        if (value.isBlank()) "_Not specified directly._" else "`${escapeInlineCode(value)}`"

    private fun escapeInlineCode(value: String): String = value.replace("`", "\\`")

    private val dependencyKinds = setOf(LinkStereotype.UsageImport, LinkStereotype.DependencyInjection)
}

private data class DocumentationScope(
    val title: String,
    val fileBaseName: String,
    val displayPath: String,
    val anchorNodeId: NodeId,
    val processingNodes: List<Node>,
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
            val processingNodes = ordered.filter { !it.isLink && it.kind != NodeKind.Note }
            val notes = ordered.filter { it.kind == NodeKind.Note }
            val links = document.nodes.values
                .filter(Node::isLink)
                .filter { linkNode ->
                    val link = linkNode.link
                    linkNode.id in includedIds ||
                        (link != null && (link.sourceNodeId in includedIds || link.targetNodeId in includedIds))
                }
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
