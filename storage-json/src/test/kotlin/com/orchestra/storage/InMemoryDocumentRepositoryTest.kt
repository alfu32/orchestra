package com.orchestra.storage

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.PortDirection
import com.orchestra.core.validation.DocumentValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryDocumentRepositoryTest {
    @Test
    fun `generated ids are uuid backed`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val node = repository.createNode(repository.getDocument().rootNodeId, "source", NodeKind.Processor)

        assertTrue(uuidBackedId.matches(node.id.value))
    }

    @Test
    fun `generated ids do not collide after replacing document`() {
        val root = NodeId("root")
        val existing = NodeId("node_1")
        val document = InflowDocument(
            id = "doc",
            name = "opened",
            rootNodeId = root,
            nodes = mutableMapOf(
                root to Node(root, "opened", NodeKind.Group, children = mutableListOf(existing)),
                existing to Node(existing, "existing", NodeKind.Processor, parentId = root),
            ),
        )
        val repository = InMemoryDocumentRepository()

        repository.replaceDocument(document)
        val inserted = repository.createNode(root, "inserted", NodeKind.Processor)

        assertFalse(inserted.id in setOf(root, existing))
        assertTrue(inserted.id in repository.getDocument().nodes)
        assertTrue(uuidBackedId.matches(inserted.id.value))
    }

    @Test
    fun `create link synchronizes endpoints`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))

        val link = repository.createLink(root, "source to target", source.id, "data", target.id, "data")

        assertEquals(listOf(link.id), repository.requireNode(source.id).outgoingLinks)
        assertEquals(listOf(link.id), repository.requireNode(target.id).incomingLinks)
        assertTrue(DocumentValidator.validate(repository.getDocument()).isEmpty())
        assertTrue(repository.isDirty())
    }

    @Test
    fun `links can target another link as an endpoint`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))
        val outerLink = repository.createLink(root, "outer", source.id, "data", target.id, "data")
        repository.addPort(source.id, NodePort("out_tap", "tap", PortDirection.Output))
        repository.addPort(outerLink.id, NodePort("in_tap", "tap", PortDirection.Input))

        val tapLink = repository.createLink(root, "tap outer", source.id, "tap", outerLink.id, "tap")

        assertEquals(listOf(outerLink.id), repository.requireNode(target.id).incomingLinks)
        assertTrue(tapLink.id in repository.requireNode(source.id).outgoingLinks)
        assertEquals(listOf(tapLink.id), repository.requireNode(outerLink.id).incomingLinks)
        assertTrue(DocumentValidator.validate(repository.getDocument()).isEmpty())
    }

    @Test
    fun `move node updates old and new parent children`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val parentA = repository.createNode(root, "a", NodeKind.Group)
        val parentB = repository.createNode(root, "b", NodeKind.Group)
        val child = repository.createNode(parentA.id, "child", NodeKind.Processor)

        repository.moveNode(child.id, parentB.id)

        assertFalse(child.id in repository.requireNode(parentA.id).children)
        assertTrue(child.id in repository.requireNode(parentB.id).children)
        assertEquals(parentB.id, repository.requireNode(child.id).parentId)
    }

    private companion object {
        val uuidBackedId = Regex("""node_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")
    }
}
