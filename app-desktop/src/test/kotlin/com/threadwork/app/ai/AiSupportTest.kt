package com.threadwork.app.ai

import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.TechnologyMetadata
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
        assertContains(prompt, "complete source implementation")
        assertContains(prompt, "compiler owns the enclosing function")
        assertContains(prompt, "# Component")
        assertContains(prompt, "request: Request")
    }

    @Test
    fun `C source prompt requests an implementation body and compiler buffer API`() {
        val node = Node(NodeId("node"), "process_packets", NodeKind.Processor).also {
            it.technology = TechnologyMetadata(languageId = "c", technologyId = "c-native")
            it.text.declaration = "push(outgoing_packets, &packet);"
        }
        val document = ThreadworkDocument(
            id = "document",
            name = "Document",
            rootNodeId = node.id,
            nodes = mutableMapOf(node.id to node),
        )

        val prompt = assertIs<AiPromptResponse.Text>(
            PromptToClipboardProvider.submit(
                AiPromptRequest(AiSupportTask.GenerateSource, "# process_packets", document, listOf(node.id)),
            ),
        ).value

        assertContains(prompt, "do not emit a function signature, prototype, forward declaration")
        assertContains(prompt, "pop(input_buffer, &item)")
        assertContains(prompt, "push(output_buffer, &item)")
        assertContains(prompt, "do not rename it or change snake_case to camelCase")
        assertContains(prompt, "Current implementation:")
        assertContains(prompt, "push(outgoing_packets, &packet);")
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

        assertContains(prompt, "test data or a test script for the selected component in csv")
        assertContains(prompt, "normal, boundary, malformed, and dependency-failure cases")
    }
}
