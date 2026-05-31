package com.orchestra.completion

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
    InputPort,
    OutputPort,
    Import,
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
    private val technologyProviders: List<TechnologyCompletionProvider> = listOf(KotlinJvmCompletionProvider()),
) : NodeCompletionService {
    override fun getSuggestions(request: CompletionRequest): List<CompletionSuggestion> {
        val document = documentProvider()
        val node = document.nodes[request.nodeId] ?: return emptyList()
        val suggestions = mutableListOf<CompletionSuggestion>()

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
            .distinctBy { it.kind to it.label }
            .sortedWith(compareBy({ it.kind.name }, { it.label.lowercase() }))
    }
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
