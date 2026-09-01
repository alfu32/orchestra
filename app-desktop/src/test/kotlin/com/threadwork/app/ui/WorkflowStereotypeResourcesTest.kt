package com.threadwork.app.ui

import com.threadwork.storage.KotlinxJsonDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowStereotypeResourcesTest {
    @Test
    fun `bundled workflow stereotypes are valid specification-only documents`() {
        val store = KotlinxJsonDocumentStore()
        val resources = listOf(
            "/workflow-stereotypes/request-response.orch",
            "/workflow-stereotypes/validation-pipeline.orch",
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

        val catalog = requireNotNull(javaClass.getResourceAsStream("/workflow-stereotypes/catalog.tsv"))
            .bufferedReader()
            .useLines { lines -> lines.count { it.isNotBlank() && !it.startsWith('#') } }
        assertEquals(resources.size, catalog)
    }
}
