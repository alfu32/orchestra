package com.orchestra.storage

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.LinkData
import com.orchestra.core.model.ModificationMetadata
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodeLayout
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.NodeText
import com.orchestra.core.model.Revision
import com.orchestra.core.model.TechnologyMetadata
import java.util.UUID
import java.time.Instant

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
    fun updateNodeFileLayoutStrategy(id: NodeId, strategyId: String)
    fun updateNodeMetadata(id: NodeId, metadata: Map<String, String>)
    fun updateNodeResponsible(id: NodeId, responsible: String?)
    fun updateLinkData(id: NodeId, linkData: LinkData)
    fun updateMasterRevision(revision: Revision)
    fun touchNode(id: NodeId)

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
    private val idGenerator: IdGenerator = UuidIdGenerator(),
    private val modifiedDateProvider: () -> String = { Instant.now().toString() },
    private val modifiedUserProvider: () -> String = { System.getProperty("user.name").orEmpty() },
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
        val node = Node(id = nextNodeId("node"), name = name, kind = kind, parentId = parentId)
        document.nodes[node.id] = node
        parentId?.let {
            val parent = requireNode(it)
            if (node.id !in parent.children) parent.children += node.id
        }
        touchNodes(listOfNotNull(node.id, parentId))
        markDirty()
        return node
    }

    override fun deleteNode(id: NodeId) {
        if (id == document.rootNodeId) error("Cannot delete root node")
        val node = requireNode(id)
        node.children.toList().filter { it in document.nodes }.forEach(::deleteNode)
        if (node.isLink) unlink(node)
        node.incomingLinks.toList().filter { it in document.nodes }.forEach(::deleteNode)
        node.outgoingLinks.toList().filter { it in document.nodes }.forEach(::deleteNode)
        node.parentId?.let { parentId ->
            document.nodes[parentId]?.children?.removeAll { childId -> childId == id }
            touchNodes(listOf(parentId))
        }
        document.nodes.remove(id)
        markDirty()
    }

    override fun renameNode(id: NodeId, name: String) {
        val node = requireNode(id)
        val documentNameChanged = id == document.rootNodeId && document.name != name
        if (node.name == name && !documentNameChanged) return
        node.name = name
        if (id == document.rootNodeId) document.name = name
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateNodeLayout(id: NodeId, layout: NodeLayout) {
        val node = requireNode(id)
        if (node.layout == layout) return
        node.layout = layout
        markDirty()
    }

    override fun updateNodeText(id: NodeId, text: NodeText) {
        val node = requireNode(id)
        if (node.text == text) return
        node.text = text
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateNodeTechnology(id: NodeId, technology: TechnologyMetadata) {
        val node = requireNode(id)
        if (node.technology == technology) return
        node.technology = technology
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateNodeFileLayoutStrategy(id: NodeId, strategyId: String) {
        val node = requireNode(id)
        if (node.fileLayoutStrategyId == strategyId) return
        node.fileLayoutStrategyId = strategyId
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateNodeMetadata(id: NodeId, metadata: Map<String, String>) {
        val node = requireNode(id)
        if (node.metadata == metadata) return
        node.metadata.clear()
        node.metadata.putAll(metadata)
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateNodeResponsible(id: NodeId, responsible: String?) {
        val node = requireNode(id)
        val normalized = responsible?.trim()?.takeIf(String::isNotBlank)
        if (node.responsible == normalized) return
        node.responsible = normalized
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateLinkData(id: NodeId, linkData: LinkData) {
        val node = requireNode(id)
        require(node.isLink) { "Node '$id' is not a link" }
        if (node.link == linkData) return
        node.link = linkData
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateMasterRevision(revision: Revision) {
        if (document.masterRevision == revision) return
        document.masterRevision = revision.copy()
        markDirty()
    }

    override fun touchNode(id: NodeId) {
        touchNodes(listOf(id))
        markDirty()
    }

    override fun addPort(nodeId: NodeId, port: NodePort) {
        val node = requireNode(nodeId)
        require(node.ports.none { it.id == port.id }) { "Port id '${port.id}' already exists on node '$nodeId'" }
        node.ports += port
        touchNodes(listOf(nodeId))
        markDirty()
    }

    override fun removePort(nodeId: NodeId, portId: String) {
        val removed = requireNode(nodeId).ports.removeIf { it.id == portId }
        if (!removed) return
        touchNodes(listOf(nodeId))
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
            id = nextNodeId("link"),
            name = name,
            kind = NodeKind.Link,
            parentId = parentId,
            link = LinkData(sourceNodeId, sourcePortName, targetNodeId, targetPortName),
        )
        document.nodes[node.id] = node
        parentId?.let {
            val parent = requireNode(it)
            if (node.id !in parent.children) parent.children += node.id
        }
        val source = requireNode(sourceNodeId)
        val target = requireNode(targetNodeId)
        if (node.id !in source.outgoingLinks) source.outgoingLinks += node.id
        if (node.id !in target.incomingLinks) target.incomingLinks += node.id
        touchNodes(listOfNotNull(node.id, parentId, sourceNodeId, targetNodeId))
        markDirty()
        return node
    }

    override fun moveNode(id: NodeId, newParentId: NodeId?) {
        if (id == document.rootNodeId) error("Cannot move root node")
        newParentId?.let(::requireNode)
        val node = requireNode(id)
        require(!isDescendant(newParentId, id)) { "Cannot move a node under its descendant" }
        val oldParentId = node.parentId
        oldParentId?.let { document.nodes[it]?.children?.removeAll { childId -> childId == id } }
        node.parentId = newParentId
        newParentId?.let {
            val parent = requireNode(it)
            if (id !in parent.children) parent.children += id
        }
        touchNodes(listOfNotNull(id, oldParentId, newParentId))
        markDirty()
    }

    override fun markDirty() {
        dirty = true
    }

    override fun clearDirty() {
        dirty = false
    }

    override fun isDirty(): Boolean = dirty

    private fun nextNodeId(prefix: String): NodeId {
        while (true) {
            val id = NodeId(idGenerator.next(prefix))
            if (id !in document.nodes) return id
        }
    }

    private fun unlink(linkNode: Node) {
        val link = linkNode.link ?: return
        document.nodes[link.sourceNodeId]?.outgoingLinks?.removeAll { it == linkNode.id }
        document.nodes[link.targetNodeId]?.incomingLinks?.removeAll { it == linkNode.id }
        touchNodes(listOf(link.sourceNodeId, link.targetNodeId))
    }

    private fun touchNodes(ids: Iterable<NodeId>) {
        val timestamp = modifiedDateProvider()
        val user = modifiedUserProvider()
        ids.distinct().forEach { id ->
            document.nodes[id]?.let { node ->
                node.revision = document.masterRevision.copy()
                node.modified = ModificationMetadata(timestamp, user)
            }
        }
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

class UuidIdGenerator : IdGenerator {
    override fun next(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
}

fun newDocument(name: String): InflowDocument {
    val rootId = NodeId("root")
    val root = Node(rootId, name, NodeKind.Processor)
    return InflowDocument(
        id = "document_${System.currentTimeMillis()}",
        name = name,
        rootNodeId = rootId,
        nodes = mutableMapOf(rootId to root),
    )
}
