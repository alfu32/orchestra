package com.threadwork.app.ui

import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeLayout
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphCanvasCompositeLayoutTest {
    @Test
    fun `terminal nodes use the compact default height`() {
        val repository = InMemoryDocumentRepository(newDocument("compact nodes"))
        val node = repository.createNode(repository.getDocument().rootNodeId, "worker", NodeKind.Processor)
        repository.updateNodeLayout(
            node.id,
            node.layout.copy(height = 100.0, closedHeight = 100.0, openHeight = 100.0),
        )
        val canvas = GraphCanvas(repository, linkedSetOf(), {}, {}, {})

        canvas.refreshBoundsFromChildren()

        assertEquals(70.0, node.layout.height)
        assertEquals(70.0, node.layout.closedHeight)
        assertEquals(70.0, node.layout.openHeight)
    }

    @Test
    fun `expanded composite reserves horizontal routing clearance around children`() {
        val repository = InMemoryDocumentRepository(newDocument("routing clearance"))
        val root = repository.getDocument().rootNodeId
        val composite = repository.createNode(root, "group", NodeKind.Group)
        val leftChild = repository.createNode(composite.id, "left", NodeKind.Processor)
        val rightChild = repository.createNode(composite.id, "right", NodeKind.Processor)
        repository.updateNodeLayout(leftChild.id, NodeLayout(x = 1_000.0, y = 600.0, width = 240.0, height = 120.0))
        repository.updateNodeLayout(rightChild.id, NodeLayout(x = 1_800.0, y = 600.0, width = 240.0, height = 120.0))
        val canvas = GraphCanvas(repository, linkedSetOf(), {}, {}, {})

        canvas.refreshBoundsFromChildren()

        val leftClearance = leftChild.layout.x - composite.layout.x
        val rightClearance = composite.layout.x + composite.layout.width - rightChild.layout.x - rightChild.layout.width
        assertTrue(leftClearance >= 180.0, "left clearance was $leftClearance")
        assertTrue(rightClearance >= 180.0, "right clearance was $rightClearance")
    }
}
