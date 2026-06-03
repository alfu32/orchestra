package com.orchestra.core.classification

import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.LinkData
import com.orchestra.core.model.InflowDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeClassifierTest {
    @Test
    fun `classifies composite names`() {
        val node = Node(NodeId("n1"), "payment error", NodeKind.Group)
        node.children += NodeId("child")

        assertEquals(NodeStereotype.CompositeErrorHandler, NodeClassifier.classify(node))
    }

    @Test
    fun `classifies terminal topology`() {
        val node = Node(NodeId("n1"), "worker", NodeKind.Processor)
        node.incomingLinks += NodeId("in")
        node.outgoingLinks += NodeId("out")

        assertEquals(NodeStereotype.Transformer, NodeClassifier.classify(node))
    }

    @Test
    fun `classifies library naming convention`() {
        val node = Node(NodeId("n1"), "billing_lib", NodeKind.Processor)

        assertEquals(NodeStereotype.ServiceLibrary, NodeClassifier.classify(node))
    }

    @Test
    fun `classifies generic processing unit`() {
        val node = Node(NodeId("n1"), "worker", NodeKind.Processor)

        assertEquals(NodeStereotype.ProcessingUnit, NodeClassifier.classify(node))
    }

    @Test
    fun `classifies compiler template names`() {
        val node = Node(NodeId("n1"), "@transport", NodeKind.Processor)

        assertEquals(NodeStereotype.CompilerTemplate, NodeClassifier.classify(node))
    }

    @Test
    fun `classifies static file template name`() {
        val node = Node(NodeId("n1"), "@StaticFile", NodeKind.Processor)

        assertEquals(NodeStereotype.StaticFile, NodeClassifier.classify(node))
    }

    @Test
    fun `ignores inbound library dependencies when classifying data inputs`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val library = Node(NodeId("lib"), "lib_io_file", NodeKind.Processor)
        val worker = Node(NodeId("worker"), "read_config", NodeKind.Processor)
        val next = Node(NodeId("next"), "process_data", NodeKind.Processor)
        val dependency = Node(
            id = NodeId("dep"),
            name = "lib_io_file",
            kind = NodeKind.Link,
            link = LinkData(library.id, "out", worker.id, "in", transportKind = "usage"),
        )
        val output = Node(
            id = NodeId("out"),
            name = "items",
            kind = NodeKind.Link,
            link = LinkData(worker.id, "out", next.id, "in"),
        )
        library.outgoingLinks += dependency.id
        worker.incomingLinks += dependency.id
        worker.outgoingLinks += output.id
        next.incomingLinks += output.id
        val document = InflowDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(
                root.id to root,
                library.id to library,
                worker.id to worker,
                next.id to next,
                dependency.id to dependency,
                output.id to output,
            ),
        )

        assertEquals(NodeStereotype.Generator, NodeClassifier.classify(document, worker))
    }

    @Test
    fun `library dependency alone does not make a node a sink`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val library = Node(NodeId("lib"), "lib_logging", NodeKind.Processor)
        val worker = Node(NodeId("worker"), "write_log", NodeKind.Processor)
        val dependency = Node(
            id = NodeId("dep"),
            name = "lib_logging usage",
            kind = NodeKind.Link,
            link = LinkData(library.id, "out", worker.id, "in", transportKind = "usage"),
        )
        library.outgoingLinks += dependency.id
        worker.incomingLinks += dependency.id
        val document = InflowDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, library.id to library, worker.id to worker, dependency.id to dependency),
        )

        assertEquals(NodeStereotype.ProcessingUnit, NodeClassifier.classify(document, worker))
    }

    @Test
    fun `link kind takes precedence over name`() {
        val node = Node(
            id = NodeId("l1"),
            name = "@transport",
            kind = NodeKind.Link,
            link = LinkData(NodeId("source"), "out", NodeId("target"), "in"),
        )

        assertEquals(NodeStereotype.Link, NodeClassifier.classify(node))
    }

    @Test
    fun `classifies library links as usage imports`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val library = Node(NodeId("lib"), "lib_logging", NodeKind.Processor)
        val worker = Node(NodeId("worker"), "error_log", NodeKind.Processor)
        val link = Node(
            id = NodeId("l1"),
            name = "lib_logging usage",
            kind = NodeKind.Link,
            link = LinkData(library.id, "out", worker.id, "in"),
        )
        val document = InflowDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, library.id to library, worker.id to worker, link.id to link),
        )

        assertEquals(LinkStereotype.UsageImport, LinkClassifier.classify(document, link))
    }

    @Test
    fun `classifies error target links as error pipes`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val worker = Node(NodeId("worker"), "worker", NodeKind.Processor)
        val errorHandler = Node(NodeId("error"), "error_log", NodeKind.Processor)
        val link = Node(
            id = NodeId("l1"),
            name = "any_error",
            kind = NodeKind.Link,
            link = LinkData(worker.id, "error", errorHandler.id, "in"),
        )
        val document = InflowDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, worker.id to worker, errorHandler.id to errorHandler, link.id to link),
        )

        assertEquals(LinkStereotype.ErrorPipe, LinkClassifier.classify(document, link))
    }
}
