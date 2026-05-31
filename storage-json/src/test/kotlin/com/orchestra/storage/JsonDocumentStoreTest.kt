package com.orchestra.storage

import com.orchestra.core.model.NodeKind
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDocumentStoreTest {
    @Test
    fun `saves and loads document`() {
        val repository = InMemoryDocumentRepository(newDocument("json test"))
        repository.createNode(repository.getDocument().rootNodeId, "child", NodeKind.Processor)
        val file = createTempFile(suffix = ".inflow.json")
        val store = KotlinxJsonDocumentStore()

        store.save(repository.getDocument(), file)
        val loaded = store.load(file)

        assertEquals(repository.getDocument().name, loaded.name)
        assertEquals(repository.getDocument().nodes.keys, loaded.nodes.keys)
    }
}
