package com.orchestra.completion

import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.PortDirection

data class CompletionRequest(
    val nodeId: NodeId,
    val textSection: NodeTextSection,
    val languageId: String,
    val technologyId: String,
    val cursorOffset: Int,
    val fullText: String,
    val currentLine: String,
    val prefix: String,
)

data class CompletionSuggestion(
    val label: String,
    val insertText: String,
    val kind: CompletionSuggestionKind,
    val detail: String = "",
    val documentation: String = "",
    val sourcePluginId: String? = null,
)

enum class CompletionSuggestionKind {
    DependencyLink,
    IncomingLink,
    OutgoingLink,
    InputPort,
    OutputPort,
    Import,
    TemplateObject,
    TemplateField,
    SiblingNode,
    ParentNode,
    ChildNode,
    LinkedSourceNode,
    LinkedTargetNode,
    Library,
    Keyword,
    Snippet,
    CompilerSymbol,
    UserSymbol,
}

interface NodeCompletionService {
    fun getSuggestions(request: CompletionRequest): List<CompletionSuggestion>
}

interface TechnologyCompletionProvider {
    fun supports(languageId: String, technologyId: String): Boolean
    fun getSuggestions(node: Node, document: InflowDocument, request: CompletionRequest): List<CompletionSuggestion>
}

class ModelAwareCompletionService(
    private val documentProvider: () -> InflowDocument,
    private val technologyProviders: List<TechnologyCompletionProvider> = listOf(
        KotlinJvmCompletionProvider(),
        FlowTemplateCompletionProvider(),
    ),
) : NodeCompletionService {
    override fun getSuggestions(request: CompletionRequest): List<CompletionSuggestion> {
        val document = documentProvider()
        val node = document.nodes[request.nodeId] ?: return emptyList()
        val suggestions = mutableListOf<CompletionSuggestion>()

        node.incomingLinks.mapNotNull(document.nodes::get).forEach { linkNode ->
            linkNameSuggestion(document, linkNode, incoming = true)?.let(suggestions::add)
        }

        node.outgoingLinks.mapNotNull(document.nodes::get).forEach { linkNode ->
            linkNameSuggestion(document, linkNode, incoming = false)?.let(suggestions::add)
        }

        node.ports.forEach { port ->
            suggestions += CompletionSuggestion(
                label = port.name,
                insertText = port.name,
                kind = if (port.direction == PortDirection.Input) CompletionSuggestionKind.InputPort else CompletionSuggestionKind.OutputPort,
                detail = "${port.direction.name.lowercase()} port",
            )
        }

        node.parentId?.let { parentId ->
            document.nodes[parentId]?.let { parent ->
                suggestions += CompletionSuggestion(parent.name, parent.name, CompletionSuggestionKind.ParentNode, "parent node")
                parent.children.filter { it != node.id }.mapNotNull(document.nodes::get).forEach { sibling ->
                    suggestions += CompletionSuggestion(sibling.name, sibling.name, CompletionSuggestionKind.SiblingNode, "sibling node")
                }
            }
        }

        node.children.mapNotNull(document.nodes::get).forEach { child ->
            suggestions += CompletionSuggestion(child.name, child.name, CompletionSuggestionKind.ChildNode, "child node")
        }

        node.incomingLinks.mapNotNull(document.nodes::get).mapNotNull { it.link }.forEach { link ->
            document.nodes[link.sourceNodeId]?.let { source ->
                suggestions += CompletionSuggestion(source.name, source.name, CompletionSuggestionKind.LinkedSourceNode, "incoming link source")
            }
        }

        node.outgoingLinks.mapNotNull(document.nodes::get).mapNotNull { it.link }.forEach { link ->
            document.nodes[link.targetNodeId]?.let { target ->
                suggestions += CompletionSuggestion(target.name, target.name, CompletionSuggestionKind.LinkedTargetNode, "outgoing link target")
            }
        }

        node.metadata.filterKeys { it.startsWith("import.") || it == "import" || it.startsWith("library.") }.forEach { (key, value) ->
            val label = value.ifBlank { key.substringAfter('.') }
            suggestions += CompletionSuggestion(label, label, CompletionSuggestionKind.Import, "metadata import")
        }

        technologyProviders
            .filter { it.supports(request.languageId, request.technologyId) }
            .flatMapTo(suggestions) { it.getSuggestions(node, document, request) }

        return suggestions
            .filter { request.prefix.isBlank() || it.label.startsWith(request.prefix, ignoreCase = true) }
            .distinctBy { it.insertText }
            .sortedWith(compareBy({ suggestionPriority(it.kind) }, { it.label.lowercase() }))
    }

    private fun linkNameSuggestion(document: InflowDocument, linkNode: Node, incoming: Boolean): CompletionSuggestion? {
        val link = linkNode.link
        val name = linkNode.name.ifBlank {
            if (incoming) link?.sourcePortName else link?.targetPortName
        }.orEmpty()
        if (name.isBlank()) return null
        val stereotype = LinkClassifier.classify(document, linkNode)
        val kind = when {
            stereotype in dependencyLikeLinks -> CompletionSuggestionKind.DependencyLink
            incoming -> CompletionSuggestionKind.IncomingLink
            else -> CompletionSuggestionKind.OutgoingLink
        }
        val direction = if (incoming) "incoming" else "outgoing"
        val detail = when (stereotype) {
            LinkStereotype.UsageImport -> "library usage link"
            LinkStereotype.DependencyInjection -> "dependency injection link"
            LinkStereotype.ErrorPipe -> "$direction error link"
            LinkStereotype.Transport -> "$direction link"
        }
        return CompletionSuggestion(
            label = name,
            insertText = name,
            kind = kind,
            detail = detail,
        )
    }

    private fun suggestionPriority(kind: CompletionSuggestionKind): Int = when (kind) {
        CompletionSuggestionKind.DependencyLink -> 0
        CompletionSuggestionKind.IncomingLink,
        CompletionSuggestionKind.OutgoingLink -> 1
        CompletionSuggestionKind.InputPort,
        CompletionSuggestionKind.OutputPort -> 2
        CompletionSuggestionKind.Import,
        CompletionSuggestionKind.CompilerSymbol,
        CompletionSuggestionKind.Keyword,
        CompletionSuggestionKind.Snippet,
        CompletionSuggestionKind.UserSymbol -> 3
        CompletionSuggestionKind.TemplateObject,
        CompletionSuggestionKind.TemplateField -> 3
        CompletionSuggestionKind.LinkedSourceNode,
        CompletionSuggestionKind.LinkedTargetNode -> 4
        CompletionSuggestionKind.ParentNode,
        CompletionSuggestionKind.SiblingNode,
        CompletionSuggestionKind.ChildNode,
        CompletionSuggestionKind.Library -> 5
    }

    private val dependencyLikeLinks = setOf(
        LinkStereotype.UsageImport,
        LinkStereotype.DependencyInjection,
    )
}

