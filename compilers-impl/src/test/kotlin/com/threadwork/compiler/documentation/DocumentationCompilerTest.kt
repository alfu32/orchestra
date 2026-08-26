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
    fun `compiles functional and technical markdown independently of technology and layout`() {
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
            setOf("Packet Platform.SPEC.md", "Packet Platform.TECH.md", "Packet Platform.COMPONENTS.md"),
            project.files.mapTo(linkedSetOf()) { it.path },
        )
        assertTrue(project.files.all { it.elementKind == GeneratedElementKind.Documentation })
        val functional = project.files.single { it.path.endsWith(".SPEC.md") }.content
        assertTrue(functional.contains("Reads queued packets"))
        assertTrue(functional.contains("packets:Packet"))
        assertTrue(functional.contains("read packets.packets"))
        val technical = project.files.single { it.path.endsWith(".TECH.md") }.content
        assertTrue(technical.contains("**Direct technology:** `nodejs`"))
        assertTrue(technical.contains("Run with `threadwork-reader"))
        assertTrue(technical.contains("## Shared Types"))
        assertTrue(technical.contains("| id | string | No |"))
        assertTrue(technical.contains("payload uses the shared `Packet` type contract"))
        val writerSection = technical.substringAfter("### write packets").substringBefore("## Data Contracts")
        assertTrue(writerSection.contains("**Direct technology:** _Not specified directly._"))
        val components = project.files.single { it.path.endsWith(".COMPONENTS.md") }.content
        assertTrue(components.contains("## Contents"))
        assertTrue(components.contains("### Test Data"))
        assertTrue(components.contains("<!-- threadwork:page-break -->"))
    }

    @Test
    fun `selected composite names and limits the generated documentation`() {
        val repository = InMemoryDocumentRepository(newDocument("Whole Project"))
        val root = repository.getDocument().rootNodeId
        val selected = repository.createNode(root, "Order Processing", NodeKind.Group)
        val child = repository.createNode(selected.id, "validate order", NodeKind.Processor)
        val excluded = repository.createNode(root, "billing", NodeKind.Processor)
        repository.updateNodeText(child.id, child.text.copy(specification = "Validates the order."))
        repository.updateNodeText(excluded.id, excluded.text.copy(specification = "Charges the customer."))

        val result = DocumentationCompiler().compile(
            repository.getDocument(),
            CompilerOptions(scopeNodeIds = setOf(selected.id)),
        )

        val project = assertNotNull(result.generatedProject)
        assertEquals(
            setOf("Order Processing.SPEC.md", "Order Processing.TECH.md", "Order Processing.COMPONENTS.md"),
            project.files.mapTo(linkedSetOf()) { it.path },
        )
        val functional = project.files.single { it.path.endsWith(".SPEC.md") }.content
        assertTrue(functional.contains("Validates the order."))
        assertFalse(functional.contains("Charges the customer."))
    }
}
