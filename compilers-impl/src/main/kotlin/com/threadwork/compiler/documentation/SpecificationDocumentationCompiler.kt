package com.threadwork.compiler.documentation

import com.threadwork.compiler.api.CompilationResult
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.GeneratedProject
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.VOID_TECHNOLOGY_ID
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.effectiveTextLanguageId
import com.threadwork.core.model.linkTypeDisplayName
import com.threadwork.core.model.typeDisplayName

/** Produces the human-readable functional and technical project narrative. */
class SpecificationDocumentationCompiler : CompilerPlugin {
    override val id: String = "documentation-specification"
    override val displayName: String = "Narrative Specification Documentation Compiler"

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> = emptyList()

    override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult {
        val scope = DocumentationScope.resolve(document, options)
        val file = GeneratedFile(
            path = "${scope.fileBaseName}.SPEC.md",
            content = render(document, scope),
            originNodeId = scope.anchorNodeId,
            reason = "Composed narrative functional and technical specification",
            elementKind = GeneratedElementKind.Documentation,
        )
        return CompilationResult(
            generatedProject = GeneratedProject(scope.fileBaseName, listOf(file)),
            diagnostics = emptyList(),
            success = true,
        )
    }

    private fun render(document: ThreadworkDocument, scope: DocumentationScope): String = buildString {
        appendLine("# ${scope.title} Functional and Technical Specification")
        appendLine()
        appendLine("This specification describes the behavior and current implementation of `${scope.displayPath}`.")
        appendLine()
        appendContents(document, scope)
        if (scope.processingNodes.isNotEmpty()) {
            appendLine("## Processing Nodes")
            appendLine()
            scope.processingNodes.forEach { node -> appendNodeNarrative(document, node) }
        }
        appendAnnexes(document, scope)
        appendNotes(document, scope.notes)
    }.trimEnd() + "\n"

    private fun StringBuilder.appendContents(document: ThreadworkDocument, scope: DocumentationScope) {
        val services = serviceLibraries(document, scope)
        val entries = scope.processingNodes.map { it.displayName() } + listOfNotNull(
            "Shared Types".takeIf { scope.types.isNotEmpty() },
            "Service Libraries".takeIf { services.isNotEmpty() },
            "Notes".takeIf { scope.notes.any { it.text.specification.isNotBlank() } },
        )
        if (entries.isEmpty()) return
        appendLine("## Contents")
        appendLine()
        entries.forEach { appendLine("- $it") }
        appendLine()
    }

    private fun StringBuilder.appendNodeNarrative(document: ThreadworkDocument, node: Node) {
        val inputs = dataInputs(document, node)
        val outputs = dataOutputs(document, node)
        val dependencies = dependencyInputs(document, node)
        appendLine("### ${node.displayName()}")
        appendLine()
        if (node.text.specification.isNotBlank()) {
            appendLine("#### Specification")
            appendLine()
            appendRichText(node.text.specification, node.text.specificationLanguageId)
        }
        appendCurrentImplementation(document, node)
        appendTechnologyNarrative(document, node)
        appendInterfaceNarrative(document, node, inputs, outputs, dependencies)
        appendComposition(document, node)
        if (node.text.aiInstructions.isNotBlank()) {
            appendLine("#### Usage")
            appendLine()
            appendRichText(node.text.aiInstructions, node.text.aiInstructionsLanguageId)
        }
        if (node.text.tests.isNotBlank()) {
            appendLine("#### Test Data and Verification")
            appendLine()
            appendRichText(node.text.tests, node.text.testsLanguageId)
        }
    }

    private fun StringBuilder.appendCurrentImplementation(document: ThreadworkDocument, node: Node) {
        if (node.text.declaration.isNotBlank()) {
            appendLine("#### Current Implementation")
            appendLine()
            appendCodeBlock(
                node.text.declaration.trim(),
                document.effectiveTextLanguageId(node.id, NodeTextSection.Declaration),
            )
        }
        if (node.text.instantiation.isNotBlank()) {
            appendLine("#### Current Initialization")
            appendLine()
            appendCodeBlock(
                node.text.instantiation.trim(),
                document.effectiveTextLanguageId(node.id, NodeTextSection.Instantiation),
            )
        }
    }

