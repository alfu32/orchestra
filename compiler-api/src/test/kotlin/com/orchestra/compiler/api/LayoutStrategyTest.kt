package com.orchestra.compiler.api

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `node compiler context resolves single file layout from parent when current node is unspecified`() {
        val root = Node(
            id = NodeId("root"),
            name = "root",
            kind = NodeKind.Group,
            fileLayoutStrategyId = SingleFileLayoutStrategy.id,
        )
        val child = Node(
            id = NodeId("child"),
            name = "child",
            kind = NodeKind.Processor,
            parentId = root.id,
            fileLayoutStrategyId = "",
        )
        root.children += child.id
        val document = InflowDocument(
            id = "doc",
            name = "demo",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, child.id to child),
        )
        val context = NodeCompilerContext(
            compiler = EmptyCompiler,
            document = document,
            node = child,
            options = CompilerOptions(),
            projectName = "demo",
            layoutStrategy = ClassifiedFilesystemLayoutStrategy,
            extension = "txt",
            childArtifacts = emptyList(),
            linkArtifacts = emptyList(),
        )

        assertTrue(context.isSingleFileLayout)
        assertEquals(SingleFileLayoutStrategy.id, context.effectiveLayoutStrategy.id)
    }

    private object EmptyCompiler : CompilerPlugin {
        override val id: String = "empty"
        override val displayName: String = "Empty"

        override fun supports(document: InflowDocument): Boolean =
            true

        override fun validate(document: InflowDocument): List<com.orchestra.core.diagnostics.Diagnostic> =
            emptyList()

        override fun compile(document: InflowDocument, options: CompilerOptions): CompilationResult =
            CompilationResult(GeneratedProject("empty", emptyList()), emptyList(), success = true)
    }
}
