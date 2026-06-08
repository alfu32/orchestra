package com.orchestra.compiler.api

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutStrategyTest {
    @Test
    fun `single file layout merges generated files into a single artifact`() {
        val document = InflowDocument(
            id = "doc",
            name = "demo project",
            rootNodeId = NodeId("root"),
            nodes = mutableMapOf(
                NodeId("root") to Node(NodeId("root"), "root", NodeKind.Group),
            ),
        )
        val files = listOf(
            GeneratedFile("src/main/kotlin/demo/Foo.kt", "class Foo", NodeId("foo"), "first"),
            GeneratedFile("src/main/kotlin/demo/Bar.kt", "class Bar", NodeId("bar"), "second"),
        )

        val project = SingleFileLayoutStrategy.layout(document, "demo project", files, CompilerOptions())

        assertEquals(1, project.files.size)
        assertEquals("demo_project.kt", project.files.single().path)
        assertEquals("class Foo\n\nclass Bar", project.files.single().content)
    }

    @Test
    fun `single file layout uses the selected node name when available`() {
        val document = InflowDocument(
            id = "doc",
            name = "demo",
            rootNodeId = NodeId("root"),
            nodes = mutableMapOf(
                NodeId("root") to Node(NodeId("root"), "root", NodeKind.Group),
                NodeId("selected") to Node(NodeId("selected"), "chosen node", NodeKind.Processor),
            ),
        )
        val files = listOf(
            GeneratedFile("src/main/kotlin/demo/Foo.kt", "class Foo", NodeId("foo"), "only"),
        )

        val project = SingleFileLayoutStrategy.layout(
            document,
            "demo",
            files,
            CompilerOptions(scopeNodeIds = setOf(NodeId("selected"))),
        )

        assertEquals(1, project.files.size)
        assertEquals("chosen_node.kt", project.files.single().path)
        assertEquals("class Foo", project.files.single().content)
    }
}
