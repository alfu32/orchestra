package com.threadwork.app.ai

import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTextLanguageId
import com.threadwork.core.model.effectiveTechnologyId
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
        GenerateSource -> sourceInstruction(document, nodeIds)

        TidySpecification ->
            "Rewrite only the user-authored specification for the selected component as clear, precise Markdown. " +
                "Preserve all stated behavior, constraints, edge cases, inputs, outputs, dependencies, and acceptance criteria. " +
                "Do not invent implementation details, change the source implementation, or reproduce the surrounding dossier. " +
                "Return only the revised specification Markdown."

        GenerateTestData -> {
            val formats = nodeIds.mapNotNull { nodeId ->
                document.nodes[nodeId]?.let { document.effectiveTextLanguageId(it.id, NodeTextSection.Tests) }
            }.distinct()
            val format = formats.singleOrNull() ?: formats.joinToString(", ").ifBlank { "the node's test format" }
            "Generate representative test data or a test script for the selected component in $format. " +
                "Derive cases from the specification, exact input/output types, field constraints, dependencies, and failure behavior. " +
                "Include normal, boundary, malformed, and dependency-failure cases where applicable. " +
                "Use the exact compiler-provided buffer and service names from the dossier when the format is executable. " +
                "Return only the test data or test script."
        }
    }

    private fun sourceInstruction(document: ThreadworkDocument, nodeIds: Collection<NodeId>): String {
        val nodes = nodeIds.mapNotNull(document.nodes::get).filterNot(Node::isLink)
        val targets = nodes.joinToString("; ") { node ->
            "${node.name} [language=${document.effectiveLanguageId(node.id).ifBlank { "unspecified" }}, " +
                "technology=${document.effectiveTechnologyId(node.id).ifBlank { "unspecified" }}, " +
                "role=${node.stereotype(document).name}]"
        }.ifBlank { "the selected component and its effective compiler context" }
        val hasCProcessor = nodes.any {
            document.effectiveLanguageId(it.id).equals("c", ignoreCase = true) &&
                it.stereotype(document) != NodeStereotype.ServiceLibrary
        }
        val hasCLibrary = nodes.any {
            document.effectiveLanguageId(it.id).equals("c", ignoreCase = true) &&
                it.stereotype(document) == NodeStereotype.ServiceLibrary
        }
        return buildString {
            append(
                "Generate the complete source implementation for the selected Threadwork editor target: $targets. " +
                    "Honor the user specification, current implementation, exact typed inputs and outputs, service dependencies, " +
                    "usage instructions, and effective language and technology. Use every compiler-provided argument name exactly " +
                    "as written; do not rename it or change snake_case to camelCase. ",
            )
            append(
                "For an ordinary processing node, return only the statements that belong in its editable recurring implementation body. " +
                    "The Threadwork compiler owns the enclosing function, function name, parameters, forward declarations, initialization, " +
                    "transport, and entry point. Do not emit any of those generated structures. ",
            )
            if (hasCProcessor) {
                append(
                    "For a C processing node specifically: do not emit a function signature, prototype, forward declaration, main function, " +
                        "or #include directive. Read an input item with pop(input_buffer, &item), publish an output item with " +
                        "push(output_buffer, &item), and use threadwork_buffer_count(buffer) when a count is needed. The generated wrapper " +
                        "already returns THREADWORK_OK; return THREADWORK_ERROR only on an actual failure. ",
                )
            }
            if (hasCLibrary) {
                append(
                    "For a C service-library node, return a complete translation-unit-level library declaration instead of a processor body; " +
                        "system #include directives, typedefs, declarations, and function implementations are allowed there. ",
                )
            }
            append(
                "Do not return commentary, Markdown fences, a header, or a forward declaration. Return only source suitable for the selected editor field.",
            )
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
        appendEditorState()
        appendLine("Component dossier:")
        appendPromptCodeBlock(componentMarkdown.trim(), "markdown")
    }

    private fun StringBuilder.appendEditorState() {
        val nodes = nodeIds.distinct().mapNotNull(document.nodes::get).filterNot(Node::isLink)
        if (nodes.isEmpty()) return
        appendLine("Selected editor state:")
        appendLine()
        nodes.forEach { node ->
            val language = document.effectiveLanguageId(node.id).ifBlank { "unspecified" }
            val technology = document.effectiveTechnologyId(node.id).ifBlank { "unspecified" }
            appendLine("- `${node.name.ifBlank { node.id.value }}`: language `$language`, technology `$technology`, role `${node.stereotype(document).name}`")
            if (node.text.declaration.isBlank()) {
                appendLine("  Current implementation: empty.")
            } else {
                appendLine("  Current implementation:")
                appendPromptCodeBlock(node.text.declaration.trim(), language, indentation = "  ")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendPromptCodeBlock(content: String, language: String, indentation: String = "") {
        val longestRun = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        appendLine("$indentation$fence$language")
        content.lineSequence().forEach { appendLine("$indentation$it") }
        appendLine("$indentation$fence")
        appendLine()
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
