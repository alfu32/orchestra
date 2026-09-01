package com.threadwork.app.ui

import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GraphCanvasCopyTest {
    @Test
    fun `copy uses explicit selection and reconnects selected links to copied endpoints`() {
        val repository = InMemoryDocumentRepository(newDocument("Copy Test"))
        val root = repository.getDocument().rootNodeId
        val composite = repository.createNode(root, "pipeline", NodeKind.Group)
        val source = repository.createNode(composite.id, "source", NodeKind.Processor)
        val unselectedChild = repository.createNode(composite.id, "not selected", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "out", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "in", PortDirection.Input))
        repository.updateNodeText(
            source.id,
            source.text.copy(
                declaration = "source code",
                specification = "specification",
                aiInstructions = "usage",
                tests = "test data",
            ),
        )
        val link = repository.createLink(root, "payload", source.id, "out", target.id, "in")
        val selection = linkedSetOf(composite.id, source.id, target.id, link.id)
        val canvas = GraphCanvas(repository, selection, {}, {}, {})

        canvas.copySelection()
        canvas.pasteSelection()

        val copiedIds = selection.toSet()
        val copiedComposite = copiedIds.mapNotNull(repository::getNode).single { it.name == "pipeline" }
        val copiedSource = copiedIds.mapNotNull(repository::getNode).single { it.name == "source" }
        val copiedTarget = copiedIds.mapNotNull(repository::getNode).single { it.name == "target" }
        val copiedLink = copiedIds.mapNotNull(repository::getNode).single { it.isLink }
        assertNotEquals(composite.id, copiedComposite.id)
        assertEquals(copiedComposite.id, copiedSource.parentId)
        assertEquals(root, copiedTarget.parentId)
        assertTrue(copiedComposite.children.contains(copiedSource.id))
        assertTrue(copiedComposite.children.none { repository.getNode(it)?.name == unselectedChild.name })
        assertEquals(copiedSource.id, copiedLink.link?.sourceNodeId)
        assertEquals(copiedTarget.id, copiedLink.link?.targetNodeId)
        assertEquals("source code", copiedSource.text.declaration)
        assertEquals("specification", copiedSource.text.specification)
        assertEquals("usage", copiedSource.text.aiInstructions)
        assertEquals("test data", copiedSource.text.tests)
    }
}