    private fun StringBuilder.appendTechnologyNarrative(document: ThreadworkDocument, node: Node) {
        val role = humanize(node.stereotype(document).name)
        val language = document.effectiveLanguageId(node.id)
        val technology = document.effectiveTechnologyId(node.id)
        val directLanguage = node.technology.languageId.trim()
        val directTechnology = node.technology.technologyId.trim().takeUnless { it == VOID_TECHNOLOGY_ID }.orEmpty()
        appendLine("#### Implementation Profile")
        appendLine()
        append("`${node.displayName()}` is a $role at `${nodePath(document, node)}`")
        when {
            language.isNotBlank() && technology.isNotBlank() ->
                append(". It is implemented in `${escapeInlineCode(language)}` for `${escapeInlineCode(technology)}`")
            language.isNotBlank() -> append(". It is implemented in `${escapeInlineCode(language)}`")
            technology.isNotBlank() -> append(". It targets `${escapeInlineCode(technology)}`")
            else -> append(". Its implementation language and technology are not specified")
        }
        val inferred = listOfNotNull(
            "language".takeIf { directLanguage.isBlank() && language.isNotBlank() },
            "technology".takeIf { directTechnology.isBlank() && technology.isNotBlank() },
        )
        if (inferred.isNotEmpty()) append("; ${inferred.joinToString(" and ")} ${if (inferred.size == 1) "is" else "are"} inherited")
        appendLine(".")
        appendLine()
    }

    private fun StringBuilder.appendInterfaceNarrative(
        document: ThreadworkDocument,
        node: Node,
        inputs: List<Node>,
        outputs: List<Node>,
        dependencies: List<Node>,
    ) {
        if (inputs.isEmpty() && outputs.isEmpty() && dependencies.isEmpty()) return
        appendLine("#### Interfaces and Dependencies")
        appendLine()
        val clauses = buildList {
            if (inputs.isNotEmpty()) add("receives the following inputs: ${inputs.joinNames()}")
            if (dependencies.isNotEmpty()) {
                add("is provided access to ${dependencies.joinToString(", ") { dependencyReference(document, it) }}")
            }
            if (outputs.isNotEmpty()) add("produces the following outputs: ${outputs.joinNames()}")
        }
        appendLine("`${node.displayName()}` ${clauses.joinToString("; ")}.")
        appendLine()
        inputs.forEach { appendDataRelationship(document, it, incoming = true) }
        outputs.forEach { appendDataRelationship(document, it, incoming = false) }
        dependencies.forEach { appendDependencyRelationship(document, it) }
    }

    private fun StringBuilder.appendDataRelationship(
        document: ThreadworkDocument,
        linkNode: Node,
        incoming: Boolean,
    ) {
        val link = linkNode.link ?: return
        val endpointId = if (incoming) link.sourceNodeId else link.targetNodeId
        val endpoint = document.nodes[endpointId]?.displayName() ?: endpointId.value
        val typeName = document.linkTypeDisplayName(linkNode).ifBlank { "unspecified" }
        appendLine("##### ${if (incoming) "Input" else "Output"}: ${linkNode.displayName()}")
        appendLine()
        if (incoming) {
            append("`${linkNode.displayName()}` receives information packets of type `${escapeInlineCode(typeName)}` from `$endpoint`")
        } else {
            append("`${linkNode.displayName()}` publishes information packets of type `${escapeInlineCode(typeName)}` to `$endpoint`")
        }
        appendLine(" through `${endpointPath(document, linkNode)}`.")
        appendLine()
        appendTypeDescription(document, linkNode)
        if (linkNode.text.specification.isNotBlank()) {
            appendRichText(linkNode.text.specification, linkNode.text.specificationLanguageId)
        }
    }

