package com.threadwork.completion

import com.threadwork.compiler.api.CompilationResult
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelAwareCompletionServiceTest {
    @Test
    fun `suggests compiler local buffer service and type intelligence first`() {
        val repository = InMemoryDocumentRepository(newDocument("intelligence"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        val type = repository.createNode(root, "Packet", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "number"))),
        )
        val source = repository.createNode(root, "reader", NodeKind.Processor)
        val worker = repository.createNode(root, "worker", NodeKind.Processor)
        val library = repository.createNode(root, "lib_config", NodeKind.Processor)
        val input = repository.createLink(root, "incoming_packet", source.id, "out", worker.id, "in")
        repository.updateLinkData(input.id, requireNotNull(input.link).copy(typeDefinitionId = type.id.value))
        val output = repository.createLink(root, "outgoing_packet", worker.id, "out", source.id, "in")
        repository.updateLinkData(output.id, requireNotNull(output.link).copy(typeDefinitionId = type.id.value))
        val serviceLink = repository.createLink(root, "config_service", library.id, "service", worker.id, "config")
        repository.updateLinkData(serviceLink.id, requireNotNull(serviceLink.link).copy(transportKind = "dependency"))

        val suggestions = ModelAwareCompletionService(
            documentProvider = repository::getDocument,
            compilerProvider = { _, _ -> CodeIntelligenceCompiler },
        ).getSuggestions(requestFor(worker.id))

        assertEquals(
            listOf("configService", "incomingPacket", "outgoingPacket"),
            suggestions.take(3).map { it.label },
        )
        assertTrue(suggestions.any { it.label == "incomingPacket.push(item)" })
        assertTrue(suggestions.any { it.label == "incomingPacket.id" })
        assertTrue(suggestions.any { it.label == "Packet.id" })
        assertEquals(
            CompletionSuggestionKind.ServiceInstance,
            suggestions.single { it.label == "configService" }.kind,
        )
    }

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
                textSection = NodeTextSection.Declaration,
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
                textSection = NodeTextSection.Declaration,
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
                textSection = NodeTextSection.Declaration,
                languageId = "kotlin",
                technologyId = "generic",
                cursorOffset = 0,
                fullText = "",
                currentLine = "",
                prefix = "",
            ),
        ).map { it.label }.toSet()

        assertTrue("node" in labels)
        assertTrue("node: ${'$'}{node.name}" in labels)
        assertTrue("options.projectName:${'$'}{options.projectName}" in labels)
        assertTrue("options.projectName" in labels)
        assertTrue("node.name" in labels)
        assertTrue("node.kind.name" in labels)
        assertTrue("node.kind.ordinal" in labels)
        assertTrue("node.children.size" in labels)
        assertTrue("node.incomingLinks" in labels)
        assertTrue("node.layout" in labels)
        assertTrue("node.link.sourceNodeId" in labels)
        assertTrue("node.metadata.size" in labels)
        assertTrue("node.pluginData" in labels)
        assertTrue("node.ports" in labels)
        assertTrue("node.text.instantiationLanguageId" in labels)
        assertTrue("node.text.instantiation" in labels)
        assertTrue("node.text.declarationLanguageId" in labels)
        assertTrue("node.text.specificationLanguageId" in labels)
        assertTrue("node.text.aiInstructions" in labels)
        assertTrue("node.text.testsLanguageId" in labels)
        assertTrue("text.declaration" in labels)
        assertTrue("technology.languageId" in labels)
        assertTrue("children" in labels)
        assertTrue("payload" in labels)
        assertTrue("result" in labels)
    }

    private fun requestFor(nodeId: com.threadwork.core.model.NodeId): CompletionRequest = CompletionRequest(
        nodeId = nodeId,
        textSection = NodeTextSection.Declaration,
        languageId = "javascript",
        technologyId = "nodejs",
        cursorOffset = 0,
        fullText = "",
        currentLine = "",
        prefix = "",
    )

    private object CodeIntelligenceCompiler : CompilerPlugin {
        override val id: String = "test-code-intelligence"
        override val displayName: String = "Test code intelligence"

        override fun supports(document: ThreadworkDocument): Boolean = true

        override fun validate(document: ThreadworkDocument): List<Diagnostic> = emptyList()

        override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult =
            CompilationResult(generatedProject = null, diagnostics = emptyList(), success = true)
    }
}
