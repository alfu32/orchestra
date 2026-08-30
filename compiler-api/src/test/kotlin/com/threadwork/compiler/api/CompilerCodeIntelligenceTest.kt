package com.threadwork.compiler.api

import com.threadwork.core.model.LinkData
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilerCodeIntelligenceTest {
    @Test
    fun `exposes compiler local buffers services and linked type members`() {
        val document = connectedDocument()
        val worker = document.nodes.getValue(NodeId("worker"))

        val intelligence = defaultCodeIntelligence(document, worker)

        val input = assertNotNull(intelligence.symbols.singleOrNull { it.name == "incoming_packet" })
        assertEquals(CompilerCodeSymbolKind.InputBuffer, input.kind)
        assertEquals(NodeId("input-link"), input.originNodeId)
        assertEquals("Packet", input.typeName)
        assertTrue(input.members.any { it.name == "incoming_packet.push(item)" })
        assertTrue(input.members.any { it.name == "incoming_packet.id" && it.detail == "number" })

        val output = assertNotNull(intelligence.symbols.singleOrNull { it.name == "outgoing_packet" })
        assertEquals(CompilerCodeSymbolKind.OutputBuffer, output.kind)
        assertEquals("Packet", output.typeName)

        val service = assertNotNull(intelligence.symbols.singleOrNull { it.name == "config_service" })
        assertEquals(CompilerCodeSymbolKind.ServiceInstance, service.kind)
        assertEquals("lib_config", service.typeName)
        assertEquals(NodeId("dependency-link"), service.originNodeId)

        val type = assertNotNull(intelligence.types.singleOrNull { it.name == "Packet" })
        assertEquals("javascript", type.languageId)
        assertEquals(listOf("id", "active"), type.fields.map { it.name })
    }

    @Test
    fun `preserves local argument spelling without allocation indices`() {
        assertEquals("record_pipe", compilerArgumentName("record_pipe"))
        assertEquals("ORDER_ITEMS", compilerArgumentName("ORDER_ITEMS"))
        assertEquals("_private_pipe", compilerArgumentName("_private_pipe"))
        assertEquals("_24_hour_record", compilerArgumentName("24-hour record"))
    }

    @Test
    fun `C buffers expose compiler provided push and pop operations`() {
        val document = connectedDocument()
        document.nodes.getValue(NodeId("root")).technology =
            TechnologyMetadata(languageId = "c", technologyId = "c-native")
        val worker = document.nodes.getValue(NodeId("worker"))

        val intelligence = defaultCodeIntelligence(document, worker)

        val input = assertNotNull(intelligence.symbols.singleOrNull { it.name == "incoming_packet" })
        assertTrue(input.members.any { it.name == "pop(incoming_packet, &item)" })
        val output = assertNotNull(intelligence.symbols.singleOrNull { it.name == "outgoing_packet" })
        assertTrue(output.members.any { it.name == "push(outgoing_packet, &item)" })
        assertTrue(output.members.any { it.name == "threadwork_buffer_count(outgoing_packet)" })
    }

    @Test
    fun `exposes synchronous source capability methods only to the consumer`() {
        val root = Node(NodeId("root"), "Project", NodeKind.Processor)
        val provider = Node(NodeId("provider"), "page", NodeKind.Processor)
        val consumer = Node(NodeId("consumer"), "server", NodeKind.Processor)
        val link = Node(
            id = NodeId("capability"),
            name = "page_source",
            kind = NodeKind.Link,
            link = LinkData(
                provider.id,
                "src",
                consumer.id,
                "builder",
                interactionKind = LinkInteractionKinds.Source,
            ),
        )
        provider.outgoingLinks += link.id
        consumer.incomingLinks += link.id
        val document = ThreadworkDocument(
            id = "project",
            name = "Project",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, provider.id to provider, consumer.id to consumer, link.id to link),
        )

        val capability = assertNotNull(
            defaultCodeIntelligence(document, consumer).symbols.singleOrNull { it.name == "page_source" },
        )
        assertEquals(CompilerCodeSymbolKind.SourceCapability, capability.kind)
        assertEquals(NodeId("capability"), capability.originNodeId)
        assertTrue(capability.members.any { it.name == "page_source.getSource(parameters)" })
        assertTrue(defaultCodeIntelligence(document, provider).symbols.none { it.name == "page_source" })
    }

    private fun connectedDocument(): ThreadworkDocument {
        val rootId = NodeId("root")
        val typeId = NodeId("packet")
        val sourceId = NodeId("source")
        val workerId = NodeId("worker")
        val libraryId = NodeId("library")
        val inputLinkId = NodeId("input-link")
        val outputLinkId = NodeId("output-link")
        val dependencyLinkId = NodeId("dependency-link")

        val root = Node(
            id = rootId,
            name = "Project",
            kind = NodeKind.Processor,
            children = mutableListOf(typeId, sourceId, workerId, libraryId, inputLinkId, outputLinkId, dependencyLinkId),
            technology = TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"),
        )
        val packet = Node(
            id = typeId,
            name = "Packet",
            kind = NodeKind.Type,
            parentId = rootId,
            typeDefinition = TypeDefinition(
                mutableListOf(
                    TypeFieldDefinition("id", "number"),
                    TypeFieldDefinition("active", "boolean"),
                ),
            ),
        )
        val source = Node(
            id = sourceId,
            name = "reader",
            kind = NodeKind.Processor,
            parentId = rootId,
            outgoingLinks = mutableListOf(inputLinkId),
        )
        val worker = Node(
            id = workerId,
            name = "worker",
            kind = NodeKind.Processor,
            parentId = rootId,
            incomingLinks = mutableListOf(inputLinkId, dependencyLinkId),
            outgoingLinks = mutableListOf(outputLinkId),
        )
        val library = Node(
            id = libraryId,
            name = "lib_config",
            kind = NodeKind.Processor,
            parentId = rootId,
            outgoingLinks = mutableListOf(dependencyLinkId),
        )
        val inputLink = Node(
            id = inputLinkId,
            name = "incoming_packet",
            kind = NodeKind.Link,
            parentId = rootId,
            link = LinkData(sourceId, "out", workerId, "in", typeDefinitionId = typeId.value),
        )
        val outputLink = Node(
            id = outputLinkId,
            name = "outgoing_packet",
            kind = NodeKind.Link,
            parentId = rootId,
            link = LinkData(workerId, "out", sourceId, "in", typeDefinitionId = typeId.value),
        )
        val dependencyLink = Node(
            id = dependencyLinkId,
            name = "config_service",
            kind = NodeKind.Link,
            parentId = rootId,
            link = LinkData(libraryId, "service", workerId, "config", transportKind = "dependency"),
        )
        return ThreadworkDocument(
            id = "project",
            name = "Project",
            rootNodeId = rootId,
            nodes = mutableMapOf(
                rootId to root,
                typeId to packet,
                sourceId to source,
                workerId to worker,
                libraryId to library,
                inputLinkId to inputLink,
                outputLinkId to outputLink,
                dependencyLinkId to dependencyLink,
            ),
        )
    }
}
