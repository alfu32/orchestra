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
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
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
        assertTrue(generated.contains("const recordPipe1_a_port = [];"))
        assertTrue(generated.contains("const recordPipe1_b_port = [];"))
        assertTrue(generated.contains("function transport_recordPipe1(a, b)"))
        assertTrue(generated.contains("function reader(context = {}, recordPipe)"))
        assertTrue(generated.contains("function writer(context = {}, recordPipe)"))
        assertTrue(generated.contains("reader(context, recordPipe1_a_port);"))
        assertTrue(generated.contains("writer(context, recordPipe1_b_port);"))
        assertTrue(generated.contains("transport_recordPipe1(recordPipe1_a_port, recordPipe1_b_port);"))
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
        assertTrue(generated.contains("const configService1 = createConfigLibrary();"))
        assertTrue(generated.contains("function worker(context = {}, configService)"))
        assertTrue(generated.contains("worker(context, configService1);"))
        assertFalse(generated.contains("transport_configService"))
    }
}
