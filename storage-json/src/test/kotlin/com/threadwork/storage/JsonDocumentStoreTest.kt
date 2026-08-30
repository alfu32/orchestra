package com.threadwork.storage

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.LinkData
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.Revision
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.core.validation.DocumentValidator
import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonDocumentStoreTest {
    @Test
    fun `link interaction kinds round trip while legacy model defaults to auto`() {
        val repository = InMemoryDocumentRepository(newDocument("capability json"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, com.threadwork.core.model.NodePort("src", "src", PortDirection.Output))
        repository.addPort(target.id, com.threadwork.core.model.NodePort("builder", "builder", PortDirection.Input))
        val link = repository.createLink(root, "source_builder", source.id, "src", target.id, "builder")
        repository.updateLinkData(
            link.id,
            requireNotNull(link.link).copy(interactionKind = LinkInteractionKinds.Source),
        )
        val file = createTempFile(suffix = ".threadwork.orch")

        KotlinxJsonDocumentStore().save(repository.getDocument(), file)
        val loaded = KotlinxJsonDocumentStore().load(file)

        assertEquals(LinkInteractionKinds.Source, loaded.nodes.getValue(link.id).link?.interactionKind)
        assertEquals(
            LinkInteractionKinds.Auto,
            LinkData(source.id, "out", target.id, "in").interactionKind,
        )
    }

    @Test
    fun `type declarations and link type references round trip`() {
        val repository = InMemoryDocumentRepository(newDocument("typed json"))
        val root = repository.getDocument().rootNodeId
        val type = repository.createNode(root, "Envelope", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("payload", "string"))),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, com.threadwork.core.model.NodePort("out", "packet", PortDirection.Output))
        repository.addPort(target.id, com.threadwork.core.model.NodePort("in", "packet", PortDirection.Input))
        val link = repository.createLink(root, "packet", source.id, "packet", target.id, "packet")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = type.id.value))
        val file = createTempFile(suffix = ".threadwork.orch")

        KotlinxJsonDocumentStore().save(repository.getDocument(), file)
        val loaded = KotlinxJsonDocumentStore().load(file)

        assertEquals("payload", loaded.nodes.getValue(type.id).typeDefinition?.fields?.single()?.name)
        assertEquals(type.id.value, loaded.nodes.getValue(link.id).link?.typeDefinitionId)
    }

    @Test
    fun `saves and loads document`() {
        val repository = InMemoryDocumentRepository(newDocument("json test"))
        repository.getDocument().masterRevision = Revision("R4", "2026-08-17")
        val child = repository.createNode(repository.getDocument().rootNodeId, "child", NodeKind.Processor)
        repository.updateNodeResponsible(child.id, "Ada")
        val file = createTempFile(suffix = ".threadwork.json")
        val store = KotlinxJsonDocumentStore()

        store.save(repository.getDocument(), file)
        val loaded = store.load(file)
        val savedJson = Json.parseToJsonElement(Files.readString(file)).jsonObject

        assertEquals(repository.getDocument().name, loaded.name)
        assertEquals(repository.getDocument().nodes.keys, loaded.nodes.keys)
        assertEquals(Revision("R4", "2026-08-17"), loaded.masterRevision)
        assertEquals("Ada", loaded.nodes.getValue(child.id).responsible)
        assertEquals("R4", loaded.nodes.getValue(child.id).revision?.name)
        assertTrue(loaded.nodes.getValue(child.id).modified.date.isNotBlank())
        assertTrue(savedJson.getValue("nodes") is JsonArray)
    }

    @Test
    fun `loads and repairs duplicate children and missing endpoint ports`() {
        val rootId = NodeId("root")
        val sourceId = NodeId("node_1")
        val targetId = NodeId("node_2")
        val linkId = NodeId("link_1")
        val document = ThreadworkDocument(
            id = "document_repair",
            name = "repair test",
            rootNodeId = rootId,
            nodes = mutableMapOf(
                rootId to Node(
                    id = rootId,
                    name = "repair test",
                    kind = NodeKind.Group,
                    children = mutableListOf(sourceId, sourceId, targetId, targetId, linkId),
                ),
                sourceId to Node(sourceId, "source", NodeKind.Processor, parentId = rootId),
                targetId to Node(targetId, "target", NodeKind.Processor, parentId = rootId),
                linkId to Node(
                    id = linkId,
                    name = "items",
                    kind = NodeKind.Link,
                    parentId = rootId,
                    link = LinkData(sourceId, "items", targetId, "items"),
                ),
            ),
        )
        val file = createTempFile(suffix = ".threadwork.json")
        val store = KotlinxJsonDocumentStore()

        store.save(document, file)
        val loaded = store.load(file)

        assertEquals(listOf(sourceId, targetId, linkId), loaded.nodes.getValue(rootId).children)
        assertTrue(loaded.nodes.getValue(sourceId).ports.any { it.name == "items" && it.direction == PortDirection.Output })
        assertTrue(loaded.nodes.getValue(targetId).ports.any { it.name == "items" && it.direction == PortDirection.Input })
        assertTrue(linkId in loaded.nodes.getValue(sourceId).outgoingLinks)
        assertTrue(linkId in loaded.nodes.getValue(targetId).incomingLinks)
        assertTrue(DocumentValidator.validate(loaded).none { it.severity.name == "Error" })
    }

    @Test
    fun `loads legacy links with link endpoints as processing nodes`() {
        val rootId = NodeId("root")
        val sourceId = NodeId("source")
        val targetId = NodeId("target")
        val endpointLinkId = NodeId("link_endpoint")
        val legacyLinkId = NodeId("link_legacy")
        val document = ThreadworkDocument(
            id = "legacy_link_endpoint_document",
            name = "legacy link endpoints",
            rootNodeId = rootId,
            nodes = mutableMapOf(
                rootId to Node(
                    id = rootId,
                    name = "legacy link endpoints",
                    kind = NodeKind.Group,
                    children = mutableListOf(sourceId, targetId, endpointLinkId, legacyLinkId),
                ),
                sourceId to Node(sourceId, "source", NodeKind.Processor, parentId = rootId),
                targetId to Node(targetId, "target", NodeKind.Processor, parentId = rootId),
                endpointLinkId to Node(
                    id = endpointLinkId,
                    name = "endpoint",
                    kind = NodeKind.Link,
                    parentId = rootId,
                    link = LinkData(sourceId, "out", targetId, "in"),
                ),
                legacyLinkId to Node(
                    id = legacyLinkId,
                    name = "legacy bridge",
                    kind = NodeKind.Link,
                    parentId = rootId,
                    text = com.threadwork.core.model.NodeText(specification = "Preserve this content."),
                    link = LinkData(endpointLinkId, "in", targetId, "out", transportKind = "ipc"),
                ),
            ),
        )
        val file = createTempFile(suffix = ".orch")
        val store = KotlinxJsonDocumentStore()

        store.save(document, file)
        val loaded = store.load(file)
        val migrated = loaded.nodes.getValue(legacyLinkId)

        assertEquals(NodeKind.Processor, migrated.kind)
        assertFalse(migrated.isLink)
        assertEquals("Preserve this content.", migrated.text.specification)
        assertEquals(endpointLinkId.value, migrated.metadata["threadwork.migratedLinkSourceId"])
        assertEquals("ipc", migrated.metadata["threadwork.migratedLinkTransportKind"])
        assertTrue(migrated.ports.any { it.name == "in" && it.direction == PortDirection.Input })
        assertTrue(migrated.ports.any { it.name == "out" && it.direction == PortDirection.Output })
        assertTrue(DocumentValidator.validate(loaded).none { it.severity.name == "Error" })
    }

    @Test
    fun `loads legacy node map by trusting node ids instead of map keys`() {
        val file = createTempFile(suffix = ".threadwork.json")
        Files.writeString(
            file,
            """
            {
              "id": "document_legacy",
              "name": "legacy",
              "rootNodeId": "root",
              "nodes": {
                "wrong_root_key": {
                  "id": "root",
                  "name": "legacy",
                  "kind": "Group"
                }
              },
              "metadata": {}
            }
            """.trimIndent(),
        )
        val store = KotlinxJsonDocumentStore()

        val loaded = store.load(file)

        assertTrue(NodeId("root") in loaded.nodes)
        assertFalse(NodeId("wrong_root_key") in loaded.nodes)
    }
}
