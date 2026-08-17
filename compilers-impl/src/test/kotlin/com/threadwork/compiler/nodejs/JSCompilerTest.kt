package com.threadwork.compiler.nodejs

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.api.SourceSetLayoutStrategy
import com.threadwork.compiler.generated.nodejs.JSCompiler
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

class JSCompilerTest {
    @Test
    fun `compiler facade provides every layout variant`() {
        assertTrue(
            JSCompiler().supportedLayoutStrategyIds.containsAll(
                setOf(
                    SingleFileLayoutStrategy.id,
                    DirectFileSystemHomorphismLayoutStrategy.id,
                    ClassifiedFilesystemLayoutStrategy.id,
                    SourceSetLayoutStrategy.id,
                ),
            ),
        )
    }

    @Test
    fun `single file layout adds every function to module exports`() {
        val repository = InMemoryDocumentRepository(newDocument("Single File JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.createNode(root, "read_data", NodeKind.Processor)
        repository.createNode(root, "write_data", NodeKind.Processor)

        val result = JSCompiler().compile(
            repository.getDocument(),
            CompilerOptions(projectName = "Single File JS"),
        )

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        val source = project.files
            .filter { it.path.endsWith(".js") }
            .joinToString("\n") { it.content }
        assertTrue(source.contains("module.exports.read_data = read_data;"))
        assertTrue(source.contains("module.exports.write_data = write_data;"))
        assertTrue(source.contains("module.exports.Single_File_JS = Single_File_JS;"))
        assertFalse(source.contains("module.exports = {"))
    }

    @Test
    fun `multi file layout also adds its function to module exports`() {
        val repository = InMemoryDocumentRepository(newDocument("Direct JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        repository.createNode(root, "read_data", NodeKind.Processor)

        val result = JSCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Direct JS"))

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.any { it.content.contains("module.exports.read_data = read_data;") })
        assertFalse(project.files.any { it.content.contains("module.exports = {") })
    }

    @Test
    fun `single file parent imports child that overrides to direct layout`() {
        val repository = InMemoryDocumentRepository(newDocument("Mixed JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.createNode(root, "inline_child", NodeKind.Processor)
        val externalChild = repository.createNode(root, "external_child", NodeKind.Processor)
        externalChild.fileLayoutStrategyId = DirectFileSystemHomorphismLayoutStrategy.id

        val result = JSCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Mixed JS"))

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        val rootSource = project.files.single { it.originNodeId == root }.content
        assertTrue(rootSource.contains("function inline_child"))
        assertTrue(rootSource.contains("require(\"./Mixed_JS/external_child\")"))
        assertFalse(rootSource.contains("function external_child"))
        assertTrue(project.files.any { it.originNodeId == externalChild.id && it.content.contains("function external_child") })
    }

    @Test
    fun `resource templates preserve link identity and qualified endpoints`() {
        val repository = InMemoryDocumentRepository(newDocument("Template JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        val source = repository.createNode(root, "reader", NodeKind.Processor)
        val target = repository.createNode(root, "writer", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "records", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "records", PortDirection.Input))
        repository.createLink(root, "record_pipe", source.id, "records", target.id, "records")

        val result = JSCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        assertTrue(
            assertNotNull(result.generatedProject).files.any {
                it.content.contains("transport(context, \"record_pipe\", \"reader.records\", \"writer.records\");")
            },
        )
    }
}
