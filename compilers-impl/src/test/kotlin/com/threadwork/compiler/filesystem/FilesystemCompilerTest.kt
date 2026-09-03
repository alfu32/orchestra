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
                technologyId = "multi-tech",
                compilerId = "multi-tech",
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
        val readme = repository.createNode(rootId, "README.md", NodeKind.Processor)
        repository.updateNodeTechnology(
            readme.id,
            TechnologyMetadata(languageId = "markdown", technologyId = "file-export", fileExtension = "md"),
        )

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
        assertTrue("README.md" in paths)
        assertTrue(project.files.first { it.path == "README.md" }.content.isEmpty())
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
            TechnologyMetadata(languageId = "shellscript", technologyId = "file-export", fileExtension = "sh"),
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

    @Test
    fun `filesystem compiler keeps nested binary export subtrees out of C source`() {
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

        val dlls = repository.createNode(rootId, "dlls", NodeKind.Processor)
        repository.updateNodeTechnology(
            dlls.id,
            TechnologyMetadata(languageId = "", technologyId = "file-export"),
        )
        val platform = repository.createNode(dlls.id, "linux-x64", NodeKind.Processor)
        repository.updateNodeTechnology(
            platform.id,
            TechnologyMetadata(languageId = "", technologyId = "file-export"),
        )
        val binary = repository.createNode(platform.id, "qjs.so", NodeKind.Processor)
        repository.updateNodeTechnology(
            binary.id,
            TechnologyMetadata(
                languageId = "",
                technologyId = "file-export",
                fileExtension = "so",
                contentType = "application/octet-stream",
            ),
        )
        repository.updateNodeBinaryContent(binary.id, byteArrayOf(0x00, 0x01, 0x7f))

        val result = FilesystemCompiler().compile(
            repository.getDocument(),
            CompilerOptions(compilerPlugins = listOf(CCompiler())),
        )

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val files = assertNotNull(result.generatedProject).files.associateBy { it.path }
        assertTrue("ticker.c" in files)
        assertTrue("dlls/linux-x64/qjs.so" in files)
        assertTrue(files.getValue("dlls/linux-x64/qjs.so").binaryContent!!.contentEquals(byteArrayOf(0x00, 0x01, 0x7f)))
        assertFalse(files.getValue("ticker.c").content.contains("run_dlls"))
        assertFalse(files.getValue("ticker.c").content.contains("qjs.so"))
    }

    @Test
    fun `scoped filesystem validation delegates the selected C source`() {
        val repository = InMemoryDocumentRepository(newDocument("sample_c"))
        val rootId = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            rootId,
            TechnologyMetadata(
                languageId = "plain",
                technologyId = "multi-tech",
                compilerId = "multi-tech",
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
        repository.updateNodeText(ticker.id, ticker.text.copy(declaration = "int broken = ;"))

        val result = FilesystemCompiler().compile(
            repository.getDocument(),
            CompilerOptions(
                scopeNodeIds = setOf(ticker.id),
                includeScopeDescendants = false,
                compilerPlugins = listOf(CCompiler()),
            ),
        )

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = assertNotNull(result.generatedProject).files.single { it.path == "ticker.c" }
        assertTrue(generated.content.contains("int broken = ;"))
        assertTrue(generated.sourceMap.entries.any { it.nodeId == ticker.id && it.sourceLine == 1 })
    }

    @Test
    fun `filesystem compiler exposes direct filesystem layout only`() {
        assertTrue(
            FilesystemCompiler().supportedLayoutStrategyIds == setOf(DirectFileSystemHomorphismLayoutStrategy.id),
        )
    }
}
