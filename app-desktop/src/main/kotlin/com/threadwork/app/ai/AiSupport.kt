package com.threadwork.app.ai

import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveTextLanguageId
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
) {
    GenerateSource(
        "Generate Source Code",
    ),
    TidySpecification(
        "Tidy and Format Specification",
    ),
    GenerateTestData(
        "Generate Test Data",
    ),

    ;

    fun instruction(document: ThreadworkDocument, nodeIds: Collection<NodeId>): String = when (this) {
        GenerateSource ->
            "Generate the source declaration for the selected component. Honor its stated inputs, outputs, used services, type definitions, specification, and effective language and technology. Return only the source text."

        TidySpecification ->
            "Rewrite the selected component specification as clear, precise Markdown. Preserve the intended behavior, inputs, outputs, constraints, and acceptance criteria. Return only the revised specification."

        GenerateTestData -> {
            val formats = nodeIds.mapNotNull { nodeId ->
                document.nodes[nodeId]?.let { document.effectiveTextLanguageId(it.id, NodeTextSection.Tests) }
            }.distinct()
            val format = formats.singleOrNull() ?: formats.joinToString(", ").ifBlank { "the node's test format" }
            "Generate representative test data for the selected component from its specification and typed inputs and outputs in the test language $format. Return only the test data/script."
        }
    }
}

data class AiPromptRequest(
    val task: AiSupportTask,
    val componentMarkdown: String,
    val document: ThreadworkDocument,
    val nodeIds: Collection<NodeId>,
) {
    fun prompt(): String = buildString {
        appendLine("You are assisting with a Threadwork component.")
        appendLine()
        appendLine("Task: ${task.instruction(document, nodeIds)}")
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

    fun optionsPanel(): AiProviderOptionsPanel
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

    override fun optionsPanel(): AiProviderOptionsPanel = AiProviderOptionsPanel(
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(
                JLabel("Builds the selected component dossier and requested instruction, then copies the complete prompt to the system clipboard."),
                BorderLayout.NORTH,
            )
        },
    ) {}
}

abstract class ApiKeyAiSupportProvider(
    final override val id: String,
    final override val label: String,
    private val defaultEndpoint: String,
    private val defaultModel: String,
) : AiSupportProvider {
    override fun submit(request: AiPromptRequest): AiPromptResponse = AiPromptResponse.Unavailable(
        "$label inference is not implemented. Configure it under Options > Inference Providers, or select Prompt to Clipboard.",
    )

    /** Reserved for each provider's browser/OAuth token procurement workflow. */
    open fun initiateAuthorization() = Unit

    override fun optionsPanel(): AiProviderOptionsPanel = configuredProviderPanel(
        provider = this,
        defaultEndpoint = defaultEndpoint,
        defaultModel = defaultModel,
        tokenRequired = true,
    )
}

object ClaudeAiProvider : ApiKeyAiSupportProvider(
    id = "claude",
    label = "Claude",
    defaultEndpoint = "https://api.anthropic.com",
    defaultModel = "claude-sonnet",
)

object CodexAiProvider : ApiKeyAiSupportProvider(
    id = "codex",
    label = "Codex",
    defaultEndpoint = "https://api.openai.com/v1",
    defaultModel = "gpt-5-codex",
)

object ChatGptAiProvider : ApiKeyAiSupportProvider(
    id = "chatgpt",
    label = "ChatGPT",
    defaultEndpoint = "https://api.openai.com/v1",
    defaultModel = "gpt-5",
)

object OllamaAiProvider : AiSupportProvider {
    override val id: String = "ollama"
    override val label: String = "Local Ollama"

    override fun submit(request: AiPromptRequest): AiPromptResponse = AiPromptResponse.Unavailable(
        "Local Ollama inference is not implemented. Configure it under Options > Inference Providers, or select Prompt to Clipboard.",
    )

    override fun optionsPanel(): AiProviderOptionsPanel = configuredProviderPanel(
        provider = this,
        defaultEndpoint = "http://localhost:11434",
        defaultModel = "llama3.2",
        tokenRequired = false,
    )
}

object AiSupportProviders {
    private val providers = listOf<AiSupportProvider>(
        ClaudeAiProvider,
        CodexAiProvider,
        ChatGptAiProvider,
        OllamaAiProvider,
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
        val providerPanels = providers.associateWith { it.optionsPanel() }
        providers.forEach { provider -> tabs.addTab(provider.label, providerPanels.getValue(provider).component) }

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
            providerPanels.values.forEach { it.commit() }
        }
    }
}

private fun configuredProviderPanel(
    provider: AiSupportProvider,
    defaultEndpoint: String,
    defaultModel: String,
    tokenRequired: Boolean,
): AiProviderOptionsPanel {
    val endpoint = JTextField(AiSupportSettings.endpoint(provider.id, defaultEndpoint))
    val token = JPasswordField(AiSupportSettings.token(provider.id))
    val model = JTextField(AiSupportSettings.model(provider.id, defaultModel))
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
        AiSupportSettings.setEndpoint(provider.id, endpoint.text)
        AiSupportSettings.setModel(provider.id, model.text)
        if (tokenRequired) AiSupportSettings.setToken(provider.id, token.password.concatToString())
    }
}

data class AiProviderOptionsPanel(
    val component: JComponent,
    val commit: () -> Unit,
)
