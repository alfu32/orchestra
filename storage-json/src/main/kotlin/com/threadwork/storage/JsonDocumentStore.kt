package com.threadwork.storage

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.Revision
import com.threadwork.core.model.closestCommonAncestorId
import com.threadwork.core.model.compositeBoundaryIdsBetween
import com.threadwork.core.model.projectName
import com.threadwork.core.validation.DocumentValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

interface JsonDocumentStore {
    fun save(document: ThreadworkDocument, filePath: Path)
    fun load(filePath: Path): ThreadworkDocument
}

class KotlinxJsonDocumentStore(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : JsonDocumentStore {
    override fun save(document: ThreadworkDocument, filePath: Path) {
        filePath.parent?.let(Files::createDirectories)
        val file = ThreadworkDocumentFile(
            id = document.id,
            name = document.projectName(),
            rootNodeId = document.rootNodeId,
            nodes = orderedNodes(document),
            metadata = document.metadata.toMutableMap(),
            masterRevision = document.masterRevision.copy(),
        )
        Files.writeString(filePath, json.encodeToString(ThreadworkDocumentFile.serializer(), file))
    }

    override fun load(filePath: Path): ThreadworkDocument {
        val document = decodeDocument(Files.readString(filePath))
        repairDocument(document)
        document.name = document.projectName()
        val errors = DocumentValidator.validate(document).filter { it.severity.name == "Error" }
        require(errors.isEmpty()) {
            errors.joinToString(prefix = "Invalid document:\n", separator = "\n") { "- ${it.message}" }
        }
        return document
    }

    private fun decodeDocument(text: String): ThreadworkDocument {
        val element = json.parseToJsonElement(text)
        return when (element.jsonObject["nodes"]) {
            is JsonArray -> decodeListDocument(element)
            is JsonObject -> decodeLegacyMapDocument(element)
            else -> error("Invalid document:\n- Missing nodes")
        }
    }

    private fun decodeListDocument(element: JsonElement): ThreadworkDocument {
        val file = json.decodeFromJsonElement(ThreadworkDocumentFile.serializer(), element)
        return ThreadworkDocument(
            id = file.id,
            name = file.name,
            rootNodeId = file.rootNodeId,
            nodes = nodesById(file.nodes),
            metadata = file.metadata,
            masterRevision = file.masterRevision,
        )
    }

    private fun decodeLegacyMapDocument(element: JsonElement): ThreadworkDocument {
        val document = json.decodeFromJsonElement(ThreadworkDocument.serializer(), element)
        val normalizedNodes = nodesById(document.nodes.values.toList())
        document.nodes.clear()
        document.nodes.putAll(normalizedNodes)
        return document
    }

    private fun nodesById(nodes: List<Node>): MutableMap<NodeId, Node> {
        val duplicates = nodes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            duplicates.joinToString(prefix = "Invalid document:\n", separator = "\n") {
                "- Duplicate node id '${it.value}'"
            }
        }
        return nodes.associateByTo(mutableMapOf()) { it.id }
    }

    private fun orderedNodes(document: ThreadworkDocument): List<Node> {
        val visited = linkedSetOf<NodeId>()
        fun visit(id: NodeId) {
            if (!visited.add(id)) return
            document.nodes[id]?.children?.forEach(::visit)
        }
        visit(document.rootNodeId)
        document.nodes.keys.sortedBy { it.value }.forEach(::visit)
        return visited.mapNotNull(document.nodes::get)
    }

    private fun repairDocument(document: ThreadworkDocument) {
        repairParentChildReferences(document)
        repairLinkReferences(document)
    }

    private fun repairParentChildReferences(document: ThreadworkDocument) {
        document.nodes[document.rootNodeId] ?: return
        val oldChildren = document.nodes.mapValues { (_, node) -> node.children.toList() }
        val inferredParents = mutableMapOf<NodeId, NodeId>()
        oldChildren.forEach { (parentId, children) ->
            children.distinct().forEach { childId ->
                if (childId in document.nodes && childId != document.rootNodeId && childId !in inferredParents) {
                    inferredParents[childId] = parentId
                }
            }
        }
        document.nodes.values.forEach { node ->
            if (node.id == document.rootNodeId) {
                node.parentId = null
                return@forEach
            }
            val parentId = node.parentId
                ?.takeIf { it in document.nodes && it != node.id }
                ?: inferredParents[node.id]?.takeIf { it in document.nodes && it != node.id }
                ?: document.rootNodeId
            node.parentId = parentId
        }
        document.nodes.values.forEach { it.children.clear() }
        oldChildren.forEach { (parentId, children) ->
            val parent = document.nodes[parentId] ?: return@forEach
            children.distinct().forEach { childId ->
                val child = document.nodes[childId] ?: return@forEach
                if (child.parentId == parentId && child.id !in parent.children) parent.children += child.id
            }
        }
        document.nodes.values.forEach { node ->
            if (node.id == document.rootNodeId) return@forEach
            val parent = node.parentId?.let(document.nodes::get) ?: return@forEach
            if (node.id !in parent.children) parent.children += node.id
        }
    }

    private fun repairLinkReferences(document: ThreadworkDocument) {
        document.nodes.values.forEach {
            it.incomingLinks.clear()
            it.outgoingLinks.clear()
        }
        document.nodes.values.filter { it.isLink }.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            val source = document.nodes[link.sourceNodeId]
            val target = document.nodes[link.targetNodeId]
            if (source != null && target != null && !source.isLink && !target.isLink) {
                val expectedParentId = document.closestCommonAncestorId(source.id, target.id) ?: document.rootNodeId
                if (linkNode.parentId != expectedParentId) {
                    linkNode.parentId?.let { document.nodes[it]?.children?.removeAll { childId -> childId == linkNode.id } }
                    linkNode.parentId = expectedParentId
                }
                document.nodes[expectedParentId]?.children?.let { children ->
                    if (linkNode.id !in children) children += linkNode.id
                }
                link.compositeBoundaryIds = document.compositeBoundaryIdsBetween(source.id, target.id).toMutableList()
            }
            if (source != null) {
                val name = link.sourcePortName.ifBlank { "out" }
                link.sourcePortName = name
                ensurePort(source, name, PortDirection.Output)
                if (linkNode.id !in source.outgoingLinks) source.outgoingLinks += linkNode.id
            }
            if (target != null) {
                val name = link.targetPortName.ifBlank { "in" }
                link.targetPortName = name
                ensurePort(target, name, PortDirection.Input)
                if (linkNode.id !in target.incomingLinks) target.incomingLinks += linkNode.id
            }
        }
    }

    private fun ensurePort(node: Node, name: String, direction: PortDirection) {
        if (node.ports.any { it.name == name && it.direction == direction }) return
        val baseId = "${direction.name.lowercase()}_${sanitizePortId(name)}"
        var id = baseId
        var index = 2
        while (node.ports.any { it.id == id }) {
            id = "${baseId}_$index"
            index++
        }
        node.ports += NodePort(id, name, direction)
    }

    private fun sanitizePortId(name: String): String =
        name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "port" }
}

@Serializable
private data class ThreadworkDocumentFile(
    val id: String,
    var name: String,
    var rootNodeId: NodeId,
    val nodes: List<Node> = emptyList(),
    var metadata: MutableMap<String, String> = mutableMapOf(),
    var masterRevision: Revision = Revision(),
)
