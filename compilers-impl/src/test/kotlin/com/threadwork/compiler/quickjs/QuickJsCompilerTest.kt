package com.threadwork.compiler.quickjs

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.rootNode
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuickJsCompilerTest {
    @Test
    fun `ships a continuous-run assembly template`() {
        assertNotNull(
            QuickJsCompiler::class.java.getResource(
                "/compiler-templates/quickjs/assembly-single-loop.peb",
            ),
        )
    }

    @Test
    fun `generates an executable QuickJS script without CommonJS or Node APIs`() {
        val repository = InMemoryDocumentRepository(newDocument("QuickJS sample"))
        val root = repository.getDocument().rootNode()
        repository.updateNodeTechnology(
            root.id,
            TechnologyMetadata(languageId = "javascript", technologyId = "quickjs"),
        )
        root.fileLayoutStrategyId = SingleFileLayoutStrategy.id
        val worker = repository.createNode(root.id, "worker", NodeKind.Processor)
        repository.updateNodeText(
            worker.id,
            worker.text.copy(declaration = "console.log('hello');"),
        )

        val result = QuickJsCompiler().compile(
            repository.getDocument(),
            CompilerOptions(projectName = "QuickJS sample"),
        )

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = assertNotNull(result.generatedProject).files.single().content
        assertTrue(source.contains("threadworkRun();"))
        assertFalse(source.contains("require("))
        assertFalse(source.contains("module.exports"))
        assertFalse(source.contains("process.once"))
    }
}
