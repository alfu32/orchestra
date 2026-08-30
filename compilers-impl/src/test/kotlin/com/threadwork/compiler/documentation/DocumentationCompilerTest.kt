package com.threadwork.compiler.documentation

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.NodeText
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentationCompilerTest {
    @Test
    fun `compiles project and component documentation independently of technology and layout`() {
        val repository = InMemoryDocumentRepository(newDocument("Packet Platform"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(languageId = "javascript", technologyId = "nodejs", compilerId = "nodejs"),
        )
        val reader = repository.createNode(root, "read packets", NodeKind.Processor)
        val writer = repository.createNode(root, "write packets", NodeKind.Processor)
        repository.updateNodeText(
            reader.id,
            NodeText(
                specification = "Reads queued packets from the configured source.",
                aiInstructions = "Run with `threadwork-reader --config config.json`.",
            ),
        )
        repository.updateNodeTechnology(
            reader.id,
            TechnologyMetadata(languageId = "javascript", technologyId = "nodejs", compilerId = "nodejs"),
        )
        repository.addPort(reader.id, NodePort("out", "packets", PortDirection.Output))
        repository.addPort(writer.id, NodePort("in", "packets", PortDirection.Input))
        val packetType = repository.createNode(root, "Packet", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            packetType.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "string"))),
        )
        val link = repository.createLink(root, "packets", reader.id, "packets", writer.id, "packets")
        repository.updateLinkData(
            link.id,
            requireNotNull(link.link).copy(typeDefinitionId = packetType.id.value),
        )
        link.text.specification = "Carries one validated packet per message."

        val result = DocumentationCompiler().compile(
            repository.getDocument(),
            CompilerOptions(projectName = "Packet Platform"),
        )

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertEquals(
            setOf("Packet Platform.SPEC.md", "Packet Platform.COMPONENTS.md"),
            project.files.mapTo(linkedSetOf()) { it.path },
        )
        assertTrue(project.files.all { it.elementKind == GeneratedElementKind.Documentation })
        val documentation = project.files.single { it.path.endsWith(".SPEC.md") }.content
        assertTrue(documentation.contains("Reads queued packets"))
        assertTrue(documentation.contains("| packets | Packet |"))
        assertTrue(documentation.contains("**Direct technology:** `nodejs`"))
        assertTrue(documentation.contains("Run with `threadwork-reader"))
        assertTrue(documentation.contains("## Annexes"))
        assertTrue(documentation.contains("### Shared Types"))
        assertTrue(documentation.contains("- `Packet`"))
        val writerSection = documentation.substringAfter("### write packets").substringBefore("## Annexes")
        assertTrue(writerSection.contains("**Direct technology:** _Not specified directly._"))
        val components = project.files.single { it.path.endsWith(".COMPONENTS.md") }.content
        assertTrue(components.startsWith("# Packet Platform Annexes ; Components Breakdown"))
        assertTrue(components.contains("## Contents"))
        assertTrue(components.contains("### Test Data"))
        assertTrue(components.contains("#### Incoming Link Contracts"))
        assertTrue(components.contains("#### Outgoing Link Contracts"))
        assertTrue(components.contains("#### Used Types"))
        assertTrue(components.contains("**Shared type:** `Packet`"))
        assertTrue(components.contains("#### Usage Instructions"))
        assertTrue(components.contains("Run with `threadwork-reader --config config.json`."))
        assertTrue(components.contains("<!-- threadwork:page-break -->"))
    }

    @Test
    fun `selected composite names and limits the generated documentation`() {
        val repository = InMemoryDocumentRepository(newDocument("Whole Project"))
        val root = repository.getDocument().rootNodeId
        val selected = repository.createNode(root, "Order Processing", NodeKind.Group)
        val child = repository.createNode(selected.id, "validate order", NodeKind.Processor)
        val publish = repository.createNode(selected.id, "publish order", NodeKind.Processor)
        val excluded = repository.createNode(root, "billing", NodeKind.Processor)
        repository.updateNodeText(child.id, child.text.copy(specification = "Validates the order."))
        repository.createLink(selected.id, "validated order", child.id, "out", publish.id, "in")
        repository.updateNodeText(excluded.id, excluded.text.copy(specification = "Charges the customer."))

        val result = DocumentationCompiler().compile(
            repository.getDocument(),
            CompilerOptions(scopeNodeIds = setOf(selected.id)),
        )

        val project = assertNotNull(result.generatedProject)
        assertEquals(
            setOf("Order Processing.SPEC.md", "Order Processing.COMPONENTS.md"),
            project.files.mapTo(linkedSetOf()) { it.path },
        )
        val documentation = project.files.single { it.path.endsWith(".SPEC.md") }.content
        assertTrue(documentation.contains("Validates the order."))
        assertFalse(documentation.contains("Charges the customer."))
        assertTrue(documentation.contains("#### Direct Children"))
        assertTrue(documentation.contains("#### Flow Diagram (Mermaid)"))
        assertTrue(documentation.contains("flowchart LR"))
        assertTrue(documentation.contains("-->|validated order|"))
        assertTrue(documentation.indexOf("#### Direct Children") < documentation.indexOf("#### Specification"))
    }

    @Test
    fun `copies complete fiches for explicitly selected processing nodes only`() {
        val repository = InMemoryDocumentRepository(newDocument("Fiche Project"))
        val root = repository.getDocument().rootNodeId
        val selected = repository.createNode(root, "selected processor", NodeKind.Processor)
        val excluded = repository.createNode(root, "excluded processor", NodeKind.Processor)
        val type = repository.createNode(root, "Packet", NodeKind.Type)
        repository.updateNodeText(
            selected.id,
            selected.text.copy(
                specification = "Processes incoming packets.",
                aiInstructions = "Invoke after validation.",
                tests = "{\"packet\": true}",
            ),
        )
        val link = repository.createLink(root, "packets", selected.id, "out", excluded.id, "in")

        val fiches = DocumentationCompiler().componentFiches(
            repository.getDocument(),
            listOf(selected.id, type.id, link.id),
        )

        assertTrue(fiches.contains("# Selected Component Fiches"))
        assertTrue(fiches.contains("## selected processor"))
        assertTrue(fiches.contains("Processes incoming packets."))
        assertTrue(fiches.contains("Invoke after validation."))
        assertTrue(fiches.contains("{\"packet\": true}"))
        assertFalse(fiches.contains("## excluded processor"))
    }
}
