package com.threadwork.completion

import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection

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
    InputBuffer,
    OutputBuffer,
    ServiceInstance,
    BufferMember,
    Type,
    TypeMember,
    UserSymbol,
}

interface NodeCompletionService {
    fun getSuggestions(request: CompletionRequest): List<CompletionSuggestion>

    fun getDeclarationSymbols(request: CompletionRequest): List<DeclarationSymbol> = emptyList()
}

interface TechnologyCompletionProvider {
    fun supports(languageId: String, technologyId: String): Boolean
    fun getSuggestions(node: Node, document: ThreadworkDocument, request: CompletionRequest): List<CompletionSuggestion>
}

class ModelAwareCompletionService(
    private val documentProvider: () -> ThreadworkDocument,
    private val compilerProvider: (ThreadworkDocument, Node) -> CompilerPlugin? = { _, _ -> null },
    private val technologyProviders: List<TechnologyCompletionProvider> = listOf(
        FlowTemplateCompletionProvider(),
    ),
    private val declarationSymbolIndex: DocumentDeclarationSymbolIndex = DocumentDeclarationSymbolIndex(),
) : NodeCompletionService {
    override fun getSuggestions(request: CompletionRequest): List<CompletionSuggestion> {
        val document = documentProvider()
        val node = document.nodes[request.nodeId] ?: return emptyList()
        val suggestions = mutableListOf<CompletionSuggestion>()

        if (node.stereotype(document) != NodeStereotype.CompilerTemplate) {
            addProcessingScopeSuggestions(document, node, request, suggestions)
        }

        technologyProviders
            .filter { it.supports(request.languageId, request.technologyId) }
            .flatMapTo(suggestions) { it.getSuggestions(node, document, request) }

        return suggestions
            .filter { request.prefix.isBlank() || it.label.startsWith(request.prefix, ignoreCase = true) }
            .distinctBy { it.insertText }
            .sortedWith(compareBy({ suggestionPriority(it.kind) }, { it.label.lowercase() }))
    }

    override fun getDeclarationSymbols(request: CompletionRequest): List<DeclarationSymbol> =
        declarationSymbolIndex.symbols(documentProvider(), request)

    private fun addProcessingScopeSuggestions(
        document: ThreadworkDocument,
        node: Node,
        request: CompletionRequest,
        suggestions: MutableList<CompletionSuggestion>,
    ) {
        if (request.textSection != NodeTextSection.Declaration) return

        compilerProvider(document, node)
            ?.let { compiler ->
                compiler.codeIntelligence(document, node).symbols.forEach { symbol ->
                    suggestions += CompletionSuggestion(
                        label = symbol.name,
                        insertText = symbol.name,
                        kind = symbol.kind.toCompletionKind(),
                        detail = symbol.detail.ifBlank { symbol.typeName },
                        documentation = symbol.documentation,
                        sourcePluginId = compiler.id,
                    )
                    symbol.members.forEach { member ->
                        suggestions += CompletionSuggestion(
                            label = member.name,
                            insertText = member.name,
                            kind = when (symbol.kind) {
                                CompilerCodeSymbolKind.Type -> CompletionSuggestionKind.TypeMember
                                else -> CompletionSuggestionKind.BufferMember
                            },
                            detail = member.detail,
                            documentation = member.documentation,
                            sourcePluginId = compiler.id,
                        )
                    }
                }
            }

        getDeclarationSymbols(request).mapTo(suggestions) { symbol ->
            CompletionSuggestion(
                label = symbol.name,
                insertText = symbol.name,
                kind = CompletionSuggestionKind.UserSymbol,
                detail = "${symbol.kind.name.lowercase()} declared in this node",
                documentation = symbol.header,
            )
        }
    }

    private fun suggestionPriority(kind: CompletionSuggestionKind): Int = when (kind) {
        CompletionSuggestionKind.InputBuffer,
        CompletionSuggestionKind.OutputBuffer,
        CompletionSuggestionKind.ServiceInstance -> 0
        CompletionSuggestionKind.CompilerSymbol,
        CompletionSuggestionKind.BufferMember,
        CompletionSuggestionKind.Type,
        CompletionSuggestionKind.TypeMember,
        CompletionSuggestionKind.Keyword,
        CompletionSuggestionKind.Snippet,
        CompletionSuggestionKind.UserSymbol -> 4
        CompletionSuggestionKind.TemplateObject,
        CompletionSuggestionKind.TemplateField -> 4
        CompletionSuggestionKind.DependencyLink,
        CompletionSuggestionKind.IncomingLink,
        CompletionSuggestionKind.OutgoingLink,
        CompletionSuggestionKind.InputPort,
        CompletionSuggestionKind.OutputPort,
        CompletionSuggestionKind.Import,
        CompletionSuggestionKind.LinkedSourceNode,
        CompletionSuggestionKind.LinkedTargetNode,
        CompletionSuggestionKind.ParentNode,
        CompletionSuggestionKind.SiblingNode,
        CompletionSuggestionKind.ChildNode,
        CompletionSuggestionKind.Library -> 6
    }
}

private fun CompilerCodeSymbolKind.toCompletionKind(): CompletionSuggestionKind = when (this) {
    CompilerCodeSymbolKind.InputBuffer -> CompletionSuggestionKind.InputBuffer
    CompilerCodeSymbolKind.OutputBuffer -> CompletionSuggestionKind.OutputBuffer
    CompilerCodeSymbolKind.ServiceInstance -> CompletionSuggestionKind.ServiceInstance
    CompilerCodeSymbolKind.Type -> CompletionSuggestionKind.Type
    CompilerCodeSymbolKind.TypeMember -> CompletionSuggestionKind.TypeMember
    CompilerCodeSymbolKind.BufferMember -> CompletionSuggestionKind.BufferMember
}

class FlowTemplateCompletionProvider : TechnologyCompletionProvider {
    override fun supports(languageId: String, technologyId: String): Boolean = true

    override fun getSuggestions(node: Node, document: ThreadworkDocument, request: CompletionRequest): List<CompletionSuggestion> {
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
        suggestions += fieldSuggestion("childHoistedDeclarations", "child declarations required before executable code")
        suggestions += fieldSuggestion("linkHoistedDeclarations", "link declarations required before executable code")
        suggestions += fieldSuggestion("descendantHoistedDeclarations", "all descendant declarations required before executable code")
        suggestions += fieldSuggestion("hoistedDeclarations", "complete declarations required before executable code")
        suggestions += fieldSuggestion("childForwardDeclarations", "child function forward declarations")
        suggestions += fieldSuggestion("linkForwardDeclarations", "link function forward declarations")
        suggestions += fieldSuggestion("descendantForwardDeclarations", "all descendant function forward declarations")
        suggestions += fieldSuggestion("forwardDeclarations", "complete function forward declarations")
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

    private fun childNodeFields(node: Node, document: ThreadworkDocument): List<CompletionSuggestion> =
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
