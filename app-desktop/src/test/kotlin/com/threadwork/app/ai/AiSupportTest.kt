package com.threadwork.app.ai

import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.ThreadworkDocument
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class AiSupportTest {
    @Test
    fun `prompt to clipboard includes the task and complete component dossier`() {
        val node = Node(NodeId("node"), "Component", NodeKind.Processor)
        val document = ThreadworkDocument(
            id = "document",
            name = "Document",
            rootNodeId = node.id,
            nodes = mutableMapOf(node.id to node),
        )
        val response = PromptToClipboardProvider.submit(
            AiPromptRequest(
                task = AiSupportTask.GenerateSource,
                componentMarkdown = "# Component\n\n## Inputs\n\n- request: Request",
                document = document,
                nodeIds = listOf(node.id),
            ),
        )

        val prompt = assertIs<AiPromptResponse.Text>(response).value
        assertContains(prompt, "Generate the source declaration")
        assertContains(prompt, "# Component")
        assertContains(prompt, "request: Request")
    }

    @Test
    fun `test data prompt uses the selected node test format`() {
        val node = Node(NodeId("node"), "Component", NodeKind.Processor).also {
            it.text.testsLanguageId = "csv"
        }
        val document = ThreadworkDocument(
            id = "document",
            name = "Document",
            rootNodeId = node.id,
            nodes = mutableMapOf(node.id to node),
        )

        val prompt = assertIs<AiPromptResponse.Text>(
            PromptToClipboardProvider.submit(
                AiPromptRequest(AiSupportTask.GenerateTestData, "# Component", document, listOf(node.id)),
            ),
        ).value

        assertContains(prompt, "test language csv")
        assertContains(prompt, "test data/script")
    }
}
