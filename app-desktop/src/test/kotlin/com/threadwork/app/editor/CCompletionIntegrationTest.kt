package com.threadwork.app.editor

import com.threadwork.compiler.c.CCompiler
import com.threadwork.completion.CompletionRequest
import com.threadwork.completion.ModelAwareCompletionService
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CCompletionIntegrationTest {
    @Test
    fun `composite completion exposes direct child C lifecycle functions`() {
        val repository = InMemoryDocumentRepository(newDocument("ticker"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(languageId = "c", technologyId = "c-native", compilerId = "c-compiler"),
        )
        repository.createNode(root, "read_time", NodeKind.Processor)
        repository.createNode(root, "write_file", NodeKind.Processor)

        val labels = ModelAwareCompletionService(
            documentProvider = repository::getDocument,
            compilerProvider = { _, _ -> CCompiler() },
        ).getSuggestions(
            CompletionRequest(
                nodeId = root,
                textSection = NodeTextSection.Declaration,
                languageId = "c",
                technologyId = "c-native",
                cursorOffset = 0,
                fullText = "",
                currentLine = "",
                prefix = "",
            ),
        ).map { it.label }

        assertTrue(labels.any { it.startsWith("tw_init_read_time_") })
        assertTrue(labels.any { it.startsWith("tw_run_read_time_") })
        assertTrue(labels.any { it.startsWith("tw_init_write_file_") })
        assertTrue(labels.any { it.startsWith("tw_run_write_file_") })
    }

    @Test
    fun `library dependency links default to dependency injection and expose functions to libraries`() {
        val repository = InMemoryDocumentRepository(newDocument("ticker"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(languageId = "c", technologyId = "c-native", compilerId = "c-compiler"),
        )
        val provider = repository.createNode(root, "lib_math", NodeKind.Processor)
        repository.updateNodeText(
            provider.id,
            repository.requireNode(provider.id).text.copy(
                declaration = "int maximum(int left, int right) { return left > right ? left : right; }",
            ),
        )
        val consumer = repository.createNode(root, "lib_metrics", NodeKind.Processor)
        val dependency = repository.createLink(root, "math_service", provider.id, "out", consumer.id, "in")
        val worker = repository.createNode(root, "calculate_metrics", NodeKind.Processor)
        val reverseDependency = repository.createLink(root, "metrics_input", worker.id, "out", consumer.id, "in")

        assertEquals(LinkInteractionKinds.Library, dependency.link?.interactionKind)
        assertEquals(LinkInteractionKinds.Library, reverseDependency.link?.interactionKind)
        assertTrue(
            CCompiler().codeIntelligence(repository.getDocument(), consumer).symbols.any {
                it.name == "math_service__maximum"
            },
        )
    }

    @Test
    fun `C completion preserves snake case link names end to end`() {
        val repository = InMemoryDocumentRepository(newDocument("ticker"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(languageId = "c", technologyId = "c-native", compilerId = "c-compiler"),
        )
        val reader = repository.createNode(root, "read_time", NodeKind.Processor)
        val worker = repository.createNode(root, "gen_uuid", NodeKind.Processor)
        val writer = repository.createNode(root, "gen_text", NodeKind.Processor)
        repository.createLink(root, "ip_time", reader.id, "out", worker.id, "in")
        repository.createLink(root, "ip_uuid", worker.id, "out", writer.id, "in")

        val service = ModelAwareCompletionService(
            documentProvider = repository::getDocument,
            compilerProvider = { _, _ -> CCompiler() },
        )
        val labels = service.getSuggestions(
            CompletionRequest(
                nodeId = worker.id,
                textSection = NodeTextSection.Declaration,
                languageId = "c",
                technologyId = "c-native",
                cursorOffset = 0,
                fullText = "",
                currentLine = "",
                prefix = "",
            ),
        ).mapTo(linkedSetOf()) { it.label }

        assertTrue("ip_time" in labels)
        assertTrue("ip_uuid" in labels)
        assertTrue("pop(ip_time, &item)" in labels)
        assertTrue("push(ip_uuid, &item)" in labels)
        assertFalse("ipTime" in labels)
        assertFalse("ipUuid" in labels)
    }
}
