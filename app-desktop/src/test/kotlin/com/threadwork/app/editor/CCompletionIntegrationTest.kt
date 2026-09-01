package com.threadwork.app.editor

import com.threadwork.compiler.c.CCompiler
import com.threadwork.completion.CompletionRequest
import com.threadwork.completion.ModelAwareCompletionService
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CCompletionIntegrationTest {
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
