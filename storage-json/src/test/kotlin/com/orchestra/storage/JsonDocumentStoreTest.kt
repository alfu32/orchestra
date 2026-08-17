package com.orchestra.storage

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.LinkData
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.PortDirection
import com.orchestra.core.model.Revision
import com.orchestra.core.validation.DocumentValidator
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
    fun `saves and loads document`() {
        val repository = InMemoryDocumentRepository(newDocument("json test"))
        repository.getDocument().masterRevision = Revision("R4", "2026-08-17")
        val child = repository.createNode(repository.getDocument().rootNodeId, "child", NodeKind.Processor)
        repository.updateNodeResponsible(child.id, "Ada")
        val file = createTempFile(suffix = ".inflow.json")
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
        val document = InflowDocument(
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
        val file = createTempFile(suffix = ".inflow.json")
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
    fun `loads legacy node map by trusting node ids instead of map keys`() {
        val file = createTempFile(suffix = ".inflow.json")
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
