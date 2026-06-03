package com.orchestra.completion

import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.NodeTextSection
import com.orchestra.core.model.PortDirection
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `prioritizes incoming outgoing and dependency link names`() {
        val repository = InMemoryDocumentRepository(newDocument("completion"))
        val root = repository.getDocument().rootNodeId
        val library = repository.createNode(root, "lib_logging", NodeKind.Processor)
        val source = repository.createNode(root, "read_config", NodeKind.Processor)
        val worker = repository.createNode(root, "process_config", NodeKind.Processor)
        val target = repository.createNode(root, "write_config", NodeKind.Processor)
        val sibling = repository.createNode(root, "side_task", NodeKind.Processor)
        repository.addPort(worker.id, NodePort("input", "payload", PortDirection.Input))
        val usage = repository.createLink(root, "lib_logging", library.id, "out", worker.id, "in")
        usage.link?.transportKind = "usage"
        repository.createLink(root, "config_in", source.id, "out", worker.id, "in")
        repository.createLink(root, "config_out", worker.id, "out", target.id, "in")

        val service = ModelAwareCompletionService(repository::getDocument)
        val suggestions = service.getSuggestions(
            CompletionRequest(
                nodeId = worker.id,
                textSection = NodeTextSection.Source,
                languageId = "kotlin",
                technologyId = "kotlin-jvm",
                cursorOffset = 0,
                fullText = "",
                currentLine = "",
                prefix = "",
            ),
        )

        assertEquals(
            listOf("lib_logging", "config_in", "config_out"),
            suggestions.take(3).map { it.label },
        )
        assertTrue(suggestions.indexOfFirst { it.label == "payload" } < suggestions.indexOfFirst { it.label == sibling.name })
    }

    @Test
    fun `suggests template model fields for compiler overrides`() {
        val repository = InMemoryDocumentRepository(newDocument("completion"))
        val root = repository.getDocument().rootNodeId
        val template = repository.createNode(root, "@Transformer", NodeKind.Processor)
        repository.addPort(template.id, NodePort("input", "payload", PortDirection.Input))
        repository.addPort(template.id, NodePort("output", "result", PortDirection.Output))

        val service = ModelAwareCompletionService(repository::getDocument)
        val labels = service.getSuggestions(
            CompletionRequest(
                nodeId = template.id,
                textSection = NodeTextSection.Source,
                languageId = "kotlin",
                technologyId = "generic",
                cursorOffset = 0,
                fullText = "",
                currentLine = "",
                prefix = "",
            ),
        ).map { it.label }.toSet()

        assertTrue("node" in labels)
        assertTrue("node.name" in labels)
        assertTrue("text.source" in labels)
        assertTrue("technology.languageId" in labels)
        assertTrue("children" in labels)
        assertTrue("payload" in labels)
        assertTrue("result" in labels)
    }
}
