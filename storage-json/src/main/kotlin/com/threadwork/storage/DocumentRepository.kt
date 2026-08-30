package com.threadwork.storage

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.LinkData
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.ModificationMetadata
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeLayout
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.NodeText
import com.threadwork.core.model.Revision
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.closestCommonAncestorId
import com.threadwork.core.model.compositeBoundaryIdsBetween
import java.util.UUID
import java.time.Instant

private val legacyCapabilityTransportKinds = setOf(
    "usage",
    "use",
    "import",
    "library",
    "lib",
    "dependency",
    "di",
    "inject",
    "injection",
)

interface DocumentRepository {
    fun getDocument(): ThreadworkDocument
    fun replaceDocument(document: ThreadworkDocument)

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
    fun updateNodeTypeDefinition(id: NodeId, definition: TypeDefinition)
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
    private var document: ThreadworkDocument = newDocument("Untitled"),
    private val idGenerator: IdGenerator = UuidIdGenerator(),
    private val modifiedDateProvider: () -> String = { Instant.now().toString() },
    private val modifiedUserProvider: () -> String = { System.getProperty("user.name").orEmpty() },
) : DocumentRepository {
    private var dirty = false

    override fun getDocument(): ThreadworkDocument = document

    override fun replaceDocument(document: ThreadworkDocument) {
        this.document = document
        synchronizeAllLinks()
        dirty = false
    }

    override fun getNode(id: NodeId): Node? = document.nodes[id]

    override fun requireNode(id: NodeId): Node =
        getNode(id) ?: error("Node '$id' does not exist")

    override fun createNode(parentId: NodeId?, name: String, kind: NodeKind): Node {
        parentId?.let(::requireNode)
        val node = Node(
            id = nextNodeId("node"),
            name = name,
            kind = kind,
            parentId = parentId,
            typeDefinition = TypeDefinition().takeIf { kind == NodeKind.Type },
        )
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

    override fun updateNodeTypeDefinition(id: NodeId, definition: TypeDefinition) {
        val node = requireNode(id)
        require(node.kind == NodeKind.Type) { "Node '$id' is not a type" }
        if (node.typeDefinition == definition) return
        node.typeDefinition = definition.copy(fields = definition.fields.map { it.copy() }.toMutableList())
        touchNodes(listOf(id))
        markDirty()
    }

    override fun updateLinkData(id: NodeId, linkData: LinkData) {
        val node = requireNode(id)
        require(node.isLink) { "Node '$id' is not a link" }
        if (node.link == linkData) return
        requireValidEndpoint(linkData.sourceNodeId, "source")
        requireValidEndpoint(linkData.targetNodeId, "target")
        val oldLink = node.link
        oldLink?.let {
            document.nodes[it.sourceNodeId]?.outgoingLinks?.removeAll { linkId -> linkId == id }
            document.nodes[it.targetNodeId]?.incomingLinks?.removeAll { linkId -> linkId == id }
        }
        val requestedInteractionKind = LinkInteractionKinds.canonicalId(linkData.interactionKind)
        val normalizedInteractionKind = if (
            requestedInteractionKind == LinkInteractionKinds.Data &&
            linkData.transportKind.trim().lowercase() in legacyCapabilityTransportKinds
        ) {
            LinkInteractionKinds.Library
        } else {
            requestedInteractionKind
        }
        require(LinkInteractionKinds.isKnown(normalizedInteractionKind)) {
            "Unknown link interaction kind '${linkData.interactionKind}'"
        }
        node.link = linkData.copy(
            compositeBoundaryIds = linkData.compositeBoundaryIds.toMutableList(),
            interactionKind = normalizedInteractionKind,
        )
        synchronizeLink(node)
        touchNodes(
            listOfNotNull(
                id,
                oldLink?.sourceNodeId,
                oldLink?.targetNodeId,
                linkData.sourceNodeId,
                linkData.targetNodeId,
                node.parentId,
            ),
        )
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
        requireValidEndpoint(sourceNodeId, "source")
        requireValidEndpoint(targetNodeId, "target")
        val owningParentId = document.closestCommonAncestorId(sourceNodeId, targetNodeId)
            ?: document.rootNodeId
        val node = Node(
            id = nextNodeId("link"),
            name = name,
            kind = NodeKind.Link,
            parentId = owningParentId,
            link = LinkData(
                sourceNodeId,
                sourcePortName,
                targetNodeId,
                targetPortName,
                compositeBoundaryIds = document.compositeBoundaryIdsBetween(sourceNodeId, targetNodeId).toMutableList(),
                interactionKind = LinkInteractionKinds.Data,
            ),
        )
        document.nodes[node.id] = node
        val parent = requireNode(owningParentId)
        if (node.id !in parent.children) parent.children += node.id
        val source = requireNode(sourceNodeId)
        val target = requireNode(targetNodeId)
        if (node.id !in source.outgoingLinks) source.outgoingLinks += node.id
        if (node.id !in target.incomingLinks) target.incomingLinks += node.id
        touchNodes(listOf(node.id, owningParentId, sourceNodeId, targetNodeId))
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
        synchronizeAllLinks()
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

    private fun requireValidEndpoint(id: NodeId, role: String): Node =
        requireNode(id).also { endpoint ->
            require(!endpoint.isLink) { "Link $role '$id' cannot be another link" }
            require(!endpoint.isType) { "Link $role '$id' cannot be a type declaration" }
        }

    private fun synchronizeAllLinks() {
        document.nodes.values.forEach { node ->
            node.incomingLinks.clear()
            node.outgoingLinks.clear()
        }
        document.nodes.values.filter(Node::isLink).forEach(::synchronizeLink)
    }

    private fun synchronizeLink(linkNode: Node) {
        val link = linkNode.link ?: return
        val source = document.nodes[link.sourceNodeId] ?: return
        val target = document.nodes[link.targetNodeId] ?: return
        if (source.isLink || target.isLink || source.isType || target.isType) return

        val expectedParentId = document.closestCommonAncestorId(source.id, target.id) ?: document.rootNodeId
        if (linkNode.parentId != expectedParentId) {
            linkNode.parentId?.let { oldParentId ->
                document.nodes[oldParentId]?.children?.removeAll { it == linkNode.id }
            }
            linkNode.parentId = expectedParentId
        }
        document.nodes[expectedParentId]?.children?.let { children ->
            if (linkNode.id !in children) children += linkNode.id
        }
        link.compositeBoundaryIds = document.compositeBoundaryIdsBetween(source.id, target.id).toMutableList()
        if (linkNode.id !in source.outgoingLinks) source.outgoingLinks += linkNode.id
        if (linkNode.id !in target.incomingLinks) target.incomingLinks += linkNode.id
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

fun newDocument(name: String): ThreadworkDocument {
    val rootId = NodeId("root")
    val root = Node(rootId, name, NodeKind.Processor)
    return ThreadworkDocument(
        id = "document_${System.currentTimeMillis()}",
        name = name,
        rootNodeId = rootId,
        nodes = mutableMapOf(rootId to root),
    )
}
