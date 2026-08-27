package com.threadwork.app.ai

import java.awt.BorderLayout
import java.awt.Dimension
import java.util.prefs.Preferences
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTabbedPane
import javax.swing.JTextField

enum class AiSupportTask(
    val label: String,
    val instruction: String,
) {
    GenerateSource(
        "Generate Source Code",
        "Generate the source declaration for the selected component. Honor its stated inputs, outputs, used services, type definitions, specification, and effective language and technology. Return only the source text.",
    ),
    TidySpecification(
        "Tidy and Format Specification",
        "Rewrite the selected component specification as clear, precise Markdown. Preserve the intended behavior, inputs, outputs, constraints, and acceptance criteria. Return only the revised specification.",
    ),
    GenerateTestData(
        "Generate Test Data",
        "Generate representative test data for the selected component from its specification and typed inputs and outputs. Use the component's tests language where possible. Return only the test data.",
    ),
}

data class AiPromptRequest(
    val task: AiSupportTask,
    val componentMarkdown: String,
) {
    fun prompt(): String = buildString {
        appendLine("You are assisting with a Threadwork component.")
        appendLine()
        appendLine("Task: ${task.instruction}")
        appendLine()
        appendLine("Component dossier:")
        appendLine("```markdown")
        append(componentMarkdown.trim())
        appendLine()
        appendLine("```")
    }
}

sealed interface AiPromptResponse {
    data class Text(val value: String) : AiPromptResponse
    data class Unavailable(val message: String) : AiPromptResponse
}

interface AiSupportProvider {
    val id: String
    val label: String

    fun submit(request: AiPromptRequest): AiPromptResponse
}

object AiSupportSettings {
    private const val PREF_NODE = "com/threadwork/app/ai"
    private const val ACTIVE_PROVIDER_KEY = "activeProvider"
    private val preferences: Preferences = Preferences.userRoot().node(PREF_NODE)

    var activeProviderId: String
        get() = preferences.get(ACTIVE_PROVIDER_KEY, PromptToClipboardProvider.providerId)
        set(value) = preferences.put(ACTIVE_PROVIDER_KEY, value)

    fun endpoint(providerId: String, default: String): String =
        preferences.get("$providerId.endpoint", default)

    fun setEndpoint(providerId: String, value: String) {
        preferences.put("$providerId.endpoint", value.trim())
    }

    fun token(providerId: String): String = preferences.get("$providerId.token", "")

    fun setToken(providerId: String, value: String) {
        preferences.put("$providerId.token", value)
    }

    fun model(providerId: String, default: String): String =
        preferences.get("$providerId.model", default)

    fun setModel(providerId: String, value: String) {
        preferences.put("$providerId.model", value.trim())
    }
}

object PromptToClipboardProvider : AiSupportProvider {
    const val providerId = "prompt-to-clipboard"

    override val id: String = providerId

    override val label: String = "Prompt to Clipboard"

    override fun submit(request: AiPromptRequest): AiPromptResponse = AiPromptResponse.Text(request.prompt())
}

private data class StubProvider(
    override val id: String,
    override val label: String,
) : AiSupportProvider {
    override fun submit(request: AiPromptRequest): AiPromptResponse = AiPromptResponse.Unavailable(
        "$label inference is not implemented. Configure it under Options > Inference Providers, or select Prompt to Clipboard.",
    )
}

object AiSupportProviders {
    private val providers = listOf<AiSupportProvider>(
        StubProvider("claude", "Claude"),
        StubProvider("codex", "Codex"),
        StubProvider("chatgpt", "ChatGPT"),
        StubProvider("ollama", "Local Ollama"),
        PromptToClipboardProvider,
    )

    fun current(): AiSupportProvider = providers.firstOrNull { it.id == AiSupportSettings.activeProviderId }
        ?: PromptToClipboardProvider

    fun optionsPanel(): AiProviderOptionsPanel {
        val activeProvider = JComboBox(providers.map(AiSupportProvider::label).toTypedArray()).apply {
            selectedItem = current().label
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        val tabs = JTabbedPane()
        val commits = mutableListOf<() -> Unit>()

        tabs.addTab("Claude", providerPanel("claude", "https://api.anthropic.com", "claude-sonnet").also { commits += it.commit }.component)
        tabs.addTab("Codex", providerPanel("codex", "https://api.openai.com/v1", "gpt-5-codex").also { commits += it.commit }.component)
        tabs.addTab("ChatGPT", providerPanel("chatgpt", "https://api.openai.com/v1", "gpt-5").also { commits += it.commit }.component)
        tabs.addTab("Local Ollama", providerPanel("ollama", "http://localhost:11434", "llama3.2", tokenRequired = false).also { commits += it.commit }.component)
        tabs.addTab("Prompt to Clipboard", JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(JLabel("Builds the selected component dossier and requested instruction, then copies the complete prompt to the system clipboard."), BorderLayout.NORTH)
        })

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
            add(JLabel("Active provider"))
            add(activeProvider)
            add(tabs.apply { border = BorderFactory.createEmptyBorder(12, 0, 0, 0) })
        }
        return AiProviderOptionsPanel(content) {
            AiSupportSettings.activeProviderId = providers.firstOrNull { it.label == activeProvider.selectedItem }?.id
                ?: PromptToClipboardProvider.providerId
            commits.forEach { it() }
        }
    }

    private fun providerPanel(
        id: String,
        defaultEndpoint: String,
        defaultModel: String,
        tokenRequired: Boolean = true,
    ): AiProviderOptionsPanel {
        val endpoint = JTextField(AiSupportSettings.endpoint(id, defaultEndpoint))
        val token = JPasswordField(AiSupportSettings.token(id))
        val model = JTextField(AiSupportSettings.model(id, defaultModel))
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(JLabel("Endpoint URL"))
            add(endpoint)
            add(JLabel("Model").apply { border = BorderFactory.createEmptyBorder(10, 0, 0, 0) })
            add(model)
            if (tokenRequired) {
                add(JLabel("API token").apply { border = BorderFactory.createEmptyBorder(10, 0, 0, 0) })
                add(token)
                add(JButton("Authorize in browser (not implemented)").apply {
                    isEnabled = false
                    toolTipText = "Provider authorization will be implemented with the inference client."
                    border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
                })
            }
        }
        return AiProviderOptionsPanel(content) {
            AiSupportSettings.setEndpoint(id, endpoint.text)
            AiSupportSettings.setModel(id, model.text)
            if (tokenRequired) AiSupportSettings.setToken(id, token.password.concatToString())
        }
    }
}

data class AiProviderOptionsPanel(
    val component: JComponent,
    val commit: () -> Unit,
)
