package com.threadwork.app.ui

import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.CompilationResult
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.filesystem.FilesystemCompiler
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.VOID_LAYOUT_STRATEGY_ID
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class CompilerCapabilityResolverTest {
    @Test
    fun `resolves layout capabilities from inherited node technology`() {
        val repository = InMemoryDocumentRepository(newDocument("Capability test"))
        val document = repository.getDocument()
        val root = document.rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(languageId = "javascript", technologyId = "nodejs", compilerId = "js"),
        )
        val child = repository.createNode(root, "C child", NodeKind.Processor)
        repository.updateNodeTechnology(child.id, TechnologyMetadata(languageId = "c", technologyId = "c-native"))
        val grandchild = repository.createNode(child.id, "Inherited C child", NodeKind.Processor)

        val resolver = CompilerCapabilityResolver(
            listOf(
                StubCompiler("js", "nodejs", setOf(ClassifiedFilesystemLayoutStrategy.id)),
                StubCompiler("c", "c-native", setOf(SingleFileLayoutStrategy.id)),
            ),
        )

        assertEquals("c", resolver.compilerFor(document, grandchild.id)?.id)
        assertEquals(setOf(SingleFileLayoutStrategy.id), resolver.supportedLayoutStrategyIds(document, grandchild.id))
    }

    @Test
    fun `uses compiler default when plugin does not declare capabilities`() {
        val repository = InMemoryDocumentRepository(newDocument("Legacy compiler"))
        val document = repository.getDocument()
        val compiler = StubCompiler("legacy", "legacy-tech", emptySet())
        repository.updateNodeTechnology(
            document.rootNodeId,
            TechnologyMetadata(languageId = "plain", technologyId = "legacy-tech", compilerId = compiler.id),
        )

        val resolver = CompilerCapabilityResolver(listOf(compiler))

        assertEquals(
            setOf(ClassifiedFilesystemLayoutStrategy.id),
            resolver.supportedLayoutStrategyIds(document, document.rootNodeId),
        )
    }

    @Test
    fun `resolves an unsupported single-file leaf as a file export`() {
        val repository = InMemoryDocumentRepository(newDocument("Mixed project"))
        val document = repository.getDocument()
        repository.updateNodeTechnology(
            document.rootNodeId,
            TechnologyMetadata(languageId = "c", technologyId = "c-native", compilerId = "c"),
        )
        val script = repository.createNode(document.rootNodeId, "run.sh", NodeKind.Processor)
        repository.updateNodeTechnology(script.id, TechnologyMetadata(languageId = "shellscript", technologyId = "file-export"))

        val resolver = CompilerCapabilityResolver(
            listOf(
                StubCompiler("c", "c-native", setOf(SingleFileLayoutStrategy.id)),
                FilesystemCompiler(),
            ),
        )

        assertEquals("multi-tech", resolver.compilerFor(document, script.id)?.id)
        assertEquals(setOf(VOID_LAYOUT_STRATEGY_ID), resolver.supportedLayoutStrategyIds(document, script.id))
    }

    private class StubCompiler(
        override val id: String,
        technologyId: String,
        override val supportedLayoutStrategyIds: Set<String>,
    ) : CompilerPlugin {
        override val displayName: String = id
        override val supportedLanguageIds: Set<String> = setOf("any")
        override val supportedTechnologyIds: Set<String> = setOf(technologyId)
        override val providedTechnologies: List<CompilerTechnology> =
            listOf(CompilerTechnology("any", technologyId))

        override fun supports(document: com.threadwork.core.model.ThreadworkDocument): Boolean = true

        override fun validate(document: com.threadwork.core.model.ThreadworkDocument): List<Diagnostic> = emptyList()

        override fun compile(
            document: com.threadwork.core.model.ThreadworkDocument,
            options: CompilerOptions,
        ): CompilationResult = CompilationResult(null, emptyList(), false)
    }
}
