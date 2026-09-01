package com.threadwork.app.ui

import com.threadwork.storage.KotlinxJsonDocumentStore
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowArchetypeResourcesTest {
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
