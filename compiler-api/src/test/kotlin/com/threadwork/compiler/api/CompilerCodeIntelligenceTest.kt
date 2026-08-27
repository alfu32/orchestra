package com.threadwork.compiler.api

import com.threadwork.core.model.LinkData
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

        val input = assertNotNull(intelligence.symbols.singleOrNull { it.name == "incomingPacket" })
        assertEquals(CompilerCodeSymbolKind.InputBuffer, input.kind)
        assertEquals("Packet", input.typeName)
        assertTrue(input.members.any { it.name == "incomingPacket.push(item)" })
        assertTrue(input.members.any { it.name == "incomingPacket.id" && it.detail == "number" })

        val output = assertNotNull(intelligence.symbols.singleOrNull { it.name == "outgoingPacket" })
        assertEquals(CompilerCodeSymbolKind.OutputBuffer, output.kind)
        assertEquals("Packet", output.typeName)

        val service = assertNotNull(intelligence.symbols.singleOrNull { it.name == "configService" })
        assertEquals(CompilerCodeSymbolKind.ServiceInstance, service.kind)
        assertEquals("lib_config", service.typeName)

        val type = assertNotNull(intelligence.types.singleOrNull { it.name == "Packet" })
        assertEquals("javascript", type.languageId)
        assertEquals(listOf("id", "active"), type.fields.map { it.name })
    }

    @Test
    fun `camel cases local arguments without allocation indices`() {
        assertEquals("recordPipe", compilerArgumentName("record_pipe"))
        assertEquals("orderItems", compilerArgumentName("ORDER_ITEMS"))
        assertEquals("_24HourRecord", compilerArgumentName("24-hour record"))
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
