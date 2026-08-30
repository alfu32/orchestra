package com.threadwork.compiler.documentation

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.LinkInteractionKinds
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
                declaration = "packets.push(readPacket());",
                aiInstructions = "Run with `threadwork-reader --config config.json`.",
                tests = "packet fixture",
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
        assertTrue(documentation.contains("#### Current Implementation"))
        assertTrue(documentation.contains("packets.push(readPacket());"))
        assertTrue(documentation.contains("implemented in `javascript` for `nodejs`"))
        assertTrue(documentation.contains("`packets` receives information packets of type `Packet` from `read packets`"))
        assertTrue(documentation.contains("`Packet` has the following fields: `id`: `string`."))
        assertTrue(documentation.contains("Run with `threadwork-reader"))
        assertTrue(documentation.contains("## Type and Service Annexes"))
        assertTrue(documentation.contains("### Shared Types"))
        assertTrue(documentation.contains("- `Packet`"))
        val writerSection = documentation.substringAfter("### write packets").substringBefore("## Type and Service Annexes")
        assertTrue(writerSection.contains("language and technology are inherited"))
        val components = project.files.single { it.path.endsWith(".COMPONENTS.md") }.content
        assertTrue(components.startsWith("# Packet Platform Annexes ; Components Breakdown"))
        val writerFiche = components.substringAfter("## write packets")
        assertTrue(writerFiche.contains("**Language:** Inferred (`javascript`)"))
        assertTrue(writerFiche.contains("**Technology:** Inferred (`nodejs`)"))
        assertTrue(components.contains("## Contents"))
        assertTrue(components.contains("### Test Data"))
        assertTrue(components.contains("#### Incoming Link Contracts"))
        assertTrue(components.contains("#### Outgoing Link Contracts"))
        assertTrue(components.contains("#### Used Types"))
        assertTrue(components.contains("**Shared type:** `Packet`"))
        assertTrue(components.contains("#### Usage Instructions"))
        assertTrue(components.contains("Run with `threadwork-reader --config config.json`."))
        assertTrue(components.contains("<!-- threadwork:page-break -->"))
        assertFalse(writerFiche.contains("#### Outputs"))
        assertFalse(writerFiche.contains("#### Outgoing Link Contracts"))
        assertFalse(writerFiche.contains("#### Specification"))
        assertFalse(writerFiche.contains("#### Usage Instructions"))
        assertFalse(writerFiche.contains("#### Test Data"))
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
        assertTrue(documentation.contains("#### Composition"))
        assertTrue(documentation.contains("flowchart LR"))
        assertTrue(documentation.contains("-->|validated order|"))
        val childSection = documentation.substringAfter("### validate order")
        assertTrue(childSection.indexOf("#### Specification") < childSection.indexOf("#### Implementation Profile"))
    }

    @Test
    fun `specification and component annexes are generated by separate compilers`() {
        val repository = InMemoryDocumentRepository(newDocument("Split Documentation"))

        val specification = SpecificationDocumentationCompiler().compile(repository.getDocument())
        val components = ComponentDocumentationCompiler().compile(repository.getDocument())

        assertEquals(listOf("Split Documentation.SPEC.md"), assertNotNull(specification.generatedProject).files.map { it.path })
        assertEquals(listOf("Split Documentation.COMPONENTS.md"), assertNotNull(components.generatedProject).files.map { it.path })
    }

    @Test
    fun `narrative specification describes library dependency by provider and local argument`() {
        val repository = InMemoryDocumentRepository(newDocument("Dependency Narrative"))
        val root = repository.getDocument().rootNodeId
        val library = repository.createNode(root, "lib_catalog", NodeKind.Processor)
        val worker = repository.createNode(root, "enrich_product", NodeKind.Processor)
        val dependency = repository.createLink(root, "catalog_service", library.id, "service", worker.id, "catalog")
        repository.updateLinkData(
            dependency.id,
            requireNotNull(dependency.link).copy(interactionKind = LinkInteractionKinds.Library),
        )

        val result = SpecificationDocumentationCompiler().compile(repository.getDocument())
        val content = assertNotNull(result.generatedProject).files.single().content

        assertTrue(content.contains("is provided access to `catalog_service:lib_catalog`"))
        assertTrue(content.contains("`catalog_service` provides the service or library `lib_catalog`"))
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

    @Test
    fun `component fiche omits headings for absent content`() {
        val repository = InMemoryDocumentRepository(newDocument("Sparse Fiche"))
        val root = repository.getDocument().rootNodeId
        val idle = repository.createNode(root, "idle", NodeKind.Processor)

        val fiche = ComponentDocumentationCompiler().componentFiches(repository.getDocument(), listOf(idle.id))
        val idleSection = fiche.substringAfter("## idle")

        assertFalse(idleSection.contains("#### Specification"))
        assertFalse(idleSection.contains("#### Inputs"))
        assertFalse(idleSection.contains("#### Outputs"))
        assertFalse(idleSection.contains("#### Used Types"))
        assertFalse(idleSection.contains("#### Used Libraries and Capabilities"))
        assertFalse(idleSection.contains("#### Usage Instructions"))
        assertFalse(idleSection.contains("#### Test Data"))
    }
}
