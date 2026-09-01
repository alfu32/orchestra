package com.threadwork.app.ui

import com.threadwork.storage.KotlinxJsonDocumentStore
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
}