class KotlinJvmCompletionProvider : TechnologyCompletionProvider {
    override fun supports(languageId: String, technologyId: String): Boolean =
        languageId == "kotlin" || technologyId == "kotlin-jvm"

    override fun getSuggestions(node: Node, document: InflowDocument, request: CompletionRequest): List<CompletionSuggestion> {
        val portSnippets = node.ports.map { port ->
            val snippet = if (port.direction == PortDirection.Input) {
                "context.inputs[\"${port.name}\"]"
            } else {
                "context.outputs.getOrPut(\"${port.name}\") { mutableListOf() }"
            }
            CompletionSuggestion(snippet, snippet, CompletionSuggestionKind.Snippet, "Kotlin runtime snippet")
        }
        return listOf(
            CompletionSuggestion("context", "context", CompletionSuggestionKind.CompilerSymbol, "RuntimeContext parameter"),
            CompletionSuggestion("inputs", "context.inputs", CompletionSuggestionKind.CompilerSymbol, "Runtime input queues"),
            CompletionSuggestion("outputs", "context.outputs", CompletionSuggestionKind.CompilerSymbol, "Runtime output queues"),
        ) + portSnippets
    }
}

class FlowTemplateCompletionProvider : TechnologyCompletionProvider {
    override fun supports(languageId: String, technologyId: String): Boolean = true

