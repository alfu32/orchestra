package com.threadwork.app.ai

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class AiSupportTest {
    @Test
    fun `prompt to clipboard includes the task and complete component dossier`() {
        val response = PromptToClipboardProvider.submit(
            AiPromptRequest(
                task = AiSupportTask.GenerateSource,
                componentMarkdown = "# Component\n\n## Inputs\n\n- request: Request",
            ),
        )

        val prompt = assertIs<AiPromptResponse.Text>(response).value
        assertContains(prompt, "Generate the source declaration")
        assertContains(prompt, "# Component")
        assertContains(prompt, "request: Request")
    }
}
