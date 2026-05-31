package com.orchestra.storage

import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.PortDirection
import com.orchestra.core.validation.DocumentValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryDocumentRepositoryTest {
    @Test
    fun `create link synchronizes endpoints`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "data", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "data", PortDirection.Input))

        val link = repository.createLink(root, "source to target", source.id, "data", target.id, "data")

        assertEquals(listOf(link.id), repository.requireNode(source.id).outgoingLinks)
        assertEquals(listOf(link.id), repository.requireNode(target.id).incomingLinks)
        assertTrue(DocumentValidator.validate(repository.getDocument()).isEmpty())
        assertTrue(repository.isDirty())
    }

    @Test
    fun `move node updates old and new parent children`() {
        val repository = InMemoryDocumentRepository(newDocument("test"))
        val root = repository.getDocument().rootNodeId
        val parentA = repository.createNode(root, "a", NodeKind.Group)
        val parentB = repository.createNode(root, "b", NodeKind.Group)
        val child = repository.createNode(parentA.id, "child", NodeKind.Processor)

        repository.moveNode(child.id, parentB.id)

        assertFalse(child.id in repository.requireNode(parentA.id).children)
        assertTrue(child.id in repository.requireNode(parentB.id).children)
        assertEquals(parentB.id, repository.requireNode(child.id).parentId)
    }
}
