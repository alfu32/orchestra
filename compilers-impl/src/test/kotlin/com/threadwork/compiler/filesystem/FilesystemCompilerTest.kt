package com.threadwork.compiler.filesystem

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.c.CCompiler
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilesystemCompilerTest {
    @Test
    fun `filesystem compiler delegates C subtrees and preserves adjacent shell files`() {
        val repository = InMemoryDocumentRepository(newDocument("sample_c"))
        val rootId = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            rootId,
            TechnologyMetadata(
                languageId = "plain",
                technologyId = "generic",
                compilerId = "filesystem",
            ),
        )
        repository.updateNodeFileLayoutStrategy(rootId, DirectFileSystemHomorphismLayoutStrategy.id)

        val ticker = repository.createNode(rootId, "ticker", NodeKind.Processor)
        repository.updateNodeTechnology(
            ticker.id,
            TechnologyMetadata(
                languageId = "c",
                technologyId = "c-native",
                compilerId = "c-compiler",
                fileExtension = "c",
            ),
        )
        repository.updateNodeFileLayoutStrategy(ticker.id, SingleFileLayoutStrategy.id)

        val script = repository.createNode(rootId, "run.sh", NodeKind.Processor)
        repository.updateNodeTechnology(
            script.id,
            TechnologyMetadata(languageId = "shellscript", technologyId = "bash", fileExtension = "sh"),
        )
        repository.updateNodeText(script.id, script.text.copy(declaration = "#!/usr/bin/env sh\n./ticker"))

        val cleanup = repository.createNode(rootId, "cleanup.sh", NodeKind.Processor)
        repository.updateNodeTechnology(
            cleanup.id,
            TechnologyMetadata(languageId = "shellscript", technologyId = "bash", fileExtension = "sh"),
        )
        repository.updateNodeText(cleanup.id, cleanup.text.copy(declaration = "#!/usr/bin/env sh\necho cleanup"))
        repository.addPort(script.id, NodePort("out", "out", PortDirection.Output))
        repository.addPort(cleanup.id, NodePort("in", "in", PortDirection.Input))
        repository.createLink(rootId, "after_run", script.id, "out", cleanup.id, "in")

        val result = FilesystemCompiler().compile(
            repository.getDocument(),
            CompilerOptions(
                compilerPlugins = listOf(CCompiler()),
            ),
        )

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val project = assertNotNull(result.generatedProject)
        val paths = project.files.map { it.path }.toSet()
        assertTrue("ticker.c" in paths)
        assertTrue("run.sh" in paths)
        assertTrue("links.mmd" in paths)
        assertTrue(project.files.first { it.path == "links.mmd" }.content.contains("after_run"))
        assertFalse(paths.any { path -> path.startsWith("sample_c/") })
        assertFalse("sample_c.js" in paths)
        assertFalse("package.json" in paths)
    }

    @Test
    fun `filesystem compiler keeps foreign single files out of a C root compilation`() {
        val repository = InMemoryDocumentRepository(newDocument("ticker"))
        val rootId = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            rootId,
            TechnologyMetadata(
                languageId = "c",
                technologyId = "c-native",
                compilerId = "c-compiler",
                fileExtension = "c",
            ),
        )
        repository.updateNodeFileLayoutStrategy(rootId, SingleFileLayoutStrategy.id)
        val script = repository.createNode(rootId, "run.sh", NodeKind.Processor)
        repository.updateNodeTechnology(
            script.id,
            TechnologyMetadata(languageId = "shellscript", technologyId = "filesystem", fileExtension = "sh"),
        )
        repository.updateNodeText(script.id, script.text.copy(declaration = "#!/usr/bin/env sh\n./ticker"))

        assertTrue(FilesystemCompiler.shouldAggregate(repository.getDocument()))
        val result = FilesystemCompiler().compile(
            repository.getDocument(),
            CompilerOptions(compilerPlugins = listOf(CCompiler())),
        )

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val files = assertNotNull(result.generatedProject).files.associateBy { it.path }
        assertTrue("ticker.c" in files)
        assertTrue(files.getValue("run.sh").content.startsWith("#!/usr/bin/env sh"))
        assertFalse(files.getValue("ticker.c").content.contains("#!/usr/bin/env sh"))
    }
}
