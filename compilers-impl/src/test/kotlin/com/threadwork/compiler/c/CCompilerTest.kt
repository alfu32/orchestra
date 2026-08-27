package com.threadwork.compiler.c

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CCompilerTest {
    @Test
    fun `shared type entities declare typed link buffers`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
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

        val result = CCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val sourceCode = assertNotNull(result.generatedProject).files.single().content
        assertTrue(sourceCode.contains("typedef struct WorkOrder"))
        assertTrue(sourceCode.contains(".element_size = sizeof(WorkOrder)"))
        assertTrue(sourceCode.contains("transport_orders1(&orders1_a_port, &orders1_b_port)"))
    }

    @Test
    fun `single file compiler orders types prototypes definitions and entry point`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val nested = repository.createNode(root, "nested pipeline", NodeKind.Group)
        val producer = repository.createNode(nested.id, "123 producer", NodeKind.Processor)
        val consumer = repository.createNode(nested.id, "switch", NodeKind.Processor)
        repository.addPort(producer.id, NodePort("records-out", "records", PortDirection.Output))
        repository.addPort(consumer.id, NodePort("records-in", "records", PortDirection.Input))
        repository.updateNodeText(
            producer.id,
            producer.text.copy(
                declaration = """
                    WorkOrder order = { 42 };
                    if (threadwork_buffer_push(orders_to_validator, &order, sizeof(order)) != THREADWORK_OK) {
                        return THREADWORK_ERROR;
                    }
                """.trimIndent(),
            ),
        )
        val link = repository.createLink(nested.id, "orders to validator", producer.id, "records", consumer.id, "records")
        link.link!!.typeName = "WorkOrder"
        link.link!!.payloadDefinition = """
            typedef struct WorkOrder {
                int id;
            } WorkOrder;
        """.trimIndent()

        val result = CCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Native C"))

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val file = assertNotNull(result.generatedProject).files.single()
        assertTrue(file.path.endsWith(".c"))
        val source = file.content
        assertEquals(1, source.lines().count { it.trim() == "typedef struct WorkOrder {" })
        assertEquals(1, Regex("\\bint main\\(void\\)").findAll(source).count())
        assertEquals(1, source.lines().count { it.trim() == "typedef struct threadwork_context {" })
        assertTrue(source.indexOf("typedef struct WorkOrder") < source.indexOf("static int tw_init_"))
        val producerPrototype = Regex("static int (tw_init_[A-Za-z0-9_]+)\\(threadwork_context \\*context[^;]*\\);").find(source)
        assertNotNull(producerPrototype)
        val producerDefinition = source.indexOf(
            "static int ${producerPrototype.groupValues[1]}(threadwork_context *context, threadwork_buffer *ordersToValidator)\n{",
        )
        assertTrue(producerDefinition > producerPrototype.range.first)
        assertTrue(source.contains("static threadwork_buffer ordersToValidator1_a_port"))
        assertTrue(source.contains("static threadwork_buffer ordersToValidator1_b_port"))
        assertTrue(source.contains("static int transport_ordersToValidator1("))
        assertTrue(source.contains("transport_ordersToValidator1(&ordersToValidator1_a_port, &ordersToValidator1_b_port)"))
        assertFalse(source.contains("static int tw_run_switch("))
    }

    @Test
    fun `identical wire types are emitted once and conflicting definitions fail`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val firstTarget = repository.createNode(root, "target one", NodeKind.Processor)
        val secondTarget = repository.createNode(root, "target two", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "out", PortDirection.Output))
        repository.addPort(firstTarget.id, NodePort("in", "in", PortDirection.Input))
        repository.addPort(secondTarget.id, NodePort("in", "in", PortDirection.Input))
        val definition = "typedef struct Packet { int value; } Packet;"
        val first = repository.createLink(root, "first", source.id, "out", firstTarget.id, "in")
        first.link!!.typeName = "Packet"
        first.link!!.payloadDefinition = definition
        val second = repository.createLink(root, "second", source.id, "out", secondTarget.id, "in")
        second.link!!.typeName = "Packet"
        second.link!!.payloadDefinition = "  typedef struct Packet {  int value;  } Packet;  "

        val valid = CCompiler().compile(repository.getDocument())

        assertTrue(valid.success, valid.diagnostics.joinToString { it.message })
        assertEquals(1, assertNotNull(valid.generatedProject).files.single().content.lines().count { it.contains("typedef struct Packet") })

        second.link!!.payloadDefinition = "typedef struct Packet { double value; } Packet;"
        val invalid = CCompiler().compile(repository.getDocument())

        assertFalse(invalid.success)
        assertTrue(invalid.diagnostics.any { it.message.contains("conflicting payload definitions") })
    }

    @Test
    fun `file based C layout is rejected`() {
        val repository = cProject()
        repository.requireNode(repository.getDocument().rootNodeId).fileLayoutStrategyId =
            DirectFileSystemHomorphismLayoutStrategy.id

        val result = CCompiler().compile(repository.getDocument())

        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.message.contains("only the single-file layout") })
    }

    @Test
    fun `generated translation unit passes a strict native C17 compiler`() {
        if (!hasNativeCompiler()) return
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val workOrder = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            workOrder.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "number"))),
        )
        val producer = repository.createNode(root, "producer", NodeKind.Processor)
        val consumer = repository.createNode(root, "consumer", NodeKind.Processor)
        repository.addPort(producer.id, NodePort("out", "records", PortDirection.Output))
        repository.addPort(consumer.id, NodePort("in", "records", PortDirection.Input))
        repository.updateNodeText(
            producer.id,
            producer.text.copy(
                declaration = """
                    WorkOrder order = { 42 };
                    if (threadwork_buffer_push(records, &order, sizeof(order)) != THREADWORK_OK) {
                        return THREADWORK_ERROR;
                    }
                """.trimIndent(),
            ),
        )
        val link = repository.createLink(root, "records", producer.id, "records", consumer.id, "records")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = workOrder.id.value))
        val result = CCompiler().compile(repository.getDocument())
        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = assertNotNull(result.generatedProject).files.single().content
        val directory = createTempDirectory("threadwork-c-compiler-")
        try {
            val sourceFile = directory.resolve("generated.c")
            val executable = directory.resolve("generated")
            Files.writeString(sourceFile, source)
            val process = ProcessBuilder(
                "cc",
                "-std=c17",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-pedantic",
                sourceFile.toString(),
                "-o",
                executable.toString(),
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), "$output\n\n$source")
            assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun cProject(): InMemoryDocumentRepository {
        val repository = InMemoryDocumentRepository(newDocument("C Project"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(
                languageId = "c",
                technologyId = "c-native",
                compilerId = "c-compiler",
                fileExtension = "c",
                contentType = "text/x-c",
            ),
        )
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        return repository
    }

    private fun hasNativeCompiler(): Boolean =
        runCatching {
            ProcessBuilder("cc", "--version").start().waitFor() == 0
        }.getOrDefault(false)
}