    private fun StringBuilder.appendTypeDescription(document: ThreadworkDocument, linkNode: Node) {
        val link = linkNode.link ?: return
        val type = link.typeDefinitionId.takeIf(String::isNotBlank)?.let { document.nodes[NodeId(it)] }
        val fields = type?.typeDefinition?.fields.orEmpty()
        when {
            type != null && fields.isNotEmpty() -> {
                appendLine(
                    "`${type.displayName()}` has the following fields: " + fields.joinToString(", ") { field ->
                        "`${field.name}`: `${document.typeDisplayName(field.typeId)}`${if (field.isReference) " (reference)" else ""}"
                    } + ".",
                )
                appendLine()
            }
            link.payloadDefinition.isNotBlank() -> {
                appendLine("Its inline artefact declaration is:")
                appendLine()
                appendCodeBlock(link.payloadDefinition.trim(), linkNode.text.declarationLanguageId)
            }
        }
    }

    private fun StringBuilder.appendDependencyRelationship(document: ThreadworkDocument, linkNode: Node) {
        val link = linkNode.link ?: return
        val provider = document.nodes[link.sourceNodeId]
        val providerName = provider?.displayName() ?: link.sourceNodeId.value
        appendLine("##### Dependency: ${linkNode.displayName()}")
        appendLine()
        appendLine(dependencySentence(document, linkNode, providerName))
        appendLine()
        if (provider?.text?.specification?.isNotBlank() == true) {
            appendLine("The provider is specified as follows:")
            appendLine()
            appendRichText(provider.text.specification, provider.text.specificationLanguageId)
        }
        if (provider?.text?.aiInstructions?.isNotBlank() == true) {
            appendLine("The provider has these usage requirements:")
            appendLine()
            appendRichText(provider.text.aiInstructions, provider.text.aiInstructionsLanguageId)
        }
    }

    private fun dependencySentence(document: ThreadworkDocument, linkNode: Node, providerName: String): String =
        when (LinkClassifier.classify(document, linkNode)) {
            LinkStereotype.SourceCapability ->
                "`${linkNode.displayName()}` provides synchronous access to customized source artefacts compiled by `$providerName`."
            LinkStereotype.RunnableCapability ->
                "`${linkNode.displayName()}` provides synchronous access to customized runnable artefacts compiled by `$providerName`."
            LinkStereotype.UsageImport,
            LinkStereotype.DependencyInjection ->
                "`${linkNode.displayName()}` provides the service or library `$providerName` as a processor-local dependency."
            else -> "`${linkNode.displayName()}` connects the node to `$providerName`."
        }

    private fun StringBuilder.appendComposition(document: ThreadworkDocument, node: Node) {
        val children = node.children.mapNotNull(document.nodes::get).filterNot(Node::isLink)
        if (children.isEmpty()) return
        appendLine("#### Composition")
        appendLine()
        appendLine(
            "The composite directly contains " + children.joinToString(", ") {
                "`${it.displayName()}` (${humanize(it.stereotype(document).name)})"
            } + ".",
        )
        appendLine()
        appendLine("```mermaid")
        appendLine("flowchart LR")
        val identifiers = children.associate { it.id to mermaidId(it.id) }
        children.forEach { child ->
            appendLine("  ${identifiers.getValue(child.id)}[\"${mermaidLabel(child, document)}\"]")
        }
        document.nodes.values
            .asSequence()
            .filter(Node::isLink)
            .filter { it.parentId == node.id }
            .forEach { childLink ->
                val link = childLink.link ?: return@forEach
                val source = identifiers[link.sourceNodeId] ?: return@forEach
                val target = identifiers[link.targetNodeId] ?: return@forEach
                appendLine("  $source -->|${mermaidArrowLabel(childLink.displayName())}| $target")
            }
        appendLine("```")
        appendLine()
    }

