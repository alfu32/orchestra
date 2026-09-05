package com.threadwork.storage

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.Revision
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.core.validation.DocumentValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class InMemoryDocumentRepositoryTest {
    @Test
    fun `declared type can be assigned to a link and validates`() {
        val repository = InMemoryDocumentRepository(newDocument("typed flow"))
        val root = repository.getDocument().rootNodeId
        val record = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            record.id,
            TypeDefinition(
                mutableListOf(
                    TypeFieldDefinition("id", "number"),
                    TypeFieldDefinition("parent", record.id.value, isReference = true),
                ),
            ),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "orders", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "orders", PortDirection.Input))
        val link = repository.createLink(root, "orders", source.id, "orders", target.id, "orders")

        repository.updateLinkData(
            link.id,
            requireNotNull(link.link).copy(typeDefinitionId = record.id.value),
        )

        assertEquals(record.id.value, repository.requireNode(link.id).link?.typeDefinitionId)
        assertTrue(DocumentValidator.validate(repository.getDocument()).isEmpty())
    }

    @Test
    fun `unknown link type is rejected by validation`() {
        val repository = InMemoryDocumentRepository(newDocument("typed flow"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "orders", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "orders", PortDirection.Input))
        val link = repository.createLink(root, "orders", source.id, "orders", target.id, "orders")
        repository.updateLinkData(
            link.id,
            requireNotNull(link.link).copy(typeDefinitionId = "missing_type"),
        )

        assertTrue(DocumentValidator.validate(repository.getDocument()).any { it.message.contains("unknown type") })
    }

    @Test
    fun `renaming the root synchronizes the legacy document name`() {
        val repository = InMemoryDocumentRepository(newDocument("Untitled Threadwork"))

        repository.renameNode(repository.getDocument().rootNodeId, "Renamed Project")

        assertEquals("Renamed Project", repository.getDocument().name)
    }

    @Test
    fun `name detail is updated and persisted on the node`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val node = repository.createNode(repository.getDocument().rootNodeId, "@ProcessorDeclaration", NodeKind.Processor)

        repository.updateNodeNameDetail(node.id, "used for generated processor source")

        assertEquals("used for generated processor source", repository.requireNode(node.id).nameDetail)
        assertTrue(repository.isDirty())
    }

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
        val document = ThreadworkDocument(
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
    fun `links cannot target another link as an endpoint`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))
        val outerLink = repository.createLink(root, "outer", source.id, "data", target.id, "data")
        repository.addPort(source.id, NodePort("out_tap", "tap", PortDirection.Output))

        assertFailsWith<IllegalArgumentException> {
            repository.createLink(root, "tap outer", source.id, "tap", outerLink.id, "tap")
        }
    }

    @Test
    fun `link belongs to endpoint closest common parent and records crossed composites`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val left = repository.createNode(root, "left", NodeKind.Group)
        val nested = repository.createNode(left.id, "nested", NodeKind.Group)
        val source = repository.createNode(nested.id, "source", NodeKind.Processor)
        val right = repository.createNode(root, "right", NodeKind.Group)
        val target = repository.createNode(right.id, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))

        val link = repository.createLink(left.id, "data", source.id, "data", target.id, "data")

        assertEquals(root, link.parentId)
        assertEquals(
            listOf(nested.id, left.id, right.id),
            requireNotNull(link.link).compositeBoundaryIds.toList(),
        )
        assertTrue(DocumentValidator.validate(repository.getDocument()).isEmpty())
    }

    @Test
    fun `reparenting an endpoint updates link ownership and crossed composites`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val left = repository.createNode(root, "left", NodeKind.Group)
        val source = repository.createNode(left.id, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))
        val link = repository.createLink(root, "data", source.id, "data", target.id, "data")

        repository.moveNode(target.id, left.id)

        assertEquals(left.id, link.parentId)
        assertEquals(emptyList(), requireNotNull(link.link).compositeBoundaryIds.toList())
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

    @Test
    fun `semantic edits copy master revision and stamp modification metadata`() {
        val repository = InMemoryDocumentRepository(
            document = newDocument("test").apply { masterRevision = Revision("R2", "2026-08-17") },
            modifiedDateProvider = { "2026-08-17T10:15:30Z" },
            modifiedUserProvider = { "devlin" },
        )
        val root = repository.getDocument().rootNodeId
        val node = repository.createNode(root, "source", NodeKind.Processor)

        repository.updateNodeText(node.id, node.text.copy(declaration = "return value"))

        assertEquals(Revision("R2", "2026-08-17"), node.revision)
        assertEquals("2026-08-17T10:15:30Z", node.modified.date)
        assertEquals("devlin", node.modified.user)
    }

    @Test
    fun `layout edits do not change revision or modification metadata`() {
        var timestamp = "created"
        val repository = InMemoryDocumentRepository(
            document = newDocument("test").apply { masterRevision = Revision("R1", "2026-08-16") },
            modifiedDateProvider = { timestamp },
            modifiedUserProvider = { "devlin" },
        )
        val root = repository.getDocument().rootNodeId
        val node = repository.createNode(root, "source", NodeKind.Processor)
        timestamp = "moved"
        repository.updateMasterRevision(Revision("R2", "2026-08-17"))

        repository.updateNodeLayout(node.id, node.layout.copy(x = 100.0, y = 200.0))

        assertEquals(Revision("R1", "2026-08-16"), node.revision)
        assertEquals("created", node.modified.date)
    }

    @Test
    fun `link creation stamps link endpoints and parent`() {
        val repository = InMemoryDocumentRepository(
            document = newDocument("test").apply { masterRevision = Revision("R3", "2026-08-17") },
            modifiedDateProvider = { "linked" },
            modifiedUserProvider = { "devlin" },
        )
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))

        val link = repository.createLink(root, "data", source.id, "data", target.id, "data")

        listOf(link, source, target, repository.requireNode(root)).forEach { node ->
            assertEquals(Revision("R3", "2026-08-17"), node.revision)
            assertEquals("linked", node.modified.date)
        }
    }

    private companion object {
        val uuidBackedId = Regex("""node_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")
    }
}
