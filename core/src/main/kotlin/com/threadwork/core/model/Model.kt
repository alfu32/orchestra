package com.threadwork.core.model

import kotlinx.serialization.SerialName
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
    Type,
    Note,
}

@Serializable
data class NodeLayout(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 200.0,
    var height: Double = 100.0,
    var closedWidth: Double = 200.0,
    var closedHeight: Double = 100.0,
    var openWidth: Double = 200.0,
    var openHeight: Double = 100.0,
    var isExpanded: Boolean = true,
)

const val VOID_LAYOUT_STRATEGY_ID = "none"

@Serializable
data class NodeText(
    @SerialName("initialization")
    var instantiation: String = "",
    @SerialName("initializationLanguageId")
    var instantiationLanguageId: String = "",
    @SerialName("source")
    var declaration: String = "",
    @SerialName("sourceLanguageId")
    var declarationLanguageId: String = "",
    var specification: String = "",
    var specificationLanguageId: String = "markdown",
    var tests: String = "",
    var testsLanguageId: String = "json",
    var aiInstructions: String = "",
    var aiInstructionsLanguageId: String = "markdown",
)

@Serializable
enum class NodeTextSection {
    Instantiation,
    Declaration,
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
data class Revision(
    var name: String = "",
    var date: String = "",
)

@Serializable
data class ModificationMetadata(
    var date: String = "",
    var user: String = "",
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
    var transportKind: String = LinkTransportKinds.Default,
    var typeName: String = "",
    var payloadDefinition: String = "",
    var typeDefinitionId: String = "",
    var compositeBoundaryIds: MutableList<NodeId> = mutableListOf(),
)

object BuiltInTypeIds {
    const val String = "string"
    const val Number = "number"
    const val Date = "date"
    const val Array = "array"

