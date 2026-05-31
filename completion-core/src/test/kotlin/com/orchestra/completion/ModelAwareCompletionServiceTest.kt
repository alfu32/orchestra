package com.orchestra.completion

import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.PortDirection
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertTrue

class ModelAwareCompletionServiceTest {
    @Test
    fun `suggests ports and relatives`() {
        val repository = InMemoryDocumentRepository(newDocument("completion"))
        val root = repository.getDocument().rootNodeId
        val node = repository.createNode(root, "worker", NodeKind.Processor)
        val sibling = repository.createNode(root, "sibling", NodeKind.Processor)
        repository.addPort(node.id, NodePort("input", "payload", PortDirection.Input))

        val service = ModelAwareCompletionService(repository::getDocument)
        val labels = service.getSuggestions(
            CompletionRequest(
                nodeId = node.id,
                textSection = NodeTextSection.Source,
                languageId = "kotlin",
                technologyId = "kotlin-jvm",
                cursorOffset = 0,
                fullText = "",
                currentLine = "",
                prefix = "",
            ),
        ).map { it.label }.toSet()

        assertTrue("payload" in labels)
        assertTrue("sibling" in labels)
        assertTrue(repository.requireNode(sibling.id).name in labels)
    }
}
