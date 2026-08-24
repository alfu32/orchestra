package com.threadwork.app.ui

import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeLayout
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertTrue

class GraphCanvasCompositeLayoutTest {
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