    val all: List<String> = listOf(String, Number, Date, Array)
}

@Serializable
data class TypeFieldDefinition(
    var name: String = "",
    var typeId: String = BuiltInTypeIds.String,
    var isReference: Boolean = false,
)

@Serializable
data class TypeDefinition(
    val fields: MutableList<TypeFieldDefinition> = mutableListOf(),
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
    var fileLayoutStrategyId: String = VOID_LAYOUT_STRATEGY_ID,
    var text: NodeText = NodeText(),
    var technology: TechnologyMetadata = TechnologyMetadata(),
    var revision: Revision? = null,
    var responsible: String? = null,
    var modified: ModificationMetadata = ModificationMetadata(),
    val ports: MutableList<NodePort> = mutableListOf(),
    var link: LinkData? = null,
    var typeDefinition: TypeDefinition? = null,
    var metadata: MutableMap<String, String> = mutableMapOf(),
    var pluginData: MutableMap<String, JsonObject> = mutableMapOf(),
) {
    val isTerminal: Boolean get() = children.isEmpty()
    val isComposite: Boolean get() = children.isNotEmpty()
    val isLink: Boolean get() = kind == NodeKind.Link || link != null
    val isType: Boolean get() = kind == NodeKind.Type
}

@Serializable
data class ThreadworkDocument(
    val id: String,
    var name: String,
    var rootNodeId: NodeId,
    val nodes: MutableMap<NodeId, Node> = mutableMapOf(),
    var metadata: MutableMap<String, String> = mutableMapOf(),
    var masterRevision: Revision = Revision(),
)

const val VOID_LANGUAGE_ID = "plain"
const val VOID_TECHNOLOGY_ID = "none"
const val VOID_RESPONSIBLE = "none"

fun ThreadworkDocument.rootNode(): Node = nodes[rootNodeId]
    ?: error("Root node '$rootNodeId' is missing")

fun ThreadworkDocument.projectName(): String =
    nodes[rootNodeId]?.name?.trim()?.takeIf(String::isNotBlank)
        ?: name.trim().takeIf(String::isNotBlank)
        ?: "project"

fun ThreadworkDocument.getElementById(id: String): Node? = nodes[NodeId(id)]

fun ThreadworkDocument.getElementById(id: NodeId): Node? = nodes[id]

fun ThreadworkDocument.getElementsByIds(ids: List<String>): Map<String, Node> {
    val resolved = linkedMapOf<String, Node>()
    ids.forEach { id ->
        getElementById(id)?.let { resolved[id] = it }
    }
    return resolved
}

fun ThreadworkDocument.getElementsByIds(ids: Iterable<NodeId>): Map<NodeId, Node> {
    val resolved = linkedMapOf<NodeId, Node>()
    ids.forEach { id ->
        getElementById(id)?.let { resolved[id] = it }
    }
    return resolved
}

fun ThreadworkDocument.childNodes(node: Node): List<Node> =
    node.children.mapNotNull(nodes::get)

fun ThreadworkDocument.linkNodes(): List<Node> =
    nodes.values.filter { it.isLink }

fun ThreadworkDocument.typeNodes(): List<Node> =
    nodes.values.filter { it.kind == NodeKind.Type }

fun ThreadworkDocument.typeDisplayName(typeId: String): String {
    val normalized = typeId.trim()
    if (normalized in BuiltInTypeIds.all) return normalized
    return nodes[NodeId(normalized)]?.takeIf { it.kind == NodeKind.Type }?.name?.trim().orEmpty()
        .ifBlank { normalized }
}

fun ThreadworkDocument.linkTypeDisplayName(linkNode: Node): String {
    val link = linkNode.link ?: return ""
    return typeDisplayName(link.typeDefinitionId).ifBlank { link.typeName.trim() }
}

fun ThreadworkDocument.linksUsingType(typeNodeId: NodeId): List<Node> =
    linkNodes().filter { it.link?.typeDefinitionId == typeNodeId.value }

/** Returns the nearest node that contains both endpoints in the persisted hierarchy. */
fun ThreadworkDocument.closestCommonAncestorId(firstId: NodeId, secondId: NodeId): NodeId? {
    if (firstId == secondId) return nodes[firstId]?.parentId ?: rootNodeId
    val firstChain = hierarchyChain(firstId).toSet()
    return hierarchyChain(secondId).firstOrNull { it in firstChain }
}

/**
 * Composite boundaries crossed by a link, ordered from the source towards the target.
 * The common container itself is not crossed and therefore is not included.
 */
fun ThreadworkDocument.compositeBoundaryIdsBetween(sourceId: NodeId, targetId: NodeId): List<NodeId> {
    val commonAncestor = closestCommonAncestorId(sourceId, targetId) ?: return emptyList()
    val sourceBoundaries = parentChain(sourceId)
        .takeWhile { it != commonAncestor }
        .filter(::isCompositeBoundary)
    val targetBoundaries = parentChain(targetId)
        .takeWhile { it != commonAncestor }
        .filter(::isCompositeBoundary)
        .asReversed()
    return (sourceBoundaries + targetBoundaries).distinct()
}

private fun ThreadworkDocument.hierarchyChain(nodeId: NodeId): List<NodeId> =
    buildList {
        val visited = mutableSetOf<NodeId>()
        var current: NodeId? = nodeId
        while (current != null && visited.add(current)) {
            add(current)
            current = nodes[current]?.parentId
        }
    }

private fun ThreadworkDocument.parentChain(nodeId: NodeId): List<NodeId> =
    hierarchyChain(nodeId).drop(1)

private fun ThreadworkDocument.isCompositeBoundary(nodeId: NodeId): Boolean {
    if (nodeId == rootNodeId) return false
    val node = nodes[nodeId] ?: return false
    return node.kind == NodeKind.Group || node.children.any { childId -> nodes[childId]?.isLink == false }
}

fun ThreadworkDocument.effectiveLanguageId(nodeId: NodeId): String =
    inheritedTechnologyValue(nodeId, VOID_LANGUAGE_ID) { it.languageId }

fun ThreadworkDocument.effectiveTechnologyId(nodeId: NodeId): String =
    inheritedNodeValue(nodeId, VOID_TECHNOLOGY_ID) {
        val value = it.technology.technologyId.trim()
        if (value.isBlank() || value == VOID_TECHNOLOGY_ID) "" else value
    }

fun ThreadworkDocument.effectiveLayoutStrategyId(nodeId: NodeId): String =
    inheritedNodeValue(nodeId, VOID_LAYOUT_STRATEGY_ID) {
        val value = it.fileLayoutStrategyId.trim()
        if (value.isBlank() || value == VOID_LAYOUT_STRATEGY_ID) "" else value
    }

fun ThreadworkDocument.effectiveResponsible(nodeId: NodeId): String =
    inheritedNodeValue(nodeId, VOID_RESPONSIBLE) { it.responsible.orEmpty() }

fun ThreadworkDocument.effectiveRevision(nodeId: NodeId): Revision? {
    val visited = mutableSetOf<NodeId>()
    var current = nodes[nodeId]
    while (current != null && current.id !in visited) {
        visited += current.id
        current.revision?.let { return it.copy() }
        current = current.parentId?.let(nodes::get)
    }
    return null
}

fun ThreadworkDocument.effectiveTextLanguageId(nodeId: NodeId, section: NodeTextSection): String {
    val visited = mutableSetOf<NodeId>()
    var current = nodes[nodeId]
    while (current != null && current.id !in visited) {
        visited += current.id
        val languageId = current.text.languageId(section).trim()
        when (section) {
            NodeTextSection.Instantiation,
            NodeTextSection.Declaration -> if (languageId.isNotBlank()) return languageId
            NodeTextSection.Specification,
            NodeTextSection.Tests,
            NodeTextSection.AiInstructions -> return languageId.ifBlank { defaultTextLanguageId(section) }
        }
        current = current.parentId?.let(nodes::get)
    }
    return when (section) {
        NodeTextSection.Instantiation,
        NodeTextSection.Declaration -> effectiveLanguageId(nodeId)
        NodeTextSection.Specification,
        NodeTextSection.AiInstructions -> defaultTextLanguageId(section)
        NodeTextSection.Tests -> defaultTextLanguageId(section)
    }
}

fun NodeText.text(section: NodeTextSection): String =
    when (section) {
        NodeTextSection.Instantiation -> instantiation
        NodeTextSection.Declaration -> declaration
        NodeTextSection.Specification -> specification
        NodeTextSection.Tests -> tests
        NodeTextSection.AiInstructions -> aiInstructions
    }

fun NodeText.languageId(section: NodeTextSection): String =
    when (section) {
        NodeTextSection.Instantiation -> instantiationLanguageId
        NodeTextSection.Declaration -> declarationLanguageId
        NodeTextSection.Specification -> specificationLanguageId
        NodeTextSection.Tests -> testsLanguageId
        NodeTextSection.AiInstructions -> aiInstructionsLanguageId
    }

fun NodeText.withLanguageId(section: NodeTextSection, languageId: String): NodeText =
    when (section) {
        NodeTextSection.Instantiation -> copy(instantiationLanguageId = languageId)
        NodeTextSection.Declaration -> copy(declarationLanguageId = languageId)
        NodeTextSection.Specification -> copy(specificationLanguageId = languageId)
        NodeTextSection.Tests -> copy(testsLanguageId = languageId)
        NodeTextSection.AiInstructions -> copy(aiInstructionsLanguageId = languageId)
    }

fun NodeText.withText(section: NodeTextSection, value: String): NodeText =
    when (section) {
        NodeTextSection.Instantiation -> copy(instantiation = value)
        NodeTextSection.Declaration -> copy(declaration = value)
        NodeTextSection.Specification -> copy(specification = value)
        NodeTextSection.Tests -> copy(tests = value)
        NodeTextSection.AiInstructions -> copy(aiInstructions = value)
    }

private fun ThreadworkDocument.inheritedTechnologyValue(
    nodeId: NodeId,
    fallback: String,
    selector: (TechnologyMetadata) -> String,
): String {
    return inheritedNodeValue(nodeId, fallback) { selector(it.technology) }
}

private fun ThreadworkDocument.inheritedNodeValue(
    nodeId: NodeId,
    fallback: String,
    selector: (Node) -> String,
): String {
    val visited = mutableSetOf<NodeId>()
    var current = nodes[nodeId]
    while (current != null && current.id !in visited) {
        visited += current.id
        val value = selector(current).trim()
        if (value.isNotBlank()) return value
        current = current.parentId?.let(nodes::get)
    }
    return fallback
}

private fun defaultTextLanguageId(section: NodeTextSection): String =
    when (section) {
        NodeTextSection.Instantiation,
        NodeTextSection.Declaration,
        NodeTextSection.AiInstructions -> VOID_LANGUAGE_ID
        NodeTextSection.Specification -> "markdown"
        NodeTextSection.Tests -> "json"
    }
