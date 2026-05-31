package com.orchestra.storage

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.LinkData
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodeLayout
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.NodeText
import com.orchestra.core.model.TechnologyMetadata

interface DocumentRepository {
    fun getDocument(): InflowDocument
    fun replaceDocument(document: InflowDocument)

    fun getNode(id: NodeId): Node?
    fun requireNode(id: NodeId): Node

    fun createNode(parentId: NodeId?, name: String, kind: NodeKind): Node
    fun deleteNode(id: NodeId)

    fun renameNode(id: NodeId, name: String)
    fun updateNodeLayout(id: NodeId, layout: NodeLayout)
    fun updateNodeText(id: NodeId, text: NodeText)
    fun updateNodeTechnology(id: NodeId, technology: TechnologyMetadata)

    fun addPort(nodeId: NodeId, port: NodePort)
    fun removePort(nodeId: NodeId, portId: String)

    fun createLink(
        parentId: NodeId?,
        name: String,
        sourceNodeId: NodeId,
        sourcePortName: String,
        targetNodeId: NodeId,
        targetPortName: String,
    ): Node

    fun moveNode(id: NodeId, newParentId: NodeId?)

    fun markDirty()
    fun clearDirty()
    fun isDirty(): Boolean
}

class InMemoryDocumentRepository(
    private var document: InflowDocument = newDocument("Untitled"),
    private val idGenerator: IdGenerator = SequentialIdGenerator(document.nodes.keys.map { it.value }),
) : DocumentRepository {
    private var dirty = false

    override fun getDocument(): InflowDocument = document

    override fun replaceDocument(document: InflowDocument) {
        this.document = document
        dirty = false
    }

    override fun getNode(id: NodeId): Node? = document.nodes[id]

    override fun requireNode(id: NodeId): Node =
        getNode(id) ?: error("Node '$id' does not exist")

    override fun createNode(parentId: NodeId?, name: String, kind: NodeKind): Node {
        parentId?.let(::requireNode)
        val node = Node(id = NodeId(idGenerator.next("node")), name = name, kind = kind, parentId = parentId)
        document.nodes[node.id] = node
        parentId?.let { requireNode(it).children += node.id }
        markDirty()
        return node
    }

    override fun deleteNode(id: NodeId) {
        if (id == document.rootNodeId) error("Cannot delete root node")
        val node = requireNode(id)
        node.children.toList().forEach(::deleteNode)
        if (node.isLink) unlink(node)
        node.incomingLinks.toList().forEach(::deleteNode)
        node.outgoingLinks.toList().forEach(::deleteNode)
        node.parentId?.let { document.nodes[it]?.children?.remove(id) }
        document.nodes.remove(id)
        markDirty()
    }

    override fun renameNode(id: NodeId, name: String) {
        requireNode(id).name = name
        markDirty()
    }

    override fun updateNodeLayout(id: NodeId, layout: NodeLayout) {
        requireNode(id).layout = layout
        markDirty()
    }

    override fun updateNodeText(id: NodeId, text: NodeText) {
        requireNode(id).text = text
        markDirty()
    }

    override fun updateNodeTechnology(id: NodeId, technology: TechnologyMetadata) {
        requireNode(id).technology = technology
        markDirty()
    }

    override fun addPort(nodeId: NodeId, port: NodePort) {
        val node = requireNode(nodeId)
        require(node.ports.none { it.id == port.id }) { "Port id '${port.id}' already exists on node '$nodeId'" }
        node.ports += port
        markDirty()
    }

    override fun removePort(nodeId: NodeId, portId: String) {
        requireNode(nodeId).ports.removeIf { it.id == portId }
        markDirty()
    }

    override fun createLink(
        parentId: NodeId?,
        name: String,
        sourceNodeId: NodeId,
        sourcePortName: String,
        targetNodeId: NodeId,
        targetPortName: String,
    ): Node {
        parentId?.let(::requireNode)
        requireNode(sourceNodeId)
        requireNode(targetNodeId)
        val node = Node(
            id = NodeId(idGenerator.next("link")),
            name = name,
            kind = NodeKind.Link,
            parentId = parentId,
            link = LinkData(sourceNodeId, sourcePortName, targetNodeId, targetPortName),
        )
        document.nodes[node.id] = node
        parentId?.let { requireNode(it).children += node.id }
        requireNode(sourceNodeId).outgoingLinks += node.id
        requireNode(targetNodeId).incomingLinks += node.id
        markDirty()
        return node
    }

    override fun moveNode(id: NodeId, newParentId: NodeId?) {
        if (id == document.rootNodeId) error("Cannot move root node")
        newParentId?.let(::requireNode)
        val node = requireNode(id)
        require(!isDescendant(newParentId, id)) { "Cannot move a node under its descendant" }
        node.parentId?.let { document.nodes[it]?.children?.remove(id) }
        node.parentId = newParentId
        newParentId?.let { requireNode(it).children += id }
        markDirty()
    }

    override fun markDirty() {
        dirty = true
    }

    override fun clearDirty() {
        dirty = false
    }

    override fun isDirty(): Boolean = dirty

    private fun unlink(linkNode: Node) {
        val link = linkNode.link ?: return
        document.nodes[link.sourceNodeId]?.outgoingLinks?.remove(linkNode.id)
        document.nodes[link.targetNodeId]?.incomingLinks?.remove(linkNode.id)
    }

    private fun isDescendant(candidateId: NodeId?, ancestorId: NodeId): Boolean {
        var current = candidateId
        while (current != null) {
            if (current == ancestorId) return true
            current = document.nodes[current]?.parentId
        }
        return false
    }
}

interface IdGenerator {
    fun next(prefix: String): String
}

class SequentialIdGenerator(existingIds: Iterable<String> = emptyList()) : IdGenerator {
    private var nextValue = existingIds.mapNotNull { it.substringAfterLast("_").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1

    override fun next(prefix: String): String = "${prefix}_${nextValue++}"
}

fun newDocument(name: String): InflowDocument {
    val rootId = NodeId("root")
    val root = Node(rootId, name, NodeKind.Group)
    return InflowDocument(
        id = "document_${System.currentTimeMillis()}",
        name = name,
        rootNodeId = rootId,
        nodes = mutableMapOf(rootId to root),
    )
}