    override fun getSuggestions(node: Node, document: InflowDocument, request: CompletionRequest): List<CompletionSuggestion> {
        if (request.textSection !in setOf(NodeTextSection.Instantiation, NodeTextSection.Declaration)) return emptyList()
        if (node.stereotype(document) != NodeStereotype.CompilerTemplate) return emptyList()

        val suggestions = mutableListOf<CompletionSuggestion>()
        suggestions += objectSuggestion("document", "current document")
        suggestions += objectSuggestion("node", "current template node")
        suggestions += objectSuggestion("self", "alias for the current node")
        suggestions += objectSuggestion("metadata", "node metadata map")
        suggestions += objectSuggestion("text", "node text blocks")
        suggestions += objectSuggestion("technology", "node technology metadata")
        suggestions += objectSuggestion("layout", "node layout geometry")
        suggestions += objectSuggestion("children", "child nodes")
        suggestions += objectSuggestion("parent", "parent node")
        suggestions += objectSuggestion("ports", "node ports")
        suggestions += objectSuggestion("incomingLinks", "incoming links")
        suggestions += objectSuggestion("outgoingLinks", "outgoing links")

        suggestions += snippetSuggestion("node: \${node.name}", "node name line")
        suggestions += snippetSuggestion("options.projectName:\${options.projectName}", "project name line")
        suggestions += fieldSuggestion("options.projectName", "compile project name")
        suggestions += fieldSuggestion("node.id", "node identifier")
        suggestions += fieldSuggestion("node.name", "node name")
        suggestions += fieldSuggestion("node.kind", "node kind")
        suggestions += fieldSuggestion("node.kind.name", "node kind name")
        suggestions += fieldSuggestion("node.kind.ordinal", "node kind ordinal")
        suggestions += fieldSuggestion("node.parentId", "parent node id")
        suggestions += fieldSuggestion("node.children.size", "number of child nodes")
        suggestions += fieldSuggestion("node.incomingLinks", "incoming link ids")
        suggestions += fieldSuggestion("node.isLink", "true when the node is a link")
        suggestions += fieldSuggestion("node.isComposite", "true when the node has children")
        suggestions += fieldSuggestion("node.isTerminal", "true when the node has no children")
        suggestions += fieldSuggestion("node.layout", "node layout geometry")
        suggestions += fieldSuggestion("node.link.sourceNodeId", "link source node id")
        suggestions += fieldSuggestion("node.link.targetNodeId", "link target node id")
        suggestions += fieldSuggestion("node.link.sourcePortName", "link source port name")
        suggestions += fieldSuggestion("node.link.targetPortName", "link target port name")
        suggestions += fieldSuggestion("node.metadata.size", "node metadata entry count")
        suggestions += fieldSuggestion("node.metadata", "node metadata map")
        suggestions += fieldSuggestion("node.outgoingLinks", "outgoing link ids")
        suggestions += fieldSuggestion("node.pluginData", "node plugin data")
        suggestions += fieldSuggestion("node.ports", "node ports")
        suggestions += fieldSuggestion("node.text.instantiationLanguageId", "instantiation language id")
        suggestions += fieldSuggestion("node.text.instantiation", "instantiation text")
        suggestions += fieldSuggestion("node.text.declarationLanguageId", "declaration language id")
        suggestions += fieldSuggestion("node.text.declaration", "declaration text")
        suggestions += fieldSuggestion("node.text.specificationLanguageId", "specification language id")
        suggestions += fieldSuggestion("node.text.specification", "specification text")
        suggestions += fieldSuggestion("node.text.aiInstructionsLanguageId", "usage instructions language id")
        suggestions += fieldSuggestion("node.text.aiInstructions", "usage instructions text")
        suggestions += fieldSuggestion("node.text.testsLanguageId", "tests language id")
        suggestions += fieldSuggestion("node.text.tests", "tests text")

        suggestions += fieldSuggestion("metadata", "node metadata map")
        suggestions += fieldSuggestion("text.instantiation", "instantiation text")
        suggestions += fieldSuggestion("text.instantiationLanguageId", "instantiation language id")
        suggestions += fieldSuggestion("text.declaration", "declaration text")
        suggestions += fieldSuggestion("text.declarationLanguageId", "declaration language id")
        suggestions += fieldSuggestion("text.specification", "specification text")
        suggestions += fieldSuggestion("text.specificationLanguageId", "specification language id")
        suggestions += fieldSuggestion("text.tests", "tests text")
        suggestions += fieldSuggestion("text.testsLanguageId", "tests language id")
        suggestions += fieldSuggestion("text.aiInstructions", "ai instructions text")
        suggestions += fieldSuggestion("text.aiInstructionsLanguageId", "ai instructions language id")

        suggestions += fieldSuggestion("technology.languageId", "technology language id")
        suggestions += fieldSuggestion("technology.technologyId", "technology id")
        suggestions += fieldSuggestion("technology.compilerId", "compiler id")
        suggestions += fieldSuggestion("technology.fileExtension", "file extension")
        suggestions += fieldSuggestion("technology.contentType", "content type")

        suggestions += fieldSuggestion("layout.x", "node x coordinate")
        suggestions += fieldSuggestion("layout.y", "node y coordinate")
        suggestions += fieldSuggestion("layout.width", "node width")
        suggestions += fieldSuggestion("layout.height", "node height")

        suggestions += fieldSuggestion("children.size", "number of child nodes")
        suggestions += fieldSuggestion("incomingLinks.size", "number of incoming links")
        suggestions += fieldSuggestion("outgoingLinks.size", "number of outgoing links")
        suggestions += fieldSuggestion("incomingLinkVariables", "incoming link variable names")
        suggestions += fieldSuggestion("outgoingLinkVariables", "outgoing link variable names")
        suggestions += fieldSuggestion("incomingLinkTypes", "incoming link type names")
        suggestions += fieldSuggestion("outgoingLinkTypes", "outgoing link type names")
        suggestions += fieldSuggestion("incomingArguments", "incoming link name:typeName argument list")
        suggestions += fieldSuggestion("outgoingArguments", "outgoing link name:typeName argument list")
        suggestions += fieldSuggestion("incomingTypeDefinitions", "incoming link type definitions")
        suggestions += fieldSuggestion("outgoingTypeDefinitions", "outgoing link type definitions")
        suggestions += fieldSuggestion("dependencyInjectionLinks", "dependency injection link variable names")
        suggestions += fieldSuggestion("dependencyInjectionArguments", "dependency injection name:typeName argument list")
        suggestions += fieldSuggestion("ports.size", "number of ports")
        suggestions += fieldSuggestion("childArtifacts", "compiled child artifacts")
        suggestions += fieldSuggestion("linkArtifacts", "compiled link artifacts")
        suggestions += fieldSuggestion("childDeclarations", "all child declarations")
        suggestions += fieldSuggestion("inlineChildDeclarations", "single-file child declarations")
        suggestions += fieldSuggestion("childInstantiations", "all child instantiations")
        suggestions += fieldSuggestion("linkDeclarations", "all link declarations")
        suggestions += fieldSuggestion("linkInstantiations", "all link instantiations")
        suggestions += fieldSuggestion("layoutStrategy.id", "effective layout strategy id")
        suggestions += fieldSuggestion("primaryPath", "default generated file path")
        suggestions += snippetSuggestion("{% for child in children %}\n{{ child.name }}\n{% endfor %}", "iterate over child nodes")
        suggestions += snippetSuggestion("{% if node.isComposite %}\n{% endif %}", "conditional composite block")

        if (node.isLink) {
            suggestions += objectSuggestion("link", "current link data")
            suggestions += objectSuggestion("sourceNode", "source node")
            suggestions += objectSuggestion("targetNode", "target node")
            suggestions += fieldSuggestion("link.sourceNodeId", "source node id")
            suggestions += fieldSuggestion("link.sourcePortName", "source port name")
            suggestions += fieldSuggestion("link.targetNodeId", "target node id")
            suggestions += fieldSuggestion("link.targetPortName", "target port name")
            suggestions += fieldSuggestion("link.transportKind", "link transport kind")
            suggestions += fieldSuggestion("link.variableName", "link variable name")
            suggestions += fieldSuggestion("link.typeName", "link type name")
            suggestions += fieldSuggestion("link.typeDefinition", "link type definition")
            suggestions += fieldSuggestion("link.payloadDefinition", "link payload definition")
        }

        suggestions += childNodeFields(node, document)
        suggestions += portFields(node)

        return suggestions
            .distinctBy { it.insertText }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.detail }))
    }

    private fun objectSuggestion(label: String, detail: String): CompletionSuggestion =
        CompletionSuggestion(label, label, CompletionSuggestionKind.TemplateObject, detail)

    private fun fieldSuggestion(label: String, detail: String): CompletionSuggestion =
        CompletionSuggestion(label, label, CompletionSuggestionKind.TemplateField, detail)

    private fun snippetSuggestion(insertText: String, detail: String): CompletionSuggestion =
        CompletionSuggestion(insertText, insertText, CompletionSuggestionKind.Snippet, detail)

    private fun childNodeFields(node: Node, document: InflowDocument): List<CompletionSuggestion> =
        node.children.mapNotNull(document.nodes::get).flatMap { child ->
            listOf(
                CompletionSuggestion(child.name, child.name, CompletionSuggestionKind.TemplateObject, "child node"),
                CompletionSuggestion("${child.name}.name", child.name, CompletionSuggestionKind.TemplateField, "child node name"),
                CompletionSuggestion("${child.name}.id", child.id.value, CompletionSuggestionKind.TemplateField, "child node id"),
            )
        }

    private fun portFields(node: Node): List<CompletionSuggestion> =
        node.ports.flatMap { port ->
            listOf(
                CompletionSuggestion(port.name, port.name, CompletionSuggestionKind.TemplateObject, "${port.direction.name.lowercase()} port"),
                CompletionSuggestion("${port.name}.name", port.name, CompletionSuggestionKind.TemplateField, "${port.direction.name.lowercase()} port name"),
                CompletionSuggestion("${port.name}.dataType", "${port.name}.dataType", CompletionSuggestionKind.TemplateField, "${port.direction.name.lowercase()} port data type"),
            )
        }
}
