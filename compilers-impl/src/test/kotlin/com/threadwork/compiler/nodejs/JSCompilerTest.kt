package com.threadwork.compiler.nodejs

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.api.SourceSetLayoutStrategy
import com.threadwork.compiler.generated.nodejs.JSCompiler
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JSCompilerTest {
    @Test
    fun `declared link type is generated and used by transport`() {
        val repository = InMemoryDocumentRepository(newDocument("Typed JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        val type = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "number"))),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "orders", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "orders", PortDirection.Input))
        val link = repository.createLink(root, "orders", source.id, "orders", target.id, "orders")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = type.id.value))

        val result = JSCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = assertNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("@typedef {Object} WorkOrder"))
        assertTrue(generated.contains("const orders1_a_port = [];"))
        assertTrue(generated.contains("transport_orders1(orders1_a_port, orders1_b_port);"))

        val typeInfo = assertNotNull(JSCompiler().typeInformation(repository.getDocument(), source, "WorkOrder"))
        assertTrue(typeInfo.declaration.contains("@typedef {Object} WorkOrder"))
        assertTrue(typeInfo.declaration.contains("@property {number} id"))
    }

    @Test
    fun `selected endpoints include their referenced sibling type`() {
        val repository = InMemoryDocumentRepository(newDocument("Scoped Typed JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        val type = repository.createNode(root, "Envelope", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("payload", "string"))),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "packet", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "packet", PortDirection.Input))
        val link = repository.createLink(root, "packet", source.id, "packet", target.id, "packet")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = type.id.value))

        val result = JSCompiler().compile(
            repository.getDocument(),
            CompilerOptions(scopeNodeIds = setOf(source.id, target.id)),
        )

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = assertNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("@typedef {Object} Envelope"))
        assertTrue(generated.contains("function transport_packet1(a, b)"))
    }

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
    fun `resource templates generate named double buffered transport`() {
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
        val generated = assertNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("const record_pipe1_a_port = [];"))
        assertTrue(generated.contains("const record_pipe1_b_port = [];"))
        assertTrue(generated.contains("function transport_record_pipe1(a, b)"))
        assertTrue(generated.contains("function reader(context = {}, record_pipe)"))
        assertTrue(generated.contains("function writer(context = {}, record_pipe)"))
        assertTrue(generated.contains("init_reader_") && generated.contains("run_reader_"))
        assertTrue(generated.contains("init_writer_") && generated.contains("run_writer_"))
        assertTrue(generated.contains("transport_record_pipe1(record_pipe1_a_port, record_pipe1_b_port);"))
        assertTrue(generated.contains("threadworkRecordTransit()"))
        assertTrue(generated.contains("if (!threadworkIsRunning()) return;"))
        assertTrue(generated.contains("threadworkNetworkHasRecentTransit()"))
    }

    @Test
    fun `dependency injection declares an indexed instance and passes a local service argument`() {
        val repository = InMemoryDocumentRepository(newDocument("Dependency JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        val library = repository.createNode(root, "lib_config", NodeKind.Processor)
        repository.updateNodeText(
            library.id,
            repository.requireNode(library.id).text.copy(instantiation = "createConfigLibrary()"),
        )
        val worker = repository.createNode(root, "worker", NodeKind.Processor)
        repository.addPort(library.id, NodePort("service", "service", PortDirection.Output))
        repository.addPort(worker.id, NodePort("config", "config", PortDirection.Input))
        val link = repository.createLink(root, "config_service", library.id, "service", worker.id, "config")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(transportKind = "dependency"))

        val result = JSCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = assertNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("const config_service1 = createConfigLibrary();"))
        assertTrue(generated.contains("function worker(context = {}, config_service)"))
        assertTrue(generated.contains("init_worker_") && generated.contains("run_worker_"))
        assertFalse(generated.contains("transport_config_service"))
    }

    @Test
    fun `source and runnable capabilities compile as synchronous facades without transport buffers`() {
        val repository = InMemoryDocumentRepository(newDocument("Capability JS"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        val page = repository.createNode(root, "page", NodeKind.Processor)
        repository.updateNodeText(
            page.id,
            repository.requireNode(page.id).text.copy(declaration = "return 'Hello ${'$'}{name}';"),
        )
        val server = repository.createNode(root, "server", NodeKind.Processor)
        repository.addPort(page.id, NodePort("src", "src", PortDirection.Output))
        repository.addPort(page.id, NodePort("run", "run", PortDirection.Output))
        repository.addPort(server.id, NodePort("source", "source", PortDirection.Input))
        repository.addPort(server.id, NodePort("runnable", "runnable", PortDirection.Input))
        repository.updateNodeText(
            server.id,
            repository.requireNode(server.id).text.copy(
                declaration = """
                    const generatedSource = page_source.getSource({ name: "World" });
                    if (!generatedSource.includes("Hello World")) {
                      throw new Error("Source capability did not interpolate the compiled product.");
                    }
                    const generatedPage = page_runnable.getRunnable({ name: "World" });
                    if (generatedPage() !== "Hello World") {
                      throw new Error("Runnable capability did not return the compiled provider.");
                    }
                """.trimIndent(),
            ),
        )
        val source = repository.createLink(root, "page_source", page.id, "src", server.id, "source")
        repository.updateLinkData(
            source.id,
            requireNotNull(source.link).copy(interactionKind = LinkInteractionKinds.Source),
        )
        val runnable = repository.createLink(root, "page_runnable", page.id, "run", server.id, "runnable")
        repository.updateLinkData(
            runnable.id,
            requireNotNull(runnable.link).copy(interactionKind = LinkInteractionKinds.Runnable),
        )

        val result = JSCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val project = assertNotNull(result.generatedProject)
        val generated = project.files.joinToString("\n") { it.content }
        assertTrue(generated.contains("getSource(parameters = {})"))
        assertTrue(generated.contains("getRunnable(parameters = {})"))
        assertTrue(generated.contains("function page(context = {})"))
        assertTrue(generated.contains("new Function("))
        assertTrue(generated.contains("function server(context = {}, page_source, page_runnable)"))
        assertTrue(generated.contains("init_server_") && generated.contains("run_server_"))
        assertFalse(generated.contains("page_source1_a_port"))
        assertFalse(generated.contains("transport_page_source"))

        val scopedResult = JSCompiler().compile(
            repository.getDocument(),
            CompilerOptions(scopeNodeIds = setOf(server.id)),
        )
        assertTrue(scopedResult.success, scopedResult.diagnostics.joinToString { it.message })
        val scopedSource = assertNotNull(scopedResult.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(scopedSource.contains("function page(context = {})"))
        assertTrue(scopedSource.contains("getSource(parameters = {})"))
        assertTrue(scopedSource.contains("function server(context = {}, page_source, page_runnable)"))

        val nodeExecutable = System.getenv("PATH")
            ?.split(System.getProperty("path.separator"))
            ?.map { java.nio.file.Path.of(it).resolve("node") }
            ?.firstOrNull(Files::isExecutable)
            ?: return
        val outputDirectory = Files.createTempDirectory("threadwork-capability-js")
        try {
            project.writeTo(outputDirectory)
            val rootFile = project.files.single { it.originNodeId == root }
            val process = ProcessBuilder(nodeExecutable.toString(), outputDirectory.resolve(rootFile.path).toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), processOutput)
        } finally {
            outputDirectory.toFile().deleteRecursively()
        }
    }
}
