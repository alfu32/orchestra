package com.orchestra.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@JvmInline
value class NodeId(val value: String) {
    override fun toString(): String = value
}

@Serializable
enum class NodeKind {
    Node,
    Processor,
    Link,
    Group,
    Note,
}

@Serializable
data class NodeLayout(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 200.0,
    var height: Double = 100.0,
)

@Serializable
data class NodeText(
    var source: String = "",
    var specification: String = "",
    var tests: String = "",
    var aiInstructions: String = "",
)

@Serializable
enum class NodeTextSection {
    Source,
    Specification,
    Tests,
    AiInstructions,
}

@Serializable
data class TechnologyMetadata(
    var languageId: String = "",
    var technologyId: String = "",
    var compilerId: String = "",
    var fileExtension: String = "",
    var contentType: String = "",
)

@Serializable
data class NodePort(
    val id: String,
    var name: String,
    var direction: PortDirection,
    var dataType: String = "",
    var metadata: MutableMap<String, String> = mutableMapOf(),
)

@Serializable
enum class PortDirection {
    Input,
    Output,
}

@Serializable
data class LinkData(
    var sourceNodeId: NodeId,
    var sourcePortName: String,
    var targetNodeId: NodeId,
    var targetPortName: String,
    var transportKind: String = "packet",
    var payloadDefinition: String = "",
)

@Serializable
data class Node(
    val id: NodeId,
    var name: String,
    var kind: NodeKind,
    var parentId: NodeId? = null,
    val children: MutableList<NodeId> = mutableListOf(),
    val incomingLinks: MutableList<NodeId> = mutableListOf(),
    val outgoingLinks: MutableList<NodeId> = mutableListOf(),
    var layout: NodeLayout = NodeLayout(),
    var text: NodeText = NodeText(),
    var technology: TechnologyMetadata = TechnologyMetadata(),
    val ports: MutableList<NodePort> = mutableListOf(),
    var link: LinkData? = null,
    var metadata: MutableMap<String, String> = mutableMapOf(),
    var pluginData: MutableMap<String, JsonObject> = mutableMapOf(),
) {
    val isTerminal: Boolean get() = children.isEmpty()
    val isComposite: Boolean get() = children.isNotEmpty()
    val isLink: Boolean get() = kind == NodeKind.Link || link != null
}

@Serializable
data class InflowDocument(
    val id: String,
    var name: String,
    var rootNodeId: NodeId,
    val nodes: MutableMap<NodeId, Node> = mutableMapOf(),
    var metadata: MutableMap<String, String> = mutableMapOf(),
)

const val VOID_LANGUAGE_ID = "plain"
const val VOID_TECHNOLOGY_ID = "none"

fun InflowDocument.rootNode(): Node = nodes[rootNodeId]
    ?: error("Root node '$rootNodeId' is missing")

fun InflowDocument.childNodes(node: Node): List<Node> =
    node.children.mapNotNull(nodes::get)

fun InflowDocument.linkNodes(): List<Node> =
    nodes.values.filter { it.isLink }

fun InflowDocument.effectiveLanguageId(nodeId: NodeId): String =
    inheritedTechnologyValue(nodeId, VOID_LANGUAGE_ID) { it.languageId }

fun InflowDocument.effectiveTechnologyId(nodeId: NodeId): String =
    inheritedTechnologyValue(nodeId, VOID_TECHNOLOGY_ID) { it.technologyId }

private fun InflowDocument.inheritedTechnologyValue(
    nodeId: NodeId,
    fallback: String,
    selector: (TechnologyMetadata) -> String,
): String {
    val visited = mutableSetOf<NodeId>()
    var current = nodes[nodeId]
    while (current != null && current.id !in visited) {
        visited += current.id
        val value = selector(current.technology).trim()
        if (value.isNotBlank()) return value
        current = current.parentId?.let(nodes::get)
    }
    return fallback
}
