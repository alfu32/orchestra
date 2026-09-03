package com.threadwork.compiler.api

import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.ThreadworkDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompilerScopeTest {
    @Test
    fun `a validation scope keeps shared ancestors without reintroducing unrelated descendants`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val selectedBranch = Node(NodeId("selected-branch"), "selected-branch", NodeKind.Group, root.id)
        val selected = Node(NodeId("selected"), "selected", NodeKind.Processor, selectedBranch.id)
        val unrelatedBranch = Node(NodeId("unrelated-branch"), "unrelated-branch", NodeKind.Group, root.id)
        val unrelated = Node(NodeId("unrelated"), "unrelated", NodeKind.Processor, unrelatedBranch.id)
        root.children += listOf(selectedBranch.id, unrelatedBranch.id)
        selectedBranch.children += selected.id
        unrelatedBranch.children += unrelated.id
        val document = ThreadworkDocument(
            id = "doc",
            name = "scope",
            rootNodeId = root.id,
            nodes = mutableMapOf(
                root.id to root,
                selectedBranch.id to selectedBranch,
                selected.id to selected,
                unrelatedBranch.id to unrelatedBranch,
                unrelated.id to unrelated,
            ),
        )

        val result = ScopeProbeCompiler.compile(
            document,
            CompilerOptions(
                scopeNodeIds = setOf(selected.id),
                includeScopeDescendants = false,
            ),
        )
        val output = result.generatedProject?.files.orEmpty().joinToString("\n") { it.content }

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        assertTrue(output.contains("selected"))
        assertFalse(output.contains("unrelated"))
    }

    private object ScopeProbeCompiler : StructuredCompiler() {
        override val id: String = "scope-probe"
        override val displayName: String = "Scope probe"

        override fun supports(document: ThreadworkDocument): Boolean = true

        override fun validate(document: ThreadworkDocument) =
            emptyList<com.threadwork.core.diagnostics.Diagnostic>()

        override fun declarationFor(context: NodeCompilerContext): String = context.node.name
    }
}