    private fun StringBuilder.appendAnnexes(document: ThreadworkDocument, scope: DocumentationScope) {
        val services = serviceLibraries(document, scope)
        if (scope.types.isEmpty() && services.isEmpty()) return
        appendLine("## Type and Service Annexes")
        appendLine()
        if (scope.types.isNotEmpty()) {
            appendLine("### Shared Types")
            appendLine()
            scope.types.forEach { appendLine("- `${it.displayName()}`") }
            appendLine()
        }
        if (services.isNotEmpty()) {
            appendLine("### Service Libraries")
            appendLine()
            services.forEach { appendLine("- `${it.displayName()}` at `${nodePath(document, it)}`") }
            appendLine()
        }
    }

    private fun StringBuilder.appendNotes(document: ThreadworkDocument, notes: List<Node>) {
        val documentedNotes = notes.filter { it.text.specification.isNotBlank() }
        if (documentedNotes.isEmpty()) return
        appendLine("## Notes")
        appendLine()
        documentedNotes.forEach { note ->
            appendLine("### ${note.displayName()}")
            appendLine()
            appendRichText(note.text.specification, note.text.specificationLanguageId)
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

    private fun serviceLibraries(document: ThreadworkDocument, scope: DocumentationScope): List<Node> =
        (scope.processingNodes.filter { it.stereotype(document) == NodeStereotype.ServiceLibrary } +
            scope.links.filter { LinkClassifier.classify(document, it) in dependencyKinds }
                .mapNotNull { it.link?.sourceNodeId?.let(document.nodes::get) })
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }

    private fun dependencyReference(document: ThreadworkDocument, linkNode: Node): String {
        val provider = linkNode.link?.sourceNodeId?.let(document.nodes::get)?.displayName().orEmpty().ifBlank { "provider" }
        return "`${linkNode.displayName()}:$provider`"
    }

    private fun List<Node>.joinNames(): String = joinToString(", ") { "`${it.displayName()}`" }

    private fun endpointPath(document: ThreadworkDocument, linkNode: Node): String {
        val link = linkNode.link ?: return ""
        val source = document.nodes[link.sourceNodeId]?.displayName() ?: link.sourceNodeId.value
        val target = document.nodes[link.targetNodeId]?.displayName() ?: link.targetNodeId.value
        return "$source.${link.sourcePortName} -> $target.${link.targetPortName}"
    }

    private fun nodePath(document: ThreadworkDocument, node: Node): String {
        val names = mutableListOf<String>()
        val visited = mutableSetOf<NodeId>()
        var current: Node? = node
        while (current != null && visited.add(current.id)) {
            names += current.displayName()
            current = current.parentId?.let(document.nodes::get)
        }
        return names.asReversed().joinToString("/", prefix = "/")
    }

    private fun StringBuilder.appendRichText(content: String, languageId: String) {
        if (languageId.isBlank() || languageId.equals("markdown", ignoreCase = true)) {
            appendLine(content.trim())
            appendLine()
        } else {
            appendCodeBlock(content.trim(), languageId)
        }
    }

    private fun StringBuilder.appendCodeBlock(content: String, languageId: String) {
        val longestRun = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        appendLine("$fence${languageId.trim().takeUnless { it == "plain" }.orEmpty()}")
        appendLine(content)
        appendLine(fence)
        appendLine()
    }

    private fun Node.displayName(): String = name.ifBlank { id.value }

    private fun humanize(value: String): String =
        value.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").lowercase()

    private fun mermaidId(id: NodeId): String = "node_${id.value.replace(Regex("[^A-Za-z0-9_]"), "_")}"

    private fun mermaidLabel(node: Node, document: ThreadworkDocument): String =
        "${node.displayName()} (${humanize(node.stereotype(document).name)})".replace('"', '\'')

    private fun mermaidArrowLabel(value: String): String = value.replace('|', '/').replace(Regex("\\s+"), " ")

    private fun escapeInlineCode(value: String): String = value.replace("`", "\\`")

    private val dependencyKinds = setOf(
        LinkStereotype.UsageImport,
        LinkStereotype.DependencyInjection,
        LinkStereotype.SourceCapability,
        LinkStereotype.RunnableCapability,
    )
}
