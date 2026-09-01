package com.threadwork.app.ui

import com.threadwork.storage.KotlinxJsonDocumentStore
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowArchetypeResourcesTest {
    @Test
    fun `selected topology archetype retains internal links and their types`() {
        val repository = InMemoryDocumentRepository(newDocument("Archetype Test"))
        val root = repository.getDocument().rootNodeId
        val packet = repository.createNode(root, "Packet", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            packet.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "string"))),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        val omitted = repository.createNode(root, "omitted", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "out", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "in", PortDirection.Input))
        val link = repository.createLink(root, "packet", source.id, "out", target.id, "in")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = packet.id.value))

        val snapshot = archetypeSnapshot(repository.getDocument(), linkedSetOf(source.id, target.id))

        assertTrue(snapshot.nodes.containsKey(source.id))
        assertTrue(snapshot.nodes.containsKey(target.id))
        assertTrue(snapshot.nodes.containsKey(packet.id))
        assertTrue(snapshot.nodes.containsKey(link.id))
        assertTrue(snapshot.nodes.containsKey(omitted.id).not())
        assertEquals(source.id, snapshot.nodes.getValue(link.id).link?.sourceNodeId)
        assertEquals(target.id, snapshot.nodes.getValue(link.id).link?.targetNodeId)
        assertEquals(packet.id.value, snapshot.nodes.getValue(link.id).link?.typeDefinitionId)
    }

    @Test
    fun `bundled workflow archetypes are grouped valid specification-only documents`() {
        val store = KotlinxJsonDocumentStore()
        val resources = listOf(
            "/workflow-archetypes/integration/request-response.orch",
            "/workflow-archetypes/quality/validation-pipeline.orch",
        )

        resources.forEach { path ->
            val source = requireNotNull(javaClass.getResourceAsStream(path))
                .bufferedReader()
                .use { it.readText() }
            val document = store.loadText(source)
            assertTrue(document.nodes.values.any { it.isLink })
            assertTrue(document.nodes.values.filterNot { it.isLink }.all { it.text.declaration.isBlank() })
            assertTrue(
                document.nodes.values
                    .filter { it.id != document.rootNodeId && !it.isLink }
                    .all { it.text.specification.isNotBlank() },
            )
        }

        val catalogRows = requireNotNull(javaClass.getResourceAsStream("/workflow-archetypes/catalog.tsv"))
            .bufferedReader()
            .useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith('#') }.toList() }
        assertEquals(resources.size, catalogRows.size)
        assertTrue(catalogRows.all { row -> row.substringAfterLast('\t').count { it == '/' } == 1 })
    }

    @Test
    fun `user archetypes are discovered from immediate folders beside bundled archetypes`() {
        val store = KotlinxJsonDocumentStore()
        val userFolder = createTempDirectory("threadwork-user-archetypes")
        val customGroup = Files.createDirectories(userFolder.resolve("custom-flows"))
        val source = requireNotNull(
            javaClass.getResourceAsStream("/workflow-archetypes/integration/request-response.orch"),
        ).bufferedReader().use { it.readText() }
        Files.writeString(customGroup.resolve("custom-request.orch"), source)

        val archetypes = loadWorkflowArchetypes(store, userFolder)

        assertEquals(3, archetypes.size)
        assertEquals(2, archetypes.count { !it.id.startsWith("user:") })
        val custom = archetypes.single { it.id == "user:custom-flows/custom-request.orch" }
        assertEquals("custom-flows", custom.group)
        assertEquals("Request Response Template", custom.label)
        assertTrue(custom.description.startsWith("Coordinates one request"))
    }

    @Test
    fun `archetype filenames are safe without changing model names`() {
        assertEquals("Order-processing-v2", archetypeFileStem("Order processing / v2"))
        assertEquals("archetype", archetypeFileStem(" / "))
    }
}
