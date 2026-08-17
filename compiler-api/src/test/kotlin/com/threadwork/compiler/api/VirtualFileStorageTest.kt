package com.threadwork.compiler.api

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeText
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.getElementById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VirtualFileStorageTest {
    @Test
    fun `stores node hierarchy as virtual filesystem chunk`() {
        val document = sampleDocument()
        val storage = ThreadworkDocumentFilesystemStorage()

        val files = storage.store(document, document.getElementById(NodeId("root"))!!)

        assertTrue(files.any { it.path == "Root_Project/declaration.js" })
        assertTrue(files.any { it.path == "Root_Project/Worker_Node/spec.md" })
        assertTrue(files.any { it.path == "Root_Project/Worker_Node/metadata.json" })
        assertEquals("console.log('root')", files.single { it.path == "Root_Project/declaration.js" }.content)
        assertEquals("worker body", files.single { it.path == "Root_Project/Worker_Node/declaration.js" }.content)
    }

    @Test
    fun `restores node text from virtual filesystem chunk`() {
        val source = sampleDocument()
        val storage = ThreadworkDocumentFilesystemStorage()
        val files = storage.store(source, source.getElementById(NodeId("root"))!!)
            .map {
                if (it.path.endsWith("Worker_Node/declaration.js")) it.copy(content = "restored worker body") else it
            }
        val target = ThreadworkDocument(
            id = "target",
            name = "",
            rootNodeId = NodeId("missing"),
        )

        storage.restore(target, files)

        val restoredWorker = target.getElementById(NodeId("worker"))!!
        assertEquals(NodeId("root"), target.rootNodeId)
        assertEquals("Root Project", target.name)
        assertEquals("restored worker body", restoredWorker.text.declaration)
        assertEquals(NodeId("root"), restoredWorker.parentId)
        assertTrue(NodeId("worker") in target.getElementById(NodeId("root"))!!.children)
    }

    private fun sampleDocument(): ThreadworkDocument {
        val rootId = NodeId("root")
        val workerId = NodeId("worker")
        val root = Node(
            id = rootId,
            name = "Root Project",
            kind = NodeKind.Processor,
            children = mutableListOf(workerId),
            text = NodeText(declaration = "console.log('root')"),
            technology = TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"),
        )
        val worker = Node(
            id = workerId,
            name = "Worker Node",
            kind = NodeKind.Processor,
            parentId = rootId,
            text = NodeText(
                declaration = "worker body",
                specification = "worker spec",
            ),
        )
        return ThreadworkDocument(
            id = "doc",
            name = "Root Project",
            rootNodeId = rootId,
            nodes = mutableMapOf(rootId to root, workerId to worker),
        )
    }
}
